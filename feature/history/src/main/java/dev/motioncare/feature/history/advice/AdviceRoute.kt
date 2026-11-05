package dev.motioncare.feature.history.advice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import dev.motioncare.feature.history.ui.*

@Composable
internal fun AdviceRoute(driverId: PersonId?, trips: TripRepository, profiles: ProfileRepository, onBack: () -> Unit) {
    val key = driverId?.value ?: "none"
    val model: AdviceViewModel = viewModel(key = "advice-$key", factory = viewModelFactory {
        initializer { AdviceViewModel(driverId, trips, profiles) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    MotionCareBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ScreenTitle("왜 이 조언인가요?", onBack = onBack)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            GlassPanel(Modifier.fillMaxWidth()) {
                AssistChip(onClick = {}, label = { Text("✦ 오늘 바꿔볼 행동") })
                Text(state.advice.headline, fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 900.dp
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Evidence(state.advice, Modifier.weight(1.15f)); PassengerAndTips(state.passengers, state.advice.tips, Modifier.weight(.85f))
                } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { Evidence(state.advice); PassengerAndTips(state.passengers, state.advice.tips) }
            }
        }
    }
}

@Composable private fun Evidence(advice: dev.motioncare.feature.history.insights.AdviceInsight, modifier: Modifier = Modifier) {
    GlassPanel(modifier) {
        Text("관찰한 기록", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (advice.pattern == null || advice.latestCount == null) Text("기록이 더 필요해요.", color = MotionSubtle, fontSize = 25.sp)
        else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${advice.latestCount}회", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = MotionCyan)
                Column { Text(patternLabel(advice.pattern), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("최근 운행 · 분석 ${advice.latestAnalyzedMinutes ?: 0}분", color = MotionSubtle) }
            }
            HorizontalDivider(color = MotionLine)
            Text("최근 ${advice.recentRates.size}회 · 10분당", color = MotionSubtle)
            TrendBars(advice.recentRates)
            if (advice.previousAverage != null && advice.recentAverage != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("이전 3회 ${"%.1f".format(advice.previousAverage)}", color = MotionMuted)
                    Text("최근 3회 ${"%.1f".format(advice.recentAverage)}", color = MotionCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun PassengerAndTips(passengers: List<PersonProfile>, tips: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("함께 타는 사람", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            if (passengers.isEmpty()) Text("동승자 없음", color = MotionSubtle)
            passengers.forEach { person -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(person, Modifier.size(52.dp)); Spacer(Modifier.width(10.dp)); Column { Text(person.displayName, fontWeight = FontWeight.Bold)
                    Text(person.motionTriggers.joinToString(" · ") { it.short() }.ifBlank { "설정된 불편 상황 없음" }, color = MotionSubtle, fontSize = 13.sp) }
            } }
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("이렇게 해 보세요", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            tips.forEachIndexed { index, tip -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MotionBlue.copy(.28f), shape = androidx.compose.foundation.shape.CircleShape) { Text("${index + 1}", Modifier.padding(12.dp), color = MotionCyan, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp)); Text(tip, fontSize = 18.sp)
            } }
            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("데이터 기준 ${if (expanded) "⌃" else "⌄"}") }
            if (expanded) Text("차량 속도 변화로 계산한 근사값이에요. 멀미를 측정한 값은 아니에요.", color = MotionSubtle)
        }
    }
}
private fun MotionTrigger.short() = when (this) { MotionTrigger.START_STOP -> "출발·정차"; MotionTrigger.REPEATED_SPEED_CHANGE -> "반복 가감속"; MotionTrigger.CURVE -> "커브"; MotionTrigger.CABIN_ENVIRONMENT -> "차 안 환경" }