package dev.motioncare.data.profile.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Separate file from the trip database so each module owns its schema version.
internal class ProfileDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "motion-care-profile.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE people (id TEXT PRIMARY KEY, display_name TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("An explicit migration is required: $oldVersion -> $newVersion")
    }
}
