package dev.motioncare.feature.history.report

import dev.motioncare.core.model.AirGrade
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.FeedbackAnswer
import dev.motioncare.core.model.FeedbackReason
import dev.motioncare.core.model.MissingReason
import dev.motioncare.core.model.OutdoorAir
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.TripFeedback
import dev.motioncare.core.model.TripId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinSummaryTest {
    private val rider = PersonId("seoa")
    private val names = mapOf(rider to "서아")

    private fun <T : Any> known(value: T, at: Long, source: EnvironmentSource = EnvironmentSource.VEHICLE) =
        EnvironmentReading(value, Availability.AVAILABLE, source, at)

    private fun snapshot(at: Long, cabin: Double, recirculating: Boolean, air: EnvironmentReading<OutdoorAir>) = EnvironmentSnapshot(
        capturedAtEpochMs = at,
        outsideTemperature = known(25.0, at),
        cabinTemperature = known(cabin, at),
        cabinSetTemperature = known(26.0, at),
        recirculation = known(recirculating, at),
        airCleaner = known(false, at),
        outdoorAir = air,
    )

    private val badAir = { at: Long -> known(OutdoorAir(168.1, 147.6, AirGrade.VERY_BAD, 28.6, 77.2), at, EnvironmentSource.OPEN_METEO_CAMS) }

    private fun answer(vararg reasons: FeedbackReason) =
        TripFeedback(TripId("trip"), rider, FeedbackAnswer.Rating(2, "COMFORT_5_V1"), reasons.toSet())

    @Test fun `a trip without stored values or cabin feelings shows nothing`() {
        assertNull(cabinSummary(emptyList(), listOf(answer(FeedbackReason.CURVE)), names))
    }

    @Test fun `only cabin feelings are listed, in a fixed order`() {
        val summary = cabinSummary(emptyList(), listOf(answer(FeedbackReason.STUFFY_AIR, FeedbackReason.CURVE, FeedbackReason.FELT_HOT)), names)!!

        assertEquals(listOf(CabinFeeling("서아", "더웠어요 · 공기가 답답했어요")), summary.feelings)
        assertTrue(summary.values.isEmpty())
    }

    @Test fun `an answer saved before the change keeps its old name`() {
        val summary = cabinSummary(emptyList(), listOf(answer(FeedbackReason.CABIN_ENVIRONMENT)), names)!!

        assertEquals("차 안 환경", summary.feelings.single().feelings)
    }

    @Test fun `values show start and end only where they changed`() {
        val start = snapshot(1_000, cabin = 19.1, recirculating = false, air = badAir(1_000))
        val end = snapshot(60_000, cabin = 19.1, recirculating = true, air = badAir(60_000))

        val values = cabinSummary(listOf(start, end), emptyList(), names)!!.values.associateBy { it.label }

        assertEquals(CabinValue("실내 온도", "19.1°C", null), values["실내 온도"])
        assertEquals(CabinValue("공기 흐름", "외기 유입", "내기 순환"), values["공기 흐름"])
        assertEquals(CabinValue("바깥 대기질", "매우 나쁨 · PM2.5 147.6", null), values["바깥 대기질"])
    }

    @Test fun `a missing value shows why instead of a number`() {
        val offline = EnvironmentReading<OutdoorAir>(null, Availability.UNAVAILABLE, EnvironmentSource.OPEN_METEO_CAMS, missingReason = MissingReason.NETWORK)
        val values = cabinSummary(listOf(snapshot(1_000, 19.1, true, offline)), emptyList(), names)!!.values

        assertEquals("네트워크 연결 필요", values.single { it.label == "바깥 대기질" }.start)
    }
}
