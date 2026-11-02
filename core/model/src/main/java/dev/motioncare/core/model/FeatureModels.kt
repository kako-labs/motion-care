package dev.motioncare.core.model

data class PersonProfile(val id: PersonId, val displayName: String)
data class VehicleProfile(val id: VehicleId, val displayName: String)
data class TripParticipants(
    val tripId: TripId,
    val driverId: PersonId?,
    val passengerIds: Set<PersonId>,
    val revision: Long,
)
data class GuidanceSettings(val departureEnabled: Boolean = true, val liveEnabled: Boolean = true)
enum class Availability { AVAILABLE, LOADING, UNSUPPORTED, UNAVAILABLE, STALE }
data class EnvironmentReading(
    val value: Double?,
    val unit: String,
    val availability: Availability,
    val observedAtEpochMs: Long?,
    val sourceLabel: String,
)
data class Guidance(val message: String, val reason: String, val evidenceIds: List<String>)

// Independent from any medical scale; the actual questionnaire is a future feature.
sealed interface FeedbackAnswer {
    data class Rating(val value: Int, val scaleVersion: String) : FeedbackAnswer
    data object Skipped : FeedbackAnswer
}
data class TripFeedback(val tripId: TripId, val targetPersonId: PersonId, val answer: FeedbackAnswer)
