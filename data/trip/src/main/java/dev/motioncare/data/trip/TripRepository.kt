package dev.motioncare.data.trip

import dev.motioncare.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

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
    /**
     * Stores vehicle signals until the active trip ends or the caller is cancelled. Each [environment] reading may
     * lead to a cabin air card; a failing stream ends that advice only.
     */
    suspend fun record(presenter: GuidancePresenter, environment: Flow<EnvironmentSnapshot> = emptyFlow())
}

/** Displays one in-drive card. Returns false when it could not be shown. */
fun interface GuidancePresenter {
    fun show(topic: GuidanceTopic): Boolean
}

/** What the driver hears when a card appears. */
enum class GuidanceSound { CHIME, VOICE }

/** Whether cards appear during a drive and how they sound. Recording continues either way. */
interface GuidanceSettings {
    val enabled: StateFlow<Boolean>
    fun setEnabled(enabled: Boolean)
    val sound: StateFlow<GuidanceSound>
    fun setSound(sound: GuidanceSound)
}

/** Plays the sound for a card that was just shown. A sound that cannot play never affects the card. */
fun interface GuidanceAudio {
    fun play(message: String)
}

class VehicleTripData(
    val trips: TripRepository,
    val recorder: VehicleRecorder,
    val guidance: GuidanceSettings,
    val audio: GuidanceAudio,
)
