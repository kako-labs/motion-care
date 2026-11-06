package dev.motioncare.data.environment.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CelsiusTest {
    @Test fun `car float keeps the digits the car reported`() {
        assertEquals(19.1, celsiusOf(19.1f)!!, 0.0)
        assertEquals(25.0, celsiusOf(25.0f)!!, 0.0)
        assertEquals(-3.5, celsiusOf(-3.5f)!!, 0.0)
    }

    @Test fun `missing or invalid value is not a temperature`() {
        assertNull(celsiusOf(null))
        assertNull(celsiusOf(Float.NaN))
    }
}
