package dev.motioncare.data.trip.session

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.internal.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val WRITE_INTERVAL_MS = 1_000L

internal class TripSessionController(
    private val reader: TripReader,
    private val writer: TripWriter,
    private val source: VehicleDataSource,
    private val clock: EpochClock,
    private val ids: TripIdFactory,
    private val monotonic: MonotonicClock,
) : TripCommands {
    private val mutex = Mutex()
    private var initialized = false

    // Only the trip started by this controller records; a new process never resumes one.
    private var startedAtNanos = 0L
    private var lastElapsedMs = -1L
    private var lastWrittenElapsedMs = 0L
    private val pending = mutableListOf<SpeedSample>()

    // Called once per application graph; an activity rotation must not interrupt a trip.
    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        reader.records.value.filter { it.status == TripStatus.RECORDING }.forEach { record ->
            writer.saveTrip(record.copy(
                status = TripStatus.INTERRUPTED,
                endedAtEpochMs = endTime(record),
                revision = record.revision + 1,
            ))
        }
        initialized = true
    }

    override suspend fun start(vehicleId: VehicleId?): CommandResult = mutex.withLock {
        check(initialized)
        if (active() != null) return@withLock CommandResult.Rejected("이미 주행을 기록 중입니다.")
        val record = TripRecord(id = ids.next(), startedAtEpochMs = clock.nowMs(),
            vehicleId = vehicleId, origin = source.origin)
        writer.saveTrip(record)
        startedAtNanos = monotonic.nowNanos()
        lastElapsedMs = -1
        lastWrittenElapsedMs = 0
        pending.clear()
        CommandResult.Success(record.id)
    }

    override suspend fun captureSample(): CommandResult = mutex.withLock {
        check(initialized)
        val current = active() ?: return@withLock CommandResult.Rejected("기록 중인 주행이 없습니다.")
        val saved = requireNotNull(reader.get(current.id))
        val elapsed = clock.nowMs() - current.startedAtEpochMs
        if (elapsed < 0 || saved.samples.lastOrNull()?.elapsedMs?.let { elapsed <= it } == true) {
            return@withLock CommandResult.Rejected("관측 시각이 이전 샘플보다 늦어야 합니다.")
        }
        val sample = source.sample(elapsed, saved.samples.size)
        writer.appendSamples(current.copy(revision = current.revision + 1), listOf(sample))
        CommandResult.Success(current.id)
    }

    override suspend fun finish(): CommandResult = mutex.withLock {
        check(initialized)
        val current = active() ?: return@withLock CommandResult.Rejected("기록 중인 주행이 없습니다.")
        val flushed = writePending(current)
        writer.saveTrip(flushed.copy(
            status = TripStatus.COMPLETED,
            endedAtEpochMs = endTime(flushed),
            revision = flushed.revision + 1,
        ))
        CommandResult.Success(current.id)
    }

    /** Stores readings until the active trip ends or the caller is cancelled. */
    suspend fun record(readings: Flow<SpeedReading>) = coroutineScope {
        val collecting = launch { readings.collect { accept(it) } }
        reader.records.first { records -> records.none { it.status == TripStatus.RECORDING } }
        collecting.cancelAndJoin()
    }

    private suspend fun accept(reading: SpeedReading) = mutex.withLock {
        val current = active() ?: return@withLock
        val elapsed = (reading.elapsedRealtimeNanos - startedAtNanos) / 1_000_000
        if (elapsed < 0 || elapsed <= lastElapsedMs) return@withLock
        pending += SpeedSample(elapsed, reading.metersPerSecond, reading.quality)
        lastElapsedMs = elapsed
        if (elapsed - lastWrittenElapsedMs < WRITE_INTERVAL_MS) return@withLock
        // A failed write keeps the readings pending so the next interval or finish retries them.
        try { writePending(current) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
    }

    private suspend fun writePending(current: TripRecord): TripRecord {
        if (pending.isEmpty()) return current
        val updated = current.copy(revision = current.revision + 1)
        writer.appendSamples(updated, pending.toList())
        lastWrittenElapsedMs = pending.last().elapsedMs
        pending.clear()
        return updated
    }

    private suspend fun endTime(trip: TripRecord): Long = maxOf(
        clock.nowMs(), trip.startedAtEpochMs + (reader.lastSampleElapsedMs(trip.id) ?: 0),
    )

    private fun active(): TripRecord? = reader.records.value.firstOrNull { it.status == TripStatus.RECORDING }
}
