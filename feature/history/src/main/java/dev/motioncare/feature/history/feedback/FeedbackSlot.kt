package dev.motioncare.feature.history.feedback

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
import dev.motioncare.feature.history.TripExperienceViewModel
import dev.motioncare.feature.history.ui.*
import java.text.DateFormat
import java.util.Date

private data class RatingDraft(
    val rating: Int = 0,
    val reasons: Set<FeedbackReason> = emptySet(),
    val respondent: FeedbackRespondent = FeedbackRespondent.SELF,
    val handled: Boolean = false,
)

@Composable
internal fun FeedbackRoute(
    tripId: TripId,
    trips: TripRepository,
    profiles: ProfileRepository,
    onDone: () -> Unit,
) {
    val model: TripExperienceViewModel = viewModel(key = "feedback-${tripId.value}", factory = viewModelFactory {
        initializer { TripExperienceViewModel(tripId, trips, profiles) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    val passengerIds = state.participants?.passengerIds.orEmpty().toList()
    val passengers = passengerIds.mapNotNull { id -> state.people.firstOrNull { it.id == id } }
    var target by remember(passengerIds) { mutableStateOf(passengerIds.firstOrNull()) }
    val drafts = remember { mutableStateMapOf<PersonId, RatingDraft>() }
    LaunchedEffect(state.feedback) {
        state.feedback.forEach { saved ->
            val answer = saved.answer as? FeedbackAnswer.Rating ?: return@forEach
            drafts[saved.targetPersonId] = RatingDraft(answer.value, saved.reasons, saved.respondent, true)
        }
    }
    val current = target?.let { drafts[it] } ?: RatingDraft()
    MotionCareBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val trip = state.insight?.trip
            val subtitle = trip?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.startedAtEpochMs)) + " · ${state.insight?.durationMs?.div(60_000)}분 · ${"%.1f".format((state.insight?.distanceMeters ?: 0.0) / 1000)}km" }
            ScreenTitle("이동 평가", subtitle, onBack = onDone)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (passengers.isEmpty()) GlassPanel(Modifier.fillMaxWidth()) {
                Text("평가할 동승자가 없어요.", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                PrimaryAction("완료", onDone, Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    passengers.forEach { person ->
                        FilterChip(target == person.id, { target = person.id }, {
                            Row(verticalAlignment = Alignment.CenterVertically) { PersonAvatar(person, Modifier.size(42.dp)); Spacer(Modifier.width(8.dp)); Text(person.displayName + if (drafts[person.id]?.handled == true) "  ✓" else "") }
                        }, Modifier.heightIn(min = 64.dp))
                    }
                }
                val person = passengers.firstOrNull { it.id == target }
                if (person != null) GlassPanel(Modifier.fillMaxWidth()) {
                    Text("${person.displayName}님의 이동", color = MotionSubtle, fontWeight = FontWeight.Bold)
                    Text("오늘 이동은 편안했나요?", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { score -> TextButton(onClick = { drafts[person.id] = current.copy(rating = score, handled = false) }, modifier = Modifier.sizeIn(minWidth = 72.dp, minHeight = 76.dp)) {
                            Text(if (current.rating >= score) "★" else "☆", fontSize = 46.sp, color = if (current.rating >= score) MotionCyan else MotionMuted)
                        } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("많이 불편", color = MotionSubtle); Text("아주 편안", color = MotionSubtle) }
                    Text(ratingLabel(current.rating), Modifier.fillMaxWidth(), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MotionCyan)
                    Text("불편했던 점  ·  선택", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedbackReason.entries.forEach { reason ->
                            FilterChip(reason in current.reasons, { drafts[person.id] = current.copy(reasons = toggleReason(current.reasons, reason), handled = false) }, { Text(reason.label()) }, Modifier.heightIn(min = 56.dp))
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        FeedbackRespondent.entries.forEachIndexed { index, respondent -> SegmentedButton(current.respondent == respondent,
                            { drafts[person.id] = current.copy(respondent = respondent, handled = false) }, SegmentedButtonDefaults.itemShape(index, FeedbackRespondent.entries.size)) {
                            Text(if (respondent == FeedbackRespondent.SELF) "직접" else "보호자")
                        } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryAction("건너뛰기", { drafts[person.id] = current.copy(handled = true); moveNext(passengerIds, person.id, drafts, { target = it }, onDone) }, Modifier.weight(1f))
                        PrimaryAction(if (state.savingFeedback) "저장 중" else "저장", {
                            model.saveRating(person.id, current.rating, current.reasons, current.respondent) {
                                drafts[person.id] = current.copy(handled = true)
                                moveNext(passengerIds, person.id, drafts, { target = it }, onDone)
                            }
                        }, Modifier.weight(2f), enabled = current.rating in 1..5 && !state.savingFeedback)
                    }
                }
            }
        }
    }
}

private fun moveNext(ids: List<PersonId>, current: PersonId, drafts: Map<PersonId, RatingDraft>, select: (PersonId) -> Unit, done: () -> Unit) {
    ids.firstOrNull { it != current && drafts[it]?.handled != true }?.let(select) ?: done()
}
private fun toggleReason(current: Set<FeedbackReason>, reason: FeedbackReason): Set<FeedbackReason> = when {
    reason == FeedbackReason.UNKNOWN -> if (reason in current) emptySet() else setOf(reason)
    reason in current -> current - reason
    else -> (current - FeedbackReason.UNKNOWN) + reason
}
private fun ratingLabel(value: Int) = when (value) { 1 -> "많이 불편했어요"; 2 -> "불편했어요"; 3 -> "보통이었어요"; 4 -> "편안했어요"; 5 -> "아주 편안했어요"; else -> " " }
private fun FeedbackReason.label() = when (this) { FeedbackReason.START_STOP -> "출발·정차"; FeedbackReason.REPEATED_SPEED_CHANGE -> "반복 가감속"; FeedbackReason.CURVE -> "커브"; FeedbackReason.CABIN_ENVIRONMENT -> "차 안 환경"; FeedbackReason.UNKNOWN -> "잘 모르겠어요" }