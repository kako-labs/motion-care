package dev.motioncare.data.trip.analysis

import dev.motioncare.core.model.*
import kotlin.math.max

internal const val PATTERN_DETECTOR_VERSION = "pattern-v1"

/**
 * Lines that separate observed vehicle motion, not motion sickness limits. Each value and its source is
 * listed in docs/detection-thresholds.md; update that table with any change here, and change
 * [PATTERN_DETECTOR_VERSION] once observations have been stored.
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
    val hardStopWindowMs: Long = 5_000,
    val swingMps2: Double = 0.9,
    val swingWindowMs: Long = 30_000,
    val swingCount: Int = 3,
)

/** Finds patterns one sample at a time so the same code can serve live guidance and saved trips. */
internal class PatternDetector(private val limits: PatternLimits = PatternLimits()) {
    private class Point(val elapsedMs: Long, val value: Double)

    private val speeds = ArrayDeque<Point>()
    private val accelerations = ArrayDeque<Point>()
    private val swing = SwingTracker(DrivingPattern.REPEATED_SPEED_CHANGE, limits.swingMps2, limits.swingWindowMs, limits.swingCount)
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
            swing.add(now, acceleration)?.let { found += it }
        }
        trackStopState(now, speed, acceleration, found)
        return found
    }

    /** Ends tracking at a signal gap or the end of a trip, without guessing across it. */
    fun finish(): List<Observation> {
        val found = speeds.lastOrNull()?.let { closeStart(it.elapsedMs) }.orEmpty()
        speeds.clear()
        accelerations.clear()
        swing.reset()
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
