package dev.motioncare.core.model

enum class PersonKind { ADULT, CHILD }
enum class MotionSensitivity { UNSPECIFIED, LOW, MEDIUM, HIGH }
enum class MotionTrigger { START_STOP, REPEATED_SPEED_CHANGE, CURVE, CABIN_ENVIRONMENT }

data class PersonProfile(
    val id: PersonId,
    val displayName: String,
    val kind: PersonKind = PersonKind.ADULT,
    val motionSensitivity: MotionSensitivity = MotionSensitivity.UNSPECIFIED,
    val motionTriggers: Set<MotionTrigger> = emptySet(),
    val preferredRestMinutes: Int? = null,
    val preferredTemperatureCelsius: Double? = null,
    /** The character picked for this person, as a key of the app's character set. Null shows a face by [kind]. */
    val avatar: String? = null,
) {
    init {
        require(displayName.isNotBlank())
        require(avatar == null || avatar.isNotBlank())
        require(preferredRestMinutes == null || preferredRestMinutes > 0)
        require(preferredTemperatureCelsius == null || preferredTemperatureCelsius in 16.0..30.0)
    }
}

data class VehicleProfile(val id: VehicleId, val displayName: String)
enum class SeatPosition { DRIVER, FRONT_PASSENGER, REAR_LEFT, REAR_CENTER, REAR_RIGHT }
data class SeatingPlan(val assignments: Map<SeatPosition, PersonId> = emptyMap()) {
    val driverId get() = assignments[SeatPosition.DRIVER]
    val passengerIds get() = assignments.filterKeys { it != SeatPosition.DRIVER }.values.toSet()
    init { require(assignments.values.size == assignments.values.toSet().size) }
}
sealed interface SeatingResult {
    data class Updated(val plan: SeatingPlan) : SeatingResult
    data class Rejected(val reason: String) : SeatingResult
}
data class TripParticipants(
    val tripId: TripId,
    val driverId: PersonId?,
    val passengerIds: Set<PersonId>,
    val revision: Long,
    val seatAssignments: Map<SeatPosition, PersonId> = emptyMap(),
)
data class GuidanceSettings(val departureEnabled: Boolean = true, val liveEnabled: Boolean = true)
data class Guidance(val message: String, val reason: String, val evidenceIds: List<String>)

// Independent from any medical scale; the actual questionnaire is a future feature.
sealed interface FeedbackAnswer {
    data class Rating(val value: Int, val scaleVersion: String) : FeedbackAnswer
    data object Skipped : FeedbackAnswer
}
/**
 * Why a passenger felt uncomfortable. [FELT_HOT] and [STUFFY_AIR] record a feeling, not a cause: nausea itself can
 * make people feel warm (docs/environment.md). [CABIN_ENVIRONMENT] is kept for answers saved before those two
 * replaced it and is no longer offered.
 */
enum class FeedbackReason { START_STOP, REPEATED_SPEED_CHANGE, CURVE, CABIN_ENVIRONMENT, FELT_HOT, STUFFY_AIR, UNKNOWN }
enum class FeedbackRespondent { SELF, GUARDIAN }
data class TripFeedback(
    val tripId: TripId,
    val targetPersonId: PersonId,
    val answer: FeedbackAnswer,
    val reasons: Set<FeedbackReason> = emptySet(),
    val respondent: FeedbackRespondent = FeedbackRespondent.SELF,
    val createdAtEpochMs: Long = 0,
) {
    init {
        val rating = answer as? FeedbackAnswer.Rating
        require(rating == null || rating.value in 1..5)
        require(FeedbackReason.UNKNOWN !in reasons || reasons.size == 1)
        require(createdAtEpochMs >= 0)
    }
}