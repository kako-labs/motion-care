package dev.motioncare.data.trip.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.*
import dev.motioncare.data.trip.local.MotionCareDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SqliteTripStore(
    private val database: MotionCareDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TripReader, TripWriter {
    private val mutex = Mutex()
    private val mutableRecords = MutableStateFlow<List<TripRecord>>(emptyList())
    override val records = mutableRecords.asStateFlow()

    suspend fun load() = withContext(io) { mutex.withLock { refresh() } }

    override suspend fun saveTrip(trip: TripRecord) = withContext(io) {
        mutex.withLock {
            val db = database.writableDatabase
            db.transaction { writeTrip(db, trip) }
            refresh()
        }
    }

    override suspend fun appendSamples(
        trip: TripRecord,
        samples: List<SpeedSample>,
        observations: List<Observation>,
        steering: List<SteeringSample>,
        guidance: List<GuidanceEvent>,
    ) = withContext(io) {
        mutex.withLock {
            val db = database.writableDatabase
            db.transaction {
                writeTrip(db, trip)
                samples.forEach { sample ->
                    db.insertOrThrow("speed_samples", null, ContentValues().apply {
                        put("trip_id", trip.id.value); put("elapsed", sample.elapsedMs)
                        put("speed", sample.metersPerSecond); put("quality", sample.quality.name)
                    })
                }
                observations.forEach { seen ->
                    db.insertOrThrow("observations", null, ContentValues().apply {
                        put("trip_id", trip.id.value); put("pattern", seen.pattern.name)
                        put("start_ms", seen.startMs); put("end_ms", seen.endMs)
                        put("peak_mps2", seen.peakAccelerationMps2); put("detector_version", seen.detectorVersion)
                    })
                }
                steering.forEach { sample ->
                    db.insertOrThrow("steering_samples", null, ContentValues().apply {
                        put("trip_id", trip.id.value); put("elapsed", sample.elapsedMs); put("degrees", sample.degrees)
                    })
                }
                guidance.forEach { event ->
                    db.insertOrThrow("guidance_events", null, ContentValues().apply {
                        put("trip_id", trip.id.value); put("elapsed", event.elapsedMs); put("observed", event.observedMs)
                        put("pattern", topicName(event.topic)); put("outcome", event.outcome.name)
                    })
                }
            }
            refresh()
        }
    }

    override suspend fun get(id: TripId): TripSnapshot? = withContext(io) {
        mutex.withLock {
            val db = database.readableDatabase
            val trip = db.rawQuery("SELECT * FROM trips WHERE id = ?", arrayOf(id.value)).use {
                if (it.moveToFirst()) it.toRecord() else null
            } ?: return@withLock null
            val samples = db.rawQuery("SELECT * FROM speed_samples WHERE trip_id = ? ORDER BY elapsed",
                arrayOf(id.value)).use { c ->
                buildList { while (c.moveToNext()) {
                    val speed = c.getColumnIndexOrThrow("speed")
                    add(SpeedSample(c.getLong(c.getColumnIndexOrThrow("elapsed")),
                        if (c.isNull(speed)) null else c.getDouble(speed),
                        Quality.valueOf(c.getString(c.getColumnIndexOrThrow("quality")))))
                } }
            }
            val observations = db.rawQuery("SELECT * FROM observations WHERE trip_id = ? ORDER BY start_ms, pattern",
                arrayOf(id.value)).use { c ->
                buildList { while (c.moveToNext()) {
                    add(Observation(DrivingPattern.valueOf(c.getString(c.getColumnIndexOrThrow("pattern"))),
                        c.getLong(c.getColumnIndexOrThrow("start_ms")),
                        c.getLong(c.getColumnIndexOrThrow("end_ms")),
                        c.getDouble(c.getColumnIndexOrThrow("peak_mps2")),
                        c.getString(c.getColumnIndexOrThrow("detector_version"))))
                } }
            }
            val steering = db.rawQuery("SELECT elapsed, degrees FROM steering_samples WHERE trip_id = ? ORDER BY elapsed",
                arrayOf(id.value)).use { c ->
                buildList { while (c.moveToNext()) add(SteeringSample(c.getLong(0), c.getDouble(1))) }
            }
            val guidance = db.rawQuery("SELECT elapsed, observed, pattern, outcome FROM guidance_events WHERE trip_id = ? ORDER BY elapsed, rowid",
                arrayOf(id.value)).use { c ->
                buildList { while (c.moveToNext()) {
                    add(GuidanceEvent(c.getLong(0), c.getLong(1), topicOf(c.getString(2)), GuidanceOutcome.valueOf(c.getString(3))))
                } }
            }
            TripSnapshot(trip, samples, observations, steering, guidance)
        }
    }

    override suspend fun lastSampleElapsedMs(id: TripId): Long? = withContext(io) {
        mutex.withLock {
            database.readableDatabase
                .rawQuery("SELECT MAX(elapsed) FROM speed_samples WHERE trip_id = ?", arrayOf(id.value))
                .use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
        }
    }

    private fun writeTrip(db: SQLiteDatabase, trip: TripRecord) {
        val existing = db.rawQuery("SELECT revision FROM trips WHERE id = ?",
            arrayOf(trip.id.value)).use { if (it.moveToFirst()) it.getLong(0) else null }
        require(existing == null || trip.revision > existing) { "Stale trip revision" }
        val values = ContentValues().apply {
            put("id", trip.id.value); put("started", trip.startedAtEpochMs)
            put("ended", trip.endedAtEpochMs); put("status", trip.status.name)
            put("vehicle_id", trip.vehicleId?.value); put("origin", trip.origin.name)
            put("revision", trip.revision)
        }
        if (existing == null) db.insertOrThrow("trips", null, values)
        else db.update("trips", values, "id = ?", arrayOf(trip.id.value))
    }

    private fun refresh() {
        mutableRecords.value = database.readableDatabase
            .rawQuery("SELECT * FROM trips ORDER BY started DESC, id DESC", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.toRecord()) } }
    }

    private fun Cursor.toRecord(): TripRecord {
        fun text(name: String) = getString(getColumnIndexOrThrow(name))
        fun number(name: String) = getLong(getColumnIndexOrThrow(name))
        return TripRecord(TripId(text("id")), number("started"),
            if (isNull(getColumnIndexOrThrow("ended"))) null else number("ended"),
            TripStatus.valueOf(text("status")), text("vehicle_id")?.let(::VehicleId),
            DataOrigin.valueOf(text("origin")), number("revision"))
    }
}

// guidance_events.pattern holds the card topic: a pattern name or a cabin air advice name. The two sets of
// names never overlap, which SqliteTripStoreTest checks, so the column needs no migration.
internal fun topicName(topic: GuidanceTopic): String = when (topic) {
    is GuidanceTopic.Pattern -> topic.pattern.name
    is GuidanceTopic.Environment -> topic.advice.name
}

internal fun topicOf(name: String): GuidanceTopic =
    DrivingPattern.entries.firstOrNull { it.name == name }?.let { GuidanceTopic.Pattern(it) }
        ?: GuidanceTopic.Environment(EnvironmentAdvice.valueOf(name))
