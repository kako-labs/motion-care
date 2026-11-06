package dev.motioncare.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnvironmentModelsTest {
    @Test fun `available reading needs a value and its time`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnvironmentReading<Double>(null, Availability.AVAILABLE, EnvironmentSource.VEHICLE, observedAtEpochMs = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EnvironmentReading(21.0, Availability.AVAILABLE, EnvironmentSource.VEHICLE)
        }
    }

    @Test fun `stale reading keeps the old value`() {
        val reading = EnvironmentReading(21.0, Availability.STALE, EnvironmentSource.VEHICLE, observedAtEpochMs = 5)

        assertEquals(21.0, reading.value)
    }

    @Test fun `missing reading never carries a value`() {
        listOf(Availability.LOADING, Availability.UNSUPPORTED, Availability.UNAVAILABLE).forEach { availability ->
            assertThrows(IllegalArgumentException::class.java) {
                EnvironmentReading(0.0, availability, EnvironmentSource.VEHICLE, observedAtEpochMs = 1)
            }
        }
    }

    @Test fun `only unavailable readings say why`() {
        EnvironmentReading<Double>(null, Availability.UNAVAILABLE, EnvironmentSource.OPEN_METEO_CAMS,
            missingReason = MissingReason.NETWORK)

        assertThrows(IllegalArgumentException::class.java) {
            EnvironmentReading<Double>(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE,
                missingReason = MissingReason.NO_DATA)
        }
    }
}
