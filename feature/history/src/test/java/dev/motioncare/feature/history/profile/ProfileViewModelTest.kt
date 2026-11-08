package dev.motioncare.feature.history.profile

import androidx.lifecycle.viewModelScope
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class FakeRepository : ProfileRepository {
        override val people = MutableStateFlow<List<PersonProfile>>(emptyList())
        override val seating = MutableStateFlow(SeatingPlan())
        override val participants = MutableStateFlow<List<TripParticipants>>(emptyList())
        override val feedback = MutableStateFlow<List<TripFeedback>>(emptyList())
        override val departureGuidanceEnabled = MutableStateFlow(true)
        var failInitialize = false
        override suspend fun initialize() { if (failInitialize) throw IOException("disk") }
        override suspend fun addPerson(displayName: String, kind: PersonKind) = error("Not used")
        override suspend fun updatePerson(profile: PersonProfile) = error("Not used")
        override suspend fun placePerson(personId: PersonId, seat: SeatPosition) = error("Not used")
        override suspend fun clearSeat(seat: SeatPosition) = error("Not used")
        override suspend fun assignCurrentSeating(tripId: TripId) = error("Not used")
        override suspend fun saveFeedback(feedback: TripFeedback) = error("Not used")
        override fun setDepartureGuidanceEnabled(enabled: Boolean) = error("Not used")
    }

    @Test fun `screen starts loading then shows an empty list`() = runTest(dispatcher) {
        val model = ProfileViewModel(FakeRepository())
        try {
            assertTrue(model.uiState.value.loading)
            advanceUntilIdle()
            assertEquals(ProfileUiState(loading = false), model.uiState.value)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `people from the repository reach the screen`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = ProfileViewModel(repository)
        try {
            advanceUntilIdle()
            val person = PersonProfile(PersonId("one"), "민준")
            repository.people.value = listOf(person)
            advanceUntilIdle()
            assertEquals(listOf(person), model.uiState.value.people)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun `load failure is shown and retry recovers`() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failInitialize = true }
        val model = ProfileViewModel(repository)
        try {
            advanceUntilIdle()
            assertEquals(ProfileUiState(loading = false, failed = true), model.uiState.value)
            repository.failInitialize = false
            model.retry()
            assertEquals(ProfileUiState(loading = true), model.uiState.value)
            advanceUntilIdle()
            assertEquals(ProfileUiState(loading = false), model.uiState.value)
        } finally { model.viewModelScope.cancel() }
    }
}