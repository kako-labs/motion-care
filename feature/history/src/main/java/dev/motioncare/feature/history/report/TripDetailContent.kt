package dev.motioncare.feature.history.report

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.motioncare.core.model.TripSnapshot
import dev.motioncare.core.ui.ScaffoldSection

@Composable
fun TripDetailContent(snapshot: TripSnapshot?, loading: Boolean, onBack: () -> Unit, onFeedback: () -> Unit) {
    ScaffoldSection("주행 상세 · 원본 연결 확인") {
        when {
            loading -> Text("기록 확인 중")
            snapshot == null -> Text("기록이 없습니다.")
            else -> {
                Text("출처: ${snapshot.trip.origin.name} · ${snapshot.trip.status.name}")
                Text("샘플 ${snapshot.samples.size}개 · 버전 ${snapshot.trip.revision}")
                snapshot.samples.forEach { sample ->
                    Text("${sample.elapsedMs} ms · ${sample.metersPerSecond ?: "정보 없음"} m/s · ${sample.quality}")
                }
                Text("분석·비교·AI 요약은 기능 구현 단계에서 연결합니다.")
                TextButton(onClick = onFeedback) { Text("평가 연결 자리") }
            }
        }
        Button(onClick = onBack) { Text("메인으로") }
    }
}
