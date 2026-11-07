package dev.motioncare.core.model

@JvmInline value class TripId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class PersonId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class VehicleId(val value: String) { init { require(value.isNotBlank()) } }

enum class DataOrigin { SYNTHETIC, VEHICLE }
enum class TripStatus { RECORDING, COMPLETED, INTERRUPTED }
enum class Quality { VALID, UNAVAILABLE, STALE }

data class SpeedSample(
    val elapsedMs: Long,
    val metersPerSecond: Double?,
    val quality: Quality,
) {
    init {
        require(elapsedMs >= 0)
        require(metersPerSecond == null || (metersPerSecond.isFinite() && metersPerSecond >= 0))
        require(quality != Quality.VALID || metersPerSecond != null)
    }
}

data class TripRecord(
    val id: TripId,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val status: TripStatus = TripStatus.RECORDING,
    val vehicleId: VehicleId? = null,
    val origin: DataOrigin,
    val revision: Long = 0,
) {
    init {
        require(revision >= 0)
        require((status == TripStatus.RECORDING) == (endedAtEpochMs == null))
        require(endedAtEpochMs == null || endedAtEpochMs >= startedAtEpochMs)
    }
}

enum class DrivingPattern { RAPID_START, HARD_STOP, REPEATED_SPEED_CHANGE, FAST_TURN, REPEATED_SWAY }

/** A driving pattern seen in a trip. It describes vehicle motion, not a passenger's symptoms. */
data class Observation(
    val pattern: DrivingPattern,
    val startMs: Long,
    val endMs: Long,
    val peakAccelerationMps2: Double,
    val detectorVersion: String,
) {
    init {
        require(startMs >= 0)
        require(endMs >= startMs)
        require(peakAccelerationMps2.isFinite() && peakAccelerationMps2 >= 0)
        require(detectorVersion.isNotBlank())
    }
}

enum class GuidanceOutcome { SHOWN, SKIPPED_INTERVAL, SKIPPED_OFF, SKIPPED_EXPIRED, FAILED }

/** Cabin air advice. Each one needs the car's air intake state to match it; see docs/guidance-card.md. */
enum class EnvironmentAdvice {
    /** Outdoor air is bad while the car takes air from outside. */
    REDUCE_OUTSIDE_AIR,

    /** Outdoor air is good while the car has recirculated cabin air for a while. */
    TRY_OUTSIDE_AIR,
}

/** What an in-drive card is about: a driving pattern that repeated, the cabin air, or a long stretch of driving. */
sealed interface GuidanceTopic {
    data class Pattern(val pattern: DrivingPattern) : GuidanceTopic
    data class Environment(val advice: EnvironmentAdvice) : GuidanceTopic

    /** The car has been driven for a long time without a break. */
    data object LongDrive : GuidanceTopic
}

/** What happened to one in-drive card. Times are measured from the start of the trip. */
data class GuidanceEvent(
    val elapsedMs: Long,
    val observedMs: Long,
    val topic: GuidanceTopic,
    val outcome: GuidanceOutcome,
) {
    constructor(elapsedMs: Long, observedMs: Long, pattern: DrivingPattern, outcome: GuidanceOutcome) :
        this(elapsedMs, observedMs, GuidanceTopic.Pattern(pattern), outcome)

    init {
        require(elapsedMs >= 0)
        require(observedMs >= 0)
    }
}

/** A steering wheel angle in degrees, as the vehicle reports it. */
data class SteeringSample(val elapsedMs: Long, val degrees: Double) {
    init {
        require(elapsedMs >= 0)
        require(degrees.isFinite())
    }
}

data class TripSnapshot(
    val trip: TripRecord,
    val samples: List<SpeedSample> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val steering: List<SteeringSample> = emptyList(),
    val guidance: List<GuidanceEvent> = emptyList(),
) {
    init { require(samples.zipWithNext().all { (a, b) -> a.elapsedMs < b.elapsedMs }) }
}

sealed interface CommandResult {
    data class Success(val tripId: TripId) : CommandResult
    data class Rejected(val reason: String) : CommandResult
}
