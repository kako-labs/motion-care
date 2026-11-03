package dev.motioncare.data.trip.analysis

import dev.motioncare.core.model.*
import kotlin.math.abs
import kotlin.math.max

internal const val PATTERN_DETECTOR_VERSION = "pattern-v1"

/**
 * Starting values to tune against real signals. They describe vehicle motion only and are
 * not motion sickness limits. Change [PATTERN_DETECTOR_VERSION] when they change.
 */
internal data class PatternLimits(
    val stoppedBelowMps: Double = 0.5,
    val movingAboveMps: Double = 1.0,
    val maxGapMs: Long = 1_000,
    val accelerationWindowMs: Long = 1_000,
    val minAccelerationSpanMs: Long = 500,
    val rapidStartMps2: Double = 2.0,
    val rapidStartWindowMs: Long = 3_000,
    val hardStopMps2: Double = 2.0,
    val hardStopWindowMs: Long = 3_000,
    val swingMps2: Double = 0.8,
    val swingWindowMs: Long = 30_000,
    val swingCount: Int = 4,
)

/** Finds patterns one sample at a time so the same code can serve live guidance and saved trips. */
internal class PatternDetector(private val limits: PatternLimits = PatternLimits()) {
    private class Point(val elapsedMs: Long, val value: Double)

    private val speeds = ArrayDeque<Point>()
    private val accelerations = ArrayDeque<Point>()
    private val swingTimes = ArrayDeque<Long>()
    private var swingDirection = 0
    private var swingPeak = 0.0
    private var stopped: Boolean? = null
    private var startedAtMs: Long? = null
    private var startPeak = 0.0

    fun add(sample: SpeedSample): List<Observation> {
        val speed = sample.metersPerSecond
        if (sample.quality != Quality.VALID || speed == null) return finish()
        val found = mutableListOf<Observation>()
        val last = speeds.lastOrNull()
        if (last != null && sample.elapsedMs - last.elapsedMs > limits.maxGapMs) found += finish()
        val now = sample.elapsedMs
        val acceleration = acceleration(now, speed)
        if (acceleration != null) {
            accelerations.addLast(Point(now, acceleration))
            while (now - accelerations.first().elapsedMs > limits.hardStopWindowMs) accelerations.removeFirst()
            trackStart(now, acceleration, found)
            trackSwing(now, acceleration, found)
        }
        trackStopState(now, speed, acceleration, found)
        return found
    }

    /** Ends tracking at a signal gap or the end of a trip, without guessing across it. */
    fun finish(): List<Observation> {
        val found = speeds.lastOrNull()?.let { closeStart(it.elapsedMs) }.orEmpty()
        speeds.clear()
        accelerations.clear()
        swingTimes.clear()
        swingDirection = 0
        swingPeak = 0.0
        stopped = null
        startedAtMs = null
        startPeak = 0.0
        return found
    }

    private fun acceleration(now: Long, speed: Double): Double? {
        speeds.addLast(Point(now, speed))
        while (now - speeds.first().elapsedMs > limits.accelerationWindowMs) speeds.removeFirst()
        val oldest = speeds.first()
        val spanMs = now - oldest.elapsedMs
        return if (spanMs < limits.minAccelerationSpanMs) null else (speed - oldest.value) / (spanMs / 1000.0)
    }

    private fun trackStart(now: Long, acceleration: Double, found: MutableList<Observation>) {
        val started = startedAtMs ?: return
        startPeak = max(startPeak, acceleration)
        if (now - started >= limits.rapidStartWindowMs) found += closeStart(now)
    }

    private fun closeStart(endMs: Long): List<Observation> {
        val started = startedAtMs ?: return emptyList()
        startedAtMs = null
        if (startPeak < limits.rapidStartMps2) return emptyList()
        return listOf(Observation(DrivingPattern.RAPID_START, started, endMs, startPeak, PATTERN_DETECTOR_VERSION))
    }

    private fun trackSwing(now: Long, acceleration: Double, found: MutableList<Observation>) {
        val direction = when {
            acceleration >= limits.swingMps2 -> 1
            acceleration <= -limits.swingMps2 -> -1
            else -> swingDirection
        }
        if (direction != swingDirection) {
            if (swingDirection != 0) swingTimes.addLast(now)
            swingDirection = direction
        }
        while (swingTimes.isNotEmpty() && now - swingTimes.first() > limits.swingWindowMs) swingTimes.removeFirst()
        swingPeak = if (swingTimes.isEmpty()) 0.0 else max(swingPeak, abs(acceleration))
        if (swingTimes.size >= limits.swingCount) {
            found += Observation(DrivingPattern.REPEATED_SPEED_CHANGE, swingTimes.first(), now, swingPeak, PATTERN_DETECTOR_VERSION)
            swingTimes.clear()
            swingPeak = 0.0
        }
    }

    private fun trackStopState(now: Long, speed: Double, acceleration: Double?, found: MutableList<Observation>) {
        val was = stopped
        val isStopped = when (was) {
            null -> speed <= limits.stoppedBelowMps
            true -> speed < limits.movingAboveMps
            false -> speed <= limits.stoppedBelowMps
        }
        stopped = isStopped
        if (was == true && !isStopped) {
            startedAtMs = now
            startPeak = max(acceleration ?: 0.0, 0.0)
        }
        if (was == false && isStopped) {
            found += closeStart(now)
            hardStop(now)?.let { found += it }
            // Braking belongs to this stop only. Measure from here so a stop right after can't count it again.
            accelerations.clear()
            speeds.clear()
            speeds.addLast(Point(now, speed))
        }
    }

    private fun hardStop(now: Long): Observation? {
        val strong = accelerations.filter { -it.value >= limits.hardStopMps2 }
        if (strong.isEmpty()) return null
        val peak = strong.maxOf { -it.value }
        return Observation(DrivingPattern.HARD_STOP, strong.first().elapsedMs, now, peak, PATTERN_DETECTOR_VERSION)
    }
}
