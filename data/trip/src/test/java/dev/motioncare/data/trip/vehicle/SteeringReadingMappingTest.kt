package dev.motioncare.data.trip.vehicle

import dev.motioncare.data.trip.internal.SteeringReading
import org.junit.Assert.*
import org.junit.Test

class SteeringReadingMappingTest {
    @Test fun `angle is kept with its time and sign`() {
        assertEquals(SteeringReading(7L, -12.5), steeringReadingOf(7L, -12.5f))
    }

    @Test fun `missing or broken values are dropped`() {
        assertNull(steeringReadingOf(7L, null))
        assertNull(steeringReadingOf(7L, Float.NaN))
        assertNull(steeringReadingOf(7L, Float.POSITIVE_INFINITY))
    }
}
