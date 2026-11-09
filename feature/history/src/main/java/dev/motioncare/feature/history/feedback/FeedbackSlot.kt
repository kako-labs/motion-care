package dev.motioncare.feature.history.feedback

import dev.motioncare.feature.history.presentation.BFeedbackPanel
import dev.motioncare.feature.history.ui.MotionSelected
import dev.motioncare.feature.history.presentation.BReason
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    // A child rarely answers on the head unit; the guardian does unless the record says otherwise.
    val targetKind = passengers.firstOrNull { it.id == target }?.kind
    val current = target?.let { drafts[it] } ?: RatingDraft(respondent = if (targetKind == PersonKind.CHILD) FeedbackRespondent.GUARDIAN else FeedbackRespondent.SELF)
    MotionCareBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val trip = state.insight?.trip
            val subtitle = trip?.let { tripTime(it.startedAtEpochMs) + " · ${state.insight?.durationMs?.div(60_000)}분 · ${"%.1f".format((state.insight?.distanceMeters ?: 0.0) / 1000)}km" }
            ScreenTitle("이동 평가", subtitle, onBack = onDone)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (passengers.isEmpty()) GlassPanel(Modifier.fillMaxWidth()) {
                Text("평가할 동승자가 없어요.", fontSize = 32.sp, fontWeight = FontWeight.Medium)
                PrimaryAction("완료", onDone, Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    passengers.forEach { person ->
                        val chosen = target == person.id
                        Surface(onClick = { target = person.id }, shape = RoundedCornerShape(22.dp),
                            color = if (chosen) MotionSelected else MotionPanel, border = BorderStroke(1.dp, if (chosen) MotionCyan.copy(.5f) else MotionLine)) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PersonAvatar(person, Modifier.size(64.dp))
                                Text(person.displayName, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                if (drafts[person.id]?.handled == true) Text("✓", fontSize = 24.sp, color = MotionCyan)
                            }
                        }
                    }
                }
                val person = passengers.firstOrNull { it.id == target }
                if (person != null) GlassPanel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeedbackRespondent.entries.forEach { respondent ->
                            ChoiceTile("", if (respondent == FeedbackRespondent.SELF) "${person.displayName} 직접" else "보호자가 대신",
                                current.respondent == respondent, Modifier.weight(1f), compact = true, face = {
                                    if (respondent == FeedbackRespondent.SELF) PersonAvatar(person, Modifier.size(48.dp))
                                }) {
                                drafts[person.id] = current.copy(respondent = respondent, handled = false)
                            }
                        }
                    }
                    BFeedbackPanel(
                        name = person.displayName, rating = current.rating.takeIf { it in 1..5 },
                        reasons = FeedbackReason.entries.map { BReason(it.name, it.label()) },
                        selectedReasons = current.reasons.map { it.name }.toSet(), saving = state.savingFeedback,
                        onRating = { drafts[person.id] = current.copy(rating = it, handled = false) },
                        onReason = { drafts[person.id] = current.copy(reasons = toggleReason(current.reasons, FeedbackReason.valueOf(it)), handled = false) },
                        onSave = {
                            model.saveRating(person.id, current.rating, current.reasons, current.respondent) {
                                drafts[person.id] = current.copy(handled = true)
                                moveNext(passengerIds, person.id, drafts, { target = it }, onDone)
                            }
                        },
                        onSkip = { drafts[person.id] = current.copy(handled = true); moveNext(passengerIds, person.id, drafts, { target = it }, onDone) },
                    )
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
@Composable private fun ChoiceTile(icon: String, label: String, selected: Boolean, modifier: Modifier, compact: Boolean = false,
    face: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = if (selected) MotionSelected else MotionPanel,
        border = BorderStroke(1.dp, if (selected) MotionCyan.copy(.5f) else MotionLine), modifier = modifier.height(if (compact) 72.dp else 104.dp)) {
        if (compact) Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            face?.invoke()
            Text(label, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = if (selected) MotionText else MotionSubtle)
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            Text(icon, fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, color = if (selected) MotionCyan else MotionMuted)
            Text(label, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = if (selected) MotionText else MotionSubtle)
        }
    }
}

private fun FeedbackReason.icon() = when (this) {
    FeedbackReason.START_STOP -> "⇥"; FeedbackReason.REPEATED_SPEED_CHANGE -> "∿"; FeedbackReason.CURVE -> "↪"
    FeedbackReason.CABIN_ENVIRONMENT -> "◌"; FeedbackReason.UNKNOWN -> "?"
}
private fun FeedbackReason.label() = when (this) { FeedbackReason.START_STOP -> "출발·정차"; FeedbackReason.REPEATED_SPEED_CHANGE -> "반복 가감속"; FeedbackReason.CURVE -> "커브"; FeedbackReason.CABIN_ENVIRONMENT -> "차 안 환경"; FeedbackReason.UNKNOWN -> "잘 모르겠어요" }
