package dev.motioncare.data.trip.storage

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.motioncare.core.model.*
import dev.motioncare.data.trip.local.MotionCareDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SqliteTripStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun name() = "test-${UUID.randomUUID()}.db"
    private fun trip() = TripRecord(TripId("trip"), 1000, origin = DataOrigin.SYNTHETIC)

    @Test fun `snapshots survive database reopen including unavailable values`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        val store = SqliteTripStore(db)
        val samples = listOf(SpeedSample(0, 0.0, Quality.VALID), SpeedSample(10, null, Quality.UNAVAILABLE))
        store.saveTrip(trip())
        val appended = trip().copy(revision = 1)
        store.appendSamples(appended, samples)
        db.close()
        val reopened = MotionCareDatabase(context, name)
        try {
            val reader = SqliteTripStore(reopened)
            reader.load()
            assertEquals(TripSnapshot(appended, samples), reader.get(appended.id))
            assertEquals(listOf(appended), reader.records.value)
            assertNull(reader.get(TripId("missing")))
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun `stale writer cannot overwrite a newer persisted trip`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.saveTrip(trip())
            val updated = trip().copy(revision = 1)
            val sample = SpeedSample(50, 4.0, Quality.VALID)
            store.appendSamples(updated, listOf(sample))
            try { store.saveTrip(trip()); fail("Expected stale revision rejection") }
            catch (_: IllegalArgumentException) { }
            try { store.appendSamples(trip(), listOf(SpeedSample(60, 5.0, Quality.VALID))); fail("Expected stale revision rejection") }
            catch (_: IllegalArgumentException) { }
            assertEquals(TripSnapshot(updated, listOf(sample)), store.get(updated.id))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `appending leaves earlier rows in place`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.saveTrip(trip())
            store.appendSamples(trip().copy(revision = 1), listOf(SpeedSample(0, 1.0, Quality.VALID)))
            val before = rowIdAt(db, elapsed = 0)
            store.appendSamples(trip().copy(revision = 2), listOf(SpeedSample(10, 2.0, Quality.VALID)))
            assertEquals(before, rowIdAt(db, elapsed = 0))
            assertEquals(listOf(0L, 10L), store.get(trip().id)!!.samples.map { it.elapsedMs })
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `saving trip fields keeps its samples`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.saveTrip(trip())
            val sample = SpeedSample(10, 2.0, Quality.VALID)
            store.appendSamples(trip().copy(revision = 1), listOf(sample))
            val finished = trip().copy(status = TripStatus.COMPLETED, endedAtEpochMs = 2000, revision = 2)
            store.saveTrip(finished)
            assertEquals(TripSnapshot(finished, listOf(sample)), store.get(finished.id))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `last sample time is the newest sample or null`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.saveTrip(trip())
            assertNull(store.lastSampleElapsedMs(trip().id))
            store.appendSamples(trip().copy(revision = 1),
                listOf(SpeedSample(10, 1.0, Quality.VALID), SpeedSample(30, 2.0, Quality.VALID)))
            assertEquals(30L, store.lastSampleElapsedMs(trip().id))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `observations are saved with samples and read back in time order`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.saveTrip(trip())
            val late = Observation(DrivingPattern.HARD_STOP, 900, 1000, 2.5, "test-1")
            val early = Observation(DrivingPattern.RAPID_START, 100, 200, 2.1, "test-1")
            store.appendSamples(trip().copy(revision = 1), listOf(SpeedSample(10, 1.0, Quality.VALID)), listOf(late, early))
            assertEquals(listOf(early, late), store.get(trip().id)!!.observations)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `samples and observations are written together or not at all`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.saveTrip(trip())
            val observation = Observation(DrivingPattern.RAPID_START, 100, 200, 2.1, "test-1")
            val sample = SpeedSample(10, 1.0, Quality.VALID)
            store.appendSamples(trip().copy(revision = 1), listOf(sample), listOf(observation))
            try {
                // The repeated sample violates the primary key after the trip row is updated.
                store.appendSamples(trip().copy(revision = 2), listOf(sample), listOf(observation.copy(startMs = 150)))
                fail("Expected duplicate sample rejection")
            } catch (_: android.database.SQLException) { }
            val saved = store.get(trip().id)!!
            assertEquals(listOf(observation), saved.observations)
            assertEquals(1L, saved.trip.revision)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `steering is saved with samples and read back in time order`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.load()
            val first = TripRecord(TripId("trip"), 1000, origin = DataOrigin.VEHICLE)
            store.saveTrip(first)
            store.appendSamples(first.copy(revision = 2), emptyList(), emptyList(), listOf(SteeringSample(20, -5.0), SteeringSample(10, 3.5)))
            assertEquals(listOf(SteeringSample(10, 3.5), SteeringSample(20, -5.0)), store.get(TripId("trip"))!!.steering)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `guidance results are saved and read back in time order`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.load()
            val first = TripRecord(TripId("trip"), 1000, origin = DataOrigin.VEHICLE)
            store.saveTrip(first)
            val later = GuidanceEvent(900, 800, DrivingPattern.HARD_STOP, GuidanceOutcome.SKIPPED_INTERVAL)
            val earlier = GuidanceEvent(500, 400, DrivingPattern.REPEATED_SWAY, GuidanceOutcome.SHOWN)
            store.appendSamples(first.copy(revision = 2), emptyList(), emptyList(), emptyList(), listOf(later, earlier))
            assertEquals(listOf(earlier, later), store.get(TripId("trip"))!!.guidance)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `cabin air and rest cards are saved apart from pattern cards`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.load()
            val first = TripRecord(TripId("trip"), 1000, origin = DataOrigin.VEHICLE)
            store.saveTrip(first)
            val events = listOf(
                GuidanceEvent(500, 400, GuidanceTopic.Environment(EnvironmentAdvice.REDUCE_OUTSIDE_AIR), GuidanceOutcome.SHOWN),
                GuidanceEvent(600, 600, GuidanceTopic.Environment(EnvironmentAdvice.TRY_OUTSIDE_AIR), GuidanceOutcome.SKIPPED_OFF),
                GuidanceEvent(650, 640, GuidanceTopic.LongDrive, GuidanceOutcome.SHOWN),
                GuidanceEvent(700, 650, DrivingPattern.HARD_STOP, GuidanceOutcome.SHOWN),
            )
            store.appendSamples(first.copy(revision = 2), emptyList(), emptyList(), emptyList(), events)
            assertEquals(events, store.get(TripId("trip"))!!.guidance)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `every card topic has a name of its own`() {
        val names = DrivingPattern.entries.map { topicName(GuidanceTopic.Pattern(it)) } +
            EnvironmentAdvice.entries.map { topicName(GuidanceTopic.Environment(it)) } +
            topicName(GuidanceTopic.LongDrive)
        assertEquals(names.size, names.toSet().size)
        names.forEach { assertEquals(it, topicName(topicOf(it))) }
    }

    @Test fun `the same guidance result twice at one time does not block saving`() = runTest {
        val name = name()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.load()
            val first = TripRecord(TripId("trip"), 1000, origin = DataOrigin.VEHICLE)
            store.saveTrip(first)
            val event = GuidanceEvent(500, 400, DrivingPattern.REPEATED_SWAY, GuidanceOutcome.SKIPPED_INTERVAL)
            store.appendSamples(first.copy(revision = 2), emptyList(), emptyList(), emptyList(), listOf(event, event))
            assertEquals(2, store.get(TripId("trip"))!!.guidance.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `version 3 database upgrades keeping steering and adds guidance`() = runTest {
        val name = name()
        val old = object : SQLiteOpenHelper(context, name, null, 3) {
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
                db.execSQL("""CREATE TABLE observations (
                    trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
                    pattern TEXT NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL,
                    peak_mps2 REAL NOT NULL, detector_version TEXT NOT NULL,
                    PRIMARY KEY (trip_id, pattern, start_ms)
                )""")
                db.execSQL("""CREATE TABLE steering_samples (
                    trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
                    elapsed INTEGER NOT NULL, degrees REAL NOT NULL,
                    PRIMARY KEY (trip_id, elapsed)
                )""")
                db.execSQL("INSERT INTO trips VALUES ('trip', 1000, 2000, 'COMPLETED', NULL, 'VEHICLE', 3)")
                db.execSQL("INSERT INTO observations VALUES ('trip', 'HARD_STOP', 100, 200, 2.1, 'pattern-v1')")
                db.execSQL("INSERT INTO steering_samples VALUES ('trip', 10, 5.0)")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        old.writableDatabase
        old.close()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.load()
            val saved = store.get(TripId("trip"))!!
            assertEquals(listOf(DrivingPattern.HARD_STOP), saved.observations.map { it.pattern })
            assertEquals(listOf(SteeringSample(10, 5.0)), saved.steering)
            assertTrue(saved.guidance.isEmpty())
            store.appendSamples(saved.trip.copy(revision = 4), emptyList(), emptyList(), emptyList(),
                listOf(GuidanceEvent(20, 10, DrivingPattern.HARD_STOP, GuidanceOutcome.SHOWN)))
            assertEquals(1, store.get(TripId("trip"))!!.guidance.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `version 2 database upgrades keeping observations and adds steering`() = runTest {
        val name = name()
        val old = object : SQLiteOpenHelper(context, name, null, 2) {
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
                db.execSQL("""CREATE TABLE observations (
                    trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
                    pattern TEXT NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL,
                    peak_mps2 REAL NOT NULL, detector_version TEXT NOT NULL,
                    PRIMARY KEY (trip_id, pattern, start_ms)
                )""")
                db.execSQL("INSERT INTO trips VALUES ('trip', 1000, 2000, 'COMPLETED', NULL, 'VEHICLE', 3)")
                db.execSQL("INSERT INTO observations VALUES ('trip', 'HARD_STOP', 100, 200, 2.1, 'pattern-v1')")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        old.writableDatabase
        old.close()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.load()
            val saved = store.get(TripId("trip"))!!
            assertEquals(listOf(DrivingPattern.HARD_STOP), saved.observations.map { it.pattern })
            assertTrue(saved.steering.isEmpty())
            store.appendSamples(saved.trip.copy(revision = 4), emptyList(), emptyList(), listOf(SteeringSample(10, 1.0)),
                listOf(GuidanceEvent(20, 10, DrivingPattern.HARD_STOP, GuidanceOutcome.SHOWN)))
            assertEquals(listOf(SteeringSample(10, 1.0)), store.get(TripId("trip"))!!.steering)
            assertEquals(1, store.get(TripId("trip"))!!.guidance.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `version 1 database upgrades without losing trips`() = runTest {
        val name = name()
        val old = object : SQLiteOpenHelper(context, name, null, 1) {
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
                db.execSQL("INSERT INTO trips VALUES ('trip', 1000, 2000, 'COMPLETED', NULL, 'SYNTHETIC', 3)")
                db.execSQL("INSERT INTO speed_samples VALUES ('trip', 10, 1.5, 'VALID')")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        old.writableDatabase
        old.close()
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            store.load()
            val saved = store.get(TripId("trip"))!!
            assertEquals(3L, saved.trip.revision)
            assertEquals(listOf(SpeedSample(10, 1.5, Quality.VALID)), saved.samples)
            assertTrue(saved.observations.isEmpty())
            val observation = Observation(DrivingPattern.HARD_STOP, 100, 200, 2.1, "test-1")
            store.appendSamples(saved.trip.copy(revision = 4), emptyList(), listOf(observation))
            assertEquals(listOf(observation), store.get(TripId("trip"))!!.observations)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun rowIdAt(db: MotionCareDatabase, elapsed: Long): Long =
        db.readableDatabase.rawQuery("SELECT rowid FROM speed_samples WHERE elapsed = ?", arrayOf(elapsed.toString()))
            .use { it.moveToFirst(); it.getLong(0) }
}
