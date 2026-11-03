package dev.motioncare.data.trip.vehicle

import dev.motioncare.core.model.Quality
import dev.motioncare.data.trip.internal.SpeedReading
import kotlin.math.abs

/** Reverse driving reports negative speed; analysis only needs its magnitude. */
internal fun speedReadingOf(timestampNanos: Long, available: Boolean, metersPerSecond: Double?): SpeedReading {
    val value = metersPerSecond?.takeIf { available && it.isFinite() }
    return if (value == null) SpeedReading(timestampNanos, null, Quality.UNAVAILABLE)
    else SpeedReading(timestampNanos, abs(value), Quality.VALID)
}
