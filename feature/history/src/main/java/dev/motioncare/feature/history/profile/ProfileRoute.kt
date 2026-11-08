package dev.motioncare.feature.history.profile

import dev.motioncare.feature.history.presentation.*
import kotlin.math.roundToInt
import dev.motioncare.feature.history.insights.AdviceState
import dev.motioncare.feature.history.insights.patternIcon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import dev.motioncare.feature.history.PersonDetailViewModel
import dev.motioncare.feature.history.insights.*
import dev.motioncare.feature.history.ui.*

enum class ProfileTab { DRIVER, PASSENGER }

@Composable
internal fun PersonDetailRoute(
    id: PersonId,
    trips: TripRepository,
    profiles: ProfileRepository,
    onBack: () -> Unit,
    onAdvice: (PersonId) -> Unit,
) {
    val model: PersonDetailViewModel = viewModel(key = "person-${id.value}", factory = viewModelFactory {
        initializer { PersonDetailViewModel(id, trips, profiles) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    PersonDetailContent(state, onBack, onAdvice, model::update)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonDetailContent(
    state: dev.motioncare.feature.history.PersonDetailUiState,
    onBack: () -> Unit,
    onAdvice: (PersonId) -> Unit,
    onUpdate: (PersonProfile) -> Unit,
) {
    val person = state.person
    var tab by remember(person?.id) { mutableStateOf(if (person?.kind == PersonKind.CHILD) ProfileTab.PASSENGER else ProfileTab.DRIVER) }
    var editing by remember { mutableStateOf(false) }
    MotionCareBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (person == null) {
                ScreenTitle("프로필", onBack = onBack); Text(if (state.loading) "프로필 확인 중" else state.error ?: "프로필이 없습니다.")
                return@Column
            }
            val driven = state.driverTrips.size
            val ridden = state.passengerFeedback.size
            val summary = if (person.kind == PersonKind.CHILD) "동승자 · 어린이 · 평가 $ridden" else "성인 · 운전 $driven · 평가 $ridden"
            ScreenTitle(person.displayName, summary, onBack,
                leading = { PersonAvatar(person, Modifier.size(84.dp)) }) {
                SecondaryAction("수정", { editing = true })
            }
            // A child only rides, so there is nothing to switch between.
            if (person.kind == PersonKind.ADULT) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabTile("◉", "운전할 때", tab == ProfileTab.DRIVER) { tab = ProfileTab.DRIVER }
                TabTile("♙", "동승할 때", tab == ProfileTab.PASSENGER) { tab = ProfileTab.PASSENGER }
            }
            if (tab == ProfileTab.DRIVER) DriverProfile(person, state.driverTrips, state.advice, onAdvice)
            else BPassengerProfilePanel(
                name = person.displayName, role = if (person.kind == PersonKind.CHILD) "동승자 · 어린이" else "동승자",
                preferences = listOf(BMetric("민감도", person.motionSensitivity.label()), BMetric("휴식 간격", person.preferredRestMinutes?.let { "${it}분" }), BMetric("선호 온도", person.preferredTemperatureCelsius?.let { "${it}℃" })),
                moments = person.motionTriggers.map { BMoment(it.label(), "등록한 이동 선호") } + state.passengerFeedback.sortedByDescending { it.createdAtEpochMs }.take(5).map { BMoment(tripDate(it.createdAtEpochMs), (it.answer as? FeedbackAnswer.Rating)?.let { rating -> "편안함 ${rating.value}/5" } ?: "미응답") },
                portrait = { PersonAvatar(person, Modifier.size(100.dp)) }, onEdit = { editing = true }, showIdentity = false)
        }
    }
    if (editing && person != null) PreferenceDialog(person, { editing = false }, { onUpdate(it); editing = false })
}

@Composable private fun DriverProfile(person: PersonProfile, trips: List<TripInsight>, advice: AdviceInsight, onAdvice: (PersonId) -> Unit) {
    var pattern by remember { mutableStateOf(DrivingPattern.HARD_STOP) }
    val chronological = trips.sortedBy { it.trip.startedAtEpochMs }
    val values = chronological.mapNotNull { it.rate(pattern) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 960.dp
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TrendPanel(pattern, { pattern = it }, chronological, Modifier.weight(1f))
            NextAdvice(advice, { onAdvice(person.id) }, Modifier.weight(.72f))
        } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            TrendPanel(pattern, { pattern = it }, chronological); NextAdvice(advice, { onAdvice(person.id) })
        }
    }
    if (values.isNotEmpty()) GlassPanel(Modifier.fillMaxWidth()) {
        Text("전체 운전 기록", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${patternLabel(pattern)} · 10분당", color = MotionSubtle)
        LineTrend(values)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("처음 ${"%.1f".format(values.first())}회", color = MotionMuted, fontSize = 18.sp)
            Text("최근 ${"%.1f".format(values.last())}회", color = MotionCyan, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Text("최근 운행", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (chronological.isEmpty()) Text("아직 운전 기록이 없어요.", color = MotionSubtle)
        chronological.takeLast(5).reversed().forEach { trip ->
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tripTime(trip.trip.startedAtEpochMs), Modifier.weight(1f))
                Text("${trip.durationMs / 60_000}분 · ${"%.1f".format(trip.distanceMeters / 1000)}km", color = MotionSubtle)
            }
            HorizontalDivider(color = MotionLine)
        }
    }
}

@Composable private fun TrendPanel(pattern: DrivingPattern, onPattern: (DrivingPattern) -> Unit, trips: List<TripInsight>, modifier: Modifier = Modifier) {
    GlassPanel(modifier) {
        Text("최근 운전 경향", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val selected = trips.mapNotNull { it.rate(pattern) }
        val change = if (selected.size >= 2 && selected.first() > 0) ((selected.last() - selected.first()) / selected.first() * 100).roundToInt() else null
        Text(when {
            change == null || trips.size < 4 -> "비교하려면 기록이 더 필요해요."
            change < 0 -> "${patternLabel(pattern)} ↓ ${-change}%"
            change > 0 -> "${patternLabel(pattern)} ↑ $change%"
            else -> "${patternLabel(pattern)} 변화 없음"
        }, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = if (change != null && change < 0 && trips.size >= 4) MotionCyan else MotionText)
        listOf(DrivingPattern.HARD_STOP, DrivingPattern.RAPID_START, DrivingPattern.REPEATED_SPEED_CHANGE).forEach { item ->
            val rates = trips.mapNotNull { it.rate(item) }
            OutlinedButton(onClick = { onPattern(item) }, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (pattern == item) MotionBlue.copy(.26f) else Color.Transparent)) {
                Text(patternIcon(item), fontSize = 26.sp, color = MotionCyan); Spacer(Modifier.width(14.dp))
                Text(patternLabel(item), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MotionText)
                Text(rates.lastOrNull()?.let { "${"%.1f".format(it)}회" } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MotionCyan)
            }
        }
    }
}

@Composable private fun TabTile(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = if (selected) MotionBlue.copy(.35f) else MotionPanel,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MotionCyan else MotionLine), modifier = Modifier.heightIn(min = 68.dp).widthIn(min = 220.dp)) {
        Row(Modifier.padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp, color = if (selected) MotionCyan else MotionMuted)
            Text(label, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = if (selected) MotionText else MotionSubtle)
        }
    }
}

@Composable private fun NextAdvice(advice: AdviceInsight, onAdvice: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier) {
        Text("다음 출발", color = MotionSubtle, fontWeight = FontWeight.Bold)
        Text(advice.headline, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
        if (advice.state == AdviceState.INSUFFICIENT) Text("1분 넘게 달린 기록부터 분석해요", color = MotionCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (advice.pattern != null) SecondaryAction("자세히  ›", onAdvice, Modifier.fillMaxWidth())
    }
}

@Composable private fun PassengerProfile(person: PersonProfile, feedback: List<TripFeedback>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 900.dp
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Preferences(person, Modifier.weight(1f)); FeedbackHistory(feedback, Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { Preferences(person); FeedbackHistory(feedback) }
    }
}

@Composable private fun Preferences(person: PersonProfile, modifier: Modifier = Modifier) {
    GlassPanel(modifier) {
        Text("편안한 이동을 위한 선호", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        ValueRow("멀미 민감도", person.motionSensitivity.label())
        ValueRow("불편한 상황", person.motionTriggers.joinToString(" · ") { it.label() }.ifBlank { "미설정" })
        ValueRow("휴식 간격", person.preferredRestMinutes?.let { "${it}분" } ?: "미설정")
        ValueRow("선호 온도", person.preferredTemperatureCelsius?.let { "${"%.1f".format(it)}℃" } ?: "미설정")
    }
}
@Composable private fun ValueRow(label: String, value: String) { Row(Modifier.fillMaxWidth().heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = MotionSubtle, modifier = Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Bold) } }

@Composable private fun FeedbackHistory(feedback: List<TripFeedback>, modifier: Modifier = Modifier) {
    GlassPanel(modifier) {
        Text("남긴 평가", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (feedback.isEmpty()) Text("아직 평가가 없어요.", color = MotionSubtle)
        val rated = feedback.mapNotNull { item -> (item.answer as? FeedbackAnswer.Rating)?.value?.let { item to it } }
        val oldestFirst = rated.sortedBy { it.first.createdAtEpochMs }.map { it.second }.takeLast(8)
        if (oldestFirst.size >= 2) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("★${oldestFirst.first()}", fontSize = 28.sp, color = MotionMuted, fontWeight = FontWeight.Bold)
                Text("→", fontSize = 26.sp, color = MotionMuted)
                Text("★${oldestFirst.last()}", fontSize = 40.sp, lineHeight = 44.sp, color = MotionCyan, fontWeight = FontWeight.ExtraBold)
                Text("최근 ${oldestFirst.size}번", fontSize = 17.sp, color = MotionSubtle)
            }
            TrendBars(oldestFirst.map { it.toDouble() }, Modifier.fillMaxWidth().height(110.dp))
        }
        rated.sortedByDescending { it.first.createdAtEpochMs }.take(6).forEach { (item, rating) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tripDate(item.createdAtEpochMs), color = MotionSubtle, fontSize = 18.sp)
                if (item.respondent == FeedbackRespondent.GUARDIAN) Text("  · 보호자", color = MotionMuted, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text("★".repeat(rating) + "☆".repeat(5 - rating), color = MotionCyan, fontSize = 24.sp, letterSpacing = 2.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PreferenceDialog(person: PersonProfile, onDismiss: () -> Unit, onSave: (PersonProfile) -> Unit) {
    var sensitivity by remember { mutableStateOf(person.motionSensitivity) }
    var triggers by remember { mutableStateOf(person.motionTriggers) }
    MotionCareTheme { AlertDialog(onDismissRequest = onDismiss, title = { Text("이동 선호 수정", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("멀미 민감도", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { MotionSensitivity.entries.filter { it != MotionSensitivity.UNSPECIFIED }.forEach { item -> FilterChip(sensitivity == item, { sensitivity = item }, { Text(item.label(), fontSize = 19.sp) }, Modifier.heightIn(min = 56.dp)) } }
        Text("불편한 상황", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionTrigger.entries.forEach { item -> FilterChip(item in triggers, { triggers = if (item in triggers) triggers - item else triggers + item }, { Text(item.label(), fontSize = 19.sp) }, Modifier.heightIn(min = 56.dp)) }
        }
    } }, confirmButton = { TextButton(onClick = { onSave(person.copy(motionSensitivity = sensitivity, motionTriggers = triggers)) }) { Text("저장", fontSize = 21.sp, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소", fontSize = 21.sp) } },
        modifier = Modifier.width(620.dp), properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false))
} }

private fun MotionSensitivity.label() = when (this) { MotionSensitivity.UNSPECIFIED -> "미설정"; MotionSensitivity.LOW -> "낮음"; MotionSensitivity.MEDIUM -> "보통"; MotionSensitivity.HIGH -> "높음" }
private fun MotionTrigger.label() = when (this) { MotionTrigger.START_STOP -> "출발·정차"; MotionTrigger.REPEATED_SPEED_CHANGE -> "반복 가감속"; MotionTrigger.CURVE -> "커브"; MotionTrigger.CABIN_ENVIRONMENT -> "차 안 환경" }
