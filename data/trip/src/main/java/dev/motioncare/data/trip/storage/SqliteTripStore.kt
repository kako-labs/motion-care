package dev.motioncare.data.trip.storage

import android.content.ContentValues
import android.database.Cursor
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

    override suspend fun save(snapshot: TripSnapshot) = withContext(io) {
        mutex.withLock {
            val db = database.writableDatabase
            db.transaction {
                val existing = db.rawQuery("SELECT revision FROM trips WHERE id = ?",
                    arrayOf(snapshot.trip.id.value)).use { if (it.moveToFirst()) it.getLong(0) else null }
                require(existing == null || snapshot.trip.revision > existing) { "Stale trip revision" }
                val t = snapshot.trip
                val values = ContentValues().apply {
                    put("id", t.id.value); put("started", t.startedAtEpochMs)
                    put("ended", t.endedAtEpochMs); put("status", t.status.name)
                    put("vehicle_id", t.vehicleId?.value); put("origin", t.origin.name)
                    put("revision", t.revision)
                }
                if (existing == null) db.insertOrThrow("trips", null, values)
                else db.update("trips", values, "id = ?", arrayOf(t.id.value))
                db.delete("speed_samples", "trip_id = ?", arrayOf(t.id.value))
                snapshot.samples.forEach { sample ->
                    db.insertOrThrow("speed_samples", null, ContentValues().apply {
                        put("trip_id", t.id.value); put("elapsed", sample.elapsedMs)
                        put("speed", sample.metersPerSecond); put("quality", sample.quality.name)
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
            TripSnapshot(trip, samples)
        }
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
