package dev.motioncare.data.trip.drive

/** The gear positions automatic recording needs. Manual gears and drive modes all count as [DRIVE]. */
internal enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

internal sealed interface AutoAction {
    data object None : AutoAction
    data object Start : AutoAction
    /** Ends the recording trip at [parkedAtMs], when the car went into park rather than when the wait ran out. */
    data class Finish(val parkedAtMs: Long) : AutoAction
    /** Ends the recording trip at [parkedAtMs], then starts a new one. */
    data class Restart(val parkedAtMs: Long) : AutoAction
}

// android.car.VehicleGear values, kept here so the policy has no Android dependency.
private const val VEHICLE_GEAR_NEUTRAL = 0x1
private const val VEHICLE_GEAR_REVERSE = 0x2
private const val VEHICLE_GEAR_PARK = 0x4
private const val VEHICLE_GEAR_DRIVE = 0x8
private const val VEHICLE_GEAR_FIRST_MANUAL = 0x10

internal fun gearOf(value: Int?): Gear = when {
    value == null -> Gear.UNKNOWN
    value == VEHICLE_GEAR_PARK -> Gear.PARK
    value == VEHICLE_GEAR_REVERSE -> Gear.REVERSE
    value == VEHICLE_GEAR_NEUTRAL -> Gear.NEUTRAL
    value == VEHICLE_GEAR_DRIVE || value >= VEHICLE_GEAR_FIRST_MANUAL -> Gear.DRIVE
    else -> Gear.UNKNOWN
}

/**
 * Starts a trip when the car leaves park and finishes it once the car has stayed in park for [parkedFinishMs].
 *
 * A trip that ended another way, such as the driver tapping "운행 기록 종료" while still in drive, does not start
 * again until the car has been in park, so one drive is never split by the driver's own stop. Neutral and a lost
 * signal neither start nor finish a trip. If the head unit sleeps in park, the drive gear may arrive before the
 * next tick; a park that already lasted [parkedFinishMs] then ends the old trip before the new one starts.
 * [nowMs] must count time asleep. Values are product judgements, see docs/auto-record.md.
 */
internal class AutoTripPolicy(private val parkedFinishMs: Long = PARKED_FINISH_MS) {
    private var armed = true
    private var parkedSinceMs: Long? = null

    fun onGear(gear: Gear, nowMs: Long, recording: Boolean): AutoAction = when (gear) {
        Gear.PARK -> {
            armed = true
            if (parkedSinceMs == null) parkedSinceMs = nowMs
            AutoAction.None
        }
        Gear.DRIVE, Gear.REVERSE -> {
            val longPark = parkedSinceMs?.takeIf { nowMs - it >= parkedFinishMs }
            parkedSinceMs = null
            val wasArmed = armed
            armed = false
            when {
                recording && longPark != null -> AutoAction.Restart(longPark)
                wasArmed && !recording -> AutoAction.Start
                else -> AutoAction.None
            }
        }
        Gear.NEUTRAL -> {
            parkedSinceMs = null
            AutoAction.None
        }
        Gear.UNKNOWN -> AutoAction.None
    }

    fun onTick(nowMs: Long, recording: Boolean): AutoAction {
        val since = parkedSinceMs ?: return AutoAction.None
        if (!recording || nowMs - since < parkedFinishMs) return AutoAction.None
        parkedSinceMs = null
        return AutoAction.Finish(since)
    }

    companion object {
        const val PARKED_FINISH_MS = 3 * 60_000L
    }
}
