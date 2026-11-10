package dev.motioncare.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.GuidanceSettings
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.insights.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DepartureUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val people: List<PersonProfile> = emptyList(),
    val seating: SeatingPlan = SeatingPlan(),
    val advice: AdviceInsight = adviceFor(null, emptyList()),
    val guidanceEnabled: Boolean = true,
    /** The most recent finished trip in this car, whoever drove. */
    val lastTrip: TripInsight? = null,
    val recentTrips: List<TripInsight> = emptyList(),
    /** Live departure reading. Missing and unsupported values remain explicit in the reading. */
    val environment: EnvironmentSnapshot? = null,
)

class DepartureViewModel(
    private val trips: TripRepository,
    private val profiles: ProfileRepository,
    private val environment: Flow<EnvironmentSnapshot?> = flowOf(null),
) : ViewModel() {
    private val mutable = MutableStateFlow(DepartureUiState())
    val uiState = mutable.asStateFlow()
    init { observe() }
    private fun observe() = viewModelScope.launch {
        try {
            profiles.initialize(); trips.initialize()
            combine(changes(trips, profiles), profiles.departureGuidanceEnabled, environment) { _, enabled, snapshot ->
                enabled to snapshot
            }.collectLatest { (enabled, snapshot) ->
                val insights = loadInsights(trips, profiles)
                val seating = profiles.seating.value
                mutable.value = DepartureUiState(false, null, profiles.people.value, seating,
                    adviceFor(seating.driverId, insights), enabled,
                    insights.filter { it.trip.status == TripStatus.COMPLETED }.maxByOrNull { it.trip.startedAtEpochMs },
                    insights.filter { it.participants?.driverId == seating.driverId && seating.driverId != null && it.trip.status == TripStatus.COMPLETED }.take(3),
                    snapshot)
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(loading = false, error = "정보를 불러오지 못했습니다.") } }
    }
    fun place(person: PersonId, seat: SeatPosition) = launchAction {
        when (val result = profiles.placePerson(person, seat)) {
            is SeatingResult.Rejected -> mutable.update { it.copy(error = result.reason) }
            is SeatingResult.Updated -> Unit
        }
    }
    fun clear(seat: SeatPosition) = launchAction { profiles.clearSeat(seat) }
    /** Adds a person and, when the drawer was opened from a seat, seats them there right away. */
    fun addPerson(name: String, kind: PersonKind, seat: SeatPosition? = null) = launchAction {
        val person = profiles.addPerson(name, kind)
        if (seat != null) (profiles.placePerson(person.id, seat) as? SeatingResult.Rejected)
            ?.let { rejected -> mutable.update { it.copy(error = rejected.reason) } }
    }
    fun toggleGuidance() = profiles.setDepartureGuidanceEnabled(!mutable.value.guidanceEnabled)
    private fun launchAction(action: suspend () -> Unit) = viewModelScope.launch {
        try { mutable.update { it.copy(error = null) }; action() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(error = "저장하지 못했습니다.") } }
    }
}

data class PersonDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val person: PersonProfile? = null,
    val driverTrips: List<TripInsight> = emptyList(),
    val passengerFeedback: List<TripFeedback> = emptyList(),
    val advice: AdviceInsight = adviceFor(null, emptyList()),
)

class PersonDetailViewModel(
    private val personId: PersonId,
    private val trips: TripRepository,
    private val profiles: ProfileRepository,
    private val guidance: GuidanceSettings? = null,
) : ViewModel() {
    private val fallbackGuidance = MutableStateFlow(true)
    private val mutable = MutableStateFlow(PersonDetailUiState())
    val uiState = mutable.asStateFlow()
    init { viewModelScope.launch {
        try {
            profiles.initialize(); trips.initialize()
            changes(trips, profiles).collectLatest {
                val all = loadInsights(trips, profiles)
                mutable.value = PersonDetailUiState(false, null, profiles.people.value.firstOrNull { it.id == personId },
                    all.filter { it.participants?.driverId == personId },
                    profiles.feedback.value.filter { it.targetPersonId == personId }, adviceFor(personId, all))
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(loading = false, error = "프로필을 불러오지 못했습니다.") } }
    } }
    fun update(profile: PersonProfile) = viewModelScope.launch {
        try { profiles.updatePerson(profile) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(error = "프로필을 저장하지 못했습니다.") } }
    }
}

data class AdviceUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val advice: AdviceInsight = adviceFor(null, emptyList()),
    val passengers: List<PersonProfile> = emptyList(),
    /** Each passenger's comfort ratings on this driver's recent trips, oldest first. */
    val passengerRatings: Map<PersonId, List<Int>> = emptyMap(),
    val driverTrips: List<TripInsight> = emptyList(),
)
class AdviceViewModel(
    private val driverId: PersonId?,
    private val trips: TripRepository,
    private val profiles: ProfileRepository,
    private val guidance: GuidanceSettings? = null,
) : ViewModel() {
    private val fallbackGuidance = MutableStateFlow(true)
    private val mutable = MutableStateFlow(AdviceUiState())
    val uiState = mutable.asStateFlow()
    init { viewModelScope.launch {
        try {
            profiles.initialize(); trips.initialize()
            changes(trips, profiles).collectLatest {
                val all = loadInsights(trips, profiles)
                val passengerIds = profiles.seating.value.passengerIds
                val driven = all.filter { it.participants?.driverId == driverId && driverId != null }
                    .sortedBy { it.trip.startedAtEpochMs }.map { it.trip.id }
                val ratings = passengerIds.associateWith { passenger ->
                    driven.mapNotNull { trip ->
                        profiles.feedback.value.firstOrNull { it.tripId == trip && it.targetPersonId == passenger }
                            ?.let { (it.answer as? FeedbackAnswer.Rating)?.value }
                    }.takeLast(5)
                }
                mutable.value = AdviceUiState(false, null, adviceFor(driverId, all),
                    profiles.people.value.filter { it.id in passengerIds }, ratings,
                    all.filter { driverId != null && it.participants?.driverId == driverId && it.trip.status == TripStatus.COMPLETED && it.analyzedDurationMs >= 60_000 })
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(loading = false, error = "조언 근거를 불러오지 못했습니다.") } }
    } }
}

data class TripExperienceUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val insight: TripInsight? = null,
    val participants: TripParticipants? = null,
    val people: List<PersonProfile> = emptyList(),
    val feedback: List<TripFeedback> = emptyList(),
    val recentDriverTrips: List<TripInsight> = emptyList(),
    val savingFeedback: Boolean = false,
    /** Values stored for this trip in capture order. Empty when none were stored or they could not be read. */
    val environment: List<EnvironmentSnapshot> = emptyList(),
)
class TripExperienceViewModel(
    private val tripId: TripId,
    private val trips: TripRepository,
    private val profiles: ProfileRepository,
    private val guidance: GuidanceSettings? = null,
    private val tripEnvironment: suspend (TripId) -> List<EnvironmentSnapshot> = { emptyList() },
) : ViewModel() {
    private val fallbackGuidance = MutableStateFlow(true)
    private val mutable = MutableStateFlow(TripExperienceUiState())
    val uiState = mutable.asStateFlow()
    init { observe() }
    private fun observe() = viewModelScope.launch {
        try {
            profiles.initialize(); trips.initialize()
            // Stored once per capture and never edited, so one read is enough. A failed read shows nothing
            // rather than another trip's values.
            val environment = try { tripEnvironment(tripId) } catch (error: CancellationException) { throw error } catch (_: Exception) { emptyList() }
            changes(trips, profiles).collectLatest {
                val all = loadInsights(trips, profiles)
                val selected = all.firstOrNull { it.trip.id == tripId }
                val participants = profiles.participants.value.firstOrNull { it.tripId == tripId }
                mutable.value = TripExperienceUiState(false, null, selected, participants, profiles.people.value,
                    profiles.feedback.value.filter { it.tripId == tripId },
                    participants?.driverId?.let { driver -> all.filter { it.participants?.driverId == driver } }.orEmpty(),
                    environment = environment)
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(loading = false, error = "운행 정보를 불러오지 못했습니다.") } }
    }
    fun saveRating(
        personId: PersonId,
        rating: Int,
        reasons: Set<FeedbackReason>,
        respondent: FeedbackRespondent,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        mutable.update { it.copy(savingFeedback = true, error = null) }
        try {
            profiles.saveFeedback(TripFeedback(tripId, personId, FeedbackAnswer.Rating(rating, "COMFORT_5_V1"),
                reasons, respondent, System.currentTimeMillis()))
            mutable.update { it.copy(savingFeedback = false) }
            onSaved()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(savingFeedback = false, error = "평가를 저장하지 못했습니다.") } }
    }
}

private fun changes(trips: TripRepository, profiles: ProfileRepository): Flow<Unit> = merge(
    trips.records.map { Unit }, profiles.people.map { Unit }, profiles.seating.map { Unit },
    profiles.participants.map { Unit }, profiles.feedback.map { Unit },
)
