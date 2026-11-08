package dev.motioncare.feature.history.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.motioncare.feature.history.TripExperienceViewModel
import dev.motioncare.feature.history.insights.*
import dev.motioncare.feature.history.ui.*

/** Home card the app shows right after a trip; the full summary lives on the detail screen. */
@Composable
fun FinishedContent(onDetail: () -> Unit, onFeedback: () -> Unit, onDone: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Text("운행을 마쳤어요", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryAction("이동 평가", onFeedback, Modifier.weight(1.4f))
            SecondaryAction("주행 상세", onDetail, Modifier.weight(1f))
            SecondaryAction("완료", onDone, Modifier.weight(1f))
        }
    }
}

@Composable
fun FinishedRoute(
    tripId: TripId,
    trips: TripRepository,
    profiles: ProfileRepository,
    onDetail: () -> Unit,
    onFeedback: () -> Unit,
    onDone: () -> Unit,
) {
    val model: TripExperienceViewModel = viewModel(key = "finished-${tripId.value}", factory = viewModelFactory {
        initializer { TripExperienceViewModel(tripId, trips, profiles) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    val insight = state.insight
    val primary = insight?.counts?.maxByOrNull { it.value }?.key ?: DrivingPattern.HARD_STOP
    val advice = adviceFor(state.participants?.driverId, state.recentDriverTrips)
    MotionCareBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ScreenTitle("운행을 마쳤어요", insight?.let { tripTime(it.trip.startedAtEpochMs) })
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (insight != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("${insight.durationMs / 60_000}분", "운행 시간", Modifier.weight(1f))
                    MetricTile("${"%.1f".format(insight.distanceMeters / 1000)}km", "거리", Modifier.weight(1f))
                    MetricTile("${insight.count(DrivingPattern.HARD_STOP)}회", "정차 직전 속도 변화", Modifier.weight(1f), true)
                    MetricTile("${insight.count(DrivingPattern.RAPID_START)}회", "출발 직후 속도 증가", Modifier.weight(1f))
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val wide = maxWidth >= 900.dp
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        SummaryTrend(primary, state.recentDriverTrips, Modifier.weight(1.05f))
                        NextAndRatings(advice, state, onFeedback, Modifier.weight(.95f))
                    } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { SummaryTrend(primary, state.recentDriverTrips); NextAndRatings(advice, state, onFeedback) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryAction("주행 상세", onDetail, Modifier.weight(1f))
                PrimaryAction("완료", onDone, Modifier.weight(1.5f))
            }
        }
    }
}

@Composable private fun SummaryTrend(pattern: DrivingPattern, trips: List<TripInsight>, modifier: Modifier = Modifier) {
    val values = trips.sortedBy { it.trip.startedAtEpochMs }.takeLast(6).mapNotNull { it.rate(pattern) }
    GlassPanel(modifier) {
        Text("최근 ${values.size}회 · ${patternLabel(pattern)} · 10분당", color = MotionSubtle, fontWeight = FontWeight.Bold)
        if (values.isEmpty()) Text("비교할 기록이 더 필요해요.", fontSize = 24.sp) else TrendBars(values)
    }
}

@Composable private fun NextAndRatings(
    advice: AdviceInsight,
    state: dev.motioncare.feature.history.TripExperienceUiState,
    onFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier) {
        Text("다음 출발", color = MotionSubtle, fontWeight = FontWeight.Bold)
        Text(advice.headline, fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
        val passengerIds = state.participants?.passengerIds.orEmpty()
        passengerIds.forEach { id ->
            val person = state.people.firstOrNull { it.id == id } ?: return@forEach
            val saved = state.feedback.firstOrNull { it.targetPersonId == id }
            OutlinedButton(onClick = onFeedback, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                PersonAvatar(person, Modifier.size(44.dp)); Spacer(Modifier.width(10.dp)); Text(person.displayName, Modifier.weight(1f))
                val score = (saved?.answer as? FeedbackAnswer.Rating)?.value
                Text(score?.let { "★".repeat(it) } ?: "평가하기", color = MotionCyan)
            }
        }
    }
}