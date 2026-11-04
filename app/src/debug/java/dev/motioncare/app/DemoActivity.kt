package dev.motioncare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.motioncare.core.model.CommandResult
import dev.motioncare.core.model.DataOrigin
import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.FeedbackAnswer
import dev.motioncare.core.model.FeedbackReason
import dev.motioncare.core.model.FeedbackRespondent
import dev.motioncare.core.model.GuidanceEvent
import dev.motioncare.core.model.GuidanceOutcome
import dev.motioncare.core.model.MotionSensitivity
import dev.motioncare.core.model.MotionTrigger
import dev.motioncare.core.model.Observation
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.PersonKind
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.core.model.Quality
import dev.motioncare.core.model.SeatPosition
import dev.motioncare.core.model.SeatingPlan
import dev.motioncare.core.model.SeatingResult
import dev.motioncare.core.model.SpeedSample
import dev.motioncare.core.model.SteeringSample
import dev.motioncare.core.model.TripFeedback
import dev.motioncare.core.model.TripId
import dev.motioncare.core.model.TripParticipants
import dev.motioncare.core.model.TripRecord
import dev.motioncare.core.model.TripSnapshot
import dev.motioncare.core.model.TripStatus
import dev.motioncare.core.model.VehicleId
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin

/** Debug-only entry point that renders the real app with vehicle-shaped demo data. */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val fixtures = DemoFixtures()
        setContent {
            MainHost(
                trips = fixtures.trips,
                profiles = fixtures.profiles,
                vehicleConnected = false,
                guidance = null,
                onRecordingStarted = {},
            )
        }
    }
}

private class DemoFixtures {
    private val driverId = PersonId("demo-driver-minjun")
    private val passengerId = PersonId("demo-passenger-seoa")
    private val vehicleId = VehicleId("demo-medium-ev")
    private val now = System.currentTimeMillis()

    private val snapshots = listOf(
        trip(
            id = "demo-trip-latest",
            startedAt = now - 52L * 60_000L,
            durationMinutes = 38,
            patternCounts = mapOf(
                DrivingPattern.HARD_STOP to 5,
                DrivingPattern.RAPID_START to 3,
                DrivingPattern.REPEATED_SPEED_CHANGE to 2,
                DrivingPattern.FAST_TURN to 1,
            ),
            shownGuidance = true,
        ),
        trip(
            id = "demo-trip-previous-1",
            startedAt = now - 1L * 24L * 60L * 60_000L,
            durationMinutes = 34,
            patternCounts = mapOf(
                DrivingPattern.HARD_STOP to 8,
                DrivingPattern.RAPID_START to 4,
                DrivingPattern.REPEATED_SPEED_CHANGE to 3,
                DrivingPattern.FAST_TURN to 2,
            ),
        ),
        trip(
            id = "demo-trip-previous-2",
            startedAt = now - 2L * 24L * 60L * 60_000L,
            durationMinutes = 31,
            patternCounts = mapOf(
                DrivingPattern.HARD_STOP to 9,
                DrivingPattern.RAPID_START to 5,
                DrivingPattern.REPEATED_SPEED_CHANGE to 4,
                DrivingPattern.FAST_TURN to 2,
            ),
        ),
    )

    private val people = listOf(
        PersonProfile(
            id = driverId,
            displayName = "민준",
            kind = PersonKind.ADULT,
            motionSensitivity = MotionSensitivity.LOW,
            motionTriggers = setOf(MotionTrigger.START_STOP),
        ),
        PersonProfile(
            id = passengerId,
            displayName = "서아",
            kind = PersonKind.CHILD,
            motionSensitivity = MotionSensitivity.HIGH,
            motionTriggers = setOf(
                MotionTrigger.START_STOP,
                MotionTrigger.REPEATED_SPEED_CHANGE,
                MotionTrigger.CURVE,
            ),
            preferredRestMinutes = 20,
            preferredTemperatureCelsius = 22.0,
        ),
    )

    private val tripParticipants = snapshots.map { snapshot ->
        TripParticipants(
            tripId = snapshot.trip.id,
            driverId = driverId,
            passengerIds = setOf(passengerId),
            revision = 1,
            seatAssignments = mapOf(
                SeatPosition.DRIVER to driverId,
                SeatPosition.REAR_RIGHT to passengerId,
            ),
        )
    }

    val trips: TripRepository = DemoTripRepository(snapshots)
    val profiles: ProfileRepository = DemoProfileRepository(
        initialPeople = people,
        initialSeating = SeatingPlan(
            mapOf(
                SeatPosition.DRIVER to driverId,
                SeatPosition.REAR_RIGHT to passengerId,
            ),
        ),
        initialParticipants = tripParticipants,
        initialFeedback = listOf(
            TripFeedback(
                tripId = snapshots.first().trip.id,
                targetPersonId = passengerId,
                answer = FeedbackAnswer.Rating(3, "COMFORT_5_V1"),
                reasons = setOf(FeedbackReason.START_STOP),
                respondent = FeedbackRespondent.GUARDIAN,
                createdAtEpochMs = snapshots.first().trip.endedAtEpochMs ?: now,
            ),
        ),
    )

    private fun trip(
        id: String,
        startedAt: Long,
        durationMinutes: Int,
        patternCounts: Map<DrivingPattern, Int>,
        shownGuidance: Boolean = false,
    ): TripSnapshot {
        val durationMs = durationMinutes * 60_000L
        val record = TripRecord(
            id = TripId(id),
            startedAtEpochMs = startedAt,
            endedAtEpochMs = startedAt + durationMs,
            status = TripStatus.COMPLETED,
            vehicleId = vehicleId,
            origin = DataOrigin.VEHICLE,
            revision = 1,
        )
        val observations = patternCounts.entries.flatMapIndexed { patternIndex, (pattern, count) ->
            List(count) { index ->
                val at = 45_000L + patternIndex * 70_000L + index * 180_000L
                Observation(
                    pattern = pattern,
                    startMs = at.coerceAtMost(durationMs - 5_000L),
                    endMs = (at + 2_000L).coerceAtMost(durationMs),
                    peakAccelerationMps2 = when (pattern) {
                        DrivingPattern.HARD_STOP -> 2.4
                        DrivingPattern.RAPID_START -> 2.1
                        DrivingPattern.REPEATED_SPEED_CHANGE -> 1.7
                        DrivingPattern.FAST_TURN -> 1.5
                        DrivingPattern.REPEATED_SWAY -> 1.2
                    },
                    detectorVersion = "demo-v1",
                )
            }
        }.sortedBy { it.startMs }
        return TripSnapshot(
            trip = record,
            samples = (0L..durationMs step 5_000L).map { elapsed ->
                val seconds = elapsed / 1_000.0
                val speed = (9.4 + 3.8 * sin(seconds / 55.0) + 1.5 * sin(seconds / 13.0)).coerceAtLeast(0.0)
                SpeedSample(elapsed, speed, Quality.VALID)
            },
            observations = observations,
            steering = (0L..durationMs step 10_000L).map { elapsed ->
                SteeringSample(elapsed, 18.0 * sin((elapsed / 1_000.0) * PI / 45.0))
            },
            guidance = if (shownGuidance) listOf(
                GuidanceEvent(
                    elapsedMs = 16L * 60_000L,
                    observedMs = 16L * 60_000L - 2_000L,
                    pattern = DrivingPattern.HARD_STOP,
                    outcome = GuidanceOutcome.SHOWN,
                ),
            ) else emptyList(),
        )
    }
}

private class DemoTripRepository(snapshots: List<TripSnapshot>) : TripRepository {
    private val byId = snapshots.associateBy { it.trip.id }
    override val records: StateFlow<List<TripRecord>> = MutableStateFlow(snapshots.map { it.trip })
    override suspend fun initialize() = Unit
    override suspend fun get(id: TripId): TripSnapshot? = byId[id]
    override suspend fun start(vehicleId: VehicleId?): CommandResult = CommandResult.Rejected("데모 화면입니다.")
    override suspend fun captureSample(): CommandResult = CommandResult.Rejected("데모 화면입니다.")
    override suspend fun finish(): CommandResult = CommandResult.Rejected("데모 화면입니다.")
}

private class DemoProfileRepository(
    initialPeople: List<PersonProfile>,
    initialSeating: SeatingPlan,
    initialParticipants: List<TripParticipants>,
    initialFeedback: List<TripFeedback>,
) : ProfileRepository {
    private val mutablePeople = MutableStateFlow(initialPeople)
    private val mutableSeating = MutableStateFlow(initialSeating)
    private val mutableParticipants = MutableStateFlow(initialParticipants)
    private val mutableFeedback = MutableStateFlow(initialFeedback)

    override val people: StateFlow<List<PersonProfile>> = mutablePeople
    override val seating: StateFlow<SeatingPlan> = mutableSeating
    override val participants: StateFlow<List<TripParticipants>> = mutableParticipants
    override val feedback: StateFlow<List<TripFeedback>> = mutableFeedback

    override suspend fun initialize() = Unit

    override suspend fun addPerson(displayName: String, kind: PersonKind): PersonProfile {
        val person = PersonProfile(PersonId("demo-${mutablePeople.value.size + 1}"), displayName, kind)
        mutablePeople.value = mutablePeople.value + person
        return person
    }

    override suspend fun updatePerson(profile: PersonProfile) {
        mutablePeople.value = mutablePeople.value.map { if (it.id == profile.id) profile else it }
    }

    override suspend fun placePerson(personId: PersonId, seat: SeatPosition): SeatingResult {
        val person = mutablePeople.value.firstOrNull { it.id == personId }
            ?: return SeatingResult.Rejected("등록되지 않은 사람입니다.")
        if (seat == SeatPosition.DRIVER && person.kind == PersonKind.CHILD) {
            return SeatingResult.Rejected("어린이는 운전석에 배치할 수 없습니다.")
        }
        val withoutPerson = mutableSeating.value.assignments.filterValues { it != personId }
        val plan = SeatingPlan(withoutPerson + (seat to personId))
        mutableSeating.value = plan
        return SeatingResult.Updated(plan)
    }

    override suspend fun clearSeat(seat: SeatPosition): SeatingPlan {
        val plan = SeatingPlan(mutableSeating.value.assignments - seat)
        mutableSeating.value = plan
        return plan
    }

    override suspend fun assignCurrentSeating(tripId: TripId): TripParticipants {
        val plan = mutableSeating.value
        val value = TripParticipants(tripId, plan.driverId, plan.passengerIds, 1, plan.assignments)
        mutableParticipants.value = mutableParticipants.value.filterNot { it.tripId == tripId } + value
        return value
    }

    override suspend fun saveFeedback(feedback: TripFeedback) {
        mutableFeedback.value = mutableFeedback.value.filterNot {
            it.tripId == feedback.tripId && it.targetPersonId == feedback.targetPersonId
        } + feedback
    }
}
