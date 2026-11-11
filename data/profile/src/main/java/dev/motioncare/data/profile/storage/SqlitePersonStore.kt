package dev.motioncare.data.profile.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.local.ProfileDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SqlitePersonStore(
    private val database: ProfileDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val mutablePeople = MutableStateFlow<List<PersonProfile>>(emptyList())
    private val mutableSeating = MutableStateFlow(SeatingPlan())
    private val mutableParticipants = MutableStateFlow<List<TripParticipants>>(emptyList())
    private val mutableFeedback = MutableStateFlow<List<TripFeedback>>(emptyList())
    val people = mutablePeople.asStateFlow()
    val seating = mutableSeating.asStateFlow()
    val participants = mutableParticipants.asStateFlow()
    val feedback = mutableFeedback.asStateFlow()

    suspend fun load() = write { refresh() }
    suspend fun upsertPerson(person: PersonProfile) = write {
        val values = ContentValues().apply {
            put("id", person.id.value); put("display_name", person.displayName); put("kind", person.kind.name)
            put("sensitivity", person.motionSensitivity.name)
            put("triggers", person.motionTriggers.joinToString(",") { it.name })
            put("rest_minutes", person.preferredRestMinutes)
            put("preferred_temperature", person.preferredTemperatureCelsius)
            put("avatar", person.avatar)
        }
        val db = database.writableDatabase
        val updated = db.update("people", values, "id = ?", arrayOf(person.id.value))
        if (updated == 0) db.insertOrThrow("people", null, values)
        refresh()
    }
    suspend fun replaceSeating(plan: SeatingPlan) = write {
        database.writableDatabase.inTransaction {
            delete("current_seats", null, null)
            plan.assignments.forEach { (seat, person) ->
                insertOrThrow("current_seats", null, ContentValues().apply {
                    put("seat", seat.name); put("person_id", person.value)
                })
            }
        }
        refresh()
    }
    suspend fun saveParticipants(value: TripParticipants) = write {
        database.writableDatabase.inTransaction {
            delete("trip_participants", "trip_id = ?", arrayOf(value.tripId.value))
            value.seatAssignments.forEach { (seat, person) ->
                insertOrThrow("trip_participants", null, ContentValues().apply {
                    put("trip_id", value.tripId.value); put("seat", seat.name); put("person_id", person.value)
                    put("revision", value.revision)
                })
            }
        }
        refresh()
    }
    suspend fun saveFeedback(value: TripFeedback) = write {
        val rating = value.answer as? FeedbackAnswer.Rating
        if (rating == null) {
            database.writableDatabase.delete("trip_feedback", "trip_id = ? AND target_person_id = ?",
                arrayOf(value.tripId.value, value.targetPersonId.value))
        } else {
            database.writableDatabase.insertWithOnConflict("trip_feedback", null, ContentValues().apply {
                put("trip_id", value.tripId.value); put("target_person_id", value.targetPersonId.value)
                put("rating", rating.value); put("scale_version", rating.scaleVersion)
                put("reasons", value.reasons.joinToString(",") { it.name })
                put("respondent", value.respondent.name); put("created_at", value.createdAtEpochMs)
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        }
        refresh()
    }

    private suspend fun write(block: () -> Unit) = withContext(io) { mutex.withLock { block() } }
    private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }
    private fun refresh() {
        val db = database.readableDatabase
        mutablePeople.value = db.rawQuery("SELECT * FROM people ORDER BY rowid", null).use { c -> buildList {
            while (c.moveToNext()) {
                fun text(name: String) = c.getString(c.getColumnIndexOrThrow(name))
                fun nullableInt(name: String) = c.getColumnIndexOrThrow(name).let { if (c.isNull(it)) null else c.getInt(it) }
                fun nullableDouble(name: String) = c.getColumnIndexOrThrow(name).let { if (c.isNull(it)) null else c.getDouble(it) }
                add(PersonProfile(PersonId(text("id")), text("display_name"), PersonKind.valueOf(text("kind")),
                    MotionSensitivity.valueOf(text("sensitivity")), enumSet(text("triggers"), MotionTrigger::valueOf),
                    nullableInt("rest_minutes"), nullableDouble("preferred_temperature"),
                    c.getColumnIndexOrThrow("avatar").let { if (c.isNull(it)) null else c.getString(it) }))
            }
        } }
        mutableSeating.value = SeatingPlan(db.rawQuery("SELECT seat, person_id FROM current_seats", null).use { c -> buildMap {
            while (c.moveToNext()) put(SeatPosition.valueOf(c.getString(0)), PersonId(c.getString(1)))
        } })
        val rows = db.rawQuery("SELECT trip_id, seat, person_id, revision FROM trip_participants ORDER BY trip_id, seat", null)
            .use { c -> buildList { while (c.moveToNext()) add(ParticipantRow(c.getString(0), c.getString(1), c.getString(2), c.getLong(3))) } }
        mutableParticipants.value = rows.groupBy { it.tripId }.map { (tripId, grouped) ->
            val seats = grouped.associate { SeatPosition.valueOf(it.seat) to PersonId(it.personId) }
            val plan = SeatingPlan(seats)
            TripParticipants(TripId(tripId), plan.driverId, plan.passengerIds, grouped.maxOf { it.revision }, seats)
        }
        mutableFeedback.value = db.rawQuery("SELECT * FROM trip_feedback ORDER BY created_at DESC", null).use { c -> buildList {
            while (c.moveToNext()) {
                fun text(name: String) = c.getString(c.getColumnIndexOrThrow(name))
                fun number(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
                add(TripFeedback(TripId(text("trip_id")), PersonId(text("target_person_id")),
                    FeedbackAnswer.Rating(number("rating").toInt(), text("scale_version")),
                    enumSet(text("reasons"), FeedbackReason::valueOf), FeedbackRespondent.valueOf(text("respondent")),
                    number("created_at")))
            }
        } }
    }
    private fun <T> enumSet(raw: String, parse: (String) -> T): Set<T> = raw.split(',').filter { it.isNotBlank() }.map(parse).toSet()
    private data class ParticipantRow(val tripId: String, val seat: String, val personId: String, val revision: Long)
}