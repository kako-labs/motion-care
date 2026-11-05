package dev.motioncare.data.profile

import android.content.Context
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.local.ProfileDatabase
import dev.motioncare.data.profile.storage.SqlitePersonStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun createProfileRepository(context: Context): ProfileRepository =
    DefaultProfileRepository(SqlitePersonStore(ProfileDatabase(context.applicationContext)),
        DepartureGuidancePreference(context.applicationContext))

internal class DefaultProfileRepository(
    private val store: SqlitePersonStore,
    private val settings: DepartureGuidancePreference = DepartureGuidancePreference(null),
) : ProfileRepository {
    override val people = store.people
    override val seating = store.seating
    override val participants = store.participants
    override val feedback = store.feedback
    override val departureGuidanceEnabled = settings.enabled
    private val mutex = Mutex()
    private var initialized = false

    override suspend fun initialize() = mutex.withLock {
        if (!initialized) { store.load(); initialized = true }
    }
    override suspend fun addPerson(displayName: String, kind: PersonKind): PersonProfile {
        val name = displayName.trim()
        require(name.isNotEmpty()) { "Person name must not be blank" }
        initialize()
        return PersonProfile(PersonId(UUID.randomUUID().toString()), name, kind).also { store.upsertPerson(it) }
    }
    override suspend fun updatePerson(profile: PersonProfile) {
        initialize(); require(people.value.any { it.id == profile.id }) { "Unknown person" }; store.upsertPerson(profile)
    }
    override suspend fun placePerson(personId: PersonId, seat: SeatPosition): SeatingResult {
        initialize()
        val person = people.value.firstOrNull { it.id == personId } ?: return SeatingResult.Rejected("등록되지 않은 사람입니다.")
        if (seat == SeatPosition.DRIVER && person.kind == PersonKind.CHILD) {
            return SeatingResult.Rejected("어린이는 운전석에 앉을 수 없습니다.")
        }
        val current = seating.value.assignments.toMutableMap()
        val from = current.entries.firstOrNull { it.value == personId }?.key
        val displaced = current[seat]
        if (from == seat) return SeatingResult.Updated(seating.value)
        if (from != null) current.remove(from)
        current[seat] = personId
        if (displaced != null && from != null) {
            val displacedPerson = people.value.firstOrNull { it.id == displaced }
            if (from != SeatPosition.DRIVER || displacedPerson?.kind != PersonKind.CHILD) current[from] = displaced
        }
        val updated = SeatingPlan(current)
        store.replaceSeating(updated)
        return SeatingResult.Updated(updated)
    }
    override suspend fun clearSeat(seat: SeatPosition): SeatingPlan {
        initialize(); val updated = SeatingPlan(seating.value.assignments - seat); store.replaceSeating(updated); return updated
    }
    override suspend fun assignCurrentSeating(tripId: TripId): TripParticipants {
        initialize()
        val plan = seating.value
        val revision = (participants.value.firstOrNull { it.tripId == tripId }?.revision ?: -1) + 1
        return TripParticipants(tripId, plan.driverId, plan.passengerIds, revision, plan.assignments)
            .also { store.saveParticipants(it) }
    }
    override suspend fun saveFeedback(feedback: TripFeedback) {
        initialize(); store.saveFeedback(feedback)
    }
    override fun setDepartureGuidanceEnabled(enabled: Boolean) = settings.set(enabled)
}

/** Stored outside the profile database so the switch needs no schema migration. */
internal class DepartureGuidancePreference(context: Context?) {
    private val preferences = context?.getSharedPreferences("motion-care-profile-settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences?.getBoolean(KEY, true) ?: true)
    val enabled = state.asStateFlow()
    fun set(enabled: Boolean) {
        preferences?.edit()?.putBoolean(KEY, enabled)?.apply()
        state.value = enabled
    }
    private companion object { const val KEY = "departure_guidance_enabled" }
}