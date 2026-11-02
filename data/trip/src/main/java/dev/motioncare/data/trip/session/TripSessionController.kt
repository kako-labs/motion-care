package dev.motioncare.data.trip.session

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TripSessionController(
    private val reader: TripReader,
    private val writer: TripWriter,
    private val source: VehicleDataSource,
    private val clock: EpochClock,
    private val ids: TripIdFactory,
) : TripCommands {
    private val mutex = Mutex()
    private var initialized = false

    // Called once per application graph; an activity rotation must not interrupt a trip.
    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        reader.records.value.filter { it.status == TripStatus.RECORDING }.forEach { record ->
            val snapshot = requireNotNull(reader.get(record.id))
            writer.save(snapshot.copy(trip = record.copy(
                status = TripStatus.INTERRUPTED,
                endedAtEpochMs = endTime(snapshot),
                revision = record.revision + 1,
            )))
        }
        initialized = true
    }

    override suspend fun start(vehicleId: VehicleId?): CommandResult = mutex.withLock {
        check(initialized)
        if (active() != null) return@withLock CommandResult.Rejected("이미 샘플 주행을 기록 중입니다.")
        val record = TripRecord(id = ids.next(), startedAtEpochMs = clock.nowMs(),
            vehicleId = vehicleId, origin = source.origin)
        writer.save(TripSnapshot(record))
        CommandResult.Success(record.id)
    }

    override suspend fun captureSample(): CommandResult = mutex.withLock {
        check(initialized)
        val current = active() ?: return@withLock CommandResult.Rejected("기록 중인 주행이 없습니다.")
        val elapsed = clock.nowMs() - current.trip.startedAtEpochMs
        if (elapsed < 0 || current.samples.lastOrNull()?.elapsedMs?.let { elapsed <= it } == true) {
            return@withLock CommandResult.Rejected("관측 시각이 이전 샘플보다 늦어야 합니다.")
        }
        val sample = source.sample(elapsed, current.samples.size)
        writer.save(current.copy(trip = current.trip.copy(revision = current.trip.revision + 1),
            samples = current.samples + sample))
        CommandResult.Success(current.trip.id)
    }

    override suspend fun finish(): CommandResult = mutex.withLock {
        check(initialized)
        val current = active() ?: return@withLock CommandResult.Rejected("기록 중인 주행이 없습니다.")
        writer.save(current.copy(trip = current.trip.copy(
            status = TripStatus.COMPLETED,
            endedAtEpochMs = endTime(current),
            revision = current.trip.revision + 1,
        )))
        CommandResult.Success(current.trip.id)
    }

    private fun endTime(snapshot: TripSnapshot): Long = maxOf(
        clock.nowMs(), snapshot.trip.startedAtEpochMs + (snapshot.samples.lastOrNull()?.elapsedMs ?: 0),
    )

    private suspend fun active(): TripSnapshot? = reader.records.value
        .firstOrNull { it.status == TripStatus.RECORDING }?.let { reader.get(it.id) }
}
