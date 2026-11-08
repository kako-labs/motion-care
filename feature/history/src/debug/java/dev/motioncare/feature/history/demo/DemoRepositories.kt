package dev.motioncare.feature.history.demo

import android.content.Context
import android.annotation.SuppressLint
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Synthetic replay and separate demo preferences only. Never calls a database factory. */
internal class DemoRepositories(context: Context, empty: Boolean = false, unanswered: Boolean = false) {
    private val preferences = context.getSharedPreferences(if (unanswered) "history_b_debug_unanswered" else "history_b_debug_only", Context.MODE_PRIVATE)
    private val fixture = JSONObject(context.assets.open("history-b-synthetic.json").bufferedReader().use { it.readText() })
    private val snapshots: List<TripSnapshot> = if (empty) emptyList() else fixture.getJSONArray("trips").let { array ->
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val replay = item.getJSONObject("replay")
            val samples = sortedMapOf<Long, SpeedSample>()
            fun addSamples(array: JSONArray) {
                for (n in 0 until array.length()) {
                    val point = array.getJSONObject(n)
                    val time = (point.getDouble("second") * 1000).toLong()
                    val value = if (point.isNull("kmh")) null else point.getDouble("kmh") / 3.6
                    samples[time] = SpeedSample(time, value, if (value == null) Quality.UNAVAILABLE else Quality.VALID)
                }
            }
            addSamples(replay.getJSONArray("samples"))
            val events = replay.getJSONArray("events")
            val observations = (0 until events.length()).map { n ->
                val event = events.getJSONObject(n)
                addSamples(event.getJSONArray("detailSamples"))
                Observation(DrivingPattern.HARD_STOP, event.getLong("start") * 1000, event.getLong("end") * 1000,
                    0.0, "synthetic-fixture-event-not-detector")
            }
            val start = item.getLong("started")
            TripSnapshot(TripRecord(TripId(item.getString("id")), start, start + item.getLong("duration") * 60_000,
                TripStatus.COMPLETED, origin = DataOrigin.SYNTHETIC), samples.values.toList(), observations)
        }
    }
    val trips: TripRepository = object : TripRepository {
        override val records = MutableStateFlow(snapshots.map { it.trip }).asStateFlow()
        override suspend fun initialize() = Unit
        override suspend fun get(id: TripId) = snapshots.firstOrNull { it.trip.id == id }
        override suspend fun start(vehicleId: VehicleId?) = CommandResult.Rejected("표시 검증용 시연")
        override suspend fun captureSample() = CommandResult.Rejected("표시 검증용 시연")
        override suspend fun finish() = CommandResult.Rejected("표시 검증용 시연")
    }
    val profiles: ProfileRepository = object : ProfileRepository {
        override val people = MutableStateFlow(listOf(
            PersonProfile(PersonId("jiyoung"), "지영", motionSensitivity = MotionSensitivity.MEDIUM, motionTriggers = setOf(MotionTrigger.CURVE)),
            PersonProfile(PersonId("seoa"), "서아", PersonKind.CHILD, MotionSensitivity.HIGH, setOf(MotionTrigger.START_STOP), 40, 23.0),
        ))
        override val seating = MutableStateFlow(SeatingPlan(mapOf(SeatPosition.DRIVER to PersonId("jiyoung"), SeatPosition.REAR_RIGHT to PersonId("seoa"))))
        override val participants = MutableStateFlow(snapshots.map { TripParticipants(it.trip.id, PersonId("jiyoung"), setOf(PersonId("seoa")), 0, seating.value.assignments) })
        override val feedback = MutableStateFlow(snapshots.mapIndexedNotNull { index, snapshot ->
            val key = snapshot.trip.id.value
            val rating = preferences.getInt("rating-$key", if (unanswered) 0 else listOf(4, 3, 4)[index])
            if (rating !in 1..5) null else TripFeedback(snapshot.trip.id, PersonId("seoa"), FeedbackAnswer.Rating(rating, "COMFORT_5_V1"),
                preferences.getStringSet("reasons-$key", emptySet()).orEmpty().map { FeedbackReason.valueOf(it) }.toSet(),
                FeedbackRespondent.valueOf(preferences.getString("respondent-$key", "GUARDIAN")!!), snapshot.trip.endedAtEpochMs ?: 0)
        })
        override val departureGuidanceEnabled = MutableStateFlow(true)
        override suspend fun initialize() = Unit
        override suspend fun addPerson(displayName: String, kind: PersonKind): PersonProfile {
            val person = PersonProfile(PersonId("demo-${people.value.size}"), displayName, kind)
            people.value = people.value + person
            return person
        }
        override suspend fun updatePerson(profile: PersonProfile) { people.value = people.value.map { if (it.id == profile.id) profile else it } }
        override suspend fun placePerson(personId: PersonId, seat: SeatPosition): SeatingResult {
            if (seat == SeatPosition.DRIVER && people.value.first { it.id == personId }.kind == PersonKind.CHILD) return SeatingResult.Rejected("어린이는 동승석을 선택해 주세요.")
            val next = SeatingPlan(seating.value.assignments.filterValues { it != personId } + (seat to personId))
            seating.value = next
            return SeatingResult.Updated(next)
        }
        override suspend fun clearSeat(seat: SeatPosition): SeatingPlan { seating.value = SeatingPlan(seating.value.assignments - seat); return seating.value }
        override suspend fun assignCurrentSeating(tripId: TripId): TripParticipants {
            val item = TripParticipants(tripId, seating.value.driverId, seating.value.passengerIds, 0, seating.value.assignments)
            participants.value = participants.value.filterNot { it.tripId == tripId } + item
            return item
        }
        @SuppressLint("UseKtx") // The KTX edit helper discards the commit result; saving must report failure.
        override suspend fun saveFeedback(feedback: TripFeedback) {
            val answer = feedback.answer as? FeedbackAnswer.Rating
            val key = feedback.tripId.value
            check(preferences.edit().putInt("rating-$key", answer?.value ?: 0)
                .putStringSet("reasons-$key", feedback.reasons.map { it.name }.toSet())
                .putString("respondent-$key", feedback.respondent.name).commit()) { "Demo response save failed" }
            this.feedback.value = this.feedback.value.filterNot { it.tripId == feedback.tripId && it.targetPersonId == feedback.targetPersonId } + feedback
        }
        override fun setDepartureGuidanceEnabled(enabled: Boolean) { departureGuidanceEnabled.value = enabled }
    }
}
