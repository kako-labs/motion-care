package dev.motioncare.data.trip.storage

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

    private fun rowIdAt(db: MotionCareDatabase, elapsed: Long): Long =
        db.readableDatabase.rawQuery("SELECT rowid FROM speed_samples WHERE elapsed = ?", arrayOf(elapsed.toString()))
            .use { it.moveToFirst(); it.getLong(0) }
}
