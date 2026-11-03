package dev.motioncare.feature.history.report

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.motioncare.core.ui.ScaffoldSection

@Composable
fun FinishedContent(onDetail: () -> Unit, onFeedback: () -> Unit, onDone: () -> Unit) {
    ScaffoldSection("주행 저장 완료") {
        Text("저장된 원본을 상세에서 확인할 수 있습니다.")
        Button(onClick = onDetail) { Text("주행 상세") }
        TextButton(onClick = onFeedback) { Text("평가 연결 자리") }
        TextButton(onClick = onDone) { Text("완료") }
    }
}
