package dev.motioncare.data.environment.location

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinatesTest {
    @Test fun `coordinates are kept to one decimal place`() {
        assertEquals(37.6, roundCoordinate(37.5665), 0.0)
        assertEquals(127.0, roundCoordinate(126.978), 0.0)
        assertEquals(35.2, roundCoordinate(35.1796), 0.0)
    }

    @Test fun `halves round away from zero`() {
        assertEquals(37.6, roundCoordinate(37.55), 0.0)
        assertEquals(-33.9, roundCoordinate(-33.85), 0.0)
    }
}
