package dev.motioncare.feature.history.feedback

import androidx.compose.runtime.Composable
import dev.motioncare.core.ui.FeatureSlot

@Composable
fun FeedbackSlot(tripId: String?, onBack: () -> Unit) = FeatureSlot(
    "완료 평가", "연결된 주행: ${tripId ?: "없음"}. 평점·건너뛰기를 붙일 자리입니다.", onBack)
