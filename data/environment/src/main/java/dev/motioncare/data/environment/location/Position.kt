package dev.motioncare.data.environment.location

import dev.motioncare.core.model.MissingReason
import java.math.BigDecimal
import java.math.RoundingMode

/** Where the car is, or why that is not known. */
internal sealed interface Position {
    data class Found(val latitude: Double, val longitude: Double) : Position
    data object Unsupported : Position
    data class Missing(val reason: MissingReason) : Position
}

internal fun interface LocationSource {
    suspend fun current(): Position
}

/**
 * About 11 km of latitude. Finer than the 0.4° forecast grid, so the request loses nothing, while the
 * provider and the database never see the exact position.
 */
internal fun roundCoordinate(value: Double): Double =
    BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP).toDouble()

internal fun Position.Found.rounded() = Position.Found(roundCoordinate(latitude), roundCoordinate(longitude))
