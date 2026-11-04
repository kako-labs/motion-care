package dev.motioncare.data.trip.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class MotionCareDatabase(context: Context, name: String = "motion-care-sample.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 3) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE trips (
            id TEXT PRIMARY KEY, started INTEGER NOT NULL, ended INTEGER,
            status TEXT NOT NULL, vehicle_id TEXT, origin TEXT NOT NULL, revision INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE speed_samples (
            trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
            elapsed INTEGER NOT NULL, speed REAL, quality TEXT NOT NULL,
            PRIMARY KEY (trip_id, elapsed)
        )""")
        createObservations(db)
        createSteering(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion in 1..2 && newVersion == 3) { "An explicit migration is required: $oldVersion -> $newVersion" }
        if (oldVersion < 2) createObservations(db)
        createSteering(db)
    }
    private fun createObservations(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE observations (
            trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
            pattern TEXT NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL,
            peak_mps2 REAL NOT NULL, detector_version TEXT NOT NULL,
            PRIMARY KEY (trip_id, pattern, start_ms)
        )""")
    }
    private fun createSteering(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE steering_samples (
            trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
            elapsed INTEGER NOT NULL, degrees REAL NOT NULL,
            PRIMARY KEY (trip_id, elapsed)
        )""")
    }
}
