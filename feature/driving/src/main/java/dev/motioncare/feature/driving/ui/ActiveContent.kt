package dev.motioncare.feature.driving.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.motioncare.core.model.TripRecord
import dev.motioncare.core.ui.ScaffoldSection

@Composable
fun ActiveContent(
    trip: TripRecord,
    busy: Boolean,
    vehicleConnected: Boolean,
    onCapture: () -> Unit,
    onFinish: () -> Unit,
) {
    if (vehicleConnected) {
        ScaffoldSection("운행 기록 중") {
            Text("차량 속도를 기록하고 있습니다. 주행 중에는 이 화면이 가려질 수 있습니다.")
            OutlinedButton(onClick = onFinish, enabled = !busy) { Text("운행 기록 종료") }
        }
    } else {
        ScaffoldSection("샘플 주행 기록 중") {
            Text("원본 버전: ${trip.revision} · 실제 차량은 연결되지 않았습니다.")
            Button(onClick = onCapture, enabled = !busy) { Text("샘플 신호 추가") }
            OutlinedButton(onClick = onFinish, enabled = !busy) { Text("샘플 주행 종료") }
        }
    }
}
