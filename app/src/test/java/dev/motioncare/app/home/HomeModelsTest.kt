package dev.motioncare.app.home

import dev.motioncare.core.model.AirGrade
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.DataOrigin
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.MissingReason
import dev.motioncare.core.model.OutdoorAir
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.core.model.TripId
import dev.motioncare.core.model.TripRecord
import dev.motioncare.core.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

private val SEOUL: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

// 2026-11-02 08:12 KST
private const val NOV_2_0812 = 1_793_574_720_000L
private const val MINUTE = 60_000L

private fun trip(
    id: String,
    startedAt: Long,
    endedAt: Long? = null,
    status: TripStatus = TripStatus.COMPLETED,
    origin: DataOrigin = DataOrigin.VEHICLE,
) = TripRecord(TripId(id), startedAt, endedAt, status, origin = origin)

private fun <T : Any> value(value: T, source: EnvironmentSource = EnvironmentSource.VEHICLE, stale: Boolean = false) =
    EnvironmentReading(value, if (stale) Availability.STALE else Availability.AVAILABLE, source, NOV_2_0812)

private fun <T : Any> missing(
    availability: Availability,
    reason: MissingReason? = null,
    source: EnvironmentSource = EnvironmentSource.VEHICLE,
) = EnvironmentReading<T>(null, availability, source, missingReason = reason)

private fun snapshot(
    outside: EnvironmentReading<Double> = value(8.0),
    cabin: EnvironmentReading<Double> = value(23.5),
    recirculation: EnvironmentReading<Boolean> = value(false),
    air: EnvironmentReading<OutdoorAir> =
        value(OutdoorAir(40.0, 30.0, AirGrade.BAD, 37.4, 127.1), EnvironmentSource.OPEN_METEO_CAMS),
) = EnvironmentSnapshot(
    capturedAtEpochMs = NOV_2_0812 - 7 * MINUTE,
    outsideTemperature = outside,
    cabinTemperature = cabin,
    cabinSetTemperature = missing(Availability.UNSUPPORTED),
    recirculation = recirculation,
    airCleaner = missing(Availability.UNSUPPORTED),
    outdoorAir = air,
)

class HomeModelsTest {
    @Test fun recentTripsAreNewestFirstWithoutTheOneBeingRecorded() {
        val items = recentTripItems(
            listOf(
                trip("old", NOV_2_0812 - 3 * 24 * 60 * MINUTE, NOV_2_0812 - 3 * 24 * 60 * MINUTE + 10 * MINUTE),
                trip("now", NOV_2_0812 + 60 * MINUTE, status = TripStatus.RECORDING),
                trip("new", NOV_2_0812, NOV_2_0812 + 38 * MINUTE),
                trip("mid", NOV_2_0812 - 24 * 60 * MINUTE, NOV_2_0812 - 24 * 60 * MINUTE + 26 * MINUTE),
                trip("oldest", NOV_2_0812 - 5 * 24 * 60 * MINUTE, NOV_2_0812 - 5 * 24 * 60 * MINUTE + MINUTE),
            ),
            SEOUL,
        )
        assertEquals(listOf("new", "mid", "old"), items.map { it.id.value })
    }

    @Test fun aCompletedTripShowsItsStartAndLength() {
        val item = recentTripItems(listOf(trip("a", NOV_2_0812, NOV_2_0812 + 38 * MINUTE)), SEOUL).single()
        assertEquals("11월 2일 08:12", item.startedAt)
        assertEquals("38분", item.detail)
        assertFalse(item.synthetic)
    }

    @Test fun aTripShorterThanAMinuteSaysSo() {
        val item = recentTripItems(listOf(trip("a", NOV_2_0812, NOV_2_0812 + 59_000)), SEOUL).single()
        assertEquals("1분 미만", item.detail)
    }

    // An interrupted trip ends when the next process notices it, so its end time is not the recorded length.
    @Test fun anInterruptedTripSaysItStoppedWithoutALength() {
        val item = recentTripItems(
            listOf(trip("b", NOV_2_0812, NOV_2_0812 + 300 * MINUTE, TripStatus.INTERRUPTED)),
            SEOUL,
        ).single()
        assertEquals("기록 중단", item.detail)
    }

    @Test fun aSampleTripIsMarkedSynthetic() {
        val item = recentTripItems(
            listOf(trip("a", NOV_2_0812, NOV_2_0812 + MINUTE, origin = DataOrigin.SYNTHETIC)),
            SEOUL,
        ).single()
        assertTrue(item.synthetic)
    }

    @Test fun environmentShowsTheFourHomeValues() {
        val card = environmentCard(snapshot(), SEOUL)
        assertEquals(listOf("외부 대기질", "외부 기온", "실내 온도", "내기 순환"), card.items.map { it.label })
        assertEquals(listOf("나쁨", "8°C", "23.5°C", "꺼짐"), card.items.map { it.value })
        assertTrue(card.items.all { it.hasValue })
        assertTrue(card.items.first().warning)
        assertFalse(card.items[1].warning)
        assertEquals("08:05 갱신", card.updatedAt)
        assertTrue(card.showsAirSource)
        assertFalse(card.unsupported)
    }

    @Test fun missingValuesShowWhyInsteadOfANumber() {
        val card = environmentCard(
            snapshot(
                outside = missing(Availability.LOADING),
                cabin = missing(Availability.UNAVAILABLE, MissingReason.NO_DATA),
                recirculation = missing(Availability.UNSUPPORTED),
                air = missing(Availability.UNAVAILABLE, MissingReason.NO_PERMISSION, EnvironmentSource.OPEN_METEO_CAMS),
            ),
            SEOUL,
        )
        assertEquals(listOf("권한 없음", "확인 중", "값 없음", "미지원"), card.items.map { it.value })
        assertTrue(card.items.none { it.hasValue })
        assertNull(card.updatedAt)
        assertFalse(card.showsAirSource)
    }

    @Test fun eachMissingReasonHasItsOwnWords() {
        fun airWith(reason: MissingReason) = environmentCard(
            snapshot(air = missing(Availability.UNAVAILABLE, reason, EnvironmentSource.OPEN_METEO_CAMS)),
            SEOUL,
        ).items.first().value
        assertEquals("위치 확인 필요", airWith(MissingReason.NO_LOCATION))
        assertEquals("네트워크 연결 필요", airWith(MissingReason.NETWORK))
    }

    @Test fun anOldValueIsMarkedOld() {
        val card = environmentCard(
            snapshot(air = value(OutdoorAir(10.0, 8.0, AirGrade.GOOD, 37.4, 127.1), EnvironmentSource.OPEN_METEO_CAMS, stale = true)),
            SEOUL,
        )
        assertEquals("좋음", card.items.first().value)
        assertEquals("오래된 값", card.items.first().note)
        assertNull(card.items[1].note)
    }

    @Test fun anAirReadingWithoutAGradeHasNoValue() {
        val card = environmentCard(
            snapshot(air = value(OutdoorAir(null, null, null, 37.4, 127.1), EnvironmentSource.OPEN_METEO_CAMS)),
            SEOUL,
        )
        assertEquals("값 없음", card.items.first().value)
        assertFalse(card.items.first().hasValue)
        assertFalse(card.items.first().warning)
    }

    @Test fun aDeviceWithoutACarSaysSoOnce() {
        val card = environmentCard(
            snapshot(
                outside = missing(Availability.UNSUPPORTED),
                cabin = missing(Availability.UNSUPPORTED),
                recirculation = missing(Availability.UNSUPPORTED),
                air = missing(Availability.UNSUPPORTED, source = EnvironmentSource.OPEN_METEO_CAMS),
            ),
            SEOUL,
        )
        assertTrue(card.unsupported)
    }

    @Test fun peopleBeyondTwoAreCounted() {
        val people = listOf("민준", "서아", "도윤").mapIndexed { i, name -> PersonProfile(PersonId("p$i"), name) }
        assertEquals(PeopleSummary(people.take(2), 1), peopleSummary(people))
        assertEquals(PeopleSummary(people.take(1), 0), peopleSummary(people.take(1)))
    }
}
