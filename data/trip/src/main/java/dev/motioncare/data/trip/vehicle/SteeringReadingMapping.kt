package dev.motioncare.data.trip.vehicle

import dev.motioncare.data.trip.internal.SteeringReading

internal fun steeringReadingOf(timestampNanos: Long, degrees: Float?): SteeringReading? =
    degrees?.takeIf { it.isFinite() }?.let { SteeringReading(timestampNanos, it.toDouble()) }
