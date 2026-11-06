package dev.motioncare.data.environment.vehicle

import dev.motioncare.core.model.EnvironmentReading

/** Values the car reports about the outside and its climate control. Temperatures are °C. */
internal data class ClimateReadings(
    val outside: EnvironmentReading<Double>,
    val cabin: EnvironmentReading<Double>,
    val cabinSet: EnvironmentReading<Double>,
    val recirculation: EnvironmentReading<Boolean>,
    val airCleaner: EnvironmentReading<Boolean>,
)

internal fun interface ClimateSource {
    /** Never throws for a missing value; each reading carries its own state. */
    suspend fun read(): ClimateReadings
}

/** The car reports Float; widening it directly turns 19.1 into 19.100000381469727. */
internal fun celsiusOf(value: Float?): Double? = value?.takeUnless { it.isNaN() }?.toString()?.toDouble()
