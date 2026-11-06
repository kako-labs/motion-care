package dev.motioncare.data.environment.storage

import android.content.ContentValues
import android.database.Cursor
import dev.motioncare.core.model.AirGrade
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.MissingReason
import dev.motioncare.core.model.OutdoorAir
import dev.motioncare.core.model.TripId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The latest successful outdoor air request, at the rounded position it was made for. */
internal data class CachedAir(
    val latitude: Double,
    val longitude: Double,
    val pm10: Double?,
    val pm25: Double?,
    val observedAtEpochMs: Long,
    val fetchedAtEpochMs: Long,
)

internal class SqliteEnvironmentStore(
    private val database: EnvironmentDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun insert(tripId: TripId, snapshot: EnvironmentSnapshot) = withContext(io) {
        database.writableDatabase.insertOrThrow("snapshots", null, ContentValues().apply {
            put("trip_id", tripId.value)
            put("captured_at", snapshot.capturedAtEpochMs)
            putScalar("outside", snapshot.outsideTemperature) { it }
            putScalar("cabin", snapshot.cabinTemperature) { it }
            putScalar("cabin_set", snapshot.cabinSetTemperature) { it }
            putScalar("recirculation", snapshot.recirculation) { if (it) 1.0 else 0.0 }
            putScalar("air_cleaner", snapshot.airCleaner) { if (it) 1.0 else 0.0 }
            putState("air", snapshot.outdoorAir)
            snapshot.outdoorAir.value?.let { air ->
                put("air_pm10", air.pm10); put("air_pm25", air.pm25); put("air_grade", air.grade?.name)
                put("air_latitude", air.latitude); put("air_longitude", air.longitude)
            }
        })
        Unit
    }

    suspend fun history(tripId: TripId): List<EnvironmentSnapshot> = withContext(io) {
        database.readableDatabase.rawQuery(
            "SELECT * FROM snapshots WHERE trip_id = ? ORDER BY captured_at, id", arrayOf(tripId.value),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.snapshot()) } }
    }

    suspend fun cachedAir(): CachedAir? = withContext(io) {
        database.readableDatabase.rawQuery(
            "SELECT latitude, longitude, pm10, pm25, observed_at, fetched_at FROM outdoor_air_cache WHERE id = 1", null,
        ).use { c ->
            if (!c.moveToFirst()) null
            else CachedAir(c.getDouble(0), c.getDouble(1), c.doubleOrNull(2), c.doubleOrNull(3), c.getLong(4), c.getLong(5))
        }
    }

    suspend fun saveAir(air: CachedAir) = withContext(io) {
        database.writableDatabase.replaceOrThrow("outdoor_air_cache", null, ContentValues().apply {
            put("id", 1); put("latitude", air.latitude); put("longitude", air.longitude)
            put("pm10", air.pm10); put("pm25", air.pm25)
            put("observed_at", air.observedAtEpochMs); put("fetched_at", air.fetchedAtEpochMs)
        })
        Unit
    }
}

private fun <T : Any> ContentValues.putScalar(prefix: String, reading: EnvironmentReading<T>, encode: (T) -> Double) {
    putState(prefix, reading)
    put("${prefix}_value", reading.value?.let(encode))
}

private fun ContentValues.putState(prefix: String, reading: EnvironmentReading<*>) {
    put("${prefix}_state", reading.availability.name)
    put("${prefix}_reason", reading.missingReason?.name)
    put("${prefix}_at", reading.observedAtEpochMs)
    put("${prefix}_source", reading.source.name)
}

private fun Cursor.snapshot() = EnvironmentSnapshot(
    capturedAtEpochMs = getLong(getColumnIndexOrThrow("captured_at")),
    outsideTemperature = reading("outside") { doubleOrNull(column("outside_value")) },
    cabinTemperature = reading("cabin") { doubleOrNull(column("cabin_value")) },
    cabinSetTemperature = reading("cabin_set") { doubleOrNull(column("cabin_set_value")) },
    recirculation = reading("recirculation") { doubleOrNull(column("recirculation_value"))?.let { it != 0.0 } },
    airCleaner = reading("air_cleaner") { doubleOrNull(column("air_cleaner_value"))?.let { it != 0.0 } },
    outdoorAir = reading("air") {
        OutdoorAir(
            pm10 = doubleOrNull(column("air_pm10")),
            pm25 = doubleOrNull(column("air_pm25")),
            grade = stringOrNull(column("air_grade"))?.let(AirGrade::valueOf),
            latitude = getDouble(column("air_latitude")),
            longitude = getDouble(column("air_longitude")),
        )
    },
)

private fun <T : Any> Cursor.reading(prefix: String, value: Cursor.() -> T?): EnvironmentReading<T> {
    val availability = Availability.valueOf(getString(column("${prefix}_state")))
    val holdsValue = availability == Availability.AVAILABLE || availability == Availability.STALE
    return EnvironmentReading(
        value = if (holdsValue) value() else null,
        availability = availability,
        source = EnvironmentSource.valueOf(getString(column("${prefix}_source"))),
        observedAtEpochMs = longOrNull(column("${prefix}_at")),
        missingReason = stringOrNull(column("${prefix}_reason"))?.let(MissingReason::valueOf),
    )
}

private fun Cursor.column(name: String) = getColumnIndexOrThrow(name)
private fun Cursor.doubleOrNull(index: Int) = if (isNull(index)) null else getDouble(index)
private fun Cursor.longOrNull(index: Int) = if (isNull(index)) null else getLong(index)
private fun Cursor.stringOrNull(index: Int) = if (isNull(index)) null else getString(index)
