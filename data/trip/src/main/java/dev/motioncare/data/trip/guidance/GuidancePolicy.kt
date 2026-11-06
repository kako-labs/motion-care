package dev.motioncare.data.trip.guidance

import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.EnvironmentAdvice
import dev.motioncare.core.model.GuidanceEvent
import dev.motioncare.core.model.GuidanceOutcome
import dev.motioncare.core.model.GuidanceTopic
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

/**
 * Decides whether a card appears, waits or is skipped. Knows nothing about how a card is displayed.
 *
 * Pattern cards, cabin air advice and the rest card share one waiting slot and one interval. A repeated pattern
 * takes the place of waiting advice or a waiting rest card: the pattern is gone once skipped, while the cabin air
 * and the driving time stay and are offered again later.
 */
internal class GuidancePolicy(
    private val enabled: () -> Boolean,
    private val show: (GuidanceTopic) -> Boolean,
    private val limits: GuidanceLimits = GuidanceLimits(),
    longDriveLimits: LongDriveLimits = LongDriveLimits(),
) {
    private class Waiting(val topic: GuidanceTopic, val observedMs: Long)
    private class Point(val elapsedMs: Long, val speed: Double)

    private val repeats = mutableMapOf<DrivingPattern, ArrayDeque<Long>>()
    private val speeds = ArrayDeque<Point>()
    private var waiting: Waiting? = null
    private var lastShownMs: Long? = null
    private var calmSinceMs: Long? = null
    // Each piece of advice is decided once per trip.
    private val decidedAdvice = mutableSetOf<EnvironmentAdvice>()
    private val longDrive = LongDriveTracker(longDriveLimits)

    fun onObservation(seen: Observation): List<GuidanceEvent> {
        val now = seen.endMs
        if (!repeatedEnough(seen.pattern, now)) return emptyList()
        val skipped = when {
            !enabled() -> GuidanceOutcome.SKIPPED_OFF
            waiting?.topic is GuidanceTopic.Pattern || withinInterval(now) -> GuidanceOutcome.SKIPPED_INTERVAL
            else -> null
        }
        if (skipped != null) return listOf(GuidanceEvent(now, now, seen.pattern, skipped))
        waiting = Waiting(GuidanceTopic.Pattern(seen.pattern), now)
        return emptyList()
    }

    /** Offered with every environment reading that calls for [advice]. A card that cannot wait now is not recorded. */
    fun onAdvice(advice: EnvironmentAdvice, now: Long): List<GuidanceEvent> {
        if (advice in decidedAdvice) return emptyList()
        val topic = GuidanceTopic.Environment(advice)
        if (!enabled()) {
            decidedAdvice += advice
            return listOf(GuidanceEvent(now, now, topic, GuidanceOutcome.SKIPPED_OFF))
        }
        if (waiting != null || withinInterval(now)) return emptyList()
        waiting = Waiting(topic, now)
        return emptyList()
    }

    /** Pass the estimated lateral acceleration, or null when steering is not available. */
    fun onSample(now: Long, speedMps: Double, lateralMps2: Double?): List<GuidanceEvent> {
        trackCalm(now, speedMps, lateralMps2)
        val offered = if (longDrive.onSample(now, speedMps)) offerRest(now) else emptyList()
        return offered + decideWaiting(now)
    }

    /** A rest card that cannot wait now stays due and is offered again with the next sample, without a record. */
    private fun offerRest(now: Long): List<GuidanceEvent> {
        if (waiting?.topic == GuidanceTopic.LongDrive) return emptyList()
        if (!enabled()) {
            longDrive.used(now)
            return listOf(GuidanceEvent(now, now, GuidanceTopic.LongDrive, GuidanceOutcome.SKIPPED_OFF))
        }
        if (waiting == null && !withinInterval(now)) waiting = Waiting(GuidanceTopic.LongDrive, now)
        return emptyList()
    }

    private fun decideWaiting(now: Long): List<GuidanceEvent> {
        val card = waiting ?: return emptyList()
        if (!enabled()) return settle(card, now, GuidanceOutcome.SKIPPED_OFF)
        if (now - card.observedMs > limits.expireAfterMs) return settle(card, now, GuidanceOutcome.SKIPPED_EXPIRED)
        val calmSince = calmSinceMs ?: return emptyList()
        if (now - calmSince < limits.calmForMs) return emptyList()
        // A display failure must never reach recording.
        val displayed = runCatching { show(card.topic) }.getOrDefault(false)
        if (displayed) lastShownMs = now
        return settle(card, now, if (displayed) GuidanceOutcome.SHOWN else GuidanceOutcome.FAILED)
    }

    private fun withinInterval(now: Long) = lastShownMs?.let { now - it < limits.minIntervalMs } == true

    /** Speed became unavailable, so nothing is known about how calm the car is. */
    fun onSignalLost() {
        speeds.clear()
        calmSinceMs = null
    }

    fun finish(now: Long): List<GuidanceEvent> =
        waiting?.let { settle(it, now, GuidanceOutcome.SKIPPED_EXPIRED) }.orEmpty()

    private fun settle(card: Waiting, now: Long, outcome: GuidanceOutcome): List<GuidanceEvent> {
        waiting = null
        when (val topic = card.topic) {
            is GuidanceTopic.Environment -> decidedAdvice += topic.advice
            GuidanceTopic.LongDrive -> longDrive.used(now)
            is GuidanceTopic.Pattern -> Unit
        }
        return listOf(GuidanceEvent(now, card.observedMs, card.topic, outcome))
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
