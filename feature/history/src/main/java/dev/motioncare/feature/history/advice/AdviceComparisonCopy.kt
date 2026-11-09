package dev.motioncare.feature.history.advice

import dev.motioncare.core.model.DrivingPattern
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

/** Formats the rates already selected by the screen; does not select or aggregate trips. */
internal fun adviceComparisonCopy(
    latest: TripInsight,
    pattern: DrivingPattern,
    previousRates: List<Double>,
): AdviceComparisonCopy {
    val count = latest.count(pattern)
    val rate = latest.rate(pattern)
    val current = rate?.let { "이번 운행 ${decimal(it)}회/10분" } ?: "이번 운행 분석 부족"
    val previous = if (previousRates.isEmpty()) "이전 운행 없음"
        else "이전 ${previousRates.size}회 평균 ${decimal(previousRates.average())}회/10분"
    val minutes = String.format(Locale.KOREAN, "%.2f", latest.analyzedDurationMs / 60_000.0)
        .trimEnd('0').trimEnd('.')
    return AdviceComparisonCopy(
        title = if (pattern == DrivingPattern.HARD_STOP) "정차 직전 큰 감속" else patternLabel(pattern),
        comparison = "$current · $previous",
        basis = if (previousRates.isEmpty()) "비교할 같은 운전자의 이전 완료 운행이 없어요."
            else "같은 운전자의 이번 운행보다 앞선 완료 운행 중 분석 가능한 ${previousRates.size}회와 비교해요.",
        observation = "${duration(latest.durationMs)} 동안 ${count}회 관찰 · 유효 분석 ${minutes}분",
        calculation = if (rate == null) "분석할 기록이 부족해 이번 운행의 빈도는 계산하지 않았어요."
            else "${count}회 ÷ ${minutes}분 × 10 ≈ ${decimal(rate)}회/10분. 계산에는 반올림 전 유효 분석 시간을 사용하고, 표시만 소수 첫째 자리로 반올림해요. 기록 공백은 제외해요.",
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
