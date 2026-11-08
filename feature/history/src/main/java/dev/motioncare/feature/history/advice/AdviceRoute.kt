package dev.motioncare.feature.history.advice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.AdviceViewModel
import dev.motioncare.feature.history.insights.patternLabel
import dev.motioncare.feature.history.presentation.*
import dev.motioncare.feature.history.ui.*

@Composable
internal fun AdviceRoute(driverId: PersonId?, trips: TripRepository, profiles: ProfileRepository, onBack: () -> Unit, onTrip: (TripId, Int) -> Unit = { _, _ -> }) {
    val model: AdviceViewModel = viewModel(key = "advice-${driverId?.value ?: "none"}", factory = viewModelFactory {
        initializer { AdviceViewModel(driverId, trips, profiles) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    val recent = state.driverTrips.sortedByDescending { it.trip.startedAtEpochMs }.take(3)
    val latest = recent.firstOrNull()
    val pattern = state.advice.pattern
    MotionCareBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            ScreenTitle("왜 이 조언인가요?", onBack = onBack)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BActionRibbon("오늘의 제안", state.advice.headline, enabled = false, onClick = {})
            if (latest == null || pattern == null) GlassPanel(Modifier.fillMaxWidth()) {
                Text("분석할 운전 기록이 더 필요해요.", fontSize = 26.sp)
            } else {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(patternLabel(pattern), fontSize = 25.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        MetricTile("${latest.count(pattern)}회", "최근 운행", Modifier.weight(1f))
                        MetricTile(latest.rate(pattern)?.let { "%.1f".format(it) } ?: "—", "이번 · 회/10분", Modifier.weight(1f))
                        val prior = recent.drop(1).mapNotNull { it.rate(pattern) }
                        MetricTile(if (prior.isEmpty()) "—" else "%.1f".format(prior.average()), "직전 ${prior.size}회 평균", Modifier.weight(1f))
                    }
                    Text("분석 ${latest.analyzedDurationMs / 60_000}분 · 최근 ${recent.size}회에서 가장 자주 관찰된 항목", color = MotionSubtle)
                }
                if (pattern == DrivingPattern.HARD_STOP) BSpeedTrace(latest.snapshot, onOpenDetail = { onTrip(latest.trip.id, it) })
                else BActionRibbon("최근 운행", "운행 기록 보기") { onTrip(latest.trip.id, 0) }
            }
            GlassPanel(Modifier.fillMaxWidth()) {
                Text("다음 이동에서", fontSize = 24.sp)
                state.advice.tips.forEachIndexed { index, tip -> Text("${index + 1}  $tip", fontSize = 20.sp) }
                var expanded by remember { mutableStateOf(false) }
                TextButton({ expanded = !expanded }, Modifier.heightIn(min = 56.dp)) { Text("데이터 기준 ${if (expanded) "⌃" else "⌄"}") }
                if (expanded) Text("완료되고 분석 가능한 운행의 10분당 빈도를 비교합니다. 최근 최대 3회의 평균 빈도가 가장 높은 항목을 골라요. 동승자의 응답은 참고 기록이며 선정 점수에 포함하지 않아요. 그래프는 저장된 속도 표본과 관찰 시각을 표시하며 기록 공백은 연결하지 않습니다.", color = MotionSubtle)
            }
        }
    }
}
