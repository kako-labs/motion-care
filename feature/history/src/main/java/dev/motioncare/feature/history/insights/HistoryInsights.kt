package dev.motioncare.feature.history.insights

import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import kotlin.math.roundToInt

private const val MAX_CONTIGUOUS_GAP_MS = 5_000L
private const val MIN_RATE_DURATION_MS = 60_000L

data class TripInsight(
    val snapshot: TripSnapshot,
    val participants: TripParticipants?,
    val durationMs: Long,
    val analyzedDurationMs: Long,
    val distanceMeters: Double,
    val counts: Map<DrivingPattern, Int>,
    val ratesPerTenMinutes: Map<DrivingPattern, Double>,
) {
    val trip get() = snapshot.trip
    fun count(pattern: DrivingPattern) = counts[pattern] ?: 0
    fun rate(pattern: DrivingPattern) = ratesPerTenMinutes[pattern]
}

enum class AdviceState { FIRST_TRIP, INITIAL, READY, INSUFFICIENT }
data class AdviceInsight(
    val state: AdviceState,
    val pattern: DrivingPattern?,
    val headline: String,
    val context: String?,
    val latestCount: Int?,
    val latestAnalyzedMinutes: Int?,
    val recentRates: List<Double>,
    val previousAverage: Double?,
    val recentAverage: Double?,
) {
    val tips: List<String> get() = when (pattern) {
        DrivingPattern.HARD_STOP -> listOf("앞차와 거리를 넉넉히", "정차 지점 전부터 천천히 감속", "출발은 서두르지 않게")
        DrivingPattern.RAPID_START -> listOf("출발 전 주변 흐름 확인", "가속 페달을 천천히", "목표 속도에 여유 있게 도달")
        DrivingPattern.REPEATED_SPEED_CHANGE -> listOf("앞차와 간격을 넉넉히", "속도를 일정하게 유지", "정체 구간에서는 급히 따라가지 않기")
        DrivingPattern.FAST_TURN -> listOf("커브 진입 전 미리 감속", "회전 중 속도를 일정하게", "급한 조향을 줄이기")
        DrivingPattern.REPEATED_SWAY -> listOf("차선 중앙을 부드럽게 유지", "연속 차선 변경을 줄이기", "좌우 움직임 뒤 속도를 안정시키기")
        null -> listOf("출발과 감속을 여유 있게", "동승자 상태를 확인", "필요하면 쉬어가기")
    }
}

suspend fun loadInsights(trips: TripRepository, profiles: ProfileRepository): List<TripInsight> {
    val assignments = profiles.participants.value.associateBy { it.tripId }
    return trips.records.value.mapNotNull { record -> trips.get(record.id)?.let { insight(it, assignments[record.id]) } }
        .sortedByDescending { it.trip.startedAtEpochMs }
}

fun insight(snapshot: TripSnapshot, participants: TripParticipants? = null): TripInsight {
    val valid = snapshot.samples.filter { it.quality == Quality.VALID && it.metersPerSecond != null }
    val validSegments = valid.zipWithNext().filter { (left, right) ->
        val gap = right.elapsedMs - left.elapsedMs
        gap > 0 && gap <= MAX_CONTIGUOUS_GAP_MS
    }
    val analyzed = validSegments.sumOf { (left, right) -> right.elapsedMs - left.elapsedMs }
    val duration = snapshot.trip.endedAtEpochMs?.minus(snapshot.trip.startedAtEpochMs)
        ?: snapshot.samples.lastOrNull()?.elapsedMs ?: 0L
    val distance = validSegments.sumOf { (left, right) ->
        val gap = right.elapsedMs - left.elapsedMs
        ((requireNotNull(left.metersPerSecond) + requireNotNull(right.metersPerSecond)) / 2.0) * gap / 1_000.0
    }
    val counts = DrivingPattern.entries.associateWith { pattern -> snapshot.observations.count { it.pattern == pattern } }
    val rates = if (analyzed >= MIN_RATE_DURATION_MS) counts.mapValues { (_, count) -> count * 600_000.0 / analyzed } else emptyMap()
    return TripInsight(snapshot, participants, duration.coerceAtLeast(0), analyzed.coerceAtLeast(0), distance, counts, rates)
}

fun adviceFor(driverId: PersonId?, all: List<TripInsight>): AdviceInsight {
    if (driverId == null) return AdviceInsight(AdviceState.FIRST_TRIP, null, "누가 운전하나요?", null, null, null, emptyList(), null, null)
    val history = all.filter { it.participants?.driverId == driverId && it.trip.status == TripStatus.COMPLETED }
        .sortedBy { it.trip.startedAtEpochMs }
    if (history.isEmpty()) return AdviceInsight(AdviceState.FIRST_TRIP, null, "출발과 감속을 여유 있게 해보세요.", null, null, null, emptyList(), null, null)
    val usable = history.filter { it.ratesPerTenMinutes.isNotEmpty() }
    if (usable.isEmpty()) return AdviceInsight(AdviceState.INSUFFICIENT, null, "분석할 기록이 부족해요.", null, null, null, emptyList(), null, null)
    val recent = usable.takeLast(3)
    val chosen = DrivingPattern.entries.maxByOrNull { pattern -> recent.mapNotNull { it.rate(pattern) }.averageOrZero() }
    val rates = chosen?.let { pattern -> usable.takeLast(6).mapNotNull { it.rate(pattern) } }.orEmpty()
    val latest = usable.last()
    val previous = rates.dropLast(3).takeLast(3).takeIf { it.isNotEmpty() }?.average()
    val recentAverage = rates.takeLast(3).takeIf { it.isNotEmpty() }?.average()
    val state = if (usable.size == 1) AdviceState.INITIAL else AdviceState.READY
    val context = when {
        state == AdviceState.INITIAL -> "이번 운행에서 관찰됐어요."
        previous != null && recentAverage != null && recentAverage < previous -> "최근 기록에서 관찰 빈도가 줄었어요."
        else -> "최근 운행에서 같은 속도 변화가 반복됐어요."
    }
    return AdviceInsight(state, chosen, actionFor(chosen), context, chosen?.let(latest::count),
        (latest.analyzedDurationMs / 60_000.0).roundToInt(), rates, previous, recentAverage)
}

fun actionFor(pattern: DrivingPattern?): String = when (pattern) {
    DrivingPattern.HARD_STOP -> "정지 시 조금 일찍 감속해 보세요."
    DrivingPattern.RAPID_START -> "출발할 때 조금 천천히 가속해 보세요."
    DrivingPattern.REPEATED_SPEED_CHANGE -> "앞차와 간격을 두고 속도를 일정하게 유지해 보세요."
    DrivingPattern.FAST_TURN -> "커브 진입 전에 미리 감속해 보세요."
    DrivingPattern.REPEATED_SWAY -> "좌우 움직임이 이어지지 않도록 부드럽게 조향해 보세요."
    null -> "출발과 감속을 여유 있게 해보세요."
}

fun patternLabel(pattern: DrivingPattern): String = when (pattern) {
    DrivingPattern.HARD_STOP -> "정차 직전 속도 변화"
    DrivingPattern.RAPID_START -> "출발 직후 속도 증가"
    DrivingPattern.REPEATED_SPEED_CHANGE -> "반복 가감속"
    DrivingPattern.FAST_TURN -> "빠른 회전"
    DrivingPattern.REPEATED_SWAY -> "반복 좌우 움직임"
}

private fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
