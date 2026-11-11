package dev.motioncare.feature.history.advice

import dev.motioncare.core.model.*
import dev.motioncare.feature.history.insights.PatternTrend
import dev.motioncare.feature.history.insights.TripInsight
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class AdviceComparisonCopyTest {
    @Test fun comparesTheRecentAverageWithTheOneBeforeAsHomeDoes() {
        val copy = adviceComparisonCopy(latest(), DrivingPattern.HARD_STOP,
            PatternTrend(previousRates = listOf(2.7, 2.3, 0.6), recentRates = listOf(0.0, 0.7, 0.9)))
        assertEquals("정차 직전 큰 감속", copy.title)
        assertEquals("최근 3회 평균 0.5회/10분 · 이전 3회 평균 1.9회/10분", copy.comparison)
        assertTrue(copy.basis.contains("같은 운전자"))
        assertTrue(copy.basis.contains("완료 운행"))
        assertEquals("이번 운행 38분 동안 5회 관찰 · 유효 분석 37분", copy.observation)
        assertTrue(copy.calculation.contains("5회 ÷ 37분 × 10"))
        assertTrue(copy.calculation.contains("이전 3회 2.7 · 2.3 · 0.6"))
        assertTrue(copy.calculation.contains("최근 3회 0.0 · 0.7 · 0.9"))
    }

    @Test fun missingPreviousTripsDoNotReadAsZeroEvents() {
        val copy = adviceComparisonCopy(latest(), DrivingPattern.HARD_STOP, PatternTrend(emptyList(), listOf(1.4)))
        assertTrue(copy.comparison.contains("최근 1회 평균 1.4회/10분"))
        assertTrue(copy.comparison.contains("이전 운행 없음"))
        assertFalse(copy.comparison.contains("이전 0회"))
        assertTrue(copy.basis.contains("비교할"))
    }

    @Test fun unavailableCurrentRateDoesNotReadAsZero() {
        val copy = adviceComparisonCopy(latest().copy(analyzedDurationMs = 30_000, ratesPerTenMinutes = emptyMap()),
            DrivingPattern.HARD_STOP, PatternTrend(emptyList(), emptyList()))
        assertTrue(copy.comparison.contains("분석할 운행 부족"))
        assertFalse(copy.comparison.contains("0.0"))
        assertTrue(copy.calculation.contains("계산하지 않았어요"))
    }

    @Test fun keepsKoreanDecimalNotation() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val copy = adviceComparisonCopy(latest(), DrivingPattern.HARD_STOP, PatternTrend(listOf(2.2), listOf(1.4)))
            assertEquals("최근 1회 평균 1.4회/10분 · 이전 1회 평균 2.2회/10분", copy.comparison)
        } finally { Locale.setDefault(original) }
    }

    private fun latest() = TripInsight(
        TripSnapshot(TripRecord(TripId("copy"), 0, 38 * 60_000L, TripStatus.COMPLETED, origin = DataOrigin.VEHICLE), emptyList()),
        null, 38 * 60_000L, 37 * 60_000L, 0.0,
        mapOf(DrivingPattern.HARD_STOP to 5), mapOf(DrivingPattern.HARD_STOP to 5 * 10.0 / 37),
    )
}
