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

data class TripSnapshot(val trip: TripRecord, val samples: List<SpeedSample> = emptyList()) {
    init { require(samples.zipWithNext().all { (a, b) -> a.elapsedMs < b.elapsedMs }) }
}

sealed interface CommandResult {
    data class Success(val tripId: TripId) : CommandResult
    data class Rejected(val reason: String) : CommandResult
}
