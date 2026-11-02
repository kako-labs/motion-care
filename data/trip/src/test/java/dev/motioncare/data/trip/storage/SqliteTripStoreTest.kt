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
    @Test fun `snapshots survive database reopen including unavailable values`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "test-${UUID.randomUUID()}.db"
        val db = MotionCareDatabase(context, name)
        val store = SqliteTripStore(db)
        val snapshot = TripSnapshot(
            TripRecord(TripId("sample"), 1000, origin = DataOrigin.SYNTHETIC),
            listOf(SpeedSample(0, 0.0, Quality.VALID), SpeedSample(10, null, Quality.UNAVAILABLE)))
        store.save(snapshot)
        db.close()
        val reopened = MotionCareDatabase(context, name)
        try {
            val reader = SqliteTripStore(reopened)
            reader.load()
            assertEquals(snapshot, reader.get(snapshot.trip.id))
            assertEquals(listOf(snapshot.trip), reader.records.value)
            assertNull(reader.get(TripId("missing")))
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun `stale writer cannot overwrite a newer persisted snapshot`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "test-${UUID.randomUUID()}.db"
        val db = MotionCareDatabase(context, name)
        try {
            val store = SqliteTripStore(db)
            val initial = TripSnapshot(TripRecord(TripId("trip"), 1000, origin = DataOrigin.SYNTHETIC))
            store.save(initial)
            val updated = initial.copy(trip = initial.trip.copy(revision = 1),
                samples = listOf(SpeedSample(50, 4.0, Quality.VALID)))
            store.save(updated)
            try { store.save(initial); fail("Expected stale revision rejection") }
            catch (_: IllegalArgumentException) { }
            assertEquals(updated, store.get(initial.trip.id))
            assertEquals(1L, store.records.value.single().revision)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
