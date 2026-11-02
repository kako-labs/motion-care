package dev.motioncare.data.trip.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class MotionCareDatabase(context: Context, name: String = "motion-care-sample.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {
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
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("An explicit migration is required: $oldVersion -> $newVersion")
    }
}
