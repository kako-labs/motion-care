package dev.motioncare.data.environment

import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.MissingReason
import dev.motioncare.core.model.OutdoorAir
import dev.motioncare.core.model.TripId
import dev.motioncare.data.environment.air.AirResponseException
import dev.motioncare.data.environment.air.OutdoorAirSource
import dev.motioncare.data.environment.air.airGradeOf
import dev.motioncare.data.environment.location.LocationSource
import dev.motioncare.data.environment.location.Position
import dev.motioncare.data.environment.location.rounded
import dev.motioncare.data.environment.storage.CachedAir
import dev.motioncare.data.environment.storage.SqliteEnvironmentStore
import dev.motioncare.data.environment.vehicle.ClimateSource
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Product judgements, see docs/environment.md. The provider reports one value per hour.
internal const val AIR_REFRESH_MS = 60 * 60 * 1000L
internal const val AIR_STALE_MS = 3 * 60 * 60 * 1000L

internal class DefaultEnvironmentRepository(
    private val climate: ClimateSource,
    private val location: LocationSource,
    private val air: OutdoorAirSource,
    private val store: SqliteEnvironmentStore,
    private val clock: () -> Long,
) : EnvironmentRepository {
    private val mutable = MutableStateFlow(snapshotOf(clock(), Availability.LOADING))
    override val current: StateFlow<EnvironmentSnapshot> = mutable.asStateFlow()
    private val mutex = Mutex()
    private var cache: CachedAir? = null
    private var cacheLoaded = false

    override suspend fun refresh() {
        mutex.withLock { mutable.value = read() }
    }

    override suspend fun capture(tripId: TripId) = mutex.withLock {
        val snapshot = read()
        mutable.value = snapshot
        store.insert(tripId, snapshot)
    }

    override suspend fun history(tripId: TripId): List<EnvironmentSnapshot> = store.history(tripId)

    private suspend fun read(): EnvironmentSnapshot {
        val car = climate.read()
        val outdoor = outdoorAir()
        return EnvironmentSnapshot(clock(), car.outside, car.cabin, car.cabinSet, car.recirculation, car.airCleaner, outdoor)
    }

    private suspend fun outdoorAir(): EnvironmentReading<OutdoorAir> {
        val place = when (val position = location.current()) {
            is Position.Found -> position.rounded()
            Position.Unsupported -> return EnvironmentReading(null, Availability.UNSUPPORTED, EnvironmentSource.OPEN_METEO_CAMS)
            is Position.Missing -> return unavailableAir(position.reason)
        }
        val now = clock()
        val cached = cachedAir()?.takeIf { it.latitude == place.latitude && it.longitude == place.longitude }
        if (cached != null && now - cached.fetchedAtEpochMs < AIR_REFRESH_MS) return cached.reading(now)
        return try {
            val sample = air.fetch(place)
            if (sample.pm10 == null && sample.pm25 == null) return cached?.reading(now) ?: unavailableAir(MissingReason.NO_DATA)
            val fresh = CachedAir(place.latitude, place.longitude, sample.pm10, sample.pm25, sample.observedAtEpochMs, now)
            store.saveAir(fresh)
            cache = fresh
            fresh.reading(now)
        } catch (_: IOException) {
            cached?.reading(now) ?: unavailableAir(MissingReason.NETWORK)
        } catch (_: AirResponseException) {
            cached?.reading(now) ?: unavailableAir(MissingReason.NO_DATA)
        }
    }

    private suspend fun cachedAir(): CachedAir? {
        if (!cacheLoaded) {
            cache = store.cachedAir()
            cacheLoaded = true
        }
        return cache
    }
}

private fun CachedAir.reading(now: Long) = EnvironmentReading(
    value = OutdoorAir(pm10, pm25, airGradeOf(pm10, pm25), latitude, longitude),
    availability = if (now - observedAtEpochMs > AIR_STALE_MS) Availability.STALE else Availability.AVAILABLE,
    source = EnvironmentSource.OPEN_METEO_CAMS,
    observedAtEpochMs = observedAtEpochMs,
)

private fun unavailableAir(reason: MissingReason) = EnvironmentReading<OutdoorAir>(
    null, Availability.UNAVAILABLE, EnvironmentSource.OPEN_METEO_CAMS, missingReason = reason)
