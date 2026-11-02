package dev.motioncare.core.model

import org.junit.Assert.*
import org.junit.Test

class TripModelsTest {
    @Test fun `zero speed is different from unavailable speed`() {
        assertNotEquals(SpeedSample(0, 0.0, Quality.VALID), SpeedSample(0, null, Quality.UNAVAILABLE))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite speed is rejected`() { SpeedSample(0, Double.NaN, Quality.VALID) }
    @Test(expected = IllegalArgumentException::class)
    fun `completed trip must have an end time`() {
        TripRecord(TripId("trip"), 100, status = TripStatus.COMPLETED, origin = DataOrigin.SYNTHETIC)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `out of order samples are rejected`() {
        TripSnapshot(TripRecord(TripId("trip"), 100, origin = DataOrigin.SYNTHETIC),
            listOf(SpeedSample(2, 1.0, Quality.VALID), SpeedSample(1, 2.0, Quality.VALID)))
    }
}
