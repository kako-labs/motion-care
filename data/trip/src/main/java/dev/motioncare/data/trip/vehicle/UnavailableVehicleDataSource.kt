package dev.motioncare.data.trip.vehicle

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.VehicleDataSource

// A missing SDK must never silently fall back to synthetic measurements.
internal class UnavailableVehicleDataSource : VehicleDataSource {
    override val origin = DataOrigin.VEHICLE
    override suspend fun sample(elapsedMs: Long, index: Int): SpeedSample =
        throw UnsupportedOperationException("Vehicle adapter is not connected")
}
