package dev.motioncare.data.trip.vehicle

import dev.motioncare.core.model.Quality
import dev.motioncare.data.trip.internal.SpeedReading
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedReadingMappingTest {
    @Test fun `available forward speed keeps its value`() {
        assertEquals(SpeedReading(7, 11.11, Quality.VALID), speedReadingOf(7, available = true, 11.11))
    }

    @Test fun `reverse speed is stored as magnitude`() {
        assertEquals(SpeedReading(7, 2.0, Quality.VALID), speedReadingOf(7, available = true, -2.0))
    }

    @Test fun `unavailable status has no value`() {
        assertEquals(SpeedReading(7, null, Quality.UNAVAILABLE), speedReadingOf(7, available = false, 5.0))
    }

    @Test fun `non-finite or missing value has no value`() {
        assertEquals(SpeedReading(7, null, Quality.UNAVAILABLE), speedReadingOf(7, available = true, Double.NaN))
        assertEquals(SpeedReading(7, null, Quality.UNAVAILABLE), speedReadingOf(7, available = true, null))
    }
}
