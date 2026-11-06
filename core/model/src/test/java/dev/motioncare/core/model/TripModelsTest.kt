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
    fun `observation cannot end before it starts`() {
        Observation(DrivingPattern.HARD_STOP, 200, 100, 2.0, "test-1")
    }
    @Test(expected = IllegalArgumentException::class)
    fun `observation peak must be a finite magnitude`() {
        Observation(DrivingPattern.HARD_STOP, 100, 200, -1.0, "test-1")
    }
    @Test fun `a pattern card names its pattern as the topic`() {
        val event = GuidanceEvent(10, 5, DrivingPattern.HARD_STOP, GuidanceOutcome.SHOWN)
        assertEquals(GuidanceTopic.Pattern(DrivingPattern.HARD_STOP), event.topic)
    }
    @Test fun `an environment card is told apart from pattern cards`() {
        val event = GuidanceEvent(10, 5, GuidanceTopic.Environment(EnvironmentAdvice.REDUCE_OUTSIDE_AIR), GuidanceOutcome.SHOWN)
        assertNotEquals(GuidanceEvent(10, 5, DrivingPattern.HARD_STOP, GuidanceOutcome.SHOWN), event)
    }
    @Test fun `snapshot has no observations unless given`() {
        val trip = TripRecord(TripId("trip"), 100, origin = DataOrigin.SYNTHETIC)
        assertTrue(TripSnapshot(trip).observations.isEmpty())
    }
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
