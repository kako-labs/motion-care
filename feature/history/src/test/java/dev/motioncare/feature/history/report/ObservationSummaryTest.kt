package dev.motioncare.feature.history.report

import dev.motioncare.core.model.*
import org.junit.Assert.*
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

    @Test fun `left and right patterns are named`() {
        val seen = listOf(
            Observation(DrivingPattern.FAST_TURN, 0, 1, 3.0, "test"),
            Observation(DrivingPattern.REPEATED_SWAY, 2, 3, 1.0, "test"),
        )
        assertEquals("관찰된 패턴: 빠른 회전 1회 · 반복 좌우 움직임 1회", observationSummary(seen))
    }

    private fun snapshot(origin: DataOrigin, steering: List<SteeringSample>) =
        TripSnapshot(TripRecord(TripId("trip"), 0, origin = origin), steering = steering)

    @Test fun `a vehicle trip without steering says left and right were not judged`() {
        assertEquals("조향 정보가 없어 좌우 움직임은 판정하지 않았습니다.", steeringNote(snapshot(DataOrigin.VEHICLE, emptyList())))
    }

    @Test fun `a vehicle trip with steering says the values are estimates`() {
        assertEquals("좌우 움직임은 조향각과 속도로 추정한 값입니다.", steeringNote(snapshot(DataOrigin.VEHICLE, listOf(SteeringSample(1, 0.0)))))
    }

    @Test fun `a synthetic trip shows no steering note`() {
        assertNull(steeringNote(snapshot(DataOrigin.SYNTHETIC, emptyList())))
    }

    private fun guided(origin: DataOrigin, vararg outcomes: GuidanceOutcome) = TripSnapshot(
        TripRecord(TripId("trip"), 0, origin = origin),
        guidance = outcomes.map { GuidanceEvent(1, 0, DrivingPattern.HARD_STOP, it) },
    )

    @Test fun `shown cards are counted and skipped ones are not`() {
        assertEquals("주행 중 안내 1회", guidanceSummary(guided(DataOrigin.VEHICLE, GuidanceOutcome.SHOWN, GuidanceOutcome.SKIPPED_INTERVAL)))
    }

    @Test fun `a vehicle trip without cards says none were shown`() {
        assertEquals("주행 중 안내 없음", guidanceSummary(guided(DataOrigin.VEHICLE)))
    }

    @Test fun `guidance turned off is told apart from none needed`() {
        assertEquals("주행 중 안내 꺼짐", guidanceSummary(guided(DataOrigin.VEHICLE, GuidanceOutcome.SKIPPED_OFF)))
    }

    @Test fun `a synthetic trip shows no guidance line`() {
        assertNull(guidanceSummary(guided(DataOrigin.SYNTHETIC)))
    }
}
