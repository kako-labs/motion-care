package dev.motioncare.feature.history.profile

import androidx.compose.runtime.Composable
import dev.motioncare.core.ui.FeatureSlot

@Composable
fun ProfileSlot(onBack: () -> Unit) = FeatureSlot(
    "프로필", "사람·차량 등록과 주행별 인물 연결을 이 영역에 붙입니다.", onBack)
