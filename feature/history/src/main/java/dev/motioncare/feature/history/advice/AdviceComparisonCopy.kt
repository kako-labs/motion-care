package dev.motioncare.feature.history.advice

import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.feature.history.insights.PatternTrend
import dev.motioncare.feature.history.insights.TripInsight
import dev.motioncare.feature.history.insights.patternLabel
import java.util.Locale

internal data class AdviceComparisonCopy(
    val title: String,
    val comparison: String,
    val basis: String,
    val observation: String,
    val calculation: String,
)

/**
 * Words the comparison the home advice and the profile make (docs/history-analysis.md): the latest analysed trips
 * against as many before them. The latest trip only shows how one trip's rate is worked out.
 */
internal fun adviceComparisonCopy(
    latest: TripInsight,
    pattern: DrivingPattern,
    trend: PatternTrend,
): AdviceComparisonCopy {
    val count = latest.count(pattern)
    val rate = latest.rate(pattern)
    val recent = trend.recent?.let { "최근 ${trend.recentRates.size}회 평균 ${decimal(it)}회/10분" } ?: "분석할 운행 부족"
    val previous = trend.previous?.let { "이전 ${trend.previousRates.size}회 평균 ${decimal(it)}회/10분" } ?: "이전 운행 없음"
    val perTrip = listOf("이전" to trend.previousRates, "최근" to trend.recentRates).filter { it.second.isNotEmpty() }
        .joinToString(", ") { (name, rates) -> "$name ${rates.size}회 ${rates.joinToString(" · ") { decimal(it) }}" }
    val minutes = String.format(Locale.KOREAN, "%.2f", latest.analyzedDurationMs / 60_000.0)
        .trimEnd('0').trimEnd('.')
    return AdviceComparisonCopy(
        title = if (pattern == DrivingPattern.HARD_STOP) "정차 직전 큰 감속" else patternLabel(pattern),
        comparison = "$recent · $previous",
        basis = if (trend.previousRates.isEmpty()) "비교할 같은 운전자의 이전 완료 운행이 없어요."
            else "같은 운전자의 완료 운행 중 분석 가능한 최근 ${trend.recentRates.size}회와 그 앞 ${trend.previousRates.size}회의 평균을 비교해요.",
        observation = "이번 운행 ${duration(latest.durationMs)} 동안 ${count}회 관찰 · 유효 분석 ${minutes}분",
        calculation = if (rate == null) "분석할 기록이 부족해 이번 운행의 빈도는 계산하지 않았어요."
            else "${count}회 ÷ ${minutes}분 × 10 ≈ ${decimal(rate)}회/10분. 운행마다 이렇게 구한 빈도를 평균해요(오래된 순, 회/10분: $perTrip). 계산에는 반올림 전 유효 분석 시간을 사용하고, 표시만 소수 첫째 자리로 반올림해요. 기록 공백은 제외해요.",
    )
}

private fun decimal(value: Double) = String.format(Locale.KOREAN, "%.1f", value)

private fun duration(milliseconds: Long): String {
    val seconds = milliseconds / 1_000
    return when {
        seconds % 60 == 0L -> "${seconds / 60}분"
        seconds >= 60 -> "${seconds / 60}분 ${seconds % 60}초"
        else -> "${seconds}초"
    }
}
