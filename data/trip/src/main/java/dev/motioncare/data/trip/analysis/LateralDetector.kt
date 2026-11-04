package dev.motioncare.data.trip.analysis

import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.Observation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tan

/**
 * Lines that separate observed left and right motion, not motion sickness limits. Each value and its
 * source is listed in docs/detection-thresholds.md. No API reports wheelbase or steering ratio, so these
 * two are the published values of one reference car and the result is an estimate.
 */
internal data class LateralLimits(
    val wheelbaseMeters: Double = 3.0,
    val steeringRatio: Double = 14.26,
    val maxGapMs: Long = 1_000,
    val averageWindowMs: Long = 1_000,
    val minAverageSpanMs: Long = 500,
    val fastTurnMps2: Double = 2.0,
    val turnEndRatio: Double = 0.5,
    val swingMps2: Double = 0.9,
    val swingWindowMs: Long = 30_000,
    val swingCount: Int = 3,
)

/** Kinematic bicycle model. The sign follows the steering angle. */
internal fun lateralAcceleration(speedMps: Double, steeringDegrees: Double, limits: LateralLimits): Double =
    speedMps * speedMps * tan(Math.toRadians(steeringDegrees / limits.steeringRatio)) / limits.wheelbaseMeters

/** Finds left and right patterns one speed sample at a time, using the latest steering angle. */
internal class LateralDetector(private val limits: LateralLimits = LateralLimits()) {
    private class Point(val elapsedMs: Long, val value: Double)

    private val recent = ArrayDeque<Point>()
    private val swing = SwingTracker(DrivingPattern.REPEATED_SWAY, limits.swingMps2, limits.swingWindowMs, limits.swingCount)
    private var turnStartMs: Long? = null
    private var turnDirection = 0
    private var turnPeak = 0.0

    fun add(elapsedMs: Long, speedMps: Double, steeringDegrees: Double): List<Observation> {
        val found = mutableListOf<Observation>()
        recent.lastOrNull()?.let { if (elapsedMs - it.elapsedMs > limits.maxGapMs) found += finish() }
        val average = average(elapsedMs, lateralAcceleration(speedMps, steeringDegrees, limits)) ?: return found
        swing.add(elapsedMs, average)?.let { found += it }
        trackTurn(elapsedMs, average, found)
        return found
    }

    /** Ends tracking at a signal gap or the end of a trip, without guessing across it. */
    fun finish(): List<Observation> {
        val found = recent.lastOrNull()?.let { closeTurn(it.elapsedMs) }.orEmpty()
        recent.clear()
        swing.reset()
        return found
    }

    private fun average(now: Long, value: Double): Double? {
        recent.addLast(Point(now, value))
        while (now - recent.first().elapsedMs > limits.averageWindowMs) recent.removeFirst()
        if (now - recent.first().elapsedMs < limits.minAverageSpanMs) return null
        return recent.sumOf { it.value } / recent.size
    }

    private fun trackTurn(now: Long, value: Double, found: MutableList<Observation>) {
        val direction = if (value >= 0) 1 else -1
        val open = turnStartMs != null
        if (open && (direction != turnDirection || abs(value) < limits.fastTurnMps2 * limits.turnEndRatio)) {
            found += closeTurn(now)
        }
        if (turnStartMs == null && abs(value) >= limits.fastTurnMps2) {
            turnStartMs = now
            turnDirection = direction
            turnPeak = 0.0
        }
        if (turnStartMs != null) turnPeak = max(turnPeak, abs(value))
    }

    private fun closeTurn(endMs: Long): List<Observation> {
        val start = turnStartMs ?: return emptyList()
        turnStartMs = null
        return listOf(Observation(DrivingPattern.FAST_TURN, start, endMs, turnPeak, PATTERN_DETECTOR_VERSION))
    }
}
