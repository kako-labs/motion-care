package dev.motioncare.feature.history.report

import dev.motioncare.feature.history.insights.adviceFor
import dev.motioncare.feature.history.insights.patternIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.core.model.*
import dev.motioncare.feature.history.TripExperienceUiState
import dev.motioncare.feature.history.insights.patternLabel
import dev.motioncare.feature.history.ui.*

internal fun observationSummary(observations: List<Observation>): String {
    if (observations.isEmpty()) return "관찰된 패턴 없음"
    val counts = observations.groupingBy { it.pattern }.eachCount()
    val parts = DrivingPattern.entries.mapNotNull { pattern ->
        counts[pattern]?.let { count -> "${legacyLabel(pattern)} ${count}회" }
    }
    return "관찰된 패턴: " + parts.joinToString(" · ")
}

private fun legacyLabel(pattern: DrivingPattern) = when (pattern) {
    DrivingPattern.RAPID_START -> "빠른 출발"
    DrivingPattern.HARD_STOP -> "정차 직전 큰 감속"
    DrivingPattern.REPEATED_SPEED_CHANGE -> "반복 가감속"
    DrivingPattern.FAST_TURN -> "빠른 회전"
    DrivingPattern.REPEATED_SWAY -> "반복 좌우 움직임"
}

/** Left and right motion is estimated, and a missing signal must not read as "nothing happened". */
internal fun steeringNote(snapshot: TripSnapshot): String? = when {
    snapshot.trip.origin != DataOrigin.VEHICLE -> null
    snapshot.steering.isEmpty() -> "조향 정보가 없어 좌우 움직임은 판정하지 않았습니다."
    else -> "좌우 움직임은 조향각과 속도로 추정한 값입니다."
}

/** A trip with no card, a trip with guidance off and a sample trip must read differently. */
internal fun guidanceSummary(snapshot: TripSnapshot): String? {
    if (snapshot.trip.origin != DataOrigin.VEHICLE) return null
    val shown = snapshot.guidance.count { it.outcome == GuidanceOutcome.SHOWN }
    return when {
        shown > 0 -> "주행 중 안내 ${shown}회"
        snapshot.guidance.any { it.outcome == GuidanceOutcome.SKIPPED_OFF } -> "주행 중 안내 꺼짐"
        else -> "주행 중 안내 없음"
    }
}

@Composable
internal fun TripDetailContent(
    state: TripExperienceUiState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onFeedback: () -> Unit,
) {
    MotionCareBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScreenTitle("운행 상세", "저장된 차량 속도 변화와 연결된 탑승 정보", onBack)
            when {
                state.loading -> Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> ErrorPanel(state.error)
                state.insight == null -> ErrorPanel("운행 기록을 찾을 수 없어요.")
                else -> DetailBody(state, onFeedback)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SecondaryAction("홈으로", onHome, Modifier.weight(1f))
                if (state.insight != null) PrimaryAction("동승자 평가", onFeedback, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun DetailBody(state: TripExperienceUiState, onFeedback: () -> Unit) {
    val insight = requireNotNull(state.insight)
    val trip = insight.trip
    val date = tripTime(trip.startedAtEpochMs)
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(date, color = MotionSubtle, fontSize = 18.sp)
                Text(if (trip.status == TripStatus.COMPLETED) "완료된 운행" else "중단된 운행", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            }
            AssistChip(onClick = {}, label = { Text(if (trip.origin == DataOrigin.VEHICLE) "차량 데이터" else "합성 데이터") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(formatDuration(insight.durationMs), "운행 시간", Modifier.weight(1f), true)
            MetricTile("%.1f km".format(insight.distanceMeters / 1_000.0), "거리 근사", Modifier.weight(1f))
            MetricTile("${insight.analyzedDurationMs / 60_000}분", "유효 분석", Modifier.weight(1f))
            MetricTile("${insight.snapshot.samples.size}", "속도 샘플", Modifier.weight(1f))
        }
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("관찰된 운전 패턴", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("속도 변화로 본 근사치 · 멀미 여부 아님", color = MotionMuted, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DrivingPattern.entries.forEach { pattern ->
                val recentAverage = state.recentDriverTrips
                    .filter { it.trip.id != trip.id }
                    .take(3)
                    .mapNotNull { it.rate(pattern) }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                PatternTile(pattern, insight.count(pattern), insight.rate(pattern), recentAverage, Modifier.weight(1f))
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 850.dp) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ParticipantsPanel(state, Modifier.weight(1f))
            FeedbackPanel(state, onFeedback, Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ParticipantsPanel(state)
            FeedbackPanel(state, onFeedback)
        }
    }

    // Same calculation as the departure screen, so the report and the next start never disagree.
    val next = adviceFor(state.participants?.driverId, state.recentDriverTrips)
    if (next.pattern != null) GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(patternIcon(next.pattern), fontSize = 44.sp, color = MotionCyan)
            Column(Modifier.weight(1f)) {
                Text("다음 출발", color = MotionSubtle, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(next.headline, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
            }
            next.recentAverage?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.1f회".format(it), fontSize = 30.sp, color = MotionCyan, fontWeight = FontWeight.ExtraBold)
                    Text("최근 10분당", fontSize = 15.sp, color = MotionMuted)
                }
            }
        }
    }

    Text("ⓘ  유효한 속도 구간만 계산 · 5초 넘는 공백은 거리에서 제외 · 1분 미만 기록은 10분당 빈도 없음",
        color = MotionMuted, fontSize = 15.sp)
}

@Composable private fun PatternTile(pattern: DrivingPattern, count: Int, rate: Double?, recentAverage: Double?, modifier: Modifier) {
    val seen = count > 0
    Column(modifier.heightIn(min = 196.dp)
        .background(if (seen) MotionBlue.copy(.28f) else MotionPanel, RoundedCornerShape(20.dp))
        .border(if (seen) 2.dp else 1.dp, if (seen) MotionCyan else MotionLine, RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(patternIcon(pattern), fontSize = 34.sp, color = if (seen) MotionCyan else MotionMuted)
        Text("$count", fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold, color = if (seen) MotionText else MotionMuted)
        Text(patternLabel(pattern), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotionSubtle, textAlign = TextAlign.Center)
        val trend = if (rate != null && recentAverage != null) when {
            rate < recentAverage -> "↓ 평소보다 적음"
            rate > recentAverage -> "↑ 평소보다 많음"
            else -> "＝ 평소와 같음"
        } else rate?.let { "%.1f / 10분".format(it) }
        trend?.let { Text(it, fontSize = 15.sp, color = if (it.startsWith("↓")) MotionCyan else MotionMuted) }
    }
}

@Composable private fun ParticipantsPanel(state: TripExperienceUiState, modifier: Modifier = Modifier) {
    val people = state.people.associateBy { it.id }
    GlassPanel(modifier) {
        Text("이 운행의 탑승자", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val seats = state.participants?.seatAssignments.orEmpty()
        if (seats.isEmpty()) Text("연결된 탑승 정보가 없어요.", color = MotionSubtle)
        SeatPosition.entries.forEach { seat ->
            val person = seats[seat]?.let(people::get) ?: return@forEach
            Row(Modifier.fillMaxWidth().heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(person, Modifier.size(50.dp)); Spacer(Modifier.width(12.dp))
                Text(person.displayName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(seat.label(), color = MotionSubtle)
            }
        }
    }
}

@Composable private fun FeedbackPanel(state: TripExperienceUiState, onFeedback: () -> Unit, modifier: Modifier = Modifier) {
    val passengerIds = state.participants?.passengerIds.orEmpty()
    val ratings = state.feedback.mapNotNull { feedback -> (feedback.answer as? FeedbackAnswer.Rating)?.value }
    GlassPanel(modifier) {
        Text("동승자 편안함 평가", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        when {
            passengerIds.isEmpty() -> Text("이 운행에는 등록된 동승자가 없어요.", color = MotionSubtle)
            ratings.isEmpty() -> Text("${passengerIds.size}명의 평가가 아직 없어요.", color = MotionSubtle)
            else -> {
                val people = state.people.associateBy { it.id }
                val byPerson = state.feedback.associate { it.targetPersonId to (it.answer as? FeedbackAnswer.Rating)?.value }
                passengerIds.forEach { id ->
                    val person = people[id] ?: return@forEach
                    val score = byPerson[id]
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(person, Modifier.size(44.dp))
                        Text(person.displayName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(score?.let { "★".repeat(it) + "☆".repeat(5 - it) } ?: "— 응답 없음", fontSize = if (score != null) 30.sp else 18.sp,
                            color = if (score != null) MotionCyan else MotionMuted, letterSpacing = 2.sp)
                    }
                }
            }
        }
        SecondaryAction(if (ratings.isEmpty()) "평가 남기기" else "평가 수정", onFeedback, Modifier.fillMaxWidth())
    }
}

@Composable private fun ErrorPanel(message: String) = GlassPanel(Modifier.fillMaxWidth()) {
    Text(message, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

private fun formatDuration(milliseconds: Long): String {
    val minutes = milliseconds / 60_000
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

private fun SeatPosition.label() = when (this) {
    SeatPosition.DRIVER -> "운전석"
    SeatPosition.FRONT_PASSENGER -> "앞좌석"
    SeatPosition.REAR_LEFT -> "뒷좌석 왼쪽"
    SeatPosition.REAR_CENTER -> "뒷좌석 가운데"
    SeatPosition.REAR_RIGHT -> "뒷좌석 오른쪽"
}
