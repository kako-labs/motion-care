package dev.motioncare.feature.driving.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.motioncare.core.model.TripRecord
import dev.motioncare.core.ui.MotionPrimaryButton
import dev.motioncare.core.ui.MotionSecondaryButton

/** The trip controls while a trip is open. Sizes come from the caller's typography; the home card scales them. */
@Composable
fun ActiveContent(
    trip: TripRecord,
    busy: Boolean,
    vehicleConnected: Boolean,
    onCapture: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (vehicleConnected) {
            Text("운행 기록 중", style = MaterialTheme.typography.headlineMedium)
            Text("차량 속도를 기록하고 있습니다. 내비를 띄워도 기록은 계속됩니다.")
            MotionPrimaryButton("운행 기록 종료", onFinish, enabled = !busy)
        } else {
            Text("샘플 주행 기록 중", style = MaterialTheme.typography.headlineMedium)
            Text("원본 버전: ${trip.revision} · 실제 차량은 연결되지 않았습니다.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MotionPrimaryButton("샘플 신호 추가", onCapture, enabled = !busy)
                MotionSecondaryButton("샘플 주행 종료", onFinish, enabled = !busy)
            }
        }
    }
}
