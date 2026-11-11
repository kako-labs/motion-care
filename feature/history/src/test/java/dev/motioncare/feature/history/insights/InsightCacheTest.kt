package dev.motioncare.feature.history.insights

import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InsightCacheTest {
    private class Trips(snapshots: List<TripSnapshot>) : TripRepository {
        val stored = snapshots.associateBy { it.trip.id }.toMutableMap()
        val reads = mutableListOf<TripId>()
        override val records = MutableStateFlow(snapshots.map { it.trip })
        override suspend fun initialize() = Unit
        override suspend fun get(id: TripId) = stored[id].also { reads += id }
        override suspend fun start(vehicleId: VehicleId?) = error("Unused")
        override suspend fun captureSample() = error("Unused")
        override suspend fun finish() = error("Unused")
    }

    private class Profiles(links: List<TripParticipants> = emptyList()) : ProfileRepository {
        override val people = MutableStateFlow<List<PersonProfile>>(emptyList())
        override val seating = MutableStateFlow(SeatingPlan())
        override val participants = MutableStateFlow(links)
        override val feedback = MutableStateFlow<List<TripFeedback>>(emptyList())
        override val departureGuidanceEnabled = MutableStateFlow(true)
        override suspend fun initialize() = Unit
        override suspend fun addPerson(displayName: String, kind: PersonKind) = error("Unused")
        override suspend fun updatePerson(profile: PersonProfile) = error("Unused")
        override suspend fun placePerson(personId: PersonId, seat: SeatPosition) = error("Unused")
        override suspend fun clearSeat(seat: SeatPosition) = error("Unused")
        override suspend fun assignCurrentSeating(tripId: TripId) = error("Unused")
        override suspend fun saveFeedback(feedback: TripFeedback) = error("Unused")
        override fun setDepartureGuidanceEnabled(enabled: Boolean) = Unit
    }

    private fun trip(id: String, status: TripStatus = TripStatus.COMPLETED, revision: Long = 3) = TripSnapshot(
        TripRecord(TripId(id), 1_000, if (status == TripStatus.RECORDING) null else 601_000, status,
            origin = DataOrigin.VEHICLE, revision = revision),
        (0L..600_000L step 1_000L).map { SpeedSample(it, 10.0, Quality.VALID) },
        listOf(Observation(DrivingPattern.HARD_STOP, 100_000, 101_000, 2.4, "test-v1")),
        listOf(SteeringSample(5_000, 12.0)),
    )

    @Test fun `each finished trip is read once however many screens load the history`() = runTest {
        val trips = Trips(listOf(trip("a"), trip("b")))
        val profiles = Profiles()

        loadInsights(trips, profiles)
        val second = loadInsights(trips, profiles)

        assertEquals(listOf(TripId("a"), TripId("b")), trips.reads.sortedBy { it.value })
        assertEquals(1.0, second.first { it.trip.id == TripId("a") }.rate(DrivingPattern.HARD_STOP)!!, 0.001)
    }

    @Test fun `a trip still recording is read again on every load`() = runTest {
        val trips = Trips(listOf(trip("live", TripStatus.RECORDING)))

        loadInsights(trips, Profiles())
        loadInsights(trips, Profiles())

        assertEquals(2, trips.reads.size)
    }

    @Test fun `a trip is read again once its stored revision moves`() = runTest {
        val trips = Trips(listOf(trip("a", revision = 3)))
        val profiles = Profiles()
        loadInsights(trips, profiles)

        val updated = trip("a", revision = 4)
        trips.stored[updated.trip.id] = updated
        trips.records.value = listOf(updated.trip)
        loadInsights(trips, profiles)

        assertEquals(2, trips.reads.size)
    }

    @Test fun `history keeps each trip's numbers but not its samples`() = runTest {
        val loaded = loadInsights(Trips(listOf(trip("a"))), Profiles()).single()

        assertEquals(6_000.0, loaded.distanceMeters, 0.001)
        assertEquals(600_000L, loaded.analyzedDurationMs)
        assertEquals(1, loaded.count(DrivingPattern.HARD_STOP))
        assertTrue(loaded.snapshot.samples.isEmpty())
        assertTrue(loaded.snapshot.steering.isEmpty())
    }

    @Test fun `the current seating of each trip comes with every load`() = runTest {
        val trips = Trips(listOf(trip("a")))
        val profiles = Profiles()
        loadInsights(trips, profiles)

        val link = TripParticipants(TripId("a"), PersonId("driver"), emptySet(), 0)
        profiles.participants.value = listOf(link)

        assertEquals(link, loadInsights(trips, profiles).single().participants)
    }

    @Test fun `one trip can still be read in full`() = runTest {
        val trips = Trips(listOf(trip("a")))
        val link = TripParticipants(TripId("a"), PersonId("driver"), emptySet(), 0)

        val full = fullInsight(trips, TripId("a"), link)!!

        assertEquals(601, full.snapshot.samples.size)
        assertEquals(1, full.snapshot.steering.size)
        assertEquals(link, full.participants)
        assertNull(fullInsight(trips, TripId("missing"), null))
    }
}
