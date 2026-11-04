package dev.motioncare.data.trip.analysis

import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.Observation
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sin
import kotlin.random.Random

class LateralDetectorTest {
    private val stepMs = 200L
    private val limits = LateralLimits()

    private fun kmh(value: Double) = value / 3.6

    /** The steering angle that produces this lateral acceleration at this speed. */
    private fun angleFor(acceleration: Double, speedMps: Double) =
        atan(acceleration * limits.wheelbaseMeters / (speedMps * speedMps)) * limits.steeringRatio * 180 / PI

    /** Runs 5 Hz samples at a steady speed through the detector. */
    private fun detect(speedMps: Double, seconds: Double, angle: (Double) -> Double): List<Observation> {
        val detector = LateralDetector()
        val found = mutableListOf<Observation>()
        var i = 0L
        while (i * stepMs <= seconds * 1000) {
            found += detector.add(i * stepMs, speedMps, angle(i * stepMs / 1000.0))
            i++
        }
        return found + detector.finish()
    }

    private fun sway(frequencyHz: Double, peakMps2: Double, speedMps: Double = 15.0): (Double) -> Double = { t ->
        angleFor(peakMps2 * sin(2 * PI * frequencyHz * t), speedMps)
    }

    private fun List<Observation>.patterns() = map { it.pattern }

    @Test fun `lateral acceleration follows the bicycle model`() {
        // 57.04 degrees at the wheel is 4 degrees at the road wheels at the default ratio.
        assertEquals(2.33, lateralAcceleration(10.0, 57.04, limits), 0.01)
        assertEquals(-2.33, lateralAcceleration(10.0, -57.04, limits), 0.01)
        assertEquals(0.0, lateralAcceleration(0.0, 57.04, limits), 0.0)
    }

    @Test fun `driving straight produces nothing`() {
        assertEquals(emptyList<Observation>(), detect(kmh(60.0), 60.0) { 0.0 })
    }

    @Test fun `a turn over the limit is a fast turn`() {
        val found = detect(kmh(40.0), 12.0) { t -> if (t in 3.0..8.0) 60.0 else 0.0 }
        assertEquals(listOf(DrivingPattern.FAST_TURN), found.patterns())
        assertEquals(3.03, found.single().peakAccelerationMps2, 0.05)
        assertTrue(found.single().startMs in 3000L..4000L)
        assertTrue(found.single().endMs in 8000L..9500L)
    }

    @Test fun `a turn in either direction counts the same`() {
        val right = detect(kmh(40.0), 12.0) { t -> if (t in 3.0..8.0) 60.0 else 0.0 }.single()
        val left = detect(kmh(40.0), 12.0) { t -> if (t in 3.0..8.0) -60.0 else 0.0 }.single()
        assertEquals(right.peakAccelerationMps2, left.peakAccelerationMps2, 1e-9)
    }

    @Test fun `a turn just under the limit is not a fast turn`() {
        val angle = angleFor(1.9, kmh(40.0))
        assertEquals(emptyList<Observation>(), detect(kmh(40.0), 12.0) { t -> if (t in 3.0..8.0) angle else 0.0 })
    }

    @Test fun `the same steering at walking speed is not a fast turn`() {
        assertEquals(emptyList<Observation>(), detect(kmh(15.0), 12.0) { t -> if (t in 3.0..8.0) 60.0 else 0.0 })
    }

    @Test fun `a trip ending inside a turn still reports it`() {
        assertEquals(listOf(DrivingPattern.FAST_TURN), detect(kmh(40.0), 6.0) { t -> if (t >= 3.0) 60.0 else 0.0 }.patterns())
    }

    @Test fun `motion at the conditions tested in sickness experiments is repeated`() {
        // Peak 1.0 m/s2 side to side at 0.2 Hz and at 0.06 Hz.
        assertEquals(setOf(DrivingPattern.REPEATED_SWAY), detect(15.0, 60.0, sway(0.2, 1.0)).patterns().toSet())
        assertEquals(setOf(DrivingPattern.REPEATED_SWAY), detect(15.0, 90.0, sway(0.06, 1.0)).patterns().toSet())
    }

    @Test fun `small swings are not repeated`() {
        assertEquals(emptyList<Observation>(), detect(15.0, 60.0, sway(0.2, 0.5)))
    }

    @Test fun `one lane change is not repeated`() {
        val found = detect(kmh(60.0), 30.0) { t ->
            when {
                t in 5.0..7.0 -> angleFor(1.2, kmh(60.0))
                t in 7.0..9.0 -> -angleFor(1.2, kmh(60.0))
                else -> 0.0
            }
        }
        assertEquals(emptyList<Observation>(), found)
    }

    @Test fun `turns far apart are not repeated`() {
        val found = detect(kmh(40.0), 200.0) { t ->
            val phase = (t / 40.0).toInt()
            if (t % 40.0 < 4.0) angleFor(1.2, kmh(40.0)) * if (phase % 2 == 0) 1 else -1 else 0.0
        }
        assertEquals(emptyList<Observation>(), found)
    }

    @Test fun `a gap in the signal restarts the judgment`() {
        val detector = LateralDetector()
        val angle = angleFor(1.2, 15.0)
        // Left, right and left again would be repeated without the 10 second gaps between them.
        val found = listOf(0L to angle, 10_000L to -angle, 20_000L to angle, 30_000L to -angle).flatMap { (start, degrees) ->
            (0L..15L).flatMap { i -> detector.add(start + i * stepMs, 15.0, degrees) }
        } + detector.finish()
        assertEquals(emptyList<Observation>(), found)
    }

    @Test fun `observations never share a stored key on rough steering`() {
        val random = Random(11)
        var angle = 0.0
        val detector = LateralDetector()
        val found = (0L..20_000L).flatMap { i ->
            angle = (angle + random.nextDouble(-12.0, 12.0)).coerceIn(-90.0, 90.0)
            detector.add(i * stepMs, 12.0, angle)
        } + detector.finish()
        assertTrue(found.size > 10)
        assertEquals(found.size, found.map { it.pattern to it.startMs }.toSet().size)
    }

    @Test fun `same input gives the same result and carries the version`() {
        val first = detect(15.0, 60.0, sway(0.2, 1.0))
        assertEquals(first, detect(15.0, 60.0, sway(0.2, 1.0)))
        assertTrue(first.all { it.detectorVersion == PATTERN_DETECTOR_VERSION })
    }
}
