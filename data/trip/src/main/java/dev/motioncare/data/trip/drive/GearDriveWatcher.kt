package dev.motioncare.data.trip.drive

import android.util.Log
import dev.motioncare.core.model.CommandResult
import dev.motioncare.core.model.TripId
import dev.motioncare.core.model.TripRecord
import dev.motioncare.core.model.TripStatus
import dev.motioncare.core.model.VehicleId
import dev.motioncare.data.trip.DriveWatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "DriveWatcher"
// How often a park is checked against the finish wait. An implementation judgement.
private const val TICK_MS = 5_000L
private const val STARTED_BUFFER = 8

internal fun interface GearSource {
    fun gears(): Flow<Gear>
}

/** The trip commands automatic recording needs. [finishParked] takes a time on the watcher's clock. */
internal interface AutoTrips {
    val records: StateFlow<List<TripRecord>>
    suspend fun initialize()
    suspend fun start(vehicleId: VehicleId?): CommandResult
    suspend fun finishParked(parkedAtMs: Long): CommandResult
}

/** [nowMs] must keep counting while the head unit sleeps, such as SystemClock.elapsedRealtime. */
internal class GearDriveWatcher(
    private val source: GearSource,
    private val trips: AutoTrips,
    private val nowMs: () -> Long,
    private val tickMs: Long = TICK_MS,
    private val parkedFinishMs: Long = AutoTripPolicy.PARKED_FINISH_MS,
) : DriveWatcher {
    override fun startedTrips(): Flow<TripId> = channelFlow {
        trips.initialize()
        val policy = AutoTripPolicy(parkedFinishMs)
        val lock = Mutex()
        suspend fun act(decide: (recording: Boolean) -> AutoAction) = lock.withLock {
            val recording = trips.records.value.any { it.status == TripStatus.RECORDING }
            // A failed command only skips this change; the next gear or tick decides again.
            try {
                when (val action = decide(recording)) {
                    AutoAction.Start -> start()?.let { send(it) }
                    is AutoAction.Finish -> trips.finishParked(action.parkedAtMs)
                    is AutoAction.Restart -> {
                        trips.finishParked(action.parkedAtMs)
                        start()?.let { send(it) }
                    }
                    AutoAction.None -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Automatic trip command failed", error)
            }
        }
        launch {
            while (true) {
                delay(tickMs)
                act { policy.onTick(nowMs(), it) }
            }
        }
        source.gears().collect { gear -> act { policy.onGear(gear, nowMs(), it) } }
    }.buffer(STARTED_BUFFER)

    private suspend fun start(): TripId? = when (val result = trips.start(null)) {
        is CommandResult.Success -> result.tripId
        is CommandResult.Rejected -> {
            Log.w(TAG, "Automatic start refused: ${result.reason}")
            null
        }
    }
}
