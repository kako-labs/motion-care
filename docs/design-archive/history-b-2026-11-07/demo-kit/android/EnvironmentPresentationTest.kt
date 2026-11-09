package dev.motioncare.feature.history.presentation

import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.MissingReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvironmentPresentationTest {
    @Test fun missingReadingNeverLooksLikeZeroOrFreshAir() {
        listOf(MissingReason.NO_PERMISSION, MissingReason.NETWORK, MissingReason.NO_LOCATION).forEach { reason ->
            val reading = EnvironmentReading<Double>(null, Availability.UNAVAILABLE, EnvironmentSource.OPEN_METEO_CAMS, missingReason = reason)
            val field = environmentField("바깥 PM2.5", reading) { error("Missing values must not be formatted") }
            assertEquals("—", field.value)
            assertNull(field.observedAtEpochMs)
            assertEquals("대기질: Open-Meteo, CAMS 예측", field.source)
        }
    }

    @Test fun staleValueKeepsItsTimeAndSourceWithoutLookingCurrent() {
        val field = environmentField("실내 온도", EnvironmentReading(22.0, Availability.STALE, EnvironmentSource.VEHICLE, 1234L)) { "$it°C" }
        assertEquals("22.0°C", field.value)
        assertEquals("오래된 값", field.status)
        assertEquals(1234L, field.observedAtEpochMs)
        assertEquals("차량 공조·환경 신호", field.source)
    }

    @Test fun loadingUnsupportedAndPermissionAreDifferentStates() {
        fun status(state: Availability, reason: MissingReason? = null) = environmentField("환경", EnvironmentReading<Double>(null, state, EnvironmentSource.VEHICLE, missingReason = reason)) { "$it" }.status
        assertEquals("확인 중", status(Availability.LOADING))
        assertEquals("미지원", status(Availability.UNSUPPORTED))
        assertEquals("권한 없음", status(Availability.UNAVAILABLE, MissingReason.NO_PERMISSION))
        assertEquals("네트워크 연결 필요", status(Availability.UNAVAILABLE, MissingReason.NETWORK))
    }
}
