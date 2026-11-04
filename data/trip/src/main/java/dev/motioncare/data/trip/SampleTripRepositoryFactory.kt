package dev.motioncare.data.trip

import android.content.Context
import android.os.SystemClock
import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.*
import dev.motioncare.data.trip.local.MotionCareDatabase
import dev.motioncare.data.trip.sample.SampleVehicleDataSource
import dev.motioncare.data.trip.session.TripSessionController
import dev.motioncare.data.trip.storage.SqliteTripStore
import dev.motioncare.data.trip.vehicle.CarSpeedSource
import dev.motioncare.data.trip.vehicle.SdkSteeringSource
import dev.motioncare.data.trip.vehicle.UnavailableVehicleDataSource
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Explicitly sample-only, for devices without a vehicle. */
fun createSampleTripRepository(context: Context): TripRepository {
    val store = SqliteTripStore(MotionCareDatabase(context.applicationContext))
    return DefaultTripRepository(store, controller(store, SampleVehicleDataSource()))
}

/** Call only on automotive devices. Uses its own database so samples never mix with real trips. */
fun createVehicleTripData(context: Context): VehicleTripData {
    val app = context.applicationContext
    val store = SqliteTripStore(MotionCareDatabase(app, "motion-care-trips.db"))
    val controller = controller(store, UnavailableVehicleDataSource())
    val repository = DefaultTripRepository(store, controller)
    return VehicleTripData(repository, DefaultVehicleRecorder(repository, controller, CarSpeedSource(app), SdkSteeringSource(app)))
}

private fun controller(store: SqliteTripStore, source: VehicleDataSource) = TripSessionController(
    store, store, source,
    EpochClock { System.currentTimeMillis() },
    TripIdFactory { TripId(UUID.randomUUID().toString()) },
    MonotonicClock { SystemClock.elapsedRealtimeNanos() },
)

internal class DefaultTripRepository(
    private val store: SqliteTripStore,
    private val controller: TripSessionController,
) : TripRepository {
    override val records = store.records
    private val mutex = Mutex()
    private var initialized = false
    override suspend fun initialize() = mutex.withLock {
        if (!initialized) {
            store.load()
            controller.initialize()
            initialized = true
        }
    }
    override suspend fun get(id: TripId): TripSnapshot? { initialize(); return store.get(id) }
    override suspend fun start(vehicleId: VehicleId?): CommandResult {
        initialize(); return controller.start(vehicleId)
    }
    override suspend fun captureSample(): CommandResult { initialize(); return controller.captureSample() }
    override suspend fun finish(): CommandResult { initialize(); return controller.finish() }
}

internal class DefaultVehicleRecorder(
    private val repository: TripRepository,
    private val controller: TripSessionController,
    private val signals: VehicleSignalSource,
    private val steering: SteeringSignalSource,
) : VehicleRecorder {
    override suspend fun record() {
        repository.initialize()
        controller.record(signals.speeds(), steering.steering())
    }
}
