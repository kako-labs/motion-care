package dev.motioncare.data.trip.guidance

import org.junit.Assert.*
import org.junit.Test

class LongDriveTrackerTest {
    private val tracker = LongDriveTracker()
    private val minute = 60_000L
    private val hour = 60 * minute

    /** One sample a second from [fromMs] to [toMs]; returns the first time a card was due, or null. */
    private fun drive(fromMs: Long, toMs: Long, speed: Double = 15.0): Long? {
        var due: Long? = null
        for (now in fromMs..toMs step 1000) {
            if (tracker.onSample(now, speed) && due == null) due = now
        }
        return due
    }

    @Test fun `two hours of driving makes a card due`() {
        assertEquals(2 * hour, drive(0, 2 * hour + minute))
    }

    @Test fun `stops shorter than a break still count as driving`() {
        assertNull(drive(0, hour))
        assertNull(drive(hour + 1000, hour + 14 * minute, speed = 0.0))
        assertEquals(2 * hour, drive(hour + 14 * minute + 1000, 2 * hour + minute))
    }

    @Test fun `a stop as long as a break starts the count again`() {
        assertNull(drive(0, 110 * minute))
        assertNull(drive(110 * minute + 1000, 125 * minute + 1000, speed = 0.0))
        val resumed = 125 * minute + 2000
        assertNull(drive(resumed, resumed + 2 * hour - 1000))
        assertEquals(resumed + 2 * hour, drive(resumed + 2 * hour, resumed + 2 * hour))
    }

    @Test fun `a crawl at walking pace counts as stopped`() {
        assertNull(drive(0, 110 * minute))
        assertNull(drive(110 * minute + 1000, 125 * minute + 1000, speed = 0.5))
        assertNull(drive(125 * minute + 2000, 2 * hour + 30 * minute))
    }

    @Test fun `no speed for as long as a break starts the count again`() {
        assertNull(drive(0, 110 * minute))
        val back = 110 * minute + 15 * minute
        assertNull(drive(back, back + 2 * hour - 1000))
        assertEquals(back + 2 * hour, drive(back + 2 * hour, back + 2 * hour))
    }

    @Test fun `a short gap in speed does not reset the count`() {
        assertNull(drive(0, hour))
        assertEquals(2 * hour, drive(hour + 5 * minute, 2 * hour + minute))
    }

    @Test fun `nothing is due while the car is stopped`() {
        assertNull(drive(0, 2 * hour - 1000))
        assertNull(drive(2 * hour, 2 * hour + 5 * minute, speed = 0.0))
        val moving = 2 * hour + 5 * minute + 1000
        assertEquals(moving, drive(moving, moving + minute))
    }

    @Test fun `time before the car first moves is not counted`() {
        assertNull(drive(0, 10 * minute, speed = 0.0))
        val moving = 10 * minute + 1000
        assertNull(drive(moving, moving + 2 * hour - 1000))
        assertEquals(moving + 2 * hour, drive(moving + 2 * hour, moving + 2 * hour))
    }

    @Test fun `a card stays due until it is used`() {
        drive(0, 2 * hour)
        assertTrue(tracker.onSample(2 * hour + 10 * minute, 15.0))
    }

    @Test fun `the next card is due two hours after the last one was used`() {
        drive(0, 2 * hour + 10 * minute)
        tracker.used(2 * hour + 10 * minute)
        assertNull(drive(2 * hour + 10 * minute + 1000, 4 * hour + 10 * minute - 1000))
        assertEquals(4 * hour + 10 * minute, drive(4 * hour + 10 * minute, 4 * hour + 11 * minute))
    }

    @Test fun `a break after a card starts a fresh count`() {
        drive(0, 2 * hour)
        tracker.used(2 * hour)
        drive(2 * hour + 1000, 2 * hour + 20 * minute, speed = 0.0)
        val resumed = 2 * hour + 20 * minute + 1000
        assertNull(drive(resumed, resumed + 2 * hour - 1000))
        assertEquals(resumed + 2 * hour, drive(resumed + 2 * hour, resumed + 2 * hour))
    }
}
