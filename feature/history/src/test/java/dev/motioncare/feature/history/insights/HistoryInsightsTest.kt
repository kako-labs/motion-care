package dev.motioncare.feature.history.insights

import dev.motioncare.core.model.*
import org.junit.Assert.*
import org.junit.Test

class HistoryInsightsTest {
    @Test fun `an unavailable sample must not be bridged by the distance calculation`() {
        val result = insight(snapshot("missing", 3000, listOf(
            SpeedSample(0, 10.0, Quality.VALID),
            SpeedSample(1000, null, Quality.UNAVAILABLE),
            SpeedSample(2000, 10.0, Quality.VALID),
            SpeedSample(3000, 10.0, Quality.VALID),
        )))
        assertEquals(10.0, result.distanceMeters, 0.001)
        assertEquals(1000L, result.analyzedDurationMs)
    }

    @Test fun `distance uses trapezoids and excludes gaps over five seconds`() {
        val snapshot = snapshot(
            id = "distance",
            durationMs = 8_000,
            samples = listOf(
                SpeedSample(0, 10.0, Quality.VALID),
                SpeedSample(1_000, 10.0, Quality.VALID),
                SpeedSample(7_000, 20.0, Quality.VALID),
                SpeedSample(8_000, 20.0, Quality.VALID),
            ),
        )

        val result = insight(snapshot)

        assertEquals(30.0, result.distanceMeters, 0.001)
        assertEquals(2_000L, result.analyzedDurationMs)
        assertTrue(result.ratesPerTenMinutes.isEmpty())
    }

    @Test fun `ten minute rates use analyzed duration rather than trip wall time`() {
        val observations = listOf(
            observation(DrivingPattern.HARD_STOP, 100_000),
            observation(DrivingPattern.HARD_STOP, 400_000),
        )
        val result = insight(snapshot(
            id = "rate",
            durationMs = 900_000,
            samples = validTenMinuteSamples(),
            observations = observations,
        ))

        assertEquals(2, result.count(DrivingPattern.HARD_STOP))
        assertEquals(2.0, result.rate(DrivingPattern.HARD_STOP)!!, 0.001)
    }

    @Test fun `advice only compares completed trips for the selected driver`() {
        val driver = PersonId("driver")
        val another = PersonId("another")
        val first = insight(
            snapshot("one", 600_000, validTenMinuteSamples(), listOf(observation(DrivingPattern.HARD_STOP, 100_000))),
            TripParticipants(TripId("one"), driver, emptySet(), 0),
        )
        val second = insight(
            snapshot("two", 600_000, validTenMinuteSamples(), listOf(
                observation(DrivingPattern.HARD_STOP, 100_000),
                observation(DrivingPattern.HARD_STOP, 200_000),
            )),
            TripParticipants(TripId("two"), driver, emptySet(), 0),
        )
        val unrelated = insight(
            snapshot("other", 600_000, validTenMinuteSamples(), List(5) { observation(DrivingPattern.RAPID_START, it * 10_000L) }),
            TripParticipants(TripId("other"), another, emptySet(), 0),
        )

        val advice = adviceFor(driver, listOf(first, second, unrelated))

        assertEquals(AdviceState.READY, advice.state)
        assertEquals(DrivingPattern.HARD_STOP, advice.pattern)
        assertEquals(2, advice.latestCount)
        assertEquals(listOf(1.0, 2.0), advice.recentRates)
    }

    private fun validTenMinuteSamples() = (0L..600_000L step 5_000L).map {
        SpeedSample(it, 5.0, Quality.VALID)
    }

    private fun snapshot(
        id: String,
        durationMs: Long,
        samples: List<SpeedSample>,
        observations: List<Observation> = emptyList(),
    ) = TripSnapshot(
        TripRecord(TripId(id), 1_000, 1_000 + durationMs, TripStatus.COMPLETED, origin = DataOrigin.VEHICLE),
        samples,
        observations,
    )

    private fun observation(pattern: DrivingPattern, at: Long) = Observation(pattern, at, at + 1_000, 2.0, "test-v1")
}
