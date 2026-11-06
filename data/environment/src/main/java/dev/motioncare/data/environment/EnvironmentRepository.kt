package dev.motioncare.data.environment

import android.content.Context
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.TripId
import dev.motioncare.data.environment.air.OpenMeteoAirSource
import dev.motioncare.data.environment.location.PleosLocationSource
import dev.motioncare.data.environment.storage.EnvironmentDatabase
import dev.motioncare.data.environment.storage.SqliteEnvironmentStore
import dev.motioncare.data.environment.vehicle.CarClimateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Car climate, outside temperature and outdoor air. Rules and sources are in docs/environment.md. */
interface EnvironmentRepository {
    /** The latest values. Every reading is LOADING until the first refresh. */
    val current: StateFlow<EnvironmentSnapshot>

    /** Reads the car again, and outdoor air when an hour has passed or the car moved. Missing values never throw. */
    suspend fun refresh()

    /** Refreshes and stores the result for the trip. */
    suspend fun capture(tripId: TripId)

    /** Stored values for the trip in capture order. */
    suspend fun history(tripId: TripId): List<EnvironmentSnapshot>
}

/** Loads android.car and the Pleos SDKs. Call only on automotive devices. */
fun createVehicleEnvironmentRepository(context: Context): EnvironmentRepository {
    val app = context.applicationContext
    return DefaultEnvironmentRepository(
        climate = CarClimateSource(app),
        location = PleosLocationSource(app),
        air = OpenMeteoAirSource(),
        store = SqliteEnvironmentStore(EnvironmentDatabase(app)),
        clock = System::currentTimeMillis,
    )
}

/** For devices without a car. Every reading is unsupported and nothing is stored; no value is made up. */
fun createUnsupportedEnvironmentRepository(): EnvironmentRepository = UnsupportedEnvironmentRepository()

private class UnsupportedEnvironmentRepository : EnvironmentRepository {
    override val current: StateFlow<EnvironmentSnapshot> = MutableStateFlow(
        snapshotOf(System.currentTimeMillis(), Availability.UNSUPPORTED),
    ).asStateFlow()

    override suspend fun refresh() = Unit
    override suspend fun capture(tripId: TripId) = Unit
    override suspend fun history(tripId: TripId): List<EnvironmentSnapshot> = emptyList()
}

/** A snapshot whose readings all hold no value. */
internal fun snapshotOf(atEpochMs: Long, availability: Availability) = EnvironmentSnapshot(
    capturedAtEpochMs = atEpochMs,
    outsideTemperature = EnvironmentReading(null, availability, EnvironmentSource.VEHICLE),
    cabinTemperature = EnvironmentReading(null, availability, EnvironmentSource.VEHICLE),
    cabinSetTemperature = EnvironmentReading(null, availability, EnvironmentSource.VEHICLE),
    recirculation = EnvironmentReading(null, availability, EnvironmentSource.VEHICLE),
    airCleaner = EnvironmentReading(null, availability, EnvironmentSource.VEHICLE),
    outdoorAir = EnvironmentReading(null, availability, EnvironmentSource.OPEN_METEO_CAMS),
)
