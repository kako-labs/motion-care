package dev.motioncare.data.trip.internal

import dev.motioncare.core.model.*
import kotlinx.coroutines.flow.StateFlow

internal interface TripReader {
    val records: StateFlow<List<TripRecord>>
    suspend fun get(id: TripId): TripSnapshot?
}
internal interface TripWriter {
    suspend fun save(snapshot: TripSnapshot)
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
internal fun interface EpochClock { fun nowMs(): Long }
internal fun interface TripIdFactory { fun next(): TripId }
