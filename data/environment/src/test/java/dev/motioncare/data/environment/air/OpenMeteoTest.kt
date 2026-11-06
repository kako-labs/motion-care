package dev.motioncare.data.environment.air

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric supplies the real org.json implementation.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OpenMeteoTest {
    @Test fun `request asks for current pm values of the global forecast in unix time`() {
        assertEquals(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=37.6&longitude=127.0" +
                "&current=pm10,pm2_5&domains=cams_global&timeformat=unixtime",
            openMeteoUrl(37.6, 127.0),
        )
    }

    @Test fun `request uses a dot whatever the device locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals(true, openMeteoUrl(35.2, 129.1).contains("latitude=35.2&longitude=129.1"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test fun `response gives pm values and the forecast time`() {
        val body = """{"latitude":37.6,"longitude":127.0,"current_units":{"time":"unixtime"},
            "current":{"time":1793930400,"interval":3600,"pm10":12.9,"pm2_5":12.2}}"""

        val sample = parseOpenMeteo(body)

        assertEquals(AirSample(pm10 = 12.9, pm25 = 12.2, observedAtEpochMs = 1_793_930_400_000), sample)
    }

    @Test fun `missing pollutant stays null`() {
        val body = """{"current":{"time":1793930400,"pm10":null,"pm2_5":8.0}}"""

        val sample = parseOpenMeteo(body)

        assertNull(sample.pm10)
        assertEquals(8.0, sample.pm25)
    }

    @Test fun `error body is rejected`() {
        assertThrows(AirResponseException::class.java) {
            parseOpenMeteo("""{"error":true,"reason":"Latitude must be in range of -90 to 90°."}""")
        }
    }

    @Test fun `body without current values is rejected`() {
        assertThrows(AirResponseException::class.java) { parseOpenMeteo("""{"latitude":37.6}""") }
        assertThrows(AirResponseException::class.java) { parseOpenMeteo("not json") }
    }
}
