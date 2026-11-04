package dev.motioncare.feature.history.departure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.GuidanceSettings
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.DepartureUiState
import dev.motioncare.feature.history.DepartureViewModel
import dev.motioncare.feature.history.insights.AdviceState
import dev.motioncare.feature.history.insights.patternLabel
import dev.motioncare.feature.history.ui.*

@Composable
fun DepartureRoute(
    trips: TripRepository,
    profiles: ProfileRepository,
    guidance: GuidanceSettings?,
    onProfile: (PersonId) -> Unit,
    onAdvice: (PersonId?) -> Unit,
    onRecords: () -> Unit,
) {
    val model: DepartureViewModel = viewModel(factory = viewModelFactory {
        initializer { DepartureViewModel(trips, profiles, guidance) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    DepartureContent(state, model::place, model::clear, model::addPerson, model::toggleGuidance, onProfile, onAdvice, onRecords)
}

@Composable
private fun DepartureContent(
    state: DepartureUiState,
    onPlace: (PersonId, SeatPosition) -> Unit,
    onClear: (SeatPosition) -> Unit,
    onAdd: (String, PersonKind) -> Unit,
    onToggleGuidance: () -> Unit,
    onProfile: (PersonId) -> Unit,
    onAdvice: (PersonId?) -> Unit,
    onRecords: () -> Unit,
) {
    var addDialog by remember { mutableStateOf(false) }
    MotionCareBackground {
        BoxWithConstraints(Modifier.fillMaxSize().padding(22.dp)) {
            val wide = maxWidth >= 1050.dp
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ScreenTitle("출발 전 안내", "운전을 시작하면 기록이 자동으로 연결됩니다", action = { SecondaryAction("전체 기록", onRecords) })
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    AdviceCard(state, onToggleGuidance, { onAdvice(state.seating.driverId) }, Modifier.weight(.88f))
                    SeatingPanel(state, onPlace, onClear, onProfile, { addDialog = true }, Modifier.weight(1.12f))
                } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AdviceCard(state, onToggleGuidance, { onAdvice(state.seating.driverId) })
                    SeatingPanel(state, onPlace, onClear, onProfile, { addDialog = true })
                }
            }
        }
    }
    if (addDialog) AddPersonDialog({ addDialog = false }, { name, kind -> onAdd(name, kind); addDialog = false })
}

@Composable private fun AdviceCard(
    state: DepartureUiState,
    onToggle: () -> Unit,
    onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val advice = state.advice
    GlassPanel(modifier.heightIn(min = 540.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(when {
                !state.guidanceEnabled -> "안내 꺼짐"
                advice.state == AdviceState.FIRST_TRIP -> "첫 운행"
                advice.state == AdviceState.INSUFFICIENT -> "기록 부족"
                advice.state == AdviceState.INITIAL -> "기록 1회"
                else -> "✦ 오늘의 편안한 운전"
            }) })
            Spacer(Modifier.weight(1f)); Text("안내", color = MotionSubtle); Switch(checked = state.guidanceEnabled, onCheckedChange = { onToggle() })
        }
        Spacer(Modifier.height(8.dp))
        Text(if (state.guidanceEnabled) advice.headline else "출발 안내가 꺼져 있어요.",
            fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold)
        advice.context?.takeIf { state.guidanceEnabled }?.let { Text(it, color = MotionSubtle, fontSize = 19.sp) }
        Spacer(Modifier.weight(1f))
        if (state.guidanceEnabled && advice.pattern != null) {
            SecondaryAction("왜 이 조언인가요?  ↗", onExplain, Modifier.fillMaxWidth())
            advice.recentAverage?.let { recent ->
                val before = advice.previousAverage
                Surface(color = MotionBlue.copy(.22f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("〰", color = MotionCyan, fontSize = 34.sp)
                        Spacer(Modifier.width(12.dp))
                        Column { Text("10분당 ${patternLabel(advice.pattern)}", color = MotionSubtle)
                            Text(if (before == null) "최근 ${"%.1f".format(recent)}회" else "${"%.1f".format(before)}회 → ${"%.1f".format(recent)}회", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                    }
                }
            }
        } else if (!state.guidanceEnabled) PrimaryAction("켜기", onToggle, Modifier.fillMaxWidth())
    }
}

@Composable private fun SeatingPanel(
    state: DepartureUiState,
    onPlace: (PersonId, SeatPosition) -> Unit,
    onClear: (SeatPosition) -> Unit,
    onProfile: (PersonId) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf<SeatPosition?>(null) }
    val seatBounds = remember { mutableStateMapOf<SeatPosition, Rect>() }
    var dragging by remember { mutableStateOf<PersonId?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    GlassPanel(modifier.heightIn(min = 620.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("함께 타는 사람", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("운전자 ${if (state.seating.driverId == null) 0 else 1} · 동승자 ${state.seating.passengerIds.size}", color = MotionCyan)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CarInterior(state, seatBounds, { picker = it }, Modifier.weight(1.15f))
            Column(Modifier.widthIn(min = 230.dp, max = 310.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("등록된 사람", color = MotionSubtle, fontWeight = FontWeight.Bold)
                state.people.forEach { person ->
                    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val seated = state.seating.assignments.entries.firstOrNull { it.value == person.id }?.key
                    Surface(onClick = { onProfile(person.id) }, color = if (dragging == person.id) MotionBlue.copy(.45f) else MotionPanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (seated != null) MotionCyan else MotionLine),
                        shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp)
                            .onGloballyPositioned { coordinates = it }
                            .pointerInput(person.id, seatBounds.keys.toList()) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local -> dragging = person.id; dragPosition = coordinates?.positionInRoot()?.plus(local) ?: Offset.Zero },
                                    onDrag = { change, _ -> change.consume(); dragPosition = coordinates?.positionInRoot()?.plus(change.position) ?: dragPosition },
                                    onDragEnd = { seatBounds.entries.firstOrNull { dragPosition in it.value }?.key?.let { onPlace(person.id, it) }; dragging = null },
                                    onDragCancel = { dragging = null },
                                )
                            }) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PersonAvatar(person, Modifier.size(54.dp)); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(person.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(seated?.label() ?: if (person.kind == PersonKind.CHILD) "어린이" else "성인", color = if (seated != null) MotionCyan else MotionSubtle, fontSize = 13.sp) }
                            Text("⠿", color = MotionMuted, fontSize = 24.sp)
                        }
                    }
                }
                SecondaryAction("＋ 사람 추가", onAdd, Modifier.fillMaxWidth())
                Text("길게 눌러 좌석에 놓거나 좌석을 누르세요", color = MotionMuted, fontSize = 13.sp)
            }
        }
    }
    picker?.let { seat -> SeatPickerDialog(seat, state.people, state.seating, { picker = null }, { onPlace(it, seat); picker = null }, { onClear(seat); picker = null }) }
}

@Composable private fun CarInterior(state: DepartureUiState, bounds: MutableMap<SeatPosition, Rect>, onSeat: (SeatPosition) -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxHeight().background(Color(0x55123B60), RoundedCornerShape(110.dp))
        .border(2.dp, MotionCyan.copy(.48f), RoundedCornerShape(110.dp)).padding(32.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SeatButton(SeatPosition.DRIVER, state, bounds, onSeat, Modifier.weight(1f)); Spacer(Modifier.width(22.dp))
                SeatButton(SeatPosition.FRONT_PASSENGER, state, bounds, onSeat, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SeatButton(SeatPosition.REAR_LEFT, state, bounds, onSeat, Modifier.weight(1f))
                SeatButton(SeatPosition.REAR_CENTER, state, bounds, onSeat, Modifier.weight(1f))
                SeatButton(SeatPosition.REAR_RIGHT, state, bounds, onSeat, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun SeatButton(seat: SeatPosition, state: DepartureUiState, bounds: MutableMap<SeatPosition, Rect>, onSeat: (SeatPosition) -> Unit, modifier: Modifier) {
    val person = state.seating.assignments[seat]?.let { id -> state.people.firstOrNull { it.id == id } }
    OutlinedButton(onClick = { onSeat(seat) }, modifier = modifier.heightIn(min = 120.dp).onGloballyPositioned { bounds[seat] = it.boundsInRoot() },
        border = androidx.compose.foundation.BorderStroke(2.dp, if (person != null) MotionCyan else MotionLine), shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (person == null) Text("＋", fontSize = 32.sp, color = MotionMuted) else PersonAvatar(person, Modifier.size(54.dp))
            Text(person?.displayName ?: seat.shortLabel(), fontSize = 14.sp, color = if (person != null) MotionText else MotionMuted)
        }
    }
}

@Composable private fun SeatPickerDialog(seat: SeatPosition, people: List<PersonProfile>, plan: SeatingPlan, onDismiss: () -> Unit, onPick: (PersonId) -> Unit, onClear: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(seat.label()) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        people.forEach { person ->
            val disabled = seat == SeatPosition.DRIVER && person.kind == PersonKind.CHILD
            val current = plan.assignments[seat] == person.id
            OutlinedButton(onClick = { onPick(person.id) }, enabled = !disabled, modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp)) {
                PersonAvatar(person, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp)); Text(person.displayName, Modifier.weight(1f)); if (current) Text("✓")
            }
        }
    } }, confirmButton = { if (plan.assignments[seat] != null) TextButton(onClick = onClear) { Text("비우기") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

@Composable private fun AddPersonDialog(onDismiss: () -> Unit, onAdd: (String, PersonKind) -> Unit) {
    var name by remember { mutableStateOf("") }; var kind by remember { mutableStateOf(PersonKind.ADULT) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("사람 추가") }, text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == PersonKind.ADULT, { kind = PersonKind.ADULT }, { Text("성인") })
            FilterChip(kind == PersonKind.CHILD, { kind = PersonKind.CHILD }, { Text("어린이") })
        }
    } }, confirmButton = { TextButton(onClick = { onAdd(name, kind) }, enabled = name.isNotBlank()) { Text("추가") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

private fun SeatPosition.shortLabel() = when (this) { SeatPosition.DRIVER -> "운전석"; SeatPosition.FRONT_PASSENGER -> "조수석"; SeatPosition.REAR_LEFT -> "뒤 왼쪽"; SeatPosition.REAR_CENTER -> "뒤 가운데"; SeatPosition.REAR_RIGHT -> "뒤 오른쪽" }
private fun SeatPosition.label() = shortLabel()