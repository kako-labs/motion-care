package dev.motioncare.app.home

import dev.motioncare.core.model.AirGrade
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.DataOrigin
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.MissingReason
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.PersonKind
import dev.motioncare.core.model.SeatPosition
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.core.model.SeatingPlan
import dev.motioncare.core.model.TripParticipants
import dev.motioncare.feature.history.insights.AdviceInsight
import dev.motioncare.feature.history.insights.patternLabel
import dev.motioncare.core.model.TripId
import dev.motioncare.core.model.TripRecord
import dev.motioncare.core.model.TripStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** [people] names who rode, or null when no one has been registered yet. */
data class RecentTripItem(
    val id: TripId,
    val startedAt: String,
    val detail: String,
    val synthetic: Boolean,
    val people: String? = null,
)

/**
 * Finished and interrupted trips, newest first. The trip being recorded is left out. An interrupted trip gets its
 * end time when the next process notices it, so it shows no length.
 */
fun recentTripItems(
    records: List<TripRecord>,
    zone: TimeZone,
    limit: Int = 3,
    participants: List<TripParticipants> = emptyList(),
    people: List<PersonProfile> = emptyList(),
): List<RecentTripItem> {
    val format = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).apply { timeZone = zone }
    val names = people.associate { it.id to it.displayName }
    val riders = participants.associateBy { it.tripId }
    return records.filter { it.status != TripStatus.RECORDING }
        .sortedByDescending { it.startedAtEpochMs }
        .take(limit)
        .map { trip ->
            val detail = when (trip.status) {
                TripStatus.INTERRUPTED -> "기록 중단"
                else -> trip.endedAtEpochMs?.let { lengthText(it - trip.startedAtEpochMs) }.orEmpty()
            }
            val rode = riders[trip.id]?.let { listOfNotNull(it.driverId) + it.passengerIds }.orEmpty().mapNotNull(names::get)
            RecentTripItem(
                trip.id, format.format(Date(trip.startedAtEpochMs)), detail, trip.origin == DataOrigin.SYNTHETIC,
                people = when {
                    people.isEmpty() -> null
                    rode.isEmpty() -> "인물 미지정"
                    else -> rode.joinToString(" · ")
                },
            )
        }
}

private fun lengthText(ms: Long): String = if (ms < 60_000) "1분 미만" else "${(ms / 60_000.0).roundToInt()}분"

/**
 * Without [hasValue], [value] says why the value is missing. [note] marks a value kept from an earlier reading.
 * [warning] marks outdoor air graded bad or worse.
 */
data class EnvironmentItem(
    val label: String,
    val value: String,
    val hasValue: Boolean,
    val warning: Boolean,
    val note: String?,
)

/**
 * The four values the home design shows. A missing value shows why in its place, in the words of
 * docs/environment.md, and is never filled from another reading.
 */
data class EnvironmentCard(
    val items: List<EnvironmentItem>,
    val updatedAt: String?,
    val showsAirSource: Boolean,
    val unsupported: Boolean,
)

fun environmentCard(snapshot: EnvironmentSnapshot, zone: TimeZone): EnvironmentCard {
    val air = snapshot.outdoorAir
    val grade = air.value?.grade
    val shown = listOf(air, snapshot.outsideTemperature, snapshot.cabinTemperature, snapshot.recirculation)
    val items = listOf(
        // A reading without a grade has nothing to show and reads "값 없음".
        item("외부 대기질", air, grade?.let(::gradeText), warning = grade == AirGrade.BAD || grade == AirGrade.VERY_BAD),
        item("외부 기온", snapshot.outsideTemperature, snapshot.outsideTemperature.value?.let(::celsius)),
        item("실내 온도", snapshot.cabinTemperature, snapshot.cabinTemperature.value?.let(::celsius)),
        item("내기 순환", snapshot.recirculation, snapshot.recirculation.value?.let { if (it) "켜짐" else "꺼짐" }),
    )
    val time = SimpleDateFormat("HH:mm", Locale.KOREAN).apply { timeZone = zone }
    return EnvironmentCard(
        items = items,
        updatedAt = if (shown.any { it.holdsValue() }) "${time.format(Date(snapshot.capturedAtEpochMs))} 갱신" else null,
        showsAirSource = air.holdsValue(),
        unsupported = shown.all { it.availability == Availability.UNSUPPORTED },
    )
}

private fun item(label: String, reading: EnvironmentReading<*>, text: String?, warning: Boolean = false) =
    if (reading.holdsValue() && text != null) {
        EnvironmentItem(label, text, true, warning, if (reading.availability == Availability.STALE) "오래된 값" else null)
    } else {
        EnvironmentItem(label, missingText(reading), hasValue = false, warning = false, note = null)
    }

private fun EnvironmentReading<*>.holdsValue() =
    availability == Availability.AVAILABLE || availability == Availability.STALE

private fun missingText(reading: EnvironmentReading<*>): String = when (reading.availability) {
    Availability.LOADING -> "확인 중"
    Availability.UNSUPPORTED -> "미지원"
    Availability.AVAILABLE, Availability.STALE -> "값 없음"
    Availability.UNAVAILABLE -> when (reading.missingReason) {
        MissingReason.NO_PERMISSION -> "권한 없음"
        MissingReason.NO_LOCATION -> "위치 확인 필요"
        MissingReason.NETWORK -> "네트워크 연결 필요"
        MissingReason.NO_DATA, null -> "값 없음"
    }
}

private fun gradeText(grade: AirGrade) = when (grade) {
    AirGrade.GOOD -> "좋음"
    AirGrade.MODERATE -> "보통"
    AirGrade.BAD -> "나쁨"
    AirGrade.VERY_BAD -> "매우 나쁨"
}

private fun celsius(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    val text = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else "%.1f".format(Locale.KOREAN, rounded)
    return "$text°C"
}

data class SeatedPerson(val person: PersonProfile, val role: String)

/** Who sits in the car now, driver first. [registered] counts everyone in the profile store. */
data class PeopleSummary(val shown: List<SeatedPerson>, val more: Int, val registered: Int)

fun peopleSummary(people: List<PersonProfile>, seating: SeatingPlan, max: Int = 2): PeopleSummary {
    val byId = people.associateBy { it.id }
    val seated = SeatPosition.entries.mapNotNull { seat ->
        seating.assignments[seat]?.let(byId::get)?.let { person -> SeatedPerson(person, roleOf(seat, person)) }
    }
    return PeopleSummary(seated.take(max), (seated.size - max).coerceAtLeast(0), people.size)
}

private fun roleOf(seat: SeatPosition, person: PersonProfile) = when {
    seat == SeatPosition.DRIVER -> "운전자"
    person.kind == PersonKind.CHILD -> "어린이 동승자"
    else -> "동승자"
}

enum class HeroAction { EXPLAIN, CHOOSE_SEATS, NONE }

/** The departure advice in the home hero. The comparison reads like Home2: "2.4회에서 1.4회로". */
data class HomeAdvice(
    val headline: String,
    val context: String?,
    val comparisonLabel: String?,
    val comparison: String?,
    val action: HeroAction,
)

/** With the departure advice turned off the hero says so, as the history feature's seat screen does. */
fun homeAdvice(advice: AdviceInsight, driverId: PersonId?, enabled: Boolean = true): HomeAdvice {
    if (!enabled) return HomeAdvice("출발 안내가 꺼져 있어요.", "아래 안내 카드에서 다시 켤 수 있어요.", null, null, HeroAction.NONE)
    val previous = advice.previousAverage
    val recent = advice.recentAverage
    val pattern = advice.pattern
    val compares = previous != null && recent != null && pattern != null
    return HomeAdvice(
        headline = advice.headline,
        context = advice.context,
        comparisonLabel = if (compares) "10분당 ${patternLabel(pattern!!)}" else null,
        comparison = if (compares) "${rate(previous!!)}회에서 ${rate(recent!!)}회로" else null,
        action = if (driverId == null) HeroAction.CHOOSE_SEATS else HeroAction.EXPLAIN,
    )
}

private fun rate(value: Double) = "%.1f".format(Locale.KOREAN, value)
