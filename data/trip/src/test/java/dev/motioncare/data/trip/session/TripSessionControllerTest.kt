package dev.motioncare.data.trip.session

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.*
import dev.motioncare.data.trip.sample.SampleVehicleDataSource
import dev.motioncare.data.trip.vehicle.UnavailableVehicleDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TripSessionControllerTest {
    private class MemoryStore : TripReader, TripWriter {
        private val snapshots = linkedMapOf<TripId, TripSnapshot>()
        override val records = MutableStateFlow<List<TripRecord>>(emptyList())
        var failWrites = false
        override suspend fun get(id: TripId) = snapshots[id]
        override suspend fun save(snapshot: TripSnapshot) {
            check(!failWrites) { "Storage failed" }
            snapshots[snapshot.trip.id] = snapshot
            records.value = snapshots.values.map { it.trip }
        }
    }
    private val store = MemoryStore()
    private var now = 1_000L
    private var sequence = 0
    private fun controller(source: VehicleDataSource = SampleVehicleDataSource()) =
        TripSessionController(store, store, source, EpochClock { now },
            TripIdFactory { TripId("trip-${++sequence}") })

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
}
