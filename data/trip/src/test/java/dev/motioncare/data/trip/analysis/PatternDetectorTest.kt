package dev.motioncare.data.trip.analysis

import dev.motioncare.core.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class PatternDetectorTest {
    private val stepMs = 200L

    /** Builds 5 Hz samples by joining (seconds, km/h) points with straight lines. */
    private fun drive(vararg points: Pair<Double, Double>): List<SpeedSample> {
        val samples = mutableListOf<SpeedSample>()
        var elapsed = 0L
        val end = (points.last().first * 1000).toLong()
        while (elapsed <= end) {
            val seconds = elapsed / 1000.0
            val index = points.indexOfLast { it.first <= seconds }.coerceAtMost(points.size - 2)
            val (t0, v0) = points[index]
            val (t1, v1) = points[index + 1]
            val kmh = v0 + (v1 - v0) * (seconds - t0) / (t1 - t0)
            samples += SpeedSample(elapsed, kmh / 3.6, Quality.VALID)
            elapsed += stepMs
        }
        return samples
    }

    /** Builds 5 Hz samples of a sinusoidal acceleration, as used in motion sickness experiments. */
    private fun oscillate(frequencyHz: Double, peakMps2: Double, seconds: Int): List<SpeedSample> {
        val omega = 2 * PI * frequencyHz
        return (0..seconds * 5).map { i ->
            SpeedSample(i * stepMs, 12.0 + peakMps2 / omega * sin(omega * i * 0.2), Quality.VALID)
        }
    }

    private fun detect(samples: List<SpeedSample>): List<Observation> {
        val detector = PatternDetector()
        return samples.flatMap(detector::add) + detector.finish()
    }

    private fun List<Observation>.patterns() = map { it.pattern }

    @Test fun `gentle start cruise and stop produce nothing`() {
        val samples = drive(0.0 to 0.0, 2.0 to 0.0, 22.0 to 50.0, 60.0 to 50.0, 80.0 to 0.0, 82.0 to 0.0)
        assertEquals(emptyList<Observation>(), detect(samples))
    }

    @Test fun `quick acceleration from a stop is a rapid start`() {
        val found = detect(drive(0.0 to 0.0, 2.0 to 0.0, 5.0 to 30.0, 40.0 to 30.0))
        assertEquals(listOf(DrivingPattern.RAPID_START), found.patterns())
        assertEquals(2.77, found.single().peakAccelerationMps2, 0.2)
        assertTrue(found.single().startMs in 2000L..2600L)
    }

    @Test fun `acceleration below the limit is not a rapid start`() {
        // 1.4 m/s2 for 5 seconds.
        assertEquals(emptyList<Observation>(), detect(drive(0.0 to 0.0, 2.0 to 0.0, 7.0 to 25.0, 40.0 to 25.0)))
    }

    @Test fun `quick acceleration while already moving is not a rapid start`() {
        assertEquals(emptyList<Observation>(), detect(drive(0.0 to 30.0, 10.0 to 30.0, 13.0 to 60.0, 40.0 to 60.0)))
    }

    @Test fun `strong braking into a stop is a hard stop`() {
        val found = detect(drive(0.0 to 40.0, 10.0 to 40.0, 14.0 to 0.0, 20.0 to 0.0))
        assertEquals(listOf(DrivingPattern.HARD_STOP), found.patterns())
        assertEquals(2.77, found.single().peakAccelerationMps2, 0.2)
        assertTrue(found.single().endMs in 13_000L..14_200L)
    }

    @Test fun `strong braking that does not reach a stop is not a hard stop`() {
        assertEquals(emptyList<Observation>(), detect(drive(0.0 to 60.0, 10.0 to 60.0, 13.0 to 30.0, 40.0 to 30.0)))
    }

    @Test fun `a start just over the limit is a rapid start and one just under is not`() {
        assertEquals(listOf(DrivingPattern.RAPID_START), detect(drive(0.0 to 0.0, 2.0 to 0.0, 5.0 to 22.68, 40.0 to 22.68)).patterns())
        assertEquals(emptyList<Observation>(), detect(drive(0.0 to 0.0, 2.0 to 0.0, 5.0 to 20.52, 40.0 to 20.52)))
    }

    @Test fun `a stop just over the limit is a hard stop and one just under is not`() {
        assertEquals(listOf(DrivingPattern.HARD_STOP), detect(drive(0.0 to 30.24, 10.0 to 30.24, 14.0 to 0.0, 20.0 to 0.0)).patterns())
        assertEquals(emptyList<Observation>(), detect(drive(0.0 to 27.36, 10.0 to 27.36, 14.0 to 0.0, 20.0 to 0.0)))
    }

    @Test fun `braking about four seconds before the stop is a hard stop`() {
        // Most drivers reach their peak deceleration within about 5 s of stopping.
        val found = detect(drive(0.0 to 40.0, 10.0 to 40.0, 12.0 to 12.0, 17.0 to 0.0, 22.0 to 0.0))
        assertEquals(listOf(DrivingPattern.HARD_STOP), found.patterns())
    }

    @Test fun `braking long before the stop is not a hard stop`() {
        assertEquals(emptyList<Observation>(), detect(drive(0.0 to 40.0, 10.0 to 40.0, 12.0 to 12.0, 24.0 to 0.0, 30.0 to 0.0)))
    }

    @Test fun `stopping again after a short creep does not count the same braking twice`() {
        // The second stop is gentle, but the first braking is still inside the look-back window.
        val found = detect(drive(0.0 to 20.0, 5.0 to 20.0, 6.5 to 0.0, 7.2 to 5.0, 8.0 to 0.0, 14.0 to 0.0))
        assertEquals(listOf(DrivingPattern.HARD_STOP), found.patterns())
    }

    @Test fun `two strong stops close together are both reported`() {
        val found = detect(drive(0.0 to 20.0, 5.0 to 20.0, 6.5 to 0.0, 8.0 to 15.0, 9.0 to 0.0, 14.0 to 0.0))
        assertEquals(2, found.count { it.pattern == DrivingPattern.HARD_STOP })
    }

    @Test fun `observations never share a stored key on a rough signal`() {
        val random = Random(7)
        var speed = 0.0
        val samples = (0L..20_000L).map { i ->
            speed = (speed + random.nextDouble(-1.0, 1.0)).coerceIn(0.0, 8.0)
            SpeedSample(i * stepMs, speed, Quality.VALID)
        }
        val found = detect(samples)
        assertTrue(found.size > 10)
        assertEquals(found.size, found.map { it.pattern to it.startMs }.toSet().size)
    }

    @Test fun `repeated speeding up and slowing down is reported`() {
        val points = (0..10).map { it * 3.0 to if (it % 2 == 0) 30.0 else 40.0 }.toTypedArray()
        val found = detect(drive(*points))
        assertTrue(found.isNotEmpty())
        assertEquals(setOf(DrivingPattern.REPEATED_SPEED_CHANGE), found.patterns().toSet())
        assertTrue(found.first().endMs > found.first().startMs)
    }

    @Test fun `accelerating and braking twice in a row is repeated`() {
        val found = detect(drive(0.0 to 30.0, 5.0 to 30.0, 8.0 to 40.0, 11.0 to 30.0, 14.0 to 40.0, 17.0 to 30.0, 40.0 to 30.0))
        assertEquals(listOf(DrivingPattern.REPEATED_SPEED_CHANGE), found.patterns())
    }

    @Test fun `accelerating braking and accelerating again is not repeated yet`() {
        assertEquals(emptyList<Observation>(), detect(drive(0.0 to 30.0, 5.0 to 30.0, 8.0 to 40.0, 11.0 to 30.0, 14.0 to 40.0, 40.0 to 40.0)))
    }

    @Test fun `motion at the conditions tested in sickness experiments is repeated`() {
        // Peak 1.0 m/s2 along the direction of travel at 0.2 Hz and at 0.06 Hz.
        assertEquals(setOf(DrivingPattern.REPEATED_SPEED_CHANGE), detect(oscillate(0.2, 1.0, 60)).patterns().toSet())
        assertEquals(setOf(DrivingPattern.REPEATED_SPEED_CHANGE), detect(oscillate(0.06, 1.0, 90)).patterns().toSet())
    }

    @Test fun `swings under the limit are not repeated`() {
        assertEquals(emptyList<Observation>(), detect(oscillate(0.2, 0.5, 60)))
        // 0.85 m/s2 for 3 s each way.
        val points = (0..10).map { it * 3.0 to if (it % 2 == 0) 30.0 else 39.18 }.toTypedArray()
        assertEquals(emptyList<Observation>(), detect(drive(*points)))
    }

    @Test fun `one speed change and back is not repeated`() {
        assertEquals(emptyList<Observation>(), detect(drive(0.0 to 30.0, 10.0 to 30.0, 13.0 to 40.0, 16.0 to 30.0, 40.0 to 30.0)))
    }

    @Test fun `changes spread far apart are not repeated`() {
        // Each change is quick enough to count, but 20 seconds of cruising separates them.
        val points = (0..7).flatMap { i ->
            val from = if (i % 2 == 0) 30.0 else 40.0
            val to = if (i % 2 == 0) 40.0 else 30.0
            listOf(i * 23.0 to from, i * 23.0 + 3.0 to to)
        }.toTypedArray()
        assertEquals(emptyList<Observation>(), detect(drive(*points)))
    }

    @Test fun `small sensor noise while cruising produces nothing`() {
        val noisy = (0L..300L).map { i ->
            val noise = listOf(0.1, -0.1, 0.05, -0.05)[(i % 4).toInt()]
            SpeedSample(i * stepMs, 15.0 + noise, Quality.VALID)
        }
        assertEquals(emptyList<Observation>(), detect(noisy))
    }

    @Test fun `a gap in the signal does not look like acceleration`() {
        val before = (0L..10L).map { SpeedSample(it * stepMs, 0.0, Quality.VALID) }
        val after = (0L..20L).map { SpeedSample(10_000 + it * stepMs, 10.0, Quality.VALID) }
        assertEquals(emptyList<Observation>(), detect(before + after))
    }

    @Test fun `unavailable samples are not treated as zero speed`() {
        val moving = (0L..10L).map { SpeedSample(it * stepMs, 15.0, Quality.VALID) }
        val missing = (11L..15L).map { SpeedSample(it * stepMs, null, Quality.UNAVAILABLE) }
        val back = (16L..30L).map { SpeedSample(it * stepMs, 15.0, Quality.VALID) }
        assertEquals(emptyList<Observation>(), detect(moving + missing + back))
    }

    @Test fun `a trip ending right after a quick start still reports it`() {
        val found = detect(drive(0.0 to 0.0, 2.0 to 0.0, 3.5 to 20.0))
        assertEquals(listOf(DrivingPattern.RAPID_START), found.patterns())
    }

    @Test fun `same samples give the same result and carry the version`() {
        val samples = drive(0.0 to 0.0, 2.0 to 0.0, 5.0 to 30.0, 10.0 to 30.0, 13.0 to 0.0, 15.0 to 0.0)
        val first = detect(samples)
        assertEquals(first, detect(samples))
        assertEquals(listOf(DrivingPattern.RAPID_START, DrivingPattern.HARD_STOP), first.patterns())
        assertTrue(first.all { it.detectorVersion == PATTERN_DETECTOR_VERSION })
    }
}
