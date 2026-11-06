package dev.motioncare.data.environment

import dev.motioncare.core.model.AirGrade
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.MissingReason
import dev.motioncare.core.model.OutdoorAir
import dev.motioncare.core.model.TripId
import dev.motioncare.data.environment.air.AirResponseException
import dev.motioncare.data.environment.air.AirSample
import dev.motioncare.data.environment.air.OutdoorAirSource
import dev.motioncare.data.environment.location.LocationSource
import dev.motioncare.data.environment.location.Position
import dev.motioncare.data.environment.storage.EnvironmentDatabase
import dev.motioncare.data.environment.storage.SqliteEnvironmentStore
import dev.motioncare.data.environment.vehicle.ClimateReadings
import dev.motioncare.data.environment.vehicle.ClimateSource
import java.io.IOException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val HOUR_MS = 3_600_000L
private const val START_MS = 1_793_930_400_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EnvironmentRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var now = START_MS
    private var position: Position = Position.Found(37.5665, 126.978)
    private val fetched = mutableListOf<Position.Found>()
    private var airResult: () -> AirSample = { AirSample(pm10 = 20.0, pm25 = 40.0, observedAtEpochMs = now - 10 * 60_000) }

    private val climate = ClimateReadings(
        outside = EnvironmentReading(25.0, Availability.AVAILABLE, EnvironmentSource.VEHICLE, START_MS),
        cabin = EnvironmentReading(19.1, Availability.AVAILABLE, EnvironmentSource.VEHICLE, START_MS),
        cabinSet = EnvironmentReading(null, Availability.UNAVAILABLE, EnvironmentSource.VEHICLE,
            missingReason = MissingReason.NO_DATA),
        recirculation = EnvironmentReading(true, Availability.AVAILABLE, EnvironmentSource.VEHICLE, START_MS),
        airCleaner = EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.VEHICLE),
    )

    private fun repository() = DefaultEnvironmentRepository(
        climate = ClimateSource { climate },
        location = LocationSource { position },
        air = OutdoorAirSource { found -> fetched += found; airResult() },
        store = SqliteEnvironmentStore(EnvironmentDatabase(context), UnconfinedTestDispatcher()),
        clock = { now },
    )

    @Test fun `every reading is loading before the first refresh`() {
        val current = repository().current.value

        listOf(current.outsideTemperature, current.cabinTemperature, current.cabinSetTemperature,
            current.recirculation, current.airCleaner, current.outdoorAir)
            .forEach { assertEquals(Availability.LOADING, it.availability) }
        assertEquals(EnvironmentSource.OPEN_METEO_CAMS, current.outdoorAir.source)
    }

    @Test fun `refresh passes vehicle readings through untouched`() = runTest {
        val repository = repository()

        repository.refresh()

        val current = repository.current.value
        assertEquals(START_MS, current.capturedAtEpochMs)
        assertEquals(climate.outside, current.outsideTemperature)
        assertEquals(climate.cabin, current.cabinTemperature)
        assertEquals(climate.cabinSet, current.cabinSetTemperature)
        assertEquals(climate.recirculation, current.recirculation)
        assertEquals(climate.airCleaner, current.airCleaner)
    }

    @Test fun `outdoor air is fetched for the rounded position and graded`() = runTest {
        val repository = repository()

        repository.refresh()

        assertEquals(listOf(Position.Found(37.6, 127.0)), fetched)
        assertEquals(
            EnvironmentReading(OutdoorAir(20.0, 40.0, AirGrade.BAD, 37.6, 127.0), Availability.AVAILABLE,
                EnvironmentSource.OPEN_METEO_CAMS, START_MS - 10 * 60_000),
            repository.current.value.outdoorAir,
        )
    }

    @Test fun `no location permission leaves outdoor air unavailable without a request`() = runTest {
        position = Position.Missing(MissingReason.NO_PERMISSION)
        val repository = repository()

        repository.refresh()

        assertEquals(emptyList<Position.Found>(), fetched)
        assertEquals(unavailable(MissingReason.NO_PERMISSION), repository.current.value.outdoorAir)
        assertEquals(climate.outside, repository.current.value.outsideTemperature)
    }

    @Test fun `car without location support has no outdoor air`() = runTest {
        position = Position.Unsupported
        val repository = repository()

        repository.refresh()

        assertEquals(
            EnvironmentReading<OutdoorAir>(null, Availability.UNSUPPORTED, EnvironmentSource.OPEN_METEO_CAMS),
            repository.current.value.outdoorAir,
        )
    }

    @Test fun `outdoor air is fetched at most once an hour for the same place`() = runTest {
        val repository = repository()
        repository.refresh()
        now += HOUR_MS - 1
        repository.refresh()
        assertEquals(1, fetched.size)

        now += 1
        repository.refresh()

        assertEquals(2, fetched.size)
    }

    @Test fun `moving to another rounded place fetches again`() = runTest {
        val repository = repository()
        repository.refresh()
        position = Position.Found(35.1796, 129.0756)

        repository.refresh()

        assertEquals(listOf(Position.Found(37.6, 127.0), Position.Found(35.2, 129.1)), fetched)
        assertEquals(35.2, repository.current.value.outdoorAir.value?.latitude)
    }

    @Test fun `network failure without a cached value is unavailable`() = runTest {
        airResult = { throw IOException("offline") }
        val repository = repository()

        repository.refresh()

        assertEquals(unavailable(MissingReason.NETWORK), repository.current.value.outdoorAir)
    }

    @Test fun `network failure keeps the cached value and marks it stale after three hours`() = runTest {
        val repository = repository()
        repository.refresh()
        val cached = repository.current.value.outdoorAir.value
        airResult = { throw IOException("offline") }

        now += HOUR_MS
        repository.refresh()
        assertEquals(Availability.AVAILABLE, repository.current.value.outdoorAir.availability)

        now += 2 * HOUR_MS
        repository.refresh()
        assertEquals(Availability.STALE, repository.current.value.outdoorAir.availability)
        assertEquals(cached, repository.current.value.outdoorAir.value)
    }

    @Test fun `cached value for another place is not shown`() = runTest {
        val repository = repository()
        repository.refresh()
        airResult = { throw IOException("offline") }
        position = Position.Found(35.1796, 129.0756)

        repository.refresh()

        assertEquals(unavailable(MissingReason.NETWORK), repository.current.value.outdoorAir)
    }

    @Test fun `response without pollutant values is no data and is not cached`() = runTest {
        airResult = { AirSample(pm10 = null, pm25 = null, observedAtEpochMs = now) }
        val repository = repository()
        repository.refresh()
        assertEquals(unavailable(MissingReason.NO_DATA), repository.current.value.outdoorAir)
        airResult = { throw AirResponseException("bad body") }

        repository.refresh()

        assertEquals(2, fetched.size)
        assertEquals(unavailable(MissingReason.NO_DATA), repository.current.value.outdoorAir)
    }

    @Test fun `cache survives a restart and avoids a second request`() = runTest {
        repository().refresh()
        now += 10 * 60_000
        val restarted = repository()

        restarted.refresh()

        assertEquals(1, fetched.size)
        assertEquals(Availability.AVAILABLE, restarted.current.value.outdoorAir.availability)
    }

    @Test fun `capture stores the values read for the trip`() = runTest {
        val repository = repository()
        repository.capture(TripId("t1"))
        now += HOUR_MS
        repository.capture(TripId("t1"))

        val history = repository().history(TripId("t1"))

        assertEquals(listOf(START_MS, START_MS + HOUR_MS), history.map { it.capturedAtEpochMs })
        assertEquals(repository.current.value, history.last())
    }

    @Test fun `unsupported device reports every reading as unsupported and stores nothing`() = runTest {
        val repository = createUnsupportedEnvironmentRepository()

        repository.refresh()
        repository.capture(TripId("t1"))

        val current = repository.current.value
        listOf(current.outsideTemperature, current.cabinTemperature, current.cabinSetTemperature,
            current.recirculation, current.airCleaner, current.outdoorAir)
            .forEach { assertEquals(Availability.UNSUPPORTED, it.availability) }
        assertEquals(emptyList<Any>(), repository.history(TripId("t1")))
    }

    private fun unavailable(reason: MissingReason) = EnvironmentReading<OutdoorAir>(
        null, Availability.UNAVAILABLE, EnvironmentSource.OPEN_METEO_CAMS, missingReason = reason)
}
