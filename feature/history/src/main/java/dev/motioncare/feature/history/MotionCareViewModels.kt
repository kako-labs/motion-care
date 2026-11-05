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
)

class DepartureViewModel(
    private val trips: TripRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(DepartureUiState())
    val uiState = mutable.asStateFlow()
    init { observe() }
    private fun observe() = viewModelScope.launch {
        try {
            profiles.initialize(); trips.initialize()
            combine(changes(trips, profiles), profiles.departureGuidanceEnabled) { _, enabled -> enabled }.collectLatest { enabled ->
                val insights = loadInsights(trips, profiles)
                val seating = profiles.seating.value
                mutable.value = DepartureUiState(false, null, profiles.people.value, seating,
                    adviceFor(seating.driverId, insights), enabled)
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
    fun addPerson(name: String, kind: PersonKind) = launchAction { profiles.addPerson(name, kind) }
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
                mutable.value = AdviceUiState(false, null, adviceFor(driverId, all),
                    profiles.people.value.filter { it.id in passengerIds })
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
)
class TripExperienceViewModel(
    private val tripId: TripId,
    private val trips: TripRepository,
    private val profiles: ProfileRepository,
    private val guidance: GuidanceSettings? = null,
) : ViewModel() {
    private val fallbackGuidance = MutableStateFlow(true)
    private val mutable = MutableStateFlow(TripExperienceUiState())
    val uiState = mutable.asStateFlow()
    init { observe() }
    private fun observe() = viewModelScope.launch {
        try {
            profiles.initialize(); trips.initialize()
            linkSeatingIfMissing()
            changes(trips, profiles).collectLatest {
                val all = loadInsights(trips, profiles)
                val selected = all.firstOrNull { it.trip.id == tripId }
                val participants = profiles.participants.value.firstOrNull { it.tripId == tripId }
                mutable.value = TripExperienceUiState(false, null, selected, participants, profiles.people.value,
                    profiles.feedback.value.filter { it.tripId == tripId },
                    participants?.driverId?.let { driver -> all.filter { it.participants?.driverId == driver } }.orEmpty())
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { it.copy(loading = false, error = "운행 정보를 불러오지 못했습니다.") } }
    }
    // TEMPORARY: the app does not yet call assignCurrentSeating when a trip starts, so the
    // seating is attached the first time this trip's result, detail or rating screen opens.
    // Remove once the trip start hook exists (requested from the driving owner).
    private suspend fun linkSeatingIfMissing() {
        if (profiles.participants.value.any { it.tripId == tripId }) return
        if (profiles.seating.value.assignments.isEmpty()) return
        if (trips.records.value.none { it.id == tripId }) return
        profiles.assignCurrentSeating(tripId)
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