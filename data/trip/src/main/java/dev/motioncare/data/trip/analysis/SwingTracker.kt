package dev.motioncare.data.trip.analysis

import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.Observation
import kotlin.math.abs
import kotlin.math.max

/** Reports when a signal changes direction enough times inside a window, ignoring small values. */
internal class SwingTracker(
    private val pattern: DrivingPattern,
    private val limit: Double,
    private val windowMs: Long,
    private val count: Int,
) {
    private val times = ArrayDeque<Long>()
    private var direction = 0
    private var peak = 0.0

    fun add(now: Long, value: Double): Observation? {
        val next = when {
            value >= limit -> 1
            value <= -limit -> -1
            else -> direction
        }
        if (next != direction) {
            if (direction != 0) times.addLast(now)
            direction = next
        }
        while (times.isNotEmpty() && now - times.first() > windowMs) times.removeFirst()
        peak = if (times.isEmpty()) 0.0 else max(peak, abs(value))
        if (times.size < count) return null
        val found = Observation(pattern, times.first(), now, peak, PATTERN_DETECTOR_VERSION)
        times.clear()
        peak = 0.0
        return found
    }

    fun reset() {
        times.clear()
        direction = 0
        peak = 0.0
    }
}
