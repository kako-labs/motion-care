package dev.motioncare.data.trip.guidance

import dev.motioncare.core.model.*
import org.junit.Assert.*
import org.junit.Test

class EnvironmentAdvisorTest {
    private val advisor = EnvironmentAdvisor()
    private val minute = 60_000L

    private fun <T : Any> available(value: T, source: EnvironmentSource = EnvironmentSource.VEHICLE) =
        EnvironmentReading(value, Availability.AVAILABLE, source, observedAtEpochMs = 1)

    private fun <T : Any> missing(availability: Availability, source: EnvironmentSource = EnvironmentSource.VEHICLE) =
        EnvironmentReading<T>(null, availability, source,
            missingReason = if (availability == Availability.UNAVAILABLE) MissingReason.NO_DATA else null)

    private fun air(grade: AirGrade, availability: Availability = Availability.AVAILABLE) = EnvironmentReading(
        OutdoorAir(10.0, 10.0, grade, 37.6, 127.0), availability, EnvironmentSource.OPEN_METEO_CAMS, observedAtEpochMs = 1)

    private fun snapshot(
        recirculation: EnvironmentReading<Boolean>,
        outdoorAir: EnvironmentReading<OutdoorAir>,
    ) = EnvironmentSnapshot(
        capturedAtEpochMs = 1,
        outsideTemperature = missing(Availability.UNSUPPORTED),
        cabinTemperature = missing(Availability.UNSUPPORTED),
        cabinSetTemperature = missing(Availability.UNSUPPORTED),
        recirculation = recirculation,
        airCleaner = missing(Availability.UNSUPPORTED),
        outdoorAir = outdoorAir,
    )

    @Test fun `bad outdoor air while taking outside air asks to reduce it`() {
        assertEquals(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, advisor.onSnapshot(snapshot(available(false), air(AirGrade.BAD)), 0))
    }

    @Test fun `very bad outdoor air counts as bad`() {
        assertEquals(EnvironmentAdvice.REDUCE_OUTSIDE_AIR, advisor.onSnapshot(snapshot(available(false), air(AirGrade.VERY_BAD)), 0))
    }

    @Test fun `bad outdoor air while recirculating needs nothing`() {
        assertNull(advisor.onSnapshot(snapshot(available(true), air(AirGrade.BAD)), 0))
    }

    @Test fun `moderate outdoor air needs nothing either way`() {
        assertNull(advisor.onSnapshot(snapshot(available(false), air(AirGrade.MODERATE)), 0))
        assertNull(advisor.onSnapshot(snapshot(available(true), air(AirGrade.MODERATE)), 60 * minute))
    }

    @Test fun `good outdoor air after a long recirculation suggests outside air`() {
        assertNull(advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 0))
        assertNull(advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 25 * minute))
        assertEquals(EnvironmentAdvice.TRY_OUTSIDE_AIR,
            advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 30 * minute))
    }

    @Test fun `recirculation is timed even while outdoor air is unknown`() {
        advisor.onSnapshot(snapshot(available(true), missing(Availability.UNAVAILABLE, EnvironmentSource.OPEN_METEO_CAMS)), 0)
        assertEquals(EnvironmentAdvice.TRY_OUTSIDE_AIR,
            advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 30 * minute))
    }

    @Test fun `switching to outside air restarts the recirculation time`() {
        advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 0)
        advisor.onSnapshot(snapshot(available(false), air(AirGrade.GOOD)), 20 * minute)
        assertNull(advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 25 * minute))
        assertNull(advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 50 * minute))
        assertEquals(EnvironmentAdvice.TRY_OUTSIDE_AIR,
            advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 55 * minute))
    }

    @Test fun `an unknown intake state restarts the recirculation time`() {
        advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 0)
        assertNull(advisor.onSnapshot(snapshot(missing(Availability.UNAVAILABLE), air(AirGrade.GOOD)), 20 * minute))
        assertNull(advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 30 * minute))
    }

    @Test fun `old or missing outdoor air never leads to advice`() {
        assertNull(advisor.onSnapshot(snapshot(available(false), air(AirGrade.BAD, Availability.STALE)), 0))
        assertNull(advisor.onSnapshot(
            snapshot(available(false), missing(Availability.UNAVAILABLE, EnvironmentSource.OPEN_METEO_CAMS)), 0))
        advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD)), 0)
        assertNull(advisor.onSnapshot(snapshot(available(true), air(AirGrade.GOOD, Availability.STALE)), 40 * minute))
    }

    @Test fun `a car without an intake state never leads to advice`() {
        assertNull(advisor.onSnapshot(snapshot(missing(Availability.UNSUPPORTED), air(AirGrade.VERY_BAD)), 0))
        assertNull(advisor.onSnapshot(snapshot(missing(Availability.UNSUPPORTED), air(AirGrade.GOOD)), 60 * minute))
    }
}
