package dev.motioncare.data.environment.storage

import dev.motioncare.core.model.AirGrade
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.MissingReason
import dev.motioncare.core.model.OutdoorAir
import dev.motioncare.core.model.TripId
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SqliteEnvironmentStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun store() = SqliteEnvironmentStore(EnvironmentDatabase(context), UnconfinedTestDispatcher())

    private val full = EnvironmentSnapshot(
        capturedAtEpochMs = 1_000,
        outsideTemperature = EnvironmentReading(25.0, Availability.AVAILABLE, EnvironmentSource.VEHICLE, 990),
        cabinTemperature = EnvironmentReading(19.1, Availability.AVAILABLE, EnvironmentSource.VEHICLE, 995),
        cabinSetTemperature = EnvironmentReading(null, Availability.UNAVAILABLE, EnvironmentSource.VEHICLE,
            missingReason = MissingReason.NO_DATA),
        recirculation = EnvironmentReading(true, Availability.AVAILABLE, EnvironmentSource.VEHICLE, 995),
        airCleaner = EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE),
        outdoorAir = EnvironmentReading(
            OutdoorAir(pm10 = null, pm25 = 40.0, grade = AirGrade.BAD, latitude = 37.6, longitude = 127.0),
            Availability.STALE, EnvironmentSource.OPEN_METEO_CAMS, 100),
    )

    @Test fun `snapshot keeps every state, reason and missing value`() = runTest {
        store().insert(TripId("t1"), full)

        assertEquals(listOf(full), store().history(TripId("t1")))
    }

    @Test fun `history is per trip and in capture order`() = runTest {
        val later = full.copy(capturedAtEpochMs = 2_000)
        val writer = store()
        writer.insert(TripId("t1"), later)
        writer.insert(TripId("t2"), full)
        writer.insert(TripId("t1"), full)

        assertEquals(listOf(full, later), store().history(TripId("t1")))
        assertEquals(emptyList<EnvironmentSnapshot>(), store().history(TripId("none")))
    }

    @Test fun `outdoor air cache keeps only the latest fetch`() = runTest {
        val writer = store()
        assertNull(writer.cachedAir())
        writer.saveAir(CachedAir(37.6, 127.0, pm10 = 10.0, pm25 = null, observedAtEpochMs = 5, fetchedAtEpochMs = 6))
        val latest = CachedAir(35.2, 129.1, pm10 = 20.0, pm25 = 9.0, observedAtEpochMs = 7, fetchedAtEpochMs = 8)
        writer.saveAir(latest)

        assertEquals(latest, store().cachedAir())
    }
}
