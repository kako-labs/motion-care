package dev.motioncare.data.trip.session

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.GuidancePresenter
import dev.motioncare.data.trip.internal.*
import dev.motioncare.data.trip.sample.SampleVehicleDataSource
import dev.motioncare.data.trip.vehicle.UnavailableVehicleDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
            val saved = snapshots[trip.id]
            snapshots[trip.id] = TripSnapshot(trip, saved?.samples.orEmpty(), saved?.observations.orEmpty(), saved?.steering.orEmpty(), saved?.guidance.orEmpty())
            publish()
        }
        override suspend fun appendSamples(
            trip: TripRecord,
            samples: List<SpeedSample>,
            observations: List<Observation>,
            steering: List<SteeringSample>,
            guidance: List<GuidanceEvent>,
        ) {
            check(!failWrites) { "Storage failed" }
            appendCalls++
            val saved = snapshots[trip.id]
            snapshots[trip.id] = TripSnapshot(trip, saved?.samples.orEmpty() + samples,
                saved?.observations.orEmpty() + observations, saved?.steering.orEmpty() + steering, saved?.guidance.orEmpty() + guidance)
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

    @Test fun `patterns found in vehicle readings are saved with the trip`() = runTest {
        val controller = controller()
        val trip = startRecording(controller)
        for (ms in 0L..2000L step 200) trip.readings.emit(reading(ms, value = 0.0))
        // About 3 m/s2 from a stop.
        for (ms in 2200L..5000L step 200) trip.readings.emit(reading(ms, value = (ms - 2000) * 0.003))
        runCurrent()
        controller.finish()
        runCurrent()
        assertEquals(listOf(DrivingPattern.RAPID_START), store.get(trip.id)!!.observations.map { it.pattern })
    }

    @Test fun `a pattern still open at the finish is saved`() = runTest {
        val controller = controller()
        val trip = startRecording(controller)
        for (ms in 0L..1000L step 200) trip.readings.emit(reading(ms, value = 0.0))
        for (ms in 1200L..2400L step 200) trip.readings.emit(reading(ms, value = (ms - 1000) * 0.003))
        runCurrent()
        assertTrue(store.get(trip.id)!!.observations.isEmpty())
        controller.finish()
        runCurrent()
        assertEquals(listOf(DrivingPattern.RAPID_START), store.get(trip.id)!!.observations.map { it.pattern })
    }

    @Test fun `a new trip does not inherit detection state from the last one`() = runTest {
        val controller = controller()
        val first = startRecording(controller)
        for (ms in 0L..1000L step 200) first.readings.emit(reading(ms, value = 0.0))
        runCurrent()
        controller.finish()
        runCurrent()
        val second = startRecording(controller)
        // Already moving when recording starts, so this is not a start from a stop.
        for (ms in 0L..3000L step 200) second.readings.emit(reading(ms, value = 15.0))
        runCurrent()
        controller.finish()
        runCurrent()
        assertTrue(store.get(second.id)!!.observations.isEmpty())
    }

    private fun steeringAt(afterStartMs: Long, degrees: Double) = SteeringReading(nanos + afterStartMs * 1_000_000, degrees)

    private suspend fun TestScope.startRecordingWithSteering(
        controller: TripSessionController,
        steering: kotlinx.coroutines.flow.Flow<SteeringReading>,
    ): RecordingFixture {
        controller.initialize()
        val id = (controller.start() as CommandResult.Success).tripId
        val readings = MutableSharedFlow<SpeedReading>(extraBufferCapacity = 64)
        val job = launch { controller.record(readings, steering) }
        runCurrent()
        return RecordingFixture(id, readings, job)
    }

    @Test fun `steering is saved and a fast turn is found from steering and speed`() = runTest {
        val controller = controller()
        val steering = MutableSharedFlow<SteeringReading>(extraBufferCapacity = 64)
        val trip = startRecordingWithSteering(controller, steering)
        steering.emit(steeringAt(100, 0.0))
        runCurrent()
        for (ms in 200L..2000L step 200) trip.readings.emit(reading(ms, value = 11.1))
        runCurrent()
        steering.emit(steeringAt(2100, 60.0))
        runCurrent()
        for (ms in 2200L..6000L step 200) trip.readings.emit(reading(ms, value = 11.1))
        runCurrent()
        controller.finish()
        runCurrent()
        val saved = store.get(trip.id)!!
        assertEquals(listOf(SteeringSample(100, 0.0), SteeringSample(2100, 60.0)), saved.steering)
        assertEquals(listOf(DrivingPattern.FAST_TURN), saved.observations.map { it.pattern })
    }

    @Test fun `without steering no left or right pattern is reported`() = runTest {
        val controller = controller()
        val trip = startRecording(controller)
        for (ms in 200L..6000L step 200) trip.readings.emit(reading(ms, value = 11.1))
        runCurrent()
        controller.finish()
        runCurrent()
        val saved = store.get(trip.id)!!
        assertTrue(saved.steering.isEmpty())
        assertTrue(saved.observations.isEmpty())
    }

    @Test fun `steering failure keeps speed recording and stops using the old angle`() = runTest {
        val controller = controller()
        val trip = startRecordingWithSteering(controller, kotlinx.coroutines.flow.flow {
            emit(steeringAt(100, 60.0))
            throw IllegalStateException("steering service died")
        })
        for (ms in 200L..6000L step 200) trip.readings.emit(reading(ms, value = 11.1))
        runCurrent()
        assertTrue(trip.job.isActive)
        controller.finish()
        runCurrent()
        val saved = store.get(trip.id)!!
        assertEquals(30, saved.samples.size)
        assertTrue(saved.observations.none { it.pattern == DrivingPattern.FAST_TURN })
        assertEquals(listOf(SteeringSample(100, 60.0)), saved.steering)
    }

    @Test fun `steering events at the same time do not block saving`() = runTest {
        val controller = controller()
        val steering = MutableSharedFlow<SteeringReading>(extraBufferCapacity = 64)
        val trip = startRecordingWithSteering(controller, steering)
        steering.emit(steeringAt(100, 1.0))
        steering.emit(steeringAt(100, 2.0))
        steering.emit(steeringAt(90, 3.0))
        runCurrent()
        trip.readings.emit(reading(1000))
        runCurrent()
        controller.finish()
        runCurrent()
        assertEquals(listOf(SteeringSample(100, 1.0)), store.get(trip.id)!!.steering)
        assertEquals(1, store.get(trip.id)!!.samples.size)
    }

    @Test fun `a new trip does not inherit the steering angle`() = runTest {
        val controller = controller()
        val steering = MutableSharedFlow<SteeringReading>(extraBufferCapacity = 64)
        val first = startRecordingWithSteering(controller, steering)
        steering.emit(steeringAt(100, 60.0))
        runCurrent()
        controller.finish()
        runCurrent()
        first.job.join()
        val second = startRecording(controller)
        for (ms in 200L..6000L step 200) second.readings.emit(reading(ms, value = 11.1))
        runCurrent()
        controller.finish()
        runCurrent()
        assertTrue(store.get(second.id)!!.observations.isEmpty())
    }

    private suspend fun TestScope.startRecordingWithGuidance(
        controller: TripSessionController,
        enabled: () -> Boolean = { true },
        environment: Flow<EnvironmentSnapshot> = emptyFlow(),
        show: (GuidanceTopic) -> Boolean,
    ): RecordingFixture {
        controller.initialize()
        val id = (controller.start() as CommandResult.Success).tripId
        val readings = MutableSharedFlow<SpeedReading>(extraBufferCapacity = 256)
        val job = launch { controller.record(readings, emptyFlow(), GuidancePresenter(show), enabled, environment) }
        runCurrent()
        return RecordingFixture(id, readings, job)
    }

    /** Outdoor air graded bad while the car takes air from outside. */
    private fun badAirWithOutsideIntake(): EnvironmentSnapshot {
        fun <T : Any> unsupported() = EnvironmentReading<T>(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE)
        return EnvironmentSnapshot(
            capturedAtEpochMs = now,
            outsideTemperature = unsupported(),
            cabinTemperature = unsupported(),
            cabinSetTemperature = unsupported(),
            recirculation = EnvironmentReading(false, Availability.AVAILABLE, EnvironmentSource.VEHICLE, observedAtEpochMs = now),
            airCleaner = unsupported(),
            outdoorAir = EnvironmentReading(OutdoorAir(95.0, 40.0, AirGrade.BAD, 37.6, 127.0), Availability.AVAILABLE,
                EnvironmentSource.OPEN_METEO_CAMS, observedAtEpochMs = now),
        )
    }

    /** Speeds up and slows down at about 1.3 m/s2 long enough to repeat the pattern three times. */
    private suspend fun RecordingFixture.driveRepeatedChanges() {
        var ms = 0L
        var speed = 10.0
        for (phase in 0 until 11) {
            val step = if (phase % 2 == 0) 0.26 else -0.26
            repeat(15) {
                ms += 200
                speed += step
                readings.emit(reading(ms, value = speed))
            }
        }
        // Steady driving gives the card a calm moment.
        repeat(30) {
            ms += 200
            readings.emit(reading(ms, value = speed))
        }
    }

    @Test fun `a repeated pattern shows a card and saves the result with the trip`() = runTest {
        val controller = controller()
        val cards = mutableListOf<GuidanceTopic>()
        val trip = startRecordingWithGuidance(controller) { cards += it; true }
        trip.driveRepeatedChanges()
        runCurrent()
        controller.finish()
        runCurrent()
        assertEquals(listOf(GuidanceTopic.Pattern(DrivingPattern.REPEATED_SPEED_CHANGE)), cards)
        val saved = store.get(trip.id)!!
        assertEquals(listOf(GuidanceOutcome.SHOWN), saved.guidance.map { it.outcome })
        assertTrue(saved.observations.any { it.pattern == DrivingPattern.REPEATED_SPEED_CHANGE })
    }

    @Test fun `guidance turned off keeps recording and saves why no card appeared`() = runTest {
        val controller = controller()
        val cards = mutableListOf<GuidanceTopic>()
        val trip = startRecordingWithGuidance(controller, enabled = { false }) { cards += it; true }
        trip.driveRepeatedChanges()
        runCurrent()
        controller.finish()
        runCurrent()
        assertTrue(cards.isEmpty())
        val saved = store.get(trip.id)!!
        assertEquals(setOf(GuidanceOutcome.SKIPPED_OFF), saved.guidance.map { it.outcome }.toSet())
        assertFalse(saved.samples.isEmpty())
    }

    @Test fun `a failing card does not stop recording`() = runTest {
        val controller = controller()
        val trip = startRecordingWithGuidance(controller) { error("notification service died") }
        trip.driveRepeatedChanges()
        runCurrent()
        assertTrue(trip.job.isActive)
        controller.finish()
        runCurrent()
        val saved = store.get(trip.id)!!
        assertEquals(listOf(GuidanceOutcome.FAILED), saved.guidance.map { it.outcome })
        assertFalse(saved.samples.isEmpty())
    }

    @Test fun `cabin air advice from an environment reading shows a card and is saved`() = runTest {
        val controller = controller()
        val cards = mutableListOf<GuidanceTopic>()
        val environment = MutableSharedFlow<EnvironmentSnapshot>(extraBufferCapacity = 8)
        val trip = startRecordingWithGuidance(controller, environment = environment) { cards += it; true }
        environment.emit(badAirWithOutsideIntake())
        runCurrent()
        for (ms in 200L..4000L step 200) trip.readings.emit(reading(ms, value = 10.0))
        runCurrent()
        controller.finish()
        runCurrent()
        val advice = GuidanceTopic.Environment(EnvironmentAdvice.REDUCE_OUTSIDE_AIR)
        assertEquals(listOf(advice), cards)
        assertEquals(listOf(GuidanceEvent(3800, 0, advice, GuidanceOutcome.SHOWN)), store.get(trip.id)!!.guidance)
    }

    @Test fun `a failing environment stream does not stop recording`() = runTest {
        val controller = controller()
        val environment = flow<EnvironmentSnapshot> { error("climate service died") }
        val trip = startRecordingWithGuidance(controller, environment = environment) { true }
        for (ms in 200L..2000L step 200) trip.readings.emit(reading(ms))
        runCurrent()
        assertTrue(trip.job.isActive)
        controller.finish()
        runCurrent()
        assertFalse(store.get(trip.id)!!.samples.isEmpty())
    }

    @Test fun `recording without an active trip ends immediately`() = runTest {
        val controller = controller()
        controller.initialize()
        val job = launch { controller.record(MutableSharedFlow()) }
        runCurrent()
        assertTrue(job.isCompleted)
    }
}
