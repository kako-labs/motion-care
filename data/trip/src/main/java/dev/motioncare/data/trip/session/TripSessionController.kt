package dev.motioncare.data.trip.session

import dev.motioncare.core.model.*
import dev.motioncare.data.trip.analysis.LateralDetector
import dev.motioncare.data.trip.analysis.PatternDetector
import dev.motioncare.data.trip.internal.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    private val pendingObservations = mutableListOf<Observation>()
    private var detector = PatternDetector()
    private var lateral = LateralDetector()
    private val pendingSteering = mutableListOf<SteeringSample>()
    private var lastSteeringElapsedMs = -1L
    private var steeringDegrees: Double? = null

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
        pendingObservations.clear()
        detector = PatternDetector()
        lateral = LateralDetector()
        pendingSteering.clear()
        lastSteeringElapsedMs = -1
        steeringDegrees = null
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
        pendingObservations += detector.finish()
        pendingObservations += lateral.finish()
        val flushed = writePending(current)
        writer.saveTrip(flushed.copy(
            status = TripStatus.COMPLETED,
            endedAtEpochMs = endTime(flushed),
            revision = flushed.revision + 1,
        ))
        CommandResult.Success(current.id)
    }

    /** Stores readings until the active trip ends or the caller is cancelled. */
    suspend fun record(readings: Flow<SpeedReading>, steering: Flow<SteeringReading> = emptyFlow()) = coroutineScope {
        val collecting = launch { readings.collect { accept(it) } }
        val steeringJob = launch { collectSteering(steering) }
        reader.records.first { records -> records.none { it.status == TripStatus.RECORDING } }
        collecting.cancelAndJoin()
        steeringJob.cancelAndJoin()
    }

    // A steering failure ends left and right analysis only. Speed keeps recording.
    private suspend fun collectSteering(steering: Flow<SteeringReading>) {
        try {
            steering.collect { acceptSteering(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutex.withLock {
                steeringDegrees = null
                pendingObservations += lateral.finish()
            }
        }
    }

    private suspend fun acceptSteering(reading: SteeringReading) = mutex.withLock {
        active() ?: return@withLock
        val elapsed = (reading.elapsedRealtimeNanos - startedAtNanos) / 1_000_000
        steeringDegrees = reading.degrees
        if (elapsed < 0 || elapsed <= lastSteeringElapsedMs) return@withLock
        pendingSteering += SteeringSample(elapsed, reading.degrees)
        lastSteeringElapsedMs = elapsed
    }

    private suspend fun accept(reading: SpeedReading) = mutex.withLock {
        val current = active() ?: return@withLock
        val elapsed = (reading.elapsedRealtimeNanos - startedAtNanos) / 1_000_000
        if (elapsed < 0 || elapsed <= lastElapsedMs) return@withLock
        val sample = SpeedSample(elapsed, reading.metersPerSecond, reading.quality)
        pending += sample
        pendingObservations += detector.add(sample)
        val speed = sample.metersPerSecond?.takeIf { sample.quality == Quality.VALID }
        val degrees = steeringDegrees
        pendingObservations += if (speed != null && degrees != null) lateral.add(elapsed, speed, degrees) else lateral.finish()
        lastElapsedMs = elapsed
        if (elapsed - lastWrittenElapsedMs < WRITE_INTERVAL_MS) return@withLock
        // A failed write keeps the readings pending so the next interval or finish retries them.
        try { writePending(current) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
    }

    private suspend fun writePending(current: TripRecord): TripRecord {
        if (pending.isEmpty() && pendingObservations.isEmpty() && pendingSteering.isEmpty()) return current
        val updated = current.copy(revision = current.revision + 1)
        writer.appendSamples(updated, pending.toList(), pendingObservations.toList(), pendingSteering.toList())
        pending.lastOrNull()?.let { lastWrittenElapsedMs = it.elapsedMs }
        pending.clear()
        pendingObservations.clear()
        pendingSteering.clear()
        return updated
    }

    private suspend fun endTime(trip: TripRecord): Long = maxOf(
        clock.nowMs(), trip.startedAtEpochMs + (reader.lastSampleElapsedMs(trip.id) ?: 0),
    )

    private fun active(): TripRecord? = reader.records.value.firstOrNull { it.status == TripStatus.RECORDING }
}
