package dev.motioncare.data.trip.sample

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.VehicleDataSource

internal class SampleVehicleDataSource : VehicleDataSource {
    override val origin = DataOrigin.SYNTHETIC
    private val speeds = listOf(0.0, 4.0, 9.0, 7.0, 0.0)
    override suspend fun sample(elapsedMs: Long, index: Int) =
        SpeedSample(elapsedMs, speeds[index % speeds.size], Quality.VALID)
}
