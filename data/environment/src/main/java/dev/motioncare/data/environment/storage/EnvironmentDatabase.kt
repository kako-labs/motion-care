package dev.motioncare.data.environment.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal val SCALAR_METRICS = listOf("outside", "cabin", "cabin_set", "recirculation", "air_cleaner")

private fun readingColumns(prefix: String) =
    "${prefix}_state TEXT NOT NULL, ${prefix}_reason TEXT, ${prefix}_at INTEGER, ${prefix}_source TEXT NOT NULL"

// Separate file from the trip and profile databases so this module owns its schema version.
internal class EnvironmentDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "motion-care-environment.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        val scalars = SCALAR_METRICS.joinToString(", ") { "${it}_value REAL, ${readingColumns(it)}" }
        db.execSQL(
            "CREATE TABLE snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id TEXT NOT NULL, " +
                "captured_at INTEGER NOT NULL, $scalars, air_pm10 REAL, air_pm25 REAL, air_grade TEXT, " +
                "air_latitude REAL, air_longitude REAL, ${readingColumns("air")})"
        )
        db.execSQL("CREATE INDEX snapshots_by_trip ON snapshots (trip_id, captured_at)")
        // One row: the latest successful outdoor air request.
        db.execSQL(
            "CREATE TABLE outdoor_air_cache (id INTEGER PRIMARY KEY CHECK (id = 1), latitude REAL NOT NULL, " +
                "longitude REAL NOT NULL, pm10 REAL, pm25 REAL, observed_at INTEGER NOT NULL, fetched_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("An explicit migration is required: $oldVersion -> $newVersion")
    }
}
