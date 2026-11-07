package dev.motioncare.data.trip.guidance

/** Each value and its source is listed in docs/guidance-card.md. */
internal data class LongDriveLimits(
    val everyMs: Long = 2 * 60 * 60_000L,
    val breakMs: Long = 15 * 60_000L,
    val stoppedBelowMps: Double = 0.5,
)

/**
 * Counts driving time since the car first moved or since the last break. A stop or a gap in speed as long as a break
 * starts the count again; shorter stops such as traffic lights count as driving.
 */
internal class LongDriveTracker(private val limits: LongDriveLimits = LongDriveLimits()) {
    private var lastSampleMs: Long? = null
    private var stoppedSinceMs: Long? = null
    private var dueAtMs: Long? = null

    /** Returns true while a card is due and the car is moving. [now] is measured from the start of the trip. */
    fun onSample(now: Long, speedMps: Double): Boolean {
        val last = lastSampleMs
        lastSampleMs = now
        if (last != null && now - last >= limits.breakMs) dueAtMs = null
        if (speedMps <= limits.stoppedBelowMps) {
            val since = stoppedSinceMs ?: now.also { stoppedSinceMs = it }
            if (now - since >= limits.breakMs) dueAtMs = null
            return false
        }
        stoppedSinceMs = null
        val due = dueAtMs ?: (now + limits.everyMs).also { dueAtMs = it }
        return now >= due
    }

    /** A card was decided at [now]; the next one is due after another stretch of driving. */
    fun used(now: Long) {
        if (dueAtMs != null) dueAtMs = now + limits.everyMs
    }
}
