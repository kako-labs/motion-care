package dev.motioncare.data.trip.guidance

import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.GuidanceEvent
import dev.motioncare.core.model.GuidanceOutcome
import dev.motioncare.core.model.Observation
import kotlin.math.abs

/** Each value and its source is listed in docs/guidance-card.md. */
internal data class GuidanceLimits(
    val repeatCount: Int = 3,
    val repeatWindowMs: Long = 600_000,
    val minIntervalMs: Long = 900_000,
    val calmMps2: Double = 0.9,
    val calmForMs: Long = 3_000,
    val expireAfterMs: Long = 60_000,
    val accelerationWindowMs: Long = 1_000,
    val minAccelerationSpanMs: Long = 500,
    val maxGapMs: Long = 1_000,
)

/** Decides whether a card appears, waits or is skipped. Knows nothing about how a card is displayed. */
internal class GuidancePolicy(
    private val enabled: () -> Boolean,
    private val show: (DrivingPattern) -> Boolean,
    private val limits: GuidanceLimits = GuidanceLimits(),
) {
    private class Waiting(val pattern: DrivingPattern, val observedMs: Long)
    private class Point(val elapsedMs: Long, val speed: Double)

    private val repeats = mutableMapOf<DrivingPattern, ArrayDeque<Long>>()
    private val speeds = ArrayDeque<Point>()
    private var waiting: Waiting? = null
    private var lastShownMs: Long? = null
    private var calmSinceMs: Long? = null

    fun onObservation(seen: Observation): List<GuidanceEvent> {
        val now = seen.endMs
        if (!repeatedEnough(seen.pattern, now)) return emptyList()
        val skipped = when {
            !enabled() -> GuidanceOutcome.SKIPPED_OFF
            waiting != null || lastShownMs?.let { now - it < limits.minIntervalMs } == true -> GuidanceOutcome.SKIPPED_INTERVAL
            else -> null
        }
        if (skipped != null) return listOf(GuidanceEvent(now, now, seen.pattern, skipped))
        waiting = Waiting(seen.pattern, now)
        return emptyList()
    }

    /** Pass the estimated lateral acceleration, or null when steering is not available. */
    fun onSample(now: Long, speedMps: Double, lateralMps2: Double?): List<GuidanceEvent> {
        trackCalm(now, speedMps, lateralMps2)
        val card = waiting ?: return emptyList()
        if (!enabled()) return settle(card, now, GuidanceOutcome.SKIPPED_OFF)
        if (now - card.observedMs > limits.expireAfterMs) return settle(card, now, GuidanceOutcome.SKIPPED_EXPIRED)
        val calmSince = calmSinceMs ?: return emptyList()
        if (now - calmSince < limits.calmForMs) return emptyList()
        // A display failure must never reach recording.
        val displayed = runCatching { show(card.pattern) }.getOrDefault(false)
        if (displayed) lastShownMs = now
        return settle(card, now, if (displayed) GuidanceOutcome.SHOWN else GuidanceOutcome.FAILED)
    }

    /** Speed became unavailable, so nothing is known about how calm the car is. */
    fun onSignalLost() {
        speeds.clear()
        calmSinceMs = null
    }

    fun finish(now: Long): List<GuidanceEvent> =
        waiting?.let { settle(it, now, GuidanceOutcome.SKIPPED_EXPIRED) }.orEmpty()

    private fun settle(card: Waiting, now: Long, outcome: GuidanceOutcome): List<GuidanceEvent> {
        waiting = null
        return listOf(GuidanceEvent(now, card.observedMs, card.pattern, outcome))
    }

    private fun repeatedEnough(pattern: DrivingPattern, now: Long): Boolean {
        val times = repeats.getOrPut(pattern) { ArrayDeque() }
        times.addLast(now)
        while (now - times.first() > limits.repeatWindowMs) times.removeFirst()
        if (times.size < limits.repeatCount) return false
        times.clear()
        return true
    }

    private fun trackCalm(now: Long, speed: Double, lateral: Double?) {
        speeds.lastOrNull()?.let { if (now - it.elapsedMs > limits.maxGapMs) onSignalLost() }
        speeds.addLast(Point(now, speed))
        while (now - speeds.first().elapsedMs > limits.accelerationWindowMs) speeds.removeFirst()
        val span = now - speeds.first().elapsedMs
        val calm = span >= limits.minAccelerationSpanMs &&
            abs(speed - speeds.first().speed) / (span / 1000.0) < limits.calmMps2 &&
            (lateral == null || abs(lateral) < limits.calmMps2)
        calmSinceMs = if (calm) calmSinceMs ?: now else null
    }
}
