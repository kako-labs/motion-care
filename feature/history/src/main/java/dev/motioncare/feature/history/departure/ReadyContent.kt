package dev.motioncare.feature.history.departure

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import dev.motioncare.core.ui.ScaffoldSection

@Composable
fun ReadyContent(busy: Boolean, onStart: () -> Unit, onProfiles: () -> Unit) {
    ScaffoldSection("출발 전") {
        Text("샘플 기록을 만들고 저장·조회 연결을 확인합니다.")
        Text("출발 조언·환경 정보는 구현할 기능 영역입니다.")
        Button(onClick = onStart, enabled = !busy) { Text("샘플 주행 시작") }
        TextButton(onClick = onProfiles, enabled = !busy) { Text("프로필 연결 자리") }
    }
}
