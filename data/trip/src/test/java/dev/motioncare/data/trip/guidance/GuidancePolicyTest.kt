package dev.motioncare.data.trip.guidance

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.analysis.PATTERN_DETECTOR_VERSION
import org.junit.Assert.*
import org.junit.Test

class GuidancePolicyTest {
    private var enabled = true
    private var showWorks = true
    private val shown = mutableListOf<GuidanceTopic>()
    private val policy = GuidancePolicy({ enabled }, { topic ->
        if (showWorks) shown += topic
        showWorks
    })
    private val reduce = GuidanceTopic.Environment(EnvironmentAdvice.REDUCE_OUTSIDE_AIR)

    private fun card(pattern: DrivingPattern) = GuidanceTopic.Pattern(pattern)

    private fun seen(pattern: DrivingPattern, atMs: Long) =
        Observation(pattern, maxOf(atMs - 100, 0), atMs, 3.0, PATTERN_DETECTOR_VERSION)

    /** Steady driving at 5 Hz from the given time, returning all recorded events. */
    private fun cruise(fromMs: Long, toMs: Long, speed: Double = 15.0, lateral: Double? = 0.0): List<GuidanceEvent> =
        (fromMs..toMs step 200).flatMap { policy.onSample(it, speed, lateral) }

    private fun outcomes(events: List<GuidanceEvent>) = events.map { it.outcome }

    /** The third occurrence inside the window is what turns a pattern into a card. */
    private fun thirdTime(pattern: DrivingPattern, atMs: Long): List<GuidanceEvent> {
        policy.onObservation(seen(pattern, atMs - 2))
        policy.onObservation(seen(pattern, atMs - 1))
        return policy.onObservation(seen(pattern, atMs))
    }

    @Test fun `a repeated pattern is shown once the car is calm`() {
        cruise(0, 3000)
        val events = thirdTime(DrivingPattern.REPEATED_SPEED_CHANGE, 3000) + cruise(3200, 4000)
        assertEquals(listOf(card(DrivingPattern.REPEATED_SPEED_CHANGE)), shown)
        assertEquals(listOf(GuidanceOutcome.SHOWN), outcomes(events))
        assertEquals(3000L, events.single().observedMs)
    }

    @Test fun `one occurrence of any pattern is not shown`() {
        cruise(0, 3000)
        DrivingPattern.entries.forEach { policy.onObservation(seen(it, 3000)) }
        val events = cruise(3200, 20_000)
        assertTrue(shown.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test fun `the third time inside the window is shown`() {
        cruise(0, 3000)
        policy.onObservation(seen(DrivingPattern.HARD_STOP, 3000))
        cruise(3200, 60_000)
        policy.onObservation(seen(DrivingPattern.HARD_STOP, 60_000))
        cruise(60_200, 120_000)
        policy.onObservation(seen(DrivingPattern.HARD_STOP, 120_000))
        cruise(120_200, 125_000)
        assertEquals(listOf(card(DrivingPattern.HARD_STOP)), shown)
    }

    @Test fun `events spread beyond the window do not add up`() {
        cruise(0, 3000)
        for (minute in listOf(1L, 7L, 13L, 19L, 25L)) {
            policy.onObservation(seen(DrivingPattern.HARD_STOP, minute * 60_000))
            cruise(minute * 60_000 + 200, minute * 60_000 + 5000)
        }
        assertTrue(shown.isEmpty())
    }

    @Test fun `different patterns are counted separately`() {
        cruise(0, 3000)
        policy.onObservation(seen(DrivingPattern.HARD_STOP, 3000))
        policy.onObservation(seen(DrivingPattern.RAPID_START, 3000))
        policy.onObservation(seen(DrivingPattern.FAST_TURN, 3000))
        cruise(3200, 10_000)
        assertTrue(shown.isEmpty())
    }

    @Test fun `a card waits while the car is accelerating or turning`() {
        cruise(0, 3000)
        thirdTime(DrivingPattern.REPEATED_SWAY, 3000)
        // Speeding up at 2 m/s2.
        (3200L..6000L step 200).forEach { policy.onSample(it, 15.0 + (it - 3000) * 0.002, 0.0) }
        assertTrue(shown.isEmpty())
        // Steady speed but turning.
        cruise(6200, 12_000, speed = 21.0, lateral = 1.5)
        assertTrue(shown.isEmpty())
        cruise(12_200, 15_400, speed = 21.0, lateral = 0.0)
        assertEquals(listOf(card(DrivingPattern.REPEATED_SWAY)), shown)
    }

    @Test fun `a stop counts as a calm moment`() {
        cruise(0, 3000, speed = 0.0)
        thirdTime(DrivingPattern.REPEATED_SPEED_CHANGE, 3000)
        cruise(3200, 4000, speed = 0.0)
        assertEquals(1, shown.size)
    }

    @Test fun `without steering only forward motion decides calm`() {
        cruise(0, 3000, lateral = null)
        thirdTime(DrivingPattern.REPEATED_SPEED_CHANGE, 3000)
        cruise(3200, 4000, lateral = null)
        assertEquals(1, shown.size)
    }

    @Test fun `a card that never finds a calm moment expires`() {
        cruise(0, 3000)
        thirdTime(DrivingPattern.REPEATED_SWAY, 3000)
        val events = cruise(3200, 70_000, lateral = 1.5)
        assertTrue(shown.isEmpty())
        assertEquals(listOf(GuidanceOutcome.SKIPPED_EXPIRED), outcomes(events))
    }

    @Test fun `a second card inside the interval is skipped and later ones are shown`() {
        cruise(0, 3000)
        thirdTime(DrivingPattern.REPEATED_SPEED_CHANGE, 3000)
        cruise(3200, 4000)
        val tooSoon = thirdTime(DrivingPattern.REPEATED_SPEED_CHANGE, 300_000)
        assertEquals(listOf(GuidanceOutcome.SKIPPED_INTERVAL), outcomes(tooSoon))
        cruise(300_200, 310_000)
        assertEquals(1, shown.size)
        thirdTime(DrivingPattern.REPEATED_SPEED_CHANGE, 910_000)
        cruise(910_200, 914_000)
        assertEquals(2, shown.size)
    }

    @Test fun `a new pattern while one is waiting is skipped`() {
        cruise(0, 3000)
        thirdTime(DrivingPattern.REPEATED_SWAY, 3000)
        val events = thirdTime(DrivingPattern.REPEATED_SPEED_CHANGE, 3000)
        assertEquals(listOf(GuidanceOutcome.SKIPPED_INTERVAL), outcomes(events))
    }

    @Test fun `guidance turned off is recorded and not shown`() {
        enabled = false
        cruise(0, 3000)
        val events = thirdTime(DrivingPattern.REPEATED_SWAY, 3000) + cruise(3200, 8000)
        assertTrue(shown.isEmpty())
        assertEquals(listOf(GuidanceOutcome.SKIPPED_OFF), outcomes(events))
    }

    @Test fun `turning guidance off while a card waits stops it`() {
        cruise(0, 1000, lateral = 1.5)
        thirdTime(DrivingPattern.REPEATED_SWAY, 1000)
        enabled = false
        val events = cruise(1200, 8000)
        assertTrue(shown.isEmpty())
        assertEquals(listOf(GuidanceOutcome.SKIPPED_OFF), outcomes(events))
    }

    @Test fun `a card the system refuses is recorded and does not start the interval`() {
        showWorks = false
        cruise(0, 3000)
        val failed = thirdTime(DrivingPattern.REPEATED_SWAY, 3000) + cruise(3200, 4000)
        assertEquals(listOf(GuidanceOutcome.FAILED), outcomes(failed))
        showWorks = true
        thirdTime(DrivingPattern.REPEATED_SWAY, 5000)
        cruise(5200, 9000)
        assertEquals(1, shown.size)
    }

    @Test fun `a presenter that throws is recorded as failed`() {
        val throwing = GuidancePolicy({ true }, { error("notification service died") })
        (0L..3000L step 200).forEach { throwing.onSample(it, 15.0, 0.0) }
        (2998L..3000L).forEach { throwing.onObservation(seen(DrivingPattern.REPEATED_SWAY, it)) }
        val events = (3200L..4000L step 200).flatMap { throwing.onSample(it, 15.0, 0.0) }
        assertEquals(listOf(GuidanceOutcome.FAILED), outcomes(events))
    }

    @Test fun `a signal gap restarts the calm wait`() {
        cruise(0, 3000)
        thirdTime(DrivingPattern.REPEATED_SWAY, 3000)
        cruise(10_000, 11_000)
        assertTrue(shown.isEmpty())
        cruise(11_200, 14_000)
        assertEquals(1, shown.size)
    }

    @Test fun `lost speed stops a waiting card from showing on old data`() {
        cruise(0, 1000, lateral = 1.5)
        thirdTime(DrivingPattern.REPEATED_SWAY, 1000)
        policy.onSignalLost()
        assertEquals(emptyList<GuidanceEvent>(), policy.onSample(1200, 15.0, 0.0))
        assertTrue(shown.isEmpty())
    }

    @Test fun `cabin air advice is shown once the car is calm`() {
        cruise(0, 3000)
        val events = policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 3000) + cruise(3200, 4000)
        assertEquals(listOf(reduce), shown)
        assertEquals(listOf(GuidanceOutcome.SHOWN), outcomes(events))
        assertEquals(reduce, events.single().topic)
        assertEquals(3000L, events.single().observedMs)
    }

    @Test fun `the same advice appears once per trip`() {
        cruise(0, 3000)
        policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 3000)
        cruise(3200, 4000)
        val later = policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 2_000_000) + cruise(2_000_200, 2_005_000)
        assertEquals(1, shown.size)
        assertTrue(later.isEmpty())
    }

    @Test fun `different advice is counted separately`() {
        cruise(0, 3000)
        policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 3000)
        cruise(3200, 4000)
        policy.onAdvice(EnvironmentAdvice.TRY_OUTSIDE_AIR, 1_000_000)
        cruise(1_000_200, 1_004_000)
        assertEquals(listOf(reduce, GuidanceTopic.Environment(EnvironmentAdvice.TRY_OUTSIDE_AIR)), shown)
    }

    @Test fun `advice inside the interval waits for a later reading without a record`() {
        cruise(0, 3000)
        thirdTime(DrivingPattern.HARD_STOP, 3000)
        cruise(3200, 4000)
        val tooSoon = policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 300_000) + cruise(300_200, 305_000)
        assertTrue(tooSoon.isEmpty())
        assertEquals(1, shown.size)
        val later = policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 910_000) + cruise(910_200, 914_000)
        assertEquals(listOf(GuidanceOutcome.SHOWN), outcomes(later))
        assertEquals(reduce, shown.last())
    }

    @Test fun `advice while a pattern card waits is held`() {
        cruise(0, 1000, lateral = 1.5)
        thirdTime(DrivingPattern.REPEATED_SWAY, 1000)
        assertTrue(policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 1000).isEmpty())
        cruise(1200, 5000)
        assertEquals(listOf(card(DrivingPattern.REPEATED_SWAY)), shown)
    }

    @Test fun `a repeated pattern takes the place of waiting advice`() {
        cruise(0, 1000, lateral = 1.5)
        policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 1000)
        val events = thirdTime(DrivingPattern.REPEATED_SWAY, 1000) + cruise(1200, 5000)
        assertEquals(listOf(card(DrivingPattern.REPEATED_SWAY)), shown)
        assertEquals(listOf(GuidanceOutcome.SHOWN), outcomes(events))
        // The advice was never decided, so the next reading after the interval still brings it.
        policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 1_000_000)
        cruise(1_000_200, 1_004_000)
        assertEquals(reduce, shown.last())
    }

    @Test fun `a pattern soon after advice was shown is skipped`() {
        cruise(0, 3000)
        policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 3000)
        cruise(3200, 4000)
        assertEquals(listOf(GuidanceOutcome.SKIPPED_INTERVAL), outcomes(thirdTime(DrivingPattern.HARD_STOP, 300_000)))
    }

    @Test fun `advice with guidance off is recorded once`() {
        enabled = false
        cruise(0, 3000)
        val events = policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 3000) + cruise(3200, 8000) +
            policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 400_000)
        assertTrue(shown.isEmpty())
        assertEquals(listOf(GuidanceOutcome.SKIPPED_OFF), outcomes(events))
    }

    @Test fun `advice that finds no calm moment expires and is not asked again`() {
        cruise(0, 3000, lateral = 1.5)
        val events = policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 3000) + cruise(3200, 70_000, lateral = 1.5) +
            policy.onAdvice(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, 1_000_000) + cruise(1_000_200, 1_004_000)
        assertTrue(shown.isEmpty())
        assertEquals(listOf(GuidanceOutcome.SKIPPED_EXPIRED), outcomes(events))
    }

    @Test fun `finishing with a waiting card records it as expired`() {
        cruise(0, 1000, lateral = 1.5)
        thirdTime(DrivingPattern.REPEATED_SWAY, 1000)
        assertEquals(listOf(GuidanceOutcome.SKIPPED_EXPIRED), outcomes(policy.finish(2000)))
        assertEquals(emptyList<GuidanceEvent>(), policy.finish(3000))
    }
}
