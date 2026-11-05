package dev.motioncare.feature.history.departure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.DepartureUiState
import dev.motioncare.feature.history.DepartureViewModel
import dev.motioncare.feature.history.insights.AdviceState
import dev.motioncare.feature.history.insights.patternLabel
import dev.motioncare.feature.history.ui.*

/** Home card the app shows before a trip. The start action stays with the driving owner. */
@Composable
fun ReadyContent(busy: Boolean, vehicleConnected: Boolean, onStart: () -> Unit, onProfiles: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Surface(onClick = onProfiles, enabled = !busy, color = MotionBlue.copy(.28f), shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MotionCyan), modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp).border(2.dp, MotionCyan, CircleShape), contentAlignment = Alignment.Center) {
                    Text("＋", fontSize = 34.sp, color = MotionCyan)
                }
                Column(Modifier.weight(1f)) {
                    Text("함께 타는 사람 · 출발 안내", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("좌석 · 오늘의 조언", color = MotionSubtle, fontSize = 17.sp)
                }
                Text("›", fontSize = 40.sp, color = MotionCyan)
            }
        }
        PrimaryAction(if (vehicleConnected) "운행 기록 시작" else "샘플 주행 시작", onStart, Modifier.fillMaxWidth(), enabled = !busy)
    }
}

@Composable
fun DepartureRoute(
    trips: TripRepository,
    profiles: ProfileRepository,
    onBack: () -> Unit,
    onProfile: (PersonId) -> Unit,
    onAdvice: (PersonId?) -> Unit,
    onRecords: () -> Unit,
) {
    val model: DepartureViewModel = viewModel(factory = viewModelFactory {
        initializer { DepartureViewModel(trips, profiles) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    DepartureContent(state, model::place, model::clear, model::addPerson, model::toggleGuidance, onBack, onProfile, onAdvice, onRecords)
}

@Composable
private fun DepartureContent(
    state: DepartureUiState,
    onPlace: (PersonId, SeatPosition) -> Unit,
    onClear: (SeatPosition) -> Unit,
    onAdd: (String, PersonKind) -> Unit,
    onToggleGuidance: () -> Unit,
    onBack: () -> Unit,
    onProfile: (PersonId) -> Unit,
    onAdvice: (PersonId?) -> Unit,
    onRecords: () -> Unit,
) {
    var addDialog by remember { mutableStateOf(false) }
    var openSeat by remember { mutableStateOf<SeatPosition?>(null) }
    MotionCareBackground {
        BoxWithConstraints(Modifier.fillMaxSize().padding(22.dp)) {
            val wide = maxWidth >= 1050.dp
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ScreenTitle("출발 전 안내", onBack = onBack, action = { SecondaryAction("전체 기록", onRecords) })
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    AdviceCard(state, onToggleGuidance, { onAdvice(state.seating.driverId) }, Modifier.weight(1.1f))
                    SeatingPanel(state, openSeat, { openSeat = it }, Modifier.weight(.9f))
                } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AdviceCard(state, onToggleGuidance, { onAdvice(state.seating.driverId) })
                    SeatingPanel(state, openSeat, { openSeat = it })
                }
            }
        }
        SeatDrawer(openSeat, state, { openSeat = null },
            onPick = { person, seat -> onPlace(person, seat); openSeat = null },
            onClear = { seat -> onClear(seat); openSeat = null },
            onProfile = { person -> openSeat = null; onProfile(person) },
            onAdd = { addDialog = true })
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
            Spacer(Modifier.weight(1f)); Text("출발 안내", color = MotionSubtle); Switch(checked = state.guidanceEnabled, onCheckedChange = { onToggle() })
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
    openSeat: SeatPosition?,
    onSeat: (SeatPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier.heightIn(min = 620.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("함께 타는 사람", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("운전자 ${if (state.seating.driverId == null) 0 else 1} · 동승자 ${state.seating.passengerIds.size}", color = MotionCyan)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CarInterior(state, openSeat, onSeat) }
    }
}

// Top-down view with the front of the car at the top; the driver sits front-left.
@Composable private fun CarInterior(state: DepartureUiState, openSeat: SeatPosition?, onSeat: (SeatPosition) -> Unit) {
    val body = RoundedCornerShape(topStart = 170.dp, topEnd = 170.dp, bottomStart = 96.dp, bottomEnd = 96.dp)
    Box(Modifier.width(440.dp).height(720.dp)
        .background(Brush.verticalGradient(listOf(Color(0xFF1A6FA6), Color(0xFF0A3A64), Color(0xFF062646))), body)
        .border(2.dp, MotionCyan.copy(.6f), body)) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 70.dp).width(300.dp).height(70.dp)
            .background(Brush.verticalGradient(listOf(MotionCyan.copy(.45f), Color(0x88021428))),
                RoundedCornerShape(topStart = 120.dp, topEnd = 120.dp, bottomStart = 18.dp, bottomEnd = 18.dp)))
        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 170.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.SpaceEvenly) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SeatCircle(SeatPosition.DRIVER, 136.dp, state, openSeat, onSeat)
                SeatCircle(SeatPosition.FRONT_PASSENGER, 136.dp, state, openSeat, onSeat)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SeatCircle(SeatPosition.REAR_LEFT, 116.dp, state, openSeat, onSeat)
                SeatCircle(SeatPosition.REAR_CENTER, 116.dp, state, openSeat, onSeat)
                SeatCircle(SeatPosition.REAR_RIGHT, 116.dp, state, openSeat, onSeat)
            }
        }
    }
}

@Composable private fun SeatCircle(seat: SeatPosition, size: Dp, state: DepartureUiState, openSeat: SeatPosition?, onSeat: (SeatPosition) -> Unit) {
    val person = state.seating.assignments[seat]?.let { id -> state.people.firstOrNull { it.id == id } }
    val selected = openSeat == seat
    Column(Modifier.clip(RoundedCornerShape(24.dp)).clickable { onSeat(seat) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(size)
            .background(if (person != null) MotionBlue.copy(.38f) else Color(0x77061A31), CircleShape)
            .border(if (selected) 4.dp else 2.dp, when { selected -> Color.White; person != null -> MotionCyan; else -> MotionCyan.copy(.45f) }, CircleShape),
            contentAlignment = Alignment.Center) {
            if (person == null) Text("＋", fontSize = 52.sp, color = MotionCyan) else PersonAvatar(person, Modifier.size(size * .74f))
        }
        Text(person?.displayName ?: seat.label(), fontSize = 18.sp,
            fontWeight = if (person != null) FontWeight.Bold else FontWeight.Normal, color = if (person != null) MotionText else MotionSubtle)
    }
}

@Composable private fun BoxScope.SeatDrawer(
    seat: SeatPosition?,
    state: DepartureUiState,
    onDismiss: () -> Unit,
    onPick: (PersonId, SeatPosition) -> Unit,
    onClear: (SeatPosition) -> Unit,
    onProfile: (PersonId) -> Unit,
    onAdd: () -> Unit,
) {
    // Keep the last seat while the drawer slides out.
    var shown by remember { mutableStateOf(seat) }
    LaunchedEffect(seat) { if (seat != null) shown = seat }
    AnimatedVisibility(seat != null, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color(0xA6010814))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
    }
    AnimatedVisibility(seat != null, Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
        val current = seat ?: shown ?: return@AnimatedVisibility
        val occupant = state.seating.assignments[current]?.let { id -> state.people.firstOrNull { it.id == id } }
        Column(Modifier.fillMaxHeight().width(460.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF0E3358), Color(0xFF061528))))
            .border(1.dp, MotionLine).padding(24.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(current.label(), Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("✕", fontSize = 26.sp)
                }
            }
            occupant?.let { person ->
                Surface(color = MotionBlue.copy(.3f), shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MotionCyan), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(person, Modifier.size(64.dp))
                        Text(person.displayName, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        PrimaryAction("프로필", { onProfile(person.id) })
                        SecondaryAction("비우기", { onClear(current) })
                    }
                }
            }
            state.people.filter { it.id != occupant?.id }.forEach { person ->
                val blocked = current == SeatPosition.DRIVER && person.kind == PersonKind.CHILD
                val elsewhere = state.seating.assignments.entries.firstOrNull { it.value == person.id }?.key
                Surface(onClick = { onPick(person.id, current) }, enabled = !blocked,
                    color = MotionPanel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MotionLine),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).alpha(if (blocked) .4f else 1f)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PersonAvatar(person, Modifier.size(60.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(elsewhere?.label() ?: if (person.kind == PersonKind.CHILD) "어린이" else "성인",
                                color = if (elsewhere != null) MotionCyan else MotionSubtle, fontSize = 15.sp)
                        }
                    }
                }
            }
            SecondaryAction("＋ 사람 추가", onAdd, Modifier.fillMaxWidth())
        }
    }
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