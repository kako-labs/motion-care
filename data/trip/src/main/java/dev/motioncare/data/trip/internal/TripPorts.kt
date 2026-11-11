package dev.motioncare.data.trip.internal

import dev.motioncare.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface TripReader {
    val records: StateFlow<List<TripRecord>>
    suspend fun get(id: TripId): TripSnapshot?
    suspend fun lastSampleElapsedMs(id: TripId): Long?
}
internal interface TripWriter {
    suspend fun saveTrip(trip: TripRecord)
    suspend fun appendSamples(
        trip: TripRecord,
        samples: List<SpeedSample>,
        observations: List<Observation> = emptyList(),
        steering: List<SteeringSample> = emptyList(),
        guidance: List<GuidanceEvent> = emptyList(),
    )
    /** Saves the finished [trip] and drops what was recorded after [keepUntilMs], in one transaction. */
    suspend fun finishAt(trip: TripRecord, keepUntilMs: Long)
}
internal interface TripCommands {
    suspend fun start(vehicleId: VehicleId? = null): CommandResult
    suspend fun captureSample(): CommandResult
    suspend fun finish(): CommandResult
}
internal interface VehicleDataSource {
    val origin: DataOrigin
    suspend fun sample(elapsedMs: Long, index: Int): SpeedSample
}
internal interface VehicleSignalSource {
    fun speeds(): Flow<SpeedReading>
}
internal interface SteeringSignalSource {
    fun steering(): Flow<SteeringReading>
}
/** One speed event. The time comes from the same clock as [MonotonicClock]. */
internal data class SpeedReading(
    val elapsedRealtimeNanos: Long,
    val metersPerSecond: Double?,
    val quality: Quality,
)
/** One steering change, timed by [MonotonicClock] when it arrives because the SDK gives no event time. */
internal data class SteeringReading(val elapsedRealtimeNanos: Long, val degrees: Double)
internal fun interface EpochClock { fun nowMs(): Long }
internal fun interface MonotonicClock { fun nowNanos(): Long }
internal fun interface TripIdFactory { fun next(): TripId }
