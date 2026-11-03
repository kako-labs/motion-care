package dev.motioncare.feature.history.report

import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.Observation
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationSummaryTest {
    private fun seen(pattern: DrivingPattern, start: Long) = Observation(pattern, start, start + 100, 2.5, "test-1")

    @Test fun `no observations say so`() {
        assertEquals("관찰된 패턴 없음", observationSummary(emptyList()))
    }

    @Test fun `observations are counted per pattern in a fixed order`() {
        val found = listOf(
            seen(DrivingPattern.HARD_STOP, 500),
            seen(DrivingPattern.RAPID_START, 100),
            seen(DrivingPattern.HARD_STOP, 900),
            seen(DrivingPattern.REPEATED_SPEED_CHANGE, 300),
        )
        assertEquals("관찰된 패턴: 빠른 출발 1회 · 정차 직전 큰 감속 2회 · 반복 가감속 1회", observationSummary(found))
    }
}
