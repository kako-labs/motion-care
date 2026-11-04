package dev.motioncare.data.trip

import dev.motioncare.core.model.*
import kotlinx.coroutines.flow.StateFlow

/** The only data access contract exposed to features. */
interface TripRepository {
    val records: StateFlow<List<TripRecord>>
    suspend fun initialize()
    suspend fun get(id: TripId): TripSnapshot?
    suspend fun start(vehicleId: VehicleId? = null): CommandResult
    suspend fun captureSample(): CommandResult
    suspend fun finish(): CommandResult
}

/** Used by the recording service in app. Features keep using [TripRepository]. */
interface VehicleRecorder {
    /** Stores vehicle signals until the active trip ends or the caller is cancelled. */
    suspend fun record(presenter: GuidancePresenter)
}

/** Displays one in-drive card. Returns false when it could not be shown. */
fun interface GuidancePresenter {
    fun show(pattern: DrivingPattern): Boolean
}

/** Whether cards appear during a drive. Recording continues either way. */
interface GuidanceSettings {
    val enabled: StateFlow<Boolean>
    fun setEnabled(enabled: Boolean)
}

class VehicleTripData(val trips: TripRepository, val recorder: VehicleRecorder, val guidance: GuidanceSettings)
