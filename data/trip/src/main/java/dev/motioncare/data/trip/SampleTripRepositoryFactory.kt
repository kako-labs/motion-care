package dev.motioncare.data.trip

import android.content.Context
import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.*
import dev.motioncare.data.trip.local.MotionCareDatabase
import dev.motioncare.data.trip.sample.SampleVehicleDataSource
import dev.motioncare.data.trip.session.TripSessionController
import dev.motioncare.data.trip.storage.SqliteTripStore
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Explicitly sample-only. A production adapter needs its own approved wiring. */
fun createSampleTripRepository(context: Context): TripRepository {
    val store = SqliteTripStore(MotionCareDatabase(context.applicationContext))
    return DefaultTripRepository(store, TripSessionController(store, store, SampleVehicleDataSource(),
        EpochClock { System.currentTimeMillis() }, TripIdFactory { TripId(UUID.randomUUID().toString()) }))
}

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
