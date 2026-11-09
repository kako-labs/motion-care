package dev.motioncare.data.profile

import dev.motioncare.core.model.*
import kotlinx.coroutines.flow.StateFlow

/** Profile, seating and passenger feedback data owned by the history feature. */
interface ProfileRepository {
    val people: StateFlow<List<PersonProfile>>
    val seating: StateFlow<SeatingPlan>
    val participants: StateFlow<List<TripParticipants>>
    val feedback: StateFlow<List<TripFeedback>>
    /** Departure advice switch. The live guidance card switch stays in data/trip. */
    val departureGuidanceEnabled: StateFlow<Boolean>
    suspend fun initialize()
    suspend fun addPerson(displayName: String, kind: PersonKind = PersonKind.ADULT): PersonProfile
    suspend fun updatePerson(profile: PersonProfile)
    suspend fun placePerson(personId: PersonId, seat: SeatPosition): SeatingResult
    suspend fun clearSeat(seat: SeatPosition): SeatingPlan
    suspend fun assignCurrentSeating(tripId: TripId): TripParticipants
    suspend fun saveFeedback(feedback: TripFeedback)
    fun setDepartureGuidanceEnabled(enabled: Boolean)
}