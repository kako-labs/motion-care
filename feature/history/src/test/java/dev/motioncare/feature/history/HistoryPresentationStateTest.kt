package dev.motioncare.feature.history

import androidx.lifecycle.viewModelScope
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryPresentationStateTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val driver = PersonId("selected")
    private val other = PersonId("other")
    private val rider = PersonId("rider")
    private class Trips(val snapshots: List<TripSnapshot>, val gate: CompletableDeferred<Unit>? = null) : TripRepository {
        override val records = MutableStateFlow(snapshots.map { it.trip })
        override suspend fun initialize() = Unit
        override suspend fun get(id: TripId): TripSnapshot? { gate?.await(); return snapshots.firstOrNull { it.trip.id == id } }
        override suspend fun start(vehicleId: VehicleId?) = error("Must not start from presentation")
        override suspend fun captureSample() = error("Must not capture from presentation")
        override suspend fun finish() = error("Must not finish from presentation")
    }
    private class Profiles(driver: PersonId, rider: PersonId, links: List<TripParticipants>) : ProfileRepository {
        override val people = MutableStateFlow(listOf(PersonProfile(driver, "운전자"), PersonProfile(rider, "동승자", PersonKind.CHILD)))
        override val seating = MutableStateFlow(SeatingPlan(mapOf(SeatPosition.DRIVER to driver, SeatPosition.REAR_RIGHT to rider)))
        override val participants = MutableStateFlow(links)
        override val feedback = MutableStateFlow<List<TripFeedback>>(emptyList())
        override val departureGuidanceEnabled = MutableStateFlow(true)
        var failSave = false
        var assignCalls = 0
        override suspend fun initialize() = Unit
        override suspend fun addPerson(displayName: String, kind: PersonKind) = error("Unused")
        override suspend fun updatePerson(profile: PersonProfile) = Unit
        override suspend fun placePerson(personId: PersonId, seat: SeatPosition) = error("Unused")
        override suspend fun clearSeat(seat: SeatPosition) = error("Unused")
        override suspend fun assignCurrentSeating(tripId: TripId): TripParticipants {
            assignCalls += 1
            error("Presentation must not attach current seating to a past trip")
        }
        override suspend fun saveFeedback(feedback: TripFeedback) {
            if (failSave) throw java.io.IOException("disk full")
            this.feedback.value = listOf(feedback)
        }
        override fun setDepartureGuidanceEnabled(enabled: Boolean) { departureGuidanceEnabled.value = enabled }
    }
    private fun trip(id: String, duration: Long = 120_000, status: TripStatus = TripStatus.COMPLETED) =
        TripSnapshot(TripRecord(TripId(id), 1000, 1000 + duration, status, origin = DataOrigin.VEHICLE),
            (0..duration step 1000).map { SpeedSample(it, 10.0, Quality.VALID) })

    private fun environment(at: Long) = EnvironmentSnapshot(
        at,
        EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE),
        EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE),
        EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE),
        EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE),
        EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE),
        EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.OPEN_METEO_CAMS),
    )

    @Test fun selectedDriverOnlyAndEmptyRepositoryStayFaithfulToStorage() = runTest(dispatcher) {
        val trips = Trips(listOf(trip("mine"), trip("other")))
        val profiles = Profiles(driver, rider, listOf(TripParticipants(TripId("mine"), driver, setOf(rider), 0), TripParticipants(TripId("other"), other, setOf(rider), 0)))
        val environment = MutableStateFlow<EnvironmentSnapshot?>(null)
        val model = DepartureViewModel(trips, profiles, environment)
        try {
            advanceUntilIdle()
            assertEquals(listOf("mine"), model.uiState.value.recentTrips.map { it.trip.id.value })
            val unsupported = environment(1234)
            environment.value = unsupported
            advanceUntilIdle()
            assertEquals(unsupported, model.uiState.value.environment)
            profiles.seating.value = SeatingPlan()
            advanceUntilIdle()
            assertTrue(model.uiState.value.recentTrips.isEmpty())
        } finally { model.viewModelScope.cancel() }
        val empty = DepartureViewModel(Trips(emptyList()), profiles)
        try { advanceUntilIdle(); assertTrue(empty.uiState.value.recentTrips.isEmpty()); assertNull(empty.uiState.value.advice.latestCount) }
        finally { empty.viewModelScope.cancel() }
    }

    @Test fun peopleAndSeatsShowWhileTripsAreStillBeingRead() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val profiles = Profiles(driver, rider, listOf(TripParticipants(TripId("mine"), driver, setOf(rider), 0)))
        val model = DepartureViewModel(Trips(listOf(trip("mine")), gate), profiles)
        try {
            advanceUntilIdle()
            assertTrue(model.uiState.value.loading)
            assertEquals(2, model.uiState.value.people.size)
            assertEquals(driver, model.uiState.value.seating.driverId)
            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(model.uiState.value.loading)
            assertEquals(listOf("mine"), model.uiState.value.recentTrips.map { it.trip.id.value })
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun adviceDoesNotChooseAnInterruptedOrShortTripAsEvidence() = runTest(dispatcher) {
        val trips = Trips(listOf(trip("good"), trip("short", 30_000), trip("interrupted", status = TripStatus.INTERRUPTED)))
        val links = trips.records.value.map { TripParticipants(it.id, driver, setOf(rider), 0) }
        val model = AdviceViewModel(driver, trips, Profiles(driver, rider, links))
        try { advanceUntilIdle(); assertEquals(listOf("good"), model.uiState.value.driverTrips.map { it.trip.id.value }) }
        finally { model.viewModelScope.cancel() }
    }

    @Test fun feedbackFailureKeepsTheScreenOpenAndSuccessUsesTheExistingRepository() = runTest(dispatcher) {
        val profiles = Profiles(driver, rider, listOf(TripParticipants(TripId("one"), driver, setOf(rider), 0)))
        val model = TripExperienceViewModel(TripId("one"), Trips(listOf(trip("one"))), profiles)
        try {
            advanceUntilIdle()
            var completed = false
            profiles.failSave = true
            model.saveRating(rider, 4, setOf(FeedbackReason.CURVE), FeedbackRespondent.GUARDIAN) { completed = true }
            advanceUntilIdle()
            assertFalse(completed); assertNotNull(model.uiState.value.error); assertFalse(model.uiState.value.savingFeedback)
            profiles.failSave = false
            model.saveRating(rider, 4, setOf(FeedbackReason.CURVE), FeedbackRespondent.GUARDIAN) { completed = true }
            advanceUntilIdle()
            assertTrue(completed)
            assertEquals(FeedbackAnswer.Rating(4, "COMFORT_5_V1"), profiles.feedback.value.single().answer)
            assertEquals(FeedbackRespondent.GUARDIAN, profiles.feedback.value.single().respondent)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun tripDetailShowsOnlyTheValuesStoredForThatTrip() = runTest(dispatcher) {
        val profiles = Profiles(driver, rider, emptyList())
        val asked = mutableListOf<TripId>()
        val model = TripExperienceViewModel(TripId("one"), Trips(listOf(trip("one"))), profiles,
            tripEnvironment = { id -> asked += id; listOf(environment(5_000)) })
        try {
            advanceUntilIdle()
            assertEquals(listOf(TripId("one")), asked)
            assertEquals(listOf(5_000L), model.uiState.value.environment.map { it.capturedAtEpochMs })
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun unreadableTripEnvironmentShowsNothingAndKeepsTheTrip() = runTest(dispatcher) {
        val profiles = Profiles(driver, rider, emptyList())
        val model = TripExperienceViewModel(TripId("one"), Trips(listOf(trip("one"))), profiles,
            tripEnvironment = { error("database closed") })
        try {
            advanceUntilIdle()
            assertNull(model.uiState.value.error)
            assertNotNull(model.uiState.value.insight)
            assertTrue(model.uiState.value.environment.isEmpty())
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun tripDetailReadsTheChosenTripInFull() = runTest(dispatcher) {
        val links = listOf("one", "two").map { TripParticipants(TripId(it), driver, setOf(rider), 0) }
        val model = TripExperienceViewModel(TripId("one"), Trips(listOf(trip("one"), trip("two"))), Profiles(driver, rider, links))
        try {
            advanceUntilIdle()
            val insight = model.uiState.value.insight!!
            assertEquals(TripId("one"), insight.trip.id)
            assertEquals(121, insight.snapshot.samples.size)
            assertEquals(driver, insight.participants?.driverId)
            assertEquals(2, model.uiState.value.recentDriverTrips.size)
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun adviceKeepsTheLatestTripInFullForItsSpeedChart() = runTest(dispatcher) {
        fun at(id: String, started: Long) = trip(id).let { it.copy(trip = it.trip.copy(startedAtEpochMs = started, endedAtEpochMs = started + 120_000)) }
        val trips = Trips(listOf(at("older", 1_000), at("latest", 500_000)))
        val links = trips.records.value.map { TripParticipants(it.id, driver, setOf(rider), 0) }
        val model = AdviceViewModel(driver, trips, Profiles(driver, rider, links))
        try {
            advanceUntilIdle()
            val byId = model.uiState.value.driverTrips.associateBy { it.trip.id.value }
            assertEquals(121, byId.getValue("latest").snapshot.samples.size)
            assertTrue(byId.getValue("older").snapshot.samples.isEmpty())
        } finally { model.viewModelScope.cancel() }
    }

    @Test fun missingStartSnapshotRemainsMissingInsteadOfUsingCurrentSeating() = runTest(dispatcher) {
        val profiles = Profiles(driver, rider, emptyList())
        val model = TripExperienceViewModel(TripId("one"), Trips(listOf(trip("one"))), profiles)
        try {
            advanceUntilIdle()
            assertFalse(model.uiState.value.loading)
            assertNull(model.uiState.value.error)
            assertNull(model.uiState.value.participants)
            assertEquals(0, profiles.assignCalls)
        } finally { model.viewModelScope.cancel() }
    }
}
