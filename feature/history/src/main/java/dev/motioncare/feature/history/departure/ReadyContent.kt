package dev.motioncare.feature.history.departure

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.ContentScale
import dev.motioncare.feature.history.R
import dev.motioncare.feature.history.presentation.BActionRibbon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import dev.motioncare.feature.history.insights.TripInsight
import dev.motioncare.feature.history.insights.patternIcon
import dev.motioncare.feature.history.insights.patternLabel
import kotlin.math.roundToInt
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
    onTrip: (TripId) -> Unit = { onRecords() },
) {
    val model: DepartureViewModel = viewModel(factory = viewModelFactory {
        initializer { DepartureViewModel(trips, profiles) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    DepartureContent(state, model::place, model::clear, model::addPerson, model::toggleGuidance, onBack, onProfile, onAdvice, onRecords, onTrip)
}

@Composable
private fun DepartureContent(
    state: DepartureUiState,
    onPlace: (PersonId, SeatPosition) -> Unit,
    onClear: (SeatPosition) -> Unit,
    onAdd: (String, PersonKind, SeatPosition?) -> Unit,
    onToggleGuidance: () -> Unit,
    onBack: () -> Unit,
    onProfile: (PersonId) -> Unit,
    onAdvice: (PersonId?) -> Unit,
    onRecords: () -> Unit,
    onTrip: (TripId) -> Unit,
) {
    var addDialog by remember { mutableStateOf(false) }
    var addSeat by remember { mutableStateOf<SeatPosition?>(null) }
    var openSeat by remember { mutableStateOf<SeatPosition?>(null) }
    MotionCareBackground {
        BoxWithConstraints(Modifier.fillMaxSize().padding(22.dp)) {
            val wide = maxWidth >= 900.dp
            val panelHeight = (maxHeight - 90.dp).coerceAtLeast(520.dp)
            val compact = panelHeight < 850.dp
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ScreenTitle("출발 전 안내", onBack = onBack, action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryAction("전체 기록", onRecords)
                        PrimaryAction("완료  ✓", onBack, enabled = state.seating.driverId != null)
                    }
                })
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (wide) Row(Modifier.fillMaxWidth().height(panelHeight), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    SeatingPanel(state, openSeat, { openSeat = it }, Modifier.weight(1.5f), panelHeight - 94.dp)
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        DepartureRecent(state, onTrip, Modifier.weight(1f), compact)
                        BActionRibbon("오늘의 제안", if (state.guidanceEnabled) state.advice.headline else "출발 안내 꺼짐", compact = compact) { onAdvice(state.seating.driverId) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("출발 안내", Modifier.weight(1f), color = MotionSubtle)
                            Switch(state.guidanceEnabled, { onToggleGuidance() })
                        }
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    SeatingPanel(state, openSeat, { openSeat = it })
                    DepartureRecent(state, onTrip)
                    BActionRibbon("오늘의 제안", if (state.guidanceEnabled) state.advice.headline else "출발 안내 꺼짐") { onAdvice(state.seating.driverId) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("출발 안내", Modifier.weight(1f), color = MotionSubtle)
                        Switch(state.guidanceEnabled, { onToggleGuidance() })
                    }
                }
            }
        }
        SeatDrawer(openSeat, state, { openSeat = null },
            onPick = { person, seat -> onPlace(person, seat); openSeat = null },
            onClear = { seat -> onClear(seat); openSeat = null },
            onProfile = { person -> openSeat = null; onProfile(person) },
            onAdd = { addSeat = openSeat; openSeat = null; addDialog = true })
    }
    if (addDialog) AddPersonDialog(addSeat, { addDialog = false }, { name, kind -> onAdd(name, kind, addSeat); addDialog = false })
}

@Composable private fun AdviceCard(
    state: DepartureUiState,
    onToggle: () -> Unit,
    onExplain: () -> Unit,
    onPickDriver: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    val advice = state.advice
    val driver = state.seating.driverId?.let { id -> state.people.firstOrNull { it.id == id } }
    GlassPanel(if (fill) modifier.heightIn(min = 540.dp) else modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(when {
                !state.guidanceEnabled -> "안내 꺼짐"
                state.seating.driverId == null -> "준비"
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
        if (state.guidanceEnabled && advice.state == AdviceState.INSUFFICIENT)
            Text("1분 넘게 달린 기록부터 분석해요", color = MotionCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Box((if (fill) Modifier.weight(1f) else Modifier.heightIn(min = 250.dp)).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.guidanceEnabled && advice.pattern != null && advice.recentRates.isNotEmpty()) Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                driver?.let { person ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PersonAvatar(person, Modifier.size(112.dp))
                        Text(person.displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${patternIcon(advice.pattern)}  최근 ${advice.recentRates.size}번 운행", color = MotionSubtle, fontSize = 18.sp)
                    TrendBars(advice.recentRates, Modifier.fillMaxWidth().height(180.dp))
                }
            } else if (state.guidanceEnabled && advice.pattern == null) {
                if (driver == null) Column(Modifier.clip(RoundedCornerShape(28.dp)).clickable(onClick = onPickDriver).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SteeringWheel(Modifier.size(168.dp))
                    Text("운전석 선택  →", color = MotionCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(driver, Modifier.size(132.dp))
                    Text(driver.displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (state.guidanceEnabled && advice.pattern != null) {
            SecondaryAction("왜 이 조언인가요?  ↗", onExplain, Modifier.fillMaxWidth())
            advice.recentAverage?.let { recent ->
                val before = advice.previousAverage
                Surface(color = MotionBlue.copy(.22f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("10분당 ${patternLabel(advice.pattern)}", color = MotionSubtle, fontSize = 17.sp)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                before?.let { Text("%.1f".format(it), fontSize = 30.sp, color = MotionMuted, fontWeight = FontWeight.Bold); Text("→", fontSize = 28.sp, color = MotionMuted) }
                                Text("%.1f".format(recent), fontSize = 46.sp, lineHeight = 50.sp, color = MotionCyan, fontWeight = FontWeight.ExtraBold)
                                Text("회", fontSize = 20.sp, color = MotionSubtle)
                            }
                        }
                        if (before != null && before > 0) {
                            val change = ((recent - before) / before * 100).roundToInt()
                            Text(if (change <= 0) "↓ ${-change}%" else "↑ $change%", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                                color = if (change <= 0) MotionCyan else MotionSubtle)
                        }
                    }
                }
            }
        } else if (!state.guidanceEnabled) PrimaryAction("켜기", onToggle, Modifier.fillMaxWidth())
        else AdviceSteps(when {
            state.seating.driverId == null -> 0
            advice.state == AdviceState.FIRST_TRIP || advice.state == AdviceState.INSUFFICIENT -> 1
            else -> 2
        })
    }
}

@Composable private fun LastTripStrip(last: TripInsight, people: List<PersonProfile>, onOpen: () -> Unit) {
    val top = DrivingPattern.entries.maxByOrNull { last.count(it) }?.takeIf { last.count(it) > 0 }
    val riders = listOfNotNull(last.participants?.driverId).plus(last.participants?.passengerIds.orEmpty())
        .mapNotNull { id -> people.firstOrNull { it.id == id } }
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = MotionPanel, border = BorderStroke(1.dp, MotionLine),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("직전 운행", color = MotionSubtle, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(tripTime(last.trip.startedAtEpochMs), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("◷ ${last.durationMs / 60_000}분", fontSize = 20.sp, color = MotionSubtle)
            top?.let { Text("${patternIcon(it)} ${patternLabel(it)} ${last.count(it)}회", fontSize = 20.sp, color = MotionCyan, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) { riders.forEach { PersonAvatar(it, Modifier.size(44.dp)) } }
            Text("›", fontSize = 32.sp, color = MotionCyan)
        }
    }
}

@Composable private fun SteeringWheel(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ring = size.minDimension * .07f
        val radius = size.minDimension / 2 - ring
        drawCircle(MotionCyan.copy(.14f), radius)
        drawCircle(MotionCyan, radius, style = Stroke(ring))
        val hub = radius * .26f
        listOf(Offset(-radius, 0f), Offset(radius, 0f), Offset(0f, radius)).forEach { end ->
            drawLine(MotionCyan, center, center + end, strokeWidth = ring * .8f)
        }
        drawCircle(Color(0xFF0C2F55), hub)
        drawCircle(MotionCyan, hub, style = Stroke(ring * .7f))
    }
}

private val adviceSteps = listOf("◉" to "자리", "▶" to "운행", "★" to "평가", "✦" to "맞춤 조언")

// Shown until a driver has a usable record: where the advice will come from, instead of an empty card.
@Composable private fun AdviceSteps(current: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        adviceSteps.forEachIndexed { index, (icon, label) ->
            val done = index < current
            val now = index == current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(if (now) 92.dp else 76.dp)
                    .background(if (now) MotionCyan.copy(.22f) else if (done) MotionBlue.copy(.35f) else Color.Transparent, CircleShape)
                    .border(if (now) 3.dp else 1.5.dp, if (now || done) MotionCyan else MotionLine, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text(if (done) "✓" else icon, fontSize = if (now) 38.sp else 30.sp,
                        color = if (now || done) MotionCyan else MotionMuted)
                }
                Text(label, fontSize = 18.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                    color = if (now) MotionText else MotionMuted)
            }
            if (index < adviceSteps.lastIndex) Box(Modifier.width(28.dp).height(2.dp).offset(y = (-16).dp)
                .background(if (index < current) MotionCyan else MotionLine))
        }
    }
}

@Composable private fun SeatingPanel(
    state: DepartureUiState,
    openSeat: SeatPosition?,
    onSeat: (SeatPosition) -> Unit,
    modifier: Modifier = Modifier,
    maxCarHeight: Dp = 860.dp,
) {
    GlassPanel(modifier.heightIn(min = 560.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("함께 타는 사람", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("운전자 ${if (state.seating.driverId == null) 0 else 1} · 동승자 ${state.seating.passengerIds.size}", color = MotionCyan)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CarInterior(state, openSeat, onSeat, maxCarHeight) }
    }
}

// Top-down view with the front of the car at the top; the driver sits front-left.
@Composable private fun CarInterior(state: DepartureUiState, openSeat: SeatPosition?, onSeat: (SeatPosition) -> Unit, maxCarHeight: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 860.dp), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, 570.dp, maxCarHeight / 1.5f)
        val height = width * 1.5f
        Box(Modifier.size(width, height)) {
            Image(painterResource(R.drawable.mc_cabin_b), "차량 좌석", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            val narrow = width < 360.dp
            val positions = listOf(
                Triple(SeatPosition.DRIVER, .34f, .48f), Triple(SeatPosition.FRONT_PASSENGER, .675f, .48f),
                Triple(SeatPosition.REAR_LEFT, if (narrow) .22f else .315f, .70f), Triple(SeatPosition.REAR_CENTER, .502f, .70f), Triple(SeatPosition.REAR_RIGHT, if (narrow) .78f else .695f, .70f))
            positions.forEach { (seat, x, y) ->
                val size = (width * .17f).coerceAtLeast(56.dp)
                Box(Modifier.offset(width * x - size / 2, height * y - size / 2)) {
                    SeatCircle(seat, size, state, openSeat, onSeat)
                }
            }
        }
    }
}

@Composable private fun DepartureRecent(state: DepartureUiState, onTrip: (TripId) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    GlassPanel(modifier.fillMaxWidth(), compact = compact) {
        Text("최근 운행", fontSize = if (compact) 22.sp else 26.sp, fontWeight = FontWeight.Medium)
        if (state.recentTrips.isEmpty()) Text("아직 운전 기록이 없어요.", color = MotionSubtle)
        state.recentTrips.forEach { trip ->
            Surface(onClick = { onTrip(trip.trip.id) }, color = MotionPanelLight.copy(.4f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 64.dp else 94.dp)) {
                Column(Modifier.padding(if (compact) 8.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                    Text(tripTime(trip.trip.startedAtEpochMs), fontSize = if (compact) 18.sp else 21.sp)
                    Text("${trip.durationMs / 60_000}분 · ${"%.1f".format(trip.distanceMeters / 1000)}km", color = MotionSubtle, fontSize = if (compact) 13.sp else 16.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        state.advice.recentAverage?.let { Text("최근 분석 가능한 운행 평균 · ${"%.1f".format(it)}회/10분", color = MotionCyan, fontSize = if (compact) 16.sp else 21.sp) }
    }
}

@Composable private fun SeatCircle(seat: SeatPosition, size: Dp, state: DepartureUiState, openSeat: SeatPosition?, onSeat: (SeatPosition) -> Unit) {
    val person = state.seating.assignments[seat]?.let { id -> state.people.firstOrNull { it.id == id } }
    val selected = openSeat == seat
    Column(Modifier.clip(RoundedCornerShape(24.dp)).semantics { contentDescription = "${seat.label()} · ${person?.displayName ?: "빈 자리"}" }.clickable { onSeat(seat) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(size)
            .background(if (person != null) MotionBlue.copy(.38f) else Color(0x77061A31), CircleShape)
            .border(if (selected) 4.dp else 2.dp, when { selected -> Color.White; person != null -> MotionCyan; else -> MotionCyan.copy(.45f) }, CircleShape),
            contentAlignment = Alignment.Center) {
            if (person == null) Text("＋", fontSize = 52.sp, color = MotionCyan) else PersonAvatar(person, Modifier.size(size * .74f))
        }
        Text(person?.displayName ?: "", fontSize = 18.sp,
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
                RoundIconButton("✕", onDismiss)
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
            val nobodyElse = state.people.none { it.id != occupant?.id }
            Surface(onClick = onAdd, color = if (nobodyElse) MotionCyan.copy(.12f) else Color.Transparent, shape = RoundedCornerShape(20.dp),
                border = BorderStroke(if (nobodyElse) 2.dp else 1.dp, if (nobodyElse) MotionCyan else MotionLine),
                modifier = Modifier.fillMaxWidth().heightIn(min = if (nobodyElse) 220.dp else 88.dp)) {
                if (nobodyElse) Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                    Box(Modifier.size(104.dp).border(2.dp, MotionCyan, CircleShape), contentAlignment = Alignment.Center) {
                        Text("＋", fontSize = 56.sp, color = MotionCyan)
                    }
                    Text("사람 추가", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MotionCyan)
                } else Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(60.dp).border(2.dp, MotionCyan, CircleShape), contentAlignment = Alignment.Center) {
                        Text("＋", fontSize = 32.sp, color = MotionCyan)
                    }
                    Text("사람 추가", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MotionCyan)
                }
            }
        }
    }
}

@Composable private fun AddPersonDialog(seat: SeatPosition?, onDismiss: () -> Unit, onAdd: (String, PersonKind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PersonKind.ADULT) }
    val childAllowed = seat != SeatPosition.DRIVER
    Dialog(onDismissRequest = onDismiss) {
        MotionCareTheme {
            Column(Modifier.width(620.dp).background(Brush.verticalGradient(listOf(Color(0xFF123A63), Color(0xFF071A30))), RoundedCornerShape(28.dp))
                .border(1.dp, MotionLine, RoundedCornerShape(28.dp)).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("사람 추가", Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    seat?.let { Text("→ ${it.label()}", color = MotionCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().heightIn(min = 76.dp), singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall, placeholder = { Text("이름", fontSize = 22.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onAdd(name.trim(), kind) }))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KindTile("성인", PersonKind.ADULT, 88.dp, kind == PersonKind.ADULT, true, Modifier.weight(1f)) { kind = PersonKind.ADULT }
                    KindTile("어린이", PersonKind.CHILD, 72.dp, kind == PersonKind.CHILD, childAllowed, Modifier.weight(1f)) { kind = PersonKind.CHILD }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SecondaryAction("취소", onDismiss, Modifier.weight(1f))
                    PrimaryAction(if (seat != null) "추가하고 앉히기" else "추가", { onAdd(name.trim(), kind) }, Modifier.weight(1f), enabled = name.isNotBlank())
                }
            }
        }
    }
}

@Composable private fun KindTile(label: String, kindOf: PersonKind, figure: Dp, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(22.dp),
        color = if (selected) MotionCyan.copy(.18f) else MotionPanel, border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MotionCyan else MotionLine),
        modifier = modifier.height(170.dp).alpha(if (enabled) 1f else .35f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            KindFace(kindOf, Modifier.size(figure).alpha(if (selected) 1f else .6f))
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (selected) MotionText else MotionSubtle)
        }
    }
}

private fun SeatPosition.shortLabel() = when (this) { SeatPosition.DRIVER -> "운전석"; SeatPosition.FRONT_PASSENGER -> "조수석"; SeatPosition.REAR_LEFT -> "뒤 왼쪽"; SeatPosition.REAR_CENTER -> "뒤 가운데"; SeatPosition.REAR_RIGHT -> "뒤 오른쪽" }
private fun SeatPosition.label() = shortLabel()
