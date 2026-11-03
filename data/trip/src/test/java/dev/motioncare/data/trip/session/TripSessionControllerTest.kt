package dev.motioncare.data.trip.session

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.*
import dev.motioncare.data.trip.sample.SampleVehicleDataSource
import dev.motioncare.data.trip.vehicle.UnavailableVehicleDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripSessionControllerTest {
    private class MemoryStore : TripReader, TripWriter {
        private val snapshots = linkedMapOf<TripId, TripSnapshot>()
        override val records = MutableStateFlow<List<TripRecord>>(emptyList())
        var failWrites = false
        var appendCalls = 0
        override suspend fun get(id: TripId) = snapshots[id]
        override suspend fun lastSampleElapsedMs(id: TripId) = snapshots[id]?.samples?.lastOrNull()?.elapsedMs
        override suspend fun saveTrip(trip: TripRecord) {
            check(!failWrites) { "Storage failed" }
            snapshots[trip.id] = TripSnapshot(trip, snapshots[trip.id]?.samples.orEmpty())
            publish()
        }
        override suspend fun appendSamples(trip: TripRecord, samples: List<SpeedSample>) {
            check(!failWrites) { "Storage failed" }
            appendCalls++
            snapshots[trip.id] = TripSnapshot(trip, snapshots[trip.id]?.samples.orEmpty() + samples)
            publish()
        }
        private fun publish() { records.value = snapshots.values.map { it.trip } }
    }
    private val store = MemoryStore()
    private var now = 1_000L
    private var nanos = 5_000_000_000L
    private var sequence = 0
    private fun controller(source: VehicleDataSource = SampleVehicleDataSource()) =
        TripSessionController(store, store, source, EpochClock { now },
            TripIdFactory { TripId("trip-${++sequence}") }, MonotonicClock { nanos })

    /** A reading observed the given time after the trip started. */
    private fun reading(afterStartMs: Long, value: Double? = 5.0, quality: Quality = Quality.VALID) =
        SpeedReading(nanos + afterStartMs * 1_000_000, value, quality)

    private suspend fun TestScope.startRecording(controller: TripSessionController): RecordingFixture {
        controller.initialize()
        val id = (controller.start() as CommandResult.Success).tripId
        val readings = MutableSharedFlow<SpeedReading>(extraBufferCapacity = 64)
        val job = launch { controller.record(readings) }
        runCurrent()
        return RecordingFixture(id, readings, job)
    }
    private class RecordingFixture(
        val id: TripId,
        val readings: MutableSharedFlow<SpeedReading>,
        val job: kotlinx.coroutines.Job,
    )

    @Test fun `recording captures ordered samples and preserves origin on completion`() = runTest {
        val controller = controller()
        controller.initialize()
        val id = (controller.start() as CommandResult.Success).tripId
        now += 100
        controller.captureSample()
        now += 100
        controller.captureSample()
        controller.finish()
        val saved = requireNotNull(store.get(id))
        assertEquals(DataOrigin.SYNTHETIC, saved.trip.origin)
        assertEquals(TripStatus.COMPLETED, saved.trip.status)
        assertEquals(listOf(100L, 200L), saved.samples.map { it.elapsedMs })
        assertEquals(3L, saved.trip.revision)
    }

    @Test fun `duplicate start and end do not create duplicate records`() = runTest {
        val controller = controller()
        controller.initialize()
        controller.start()
        assertTrue(controller.start() is CommandResult.Rejected)
        controller.finish()
        assertTrue(controller.finish() is CommandResult.Rejected)
        assertEquals(1, store.records.value.size)
    }

    @Test fun `initializing the same graph does not interrupt an active trip`() = runTest {
        val controller = controller()
        controller.initialize()
        controller.start()
        controller.initialize()
        assertEquals(TripStatus.RECORDING, store.records.value.single().status)
    }

    @Test fun `a new process marks an unfinished recording interrupted without fabricating samples`() = runTest {
        val controller = controller()
        controller.initialize()
        val id = (controller.start() as CommandResult.Success).tripId
        now += 50
        controller.captureSample()
        now += 500
        controller().initialize()
        val saved = requireNotNull(store.get(id))
        assertEquals(TripStatus.INTERRUPTED, saved.trip.status)
        assertEquals(1, saved.samples.size)
        assertEquals(now, saved.trip.endedAtEpochMs)
    }

    @Test fun `backward or duplicate timestamps do not corrupt saved samples`() = runTest {
        val controller = controller()
        controller.initialize()
        val id = (controller.start() as CommandResult.Success).tripId
        now += 100
        controller.captureSample()
        assertTrue(controller.captureSample() is CommandResult.Rejected)
        now -= 200
        assertTrue(controller.captureSample() is CommandResult.Rejected)
        assertEquals(1, store.get(id)!!.samples.size)
    }

    @Test fun `finish time cannot precede the last saved observation after a clock change`() = runTest {
        val controller = controller()
        controller.initialize()
        val id = (controller.start() as CommandResult.Success).tripId
        now += 500
        controller.captureSample()
        now -= 400
        controller.finish()
        assertEquals(1500L, store.get(id)!!.trip.endedAtEpochMs)
    }

    @Test fun `failed storage permits retry and does not publish completion`() = runTest {
        val controller = controller()
        controller.initialize()
        controller.start()
        store.failWrites = true
        try { controller.finish(); fail("Expected write failure") } catch (_: IllegalStateException) { }
        assertEquals(TripStatus.RECORDING, store.records.value.single().status)
        store.failWrites = false
        assertTrue(controller.finish() is CommandResult.Success)
    }

    @Test fun `unavailable real adapter never supplies synthetic data`() = runTest {
        val controller = controller(UnavailableVehicleDataSource())
        controller.initialize()
        val id = (controller.start() as CommandResult.Success).tripId
        try { controller.captureSample(); fail("Expected unsupported source") }
        catch (_: UnsupportedOperationException) { }
        assertEquals(DataOrigin.VEHICLE, store.get(id)!!.trip.origin)
        assertTrue(store.get(id)!!.samples.isEmpty())
    }

    @Test fun `vehicle readings use event time and finish saves what is still pending`() = runTest {
        val controller = controller()
        val trip = startRecording(controller)
        trip.readings.emit(reading(100))
        trip.readings.emit(reading(200))
        runCurrent()
        assertTrue(store.get(trip.id)!!.samples.isEmpty())
        // Later wall-clock time must not move the observed time of pending readings.
        now += 60_000
        controller.finish()
        runCurrent()
        val saved = store.get(trip.id)!!
        assertEquals(listOf(100L, 200L), saved.samples.map { it.elapsedMs })
        assertEquals(TripStatus.COMPLETED, saved.trip.status)
        assertTrue(trip.job.isCompleted)
    }

    @Test fun `readings are written together about once a second`() = runTest {
        val controller = controller()
        val trip = startRecording(controller)
        for (ms in 100L..1000L step 100) trip.readings.emit(reading(ms))
        runCurrent()
        assertEquals(1, store.appendCalls)
        assertEquals(10, store.get(trip.id)!!.samples.size)
        assertEquals(1L, store.get(trip.id)!!.trip.revision)
        trip.job.cancel()
    }

    @Test fun `duplicate backward and pre-start readings are dropped`() = runTest {
        val controller = controller()
        val trip = startRecording(controller)
        trip.readings.emit(reading(-5))
        trip.readings.emit(reading(100))
        trip.readings.emit(reading(100))
        trip.readings.emit(reading(50))
        trip.readings.emit(reading(200))
        runCurrent()
        controller.finish()
        runCurrent()
        assertEquals(listOf(100L, 200L), store.get(trip.id)!!.samples.map { it.elapsedMs })
    }

    @Test fun `unavailable reading is stored as unavailable rather than zero`() = runTest {
        val controller = controller()
        val trip = startRecording(controller)
        trip.readings.emit(reading(100, value = null, quality = Quality.UNAVAILABLE))
        runCurrent()
        controller.finish()
        runCurrent()
        assertEquals(listOf(SpeedSample(100, null, Quality.UNAVAILABLE)), store.get(trip.id)!!.samples)
    }

    @Test fun `failed write keeps readings for the next write`() = runTest {
        val controller = controller()
        val trip = startRecording(controller)
        store.failWrites = true
        trip.readings.emit(reading(1000))
        runCurrent()
        assertTrue(trip.job.isActive)
        assertTrue(store.get(trip.id)!!.samples.isEmpty())
        store.failWrites = false
        trip.readings.emit(reading(2000))
        runCurrent()
        assertEquals(listOf(1000L, 2000L), store.get(trip.id)!!.samples.map { it.elapsedMs })
        trip.job.cancel()
    }

    @Test fun `recording without an active trip ends immediately`() = runTest {
        val controller = controller()
        controller.initialize()
        val job = launch { controller.record(MutableSharedFlow()) }
        runCurrent()
        assertTrue(job.isCompleted)
    }
}
