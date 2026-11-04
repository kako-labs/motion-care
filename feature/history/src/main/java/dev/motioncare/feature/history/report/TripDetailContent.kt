package dev.motioncare.feature.history.report

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.motioncare.core.model.DataOrigin
import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.Observation
import dev.motioncare.core.model.TripSnapshot
import dev.motioncare.core.ui.ScaffoldSection

private const val MAX_SAMPLE_ROWS = 20

internal fun observationSummary(observations: List<Observation>): String {
    if (observations.isEmpty()) return "관찰된 패턴 없음"
    val counts = observations.groupingBy { it.pattern }.eachCount()
    val parts = DrivingPattern.entries.mapNotNull { pattern ->
        counts[pattern]?.let { count -> "${label(pattern)} ${count}회" }
    }
    return "관찰된 패턴: " + parts.joinToString(" · ")
}

private fun label(pattern: DrivingPattern) = when (pattern) {
    DrivingPattern.RAPID_START -> "빠른 출발"
    DrivingPattern.HARD_STOP -> "정차 직전 큰 감속"
    DrivingPattern.REPEATED_SPEED_CHANGE -> "반복 가감속"
    DrivingPattern.FAST_TURN -> "빠른 회전"
    DrivingPattern.REPEATED_SWAY -> "반복 좌우 움직임"
}

/** Left and right motion is estimated, and a missing signal must not read as "nothing happened". */
internal fun steeringNote(snapshot: TripSnapshot): String? = when {
    snapshot.trip.origin != DataOrigin.VEHICLE -> null
    snapshot.steering.isEmpty() -> "조향 정보가 없어 좌우 움직임은 판정하지 않았습니다."
    else -> "좌우 움직임은 조향각과 속도로 추정한 값입니다."
}

@Composable
fun TripDetailContent(snapshot: TripSnapshot?, loading: Boolean, onBack: () -> Unit, onFeedback: () -> Unit) {
    ScaffoldSection("주행 상세 · 원본 연결 확인") {
        when {
            loading -> Text("기록 확인 중")
            snapshot == null -> Text("기록이 없습니다.")
            else -> {
                Text("출처: ${snapshot.trip.origin.name} · ${snapshot.trip.status.name}")
                Text("샘플 ${snapshot.samples.size}개 · 버전 ${snapshot.trip.revision}")
                Text(observationSummary(snapshot.observations))
                steeringNote(snapshot)?.let { Text(it) }
                // Real trips hold thousands of samples; a text row for each would stall the screen.
                snapshot.samples.takeLast(MAX_SAMPLE_ROWS).forEach { sample ->
                    Text("${sample.elapsedMs} ms · ${sample.metersPerSecond ?: "정보 없음"} m/s · ${sample.quality}")
                }
                Text("분석·비교·AI 요약은 기능 구현 단계에서 연결합니다.")
                TextButton(onClick = onFeedback) { Text("평가 연결 자리") }
            }
        }
        Button(onClick = onBack) { Text("메인으로") }
    }
}
