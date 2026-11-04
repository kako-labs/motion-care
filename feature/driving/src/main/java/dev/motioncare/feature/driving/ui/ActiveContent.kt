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
        ScaffoldSection("자동 운행 기록 중") {
            Text("모션케어는 내비게이션을 방해하지 않고 백그라운드에서 차량 속도를 기록해요.")
            Text("정차 상태가 이어지면 기록도 자동으로 마무리돼요.")
        }
    } else {
        ScaffoldSection("샘플 주행 기록 중") {
            Text("원본 버전: ${trip.revision} · 실제 차량은 연결되지 않았습니다.")
            Button(onClick = onCapture, enabled = !busy) { Text("샘플 신호 추가") }
            OutlinedButton(onClick = onFinish, enabled = !busy) { Text("샘플 주행 종료") }
        }
    }
}