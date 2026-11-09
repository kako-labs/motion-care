package dev.motioncare.data.profile.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val DATABASE_VERSION = 2

// Separate file from the trip database so each module owns its schema version.
internal class ProfileDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "motion-care-profile.db", null, DATABASE_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE people (
            id TEXT PRIMARY KEY, display_name TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'ADULT',
            sensitivity TEXT NOT NULL DEFAULT 'UNSPECIFIED', triggers TEXT NOT NULL DEFAULT '',
            rest_minutes INTEGER, preferred_temperature REAL
        )""")
        createFeatureTables(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2) { "An explicit migration is required: $oldVersion -> $newVersion" }
        db.execSQL("ALTER TABLE people ADD COLUMN kind TEXT NOT NULL DEFAULT 'ADULT'")
        db.execSQL("ALTER TABLE people ADD COLUMN sensitivity TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
        db.execSQL("ALTER TABLE people ADD COLUMN triggers TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE people ADD COLUMN rest_minutes INTEGER")
        db.execSQL("ALTER TABLE people ADD COLUMN preferred_temperature REAL")
        createFeatureTables(db)
    }
    private fun createFeatureTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE current_seats (
            seat TEXT PRIMARY KEY, person_id TEXT NOT NULL UNIQUE REFERENCES people(id) ON DELETE CASCADE
        )""")
        db.execSQL("""CREATE TABLE trip_participants (
            trip_id TEXT NOT NULL, seat TEXT NOT NULL, person_id TEXT NOT NULL REFERENCES people(id) ON DELETE CASCADE,
            revision INTEGER NOT NULL, PRIMARY KEY (trip_id, seat), UNIQUE (trip_id, person_id)
        )""")
        db.execSQL("""CREATE TABLE trip_feedback (
            trip_id TEXT NOT NULL, target_person_id TEXT NOT NULL REFERENCES people(id) ON DELETE CASCADE,
            rating INTEGER NOT NULL, scale_version TEXT NOT NULL, reasons TEXT NOT NULL,
            respondent TEXT NOT NULL, created_at INTEGER NOT NULL,
            PRIMARY KEY (trip_id, target_person_id)
        )""")
    }
}