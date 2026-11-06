package dev.motioncare.data.trip.guidance

import dev.motioncare.core.model.AirGrade
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentAdvice
import dev.motioncare.core.model.EnvironmentSnapshot

/** Each value and its source is listed in docs/guidance-card.md. */
internal data class EnvironmentAdviceLimits(
    val recirculatingForMs: Long = 30 * 60_000L,
)

/**
 * Picks cabin air advice only when the car's air intake contradicts the outdoor air. Temperature never leads to
 * advice because no evidence gives a value for it. Old or missing values never lead to advice.
 */
internal class EnvironmentAdvisor(private val limits: EnvironmentAdviceLimits = EnvironmentAdviceLimits()) {
    private var recirculatingSinceMs: Long? = null

    /** [now] is measured from the start of the trip, in the same time base as speed samples. */
    fun onSnapshot(snapshot: EnvironmentSnapshot, now: Long): EnvironmentAdvice? {
        val recirculating = snapshot.recirculation.takeIf { it.availability == Availability.AVAILABLE }?.value
        recirculatingSinceMs = if (recirculating == true) recirculatingSinceMs ?: now else null
        val grade = snapshot.outdoorAir.takeIf { it.availability == Availability.AVAILABLE }?.value?.grade ?: return null
        val since = recirculatingSinceMs
        return when {
            recirculating == false && (grade == AirGrade.BAD || grade == AirGrade.VERY_BAD) ->
                EnvironmentAdvice.REDUCE_OUTSIDE_AIR
            since != null && grade == AirGrade.GOOD && now - since >= limits.recirculatingForMs ->
                EnvironmentAdvice.TRY_OUTSIDE_AIR
            else -> null
        }
    }
}
