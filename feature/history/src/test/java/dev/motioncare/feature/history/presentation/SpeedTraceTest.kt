package dev.motioncare.feature.history.presentation

import dev.motioncare.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SpeedTraceTest {
    private fun snapshot(samples: List<SpeedSample>) = TripSnapshot(
        TripRecord(TripId("trace"), 0, 80_000, TripStatus.COMPLETED, origin = DataOrigin.VEHICLE), samples)
    private fun sample(ms: Long, speed: Double? = 10.0) =
        SpeedSample(ms, speed, if (speed == null) Quality.UNAVAILABLE else Quality.VALID)

    @Test fun missingSampleAndLongGapBreakTheLine() {
        val source = snapshot(listOf(sample(0), sample(1000), sample(2000, null), sample(3000), sample(4000), sample(20_000)))
        val trace = speedTrace(source)
        assertEquals(listOf(listOf(0L, 1000L), listOf(3000L, 4000L), listOf(20_000L)), trace.segments.map { line -> line.map { it.elapsedMs } })
        assertEquals(3, trace.gaps.size) // Includes the unrecorded tail of the trip.
        assertNull(trace.sampleAt(2000))
        assertNull(trace.sampleAt(12_000))
    }

    @Test fun detailUsesOriginalSamplesAndObservationWithApproachAndRestart() {
        val source = snapshot((0..80).map { sample(it * 1000L, (80 - it).toDouble()) })
        val event = Observation(DrivingPattern.HARD_STOP, 30_000, 34_000, 2.5, "original-detector")
        val trace = speedTrace(source, event)
        assertEquals(6000L, trace.startMs)
        assertEquals(70_000L, trace.endMs)
        assertEquals(source.samples.filter { it.elapsedMs in 6000L..70_000L }, trace.segments.flatten())
        assertEquals(source.samples[31], trace.sampleAt(31_100))
        assertEquals(65, trace.segments.flatten().size)
    }

    @Test fun emptyAndSingleSampleDoNotInventHistory() {
        assertTrue(speedTrace(snapshot(emptyList())).segments.isEmpty())
        val only = sample(1000)
        assertEquals(listOf(only), speedTrace(snapshot(listOf(only))).segments.flatten())
        assertNull(speedTrace(snapshot(listOf(only))).sampleAt(40_000))
    }
}
