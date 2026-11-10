package dev.motioncare.data.trip.drive

import dev.motioncare.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Log.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GearDriveWatcherTest {
    private val gears = Channel<Gear>(Channel.UNLIMITED)
    private val trips = FakeTrips()
    private var sleptMs = 0L

    private fun TestScope.watch(): MutableList<TripId> {
        val started = mutableListOf<TripId>()
        val watcher = GearDriveWatcher({ gears.receiveAsFlow() }, trips, { testScheduler.currentTime + sleptMs },
            tickMs = 1_000, parkedFinishMs = 180_000)
        backgroundScope.launch { watcher.startedTrips().collect { started += it } }
        runCurrent()
        return started
    }

    @Test fun `leaving park starts a trip and reports it`() = runTest {
        val started = watch()
        gears.send(Gear.PARK)
        gears.send(Gear.DRIVE)
        runCurrent()
        assertEquals(listOf(TripId("trip-1")), started)
        assertEquals(TripStatus.RECORDING, trips.records.value.single().status)
    }

    @Test fun `three minutes in park finishes the trip`() = runTest {
        watch()
        gears.send(Gear.DRIVE)
        runCurrent()
        gears.send(Gear.PARK)
        runCurrent()
        advanceTimeBy(179_000)
        runCurrent()
        assertEquals(TripStatus.RECORDING, trips.records.value.single().status)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(TripStatus.COMPLETED, trips.records.value.single().status)
        assertEquals(listOf(0L), trips.parkedAt)
    }

    @Test fun `a trip already recording is left alone`() = runTest {
        trips.start(null)
        val started = watch()
        gears.send(Gear.DRIVE)
        runCurrent()
        assertEquals(emptyList<TripId>(), started)
        assertEquals(1, trips.records.value.size)
    }

    @Test fun `a refused start reports nothing and watching goes on`() = runTest {
        trips.refuseStart = true
        val started = watch()
        gears.send(Gear.DRIVE)
        runCurrent()
        assertEquals(emptyList<TripId>(), started)
        trips.refuseStart = false
        gears.send(Gear.PARK)
        gears.send(Gear.DRIVE)
        runCurrent()
        assertEquals(listOf(TripId("trip-1")), started)
    }

    @Test fun `a failing start does not stop watching`() = runTest {
        trips.failStart = true
        val started = watch()
        gears.send(Gear.DRIVE)
        runCurrent()
        trips.failStart = false
        gears.send(Gear.PARK)
        gears.send(Gear.DRIVE)
        runCurrent()
        assertEquals(listOf(TripId("trip-1")), started)
    }

    // Ticks stop while the head unit sleeps in park, but the clock keeps counting, and drive may come first.
    @Test fun `leaving a long park ends the old trip and starts a new one`() = runTest {
        val started = watch()
        gears.send(Gear.DRIVE)
        runCurrent()
        gears.send(Gear.PARK)
        runCurrent()
        sleptMs = 10_000_000
        gears.send(Gear.DRIVE)
        runCurrent()
        assertEquals(listOf(TripId("trip-1"), TripId("trip-2")), started)
        assertEquals(listOf(TripStatus.COMPLETED, TripStatus.RECORDING), trips.records.value.map { it.status })
        assertEquals(listOf(0L), trips.parkedAt)
    }
}

private class FakeTrips : AutoTrips {
    override val records = MutableStateFlow<List<TripRecord>>(emptyList())
    var refuseStart = false
    var failStart = false
    val parkedAt = mutableListOf<Long>()
    private val time = 1_000L
    private var next = 0

    override suspend fun initialize() = Unit
    override suspend fun start(vehicleId: VehicleId?): CommandResult {
        if (failStart) error("storage failed")
        if (refuseStart || records.value.any { it.status == TripStatus.RECORDING }) return CommandResult.Rejected("no")
        val record = TripRecord(TripId("trip-${++next}"), startedAtEpochMs = time, origin = DataOrigin.VEHICLE)
        records.value = records.value + record
        return CommandResult.Success(record.id)
    }
    override suspend fun finishParked(parkedAtMs: Long): CommandResult {
        parkedAt += parkedAtMs
        val active = records.value.firstOrNull { it.status == TripStatus.RECORDING } ?: return CommandResult.Rejected("no")
        records.value = records.value.map {
            if (it.id == active.id) it.copy(status = TripStatus.COMPLETED, endedAtEpochMs = time) else it
        }
        return CommandResult.Success(active.id)
    }
}
