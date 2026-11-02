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
