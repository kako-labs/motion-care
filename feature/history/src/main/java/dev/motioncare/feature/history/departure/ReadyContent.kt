package dev.motioncare.feature.history.departure

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import dev.motioncare.core.ui.MotionIcons
import dev.motioncare.feature.history.ui.MotionSelected
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.ContentScale
import dev.motioncare.feature.history.R
import dev.motioncare.feature.history.presentation.BActionRibbon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
                    Icon(MotionIcons.Plus, contentDescription = null, tint = MotionCyan, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("함께 타는 사람 · 출발 안내", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Text("좌석 · 오늘의 조언", color = MotionSubtle, fontSize = 17.sp)
                }
                Icon(MotionIcons.ChevronRight, contentDescription = null, tint = MotionCyan, modifier = Modifier.size(32.dp))
            }
        }
        PrimaryAction(if (vehicleConnected) "운행 기록 시작" else "샘플 주행 시작", onStart, Modifier.fillMaxWidth(), enabled = !busy)
    }
}

@Composable
fun DepartureRoute(
    trips: TripRepository,
    profiles: ProfileRepository,
    environment: Flow<EnvironmentSnapshot?> = flowOf(null),
    onBack: () -> Unit,
    onProfile: (PersonId) -> Unit,
    onAdvice: (PersonId?) -> Unit,
    onRecords: () -> Unit,
    onTrip: (TripId) -> Unit = { onRecords() },
) {
    val model: DepartureViewModel = viewModel(factory = viewModelFactory {
        initializer { DepartureViewModel(trips, profiles, environment) }
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
                    SeatingPanel(state, openSeat, { openSeat = it }, Modifier.weight(1.15f).fillMaxHeight(), panelHeight)
                    DepartureConsole(state, { openSeat = it }, onAdvice, onTrip, onToggleGuidance,
                        Modifier.weight(.9f).fillMaxHeight(), compact)
                } else Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    SeatingPanel(state, openSeat, { openSeat = it })
                    DepartureConsole(state, { openSeat = it }, onAdvice, onTrip, onToggleGuidance)
                }
            }
        }
        SeatPicker(openSeat, state, { openSeat = null },
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
            fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium)
        advice.context?.takeIf { state.guidanceEnabled }?.let { Text(it, color = MotionSubtle, fontSize = 19.sp) }
        if (state.guidanceEnabled && advice.state == AdviceState.INSUFFICIENT)
            Text("1분 넘게 달린 기록부터 분석해요", color = MotionCyan, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Box((if (fill) Modifier.weight(1f) else Modifier.heightIn(min = 250.dp)).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.guidanceEnabled && advice.pattern != null && advice.recentRates.isNotEmpty()) Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                driver?.let { person ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PersonAvatar(person, Modifier.size(112.dp))
                        Text(person.displayName, fontSize = 22.sp, fontWeight = FontWeight.Medium)
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
                    Text("운전석 선택  →", color = MotionCyan, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(driver, Modifier.size(132.dp))
                    Text(driver.displayName, fontSize = 26.sp, fontWeight = FontWeight.Medium)
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
                                before?.let { Text("%.1f".format(it), fontSize = 30.sp, color = MotionMuted, fontWeight = FontWeight.Medium); Text("→", fontSize = 28.sp, color = MotionMuted) }
                                Text("%.1f".format(recent), fontSize = 46.sp, lineHeight = 50.sp, color = MotionCyan, fontWeight = FontWeight.Medium)
                                Text("회", fontSize = 20.sp, color = MotionSubtle)
                            }
                        }
                        if (before != null && before > 0) {
                            val change = ((recent - before) / before * 100).roundToInt()
                            Text(if (change <= 0) "↓ ${-change}%" else "↑ $change%", fontSize = 30.sp, fontWeight = FontWeight.Medium,
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
            Text("직전 운행", color = MotionSubtle, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(tripTime(last.trip.startedAtEpochMs), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("◷ ${last.durationMs / 60_000}분", fontSize = 20.sp, color = MotionSubtle)
            top?.let { Text("${patternIcon(it)} ${patternLabel(it)} ${last.count(it)}회", fontSize = 20.sp, color = MotionCyan, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) { riders.forEach { PersonAvatar(it, Modifier.size(44.dp)) } }
            Icon(MotionIcons.ChevronRight, contentDescription = null, tint = MotionCyan, modifier = Modifier.size(28.dp))
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
                Text(label, fontSize = 18.sp, fontWeight = if (now) FontWeight.Medium else FontWeight.Normal,
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
    Box(modifier.heightIn(min = 500.dp).padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        CarInterior(state, openSeat, onSeat, maxCarHeight)
    }
}

@Composable
private fun DepartureConsole(
    state: DepartureUiState,
    onSeat: (SeatPosition) -> Unit,
    onAdvice: (PersonId?) -> Unit,
    onTrip: (TripId) -> Unit,
    onToggleGuidance: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)) {
        DepartureOverview(state, onSeat, onToggleGuidance, compact)
        EnvironmentSummary(state.environment, compact)
        BActionRibbon(
            "이번 이동의 제안",
            if (state.guidanceEnabled) state.advice.headline else "출발 안내가 꺼져 있어요.",
            compact = compact,
        ) { onAdvice(state.seating.driverId) }
        DepartureRecent(state, onTrip, if (compact) Modifier.weight(1f) else Modifier, compact)
    }
}

@Composable
private fun DepartureOverview(
    state: DepartureUiState,
    onSeat: (SeatPosition) -> Unit,
    onToggleGuidance: () -> Unit,
    compact: Boolean,
) {
    val occupied = SeatPosition.entries.mapNotNull { seat ->
        state.seating.assignments[seat]?.let { id -> state.people.firstOrNull { it.id == id }?.let { seat to it } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("출발 준비", fontSize = if (compact) 25.sp else 30.sp, fontWeight = FontWeight.Medium)
                Text("탑승 인원 " + occupied.size + "명", color = MotionMuted, fontSize = 14.sp)
            }
            Text("출발 안내", color = MotionSubtle, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            Switch(state.guidanceEnabled, { onToggleGuidance() })
        }
        if (occupied.isEmpty()) {
            Text("차량 좌석을 눌러 운전자와 동승자를 선택해 주세요.", color = MotionSubtle, fontSize = 15.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                occupied.take(3).forEach { (seat, person) ->
                    Surface(
                        onClick = { onSeat(seat) },
                        color = MotionPanel,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MotionLine),
                        modifier = Modifier.weight(1f).heightIn(min = if (compact) 58.dp else 72.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            PersonAvatar(person, Modifier.size(if (compact) 34.dp else 42.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName, fontSize = if (compact) 14.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (seat == SeatPosition.DRIVER) "운전자" else seat.shortLabel(), color = MotionMuted, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentSummary(snapshot: EnvironmentSnapshot?, compact: Boolean) {
    GlassPanel(Modifier.fillMaxWidth(), compact = compact) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("실내·외부 환경", Modifier.weight(1f), fontSize = if (compact) 18.sp else 21.sp, fontWeight = FontWeight.Medium)
            Text(snapshot?.let { tripTime(it.capturedAtEpochMs) } ?: "확인 전", color = MotionMuted, fontSize = 12.sp)
        }
        if (snapshot == null) {
            Text("환경 정보를 확인하고 있어요.", color = MotionSubtle, fontSize = 14.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnvironmentValue("실내", temperatureValue(snapshot.cabinTemperature), Modifier.weight(1f))
                EnvironmentValue("바깥", temperatureValue(snapshot.outsideTemperature), Modifier.weight(1f))
                EnvironmentValue("대기질", airValue(snapshot.outdoorAir), Modifier.weight(1f))
            }
            Text(
                "실내·외기: 차량 신호 · 대기질: Open-Meteo/CAMS 예측 · " + environmentStatus(snapshot),
                color = MotionMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun EnvironmentValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MotionMuted, fontSize = 12.sp)
        Text(value, color = MotionText, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun temperatureValue(reading: EnvironmentReading<Double>): String =
    reading.value?.let { "%.1f°C".format(it) } ?: reading.availability.shortStatus()

private fun airValue(reading: EnvironmentReading<OutdoorAir>): String =
    reading.value?.grade?.let {
        when (it) {
            AirGrade.GOOD -> "좋음"
            AirGrade.MODERATE -> "보통"
            AirGrade.BAD -> "나쁨"
            AirGrade.VERY_BAD -> "매우 나쁨"
        }
    } ?: reading.availability.shortStatus()

private fun environmentStatus(snapshot: EnvironmentSnapshot): String {
    val states = listOf(snapshot.cabinTemperature, snapshot.outsideTemperature, snapshot.outdoorAir)
        .map { it.availability }
    return when {
        states.any { it == Availability.LOADING } -> "갱신 중"
        states.all { it == Availability.UNSUPPORTED } -> "이 기기에서 미지원"
        states.any { it == Availability.UNAVAILABLE } -> "일부 정보 없음"
        states.any { it == Availability.STALE } -> "이전 확인값 포함"
        else -> "최신 확인값"
    }
}

private fun Availability.shortStatus() = when (this) {
    Availability.AVAILABLE -> "확인됨"
    Availability.STALE -> "이전 값"
    Availability.LOADING -> "확인 중"
    Availability.UNSUPPORTED -> "미지원"
    Availability.UNAVAILABLE -> "정보 없음"
}

// Top-down view with the front of the car at the top; the driver sits front-left.
@Composable private fun CarInterior(state: DepartureUiState, openSeat: SeatPosition?, onSeat: (SeatPosition) -> Unit, maxCarHeight: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 860.dp), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, 570.dp, maxCarHeight / 1.5f)
        val height = width * 1.5f
        Box(Modifier.size(width, height)) {
            Image(painterResource(R.drawable.mc_cabin_b), "차량 좌석", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            val positions = listOf(
                Triple(SeatPosition.DRIVER, .34f, .50f), Triple(SeatPosition.FRONT_PASSENGER, .675f, .50f),
                Triple(SeatPosition.REAR_LEFT, .305f, .735f), Triple(SeatPosition.REAR_CENTER, .502f, .735f), Triple(SeatPosition.REAR_RIGHT, .70f, .735f))
            positions.forEach { (seat, x, y) ->
                val size = (width * .165f).coerceIn(48.dp, 64.dp)
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
            Surface(onClick = { onTrip(trip.trip.id) }, color = Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 66.dp else 90.dp)) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = if (compact) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tripTime(trip.trip.startedAtEpochMs), fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Medium)
                    Text("${trip.durationMs / 60_000}분 · ${"%.1f".format(trip.distanceMeters / 1000)}km", color = MotionMuted, fontSize = if (compact) 13.sp else 15.sp)
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
    Surface(onClick = { onSeat(seat) }, shape = RoundedCornerShape(13.dp),
        color = when { selected -> MotionCyan.copy(.16f); person != null -> Color(0xB30C1729); else -> Color.Transparent },
        border = if (selected || person != null) BorderStroke(1.dp, if (selected) MotionCyan.copy(.7f) else MotionCyan.copy(.22f)) else null,
        modifier = Modifier.size(size, size + 12.dp).semantics { contentDescription = "${seat.label()} · ${person?.displayName ?: "빈 자리"}" }) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            if (person == null) Box(Modifier.size(32.dp).background(Color(0x800A1724), RoundedCornerShape(10.dp)).border(1.dp, MotionCyan.copy(.22f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(MotionIcons.Plus, contentDescription = null, tint = MotionSubtle, modifier = Modifier.size(20.dp))
            } else PersonAvatar(person, Modifier.size((size * .52f).coerceAtLeast(28.dp)))
            if (person != null) Text(person.displayName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MotionText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SeatPicker(
    seat: SeatPosition?,
    state: DepartureUiState,
    onDismiss: () -> Unit,
    onPick: (PersonId, SeatPosition) -> Unit,
    onClear: (SeatPosition) -> Unit,
    onProfile: (PersonId) -> Unit,
    onAdd: () -> Unit,
) {
    val current = seat ?: return
    val occupant = state.seating.assignments[current]?.let { id -> state.people.firstOrNull { it.id == id } }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        MotionCareTheme {
            Column(
                // The width cap comes first: after fillMaxWidth the cap could not narrow the popup any more.
                // The surface is opaque so the screen behind does not show through the list.
                Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(max = 620.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF0E2A4F), Color(0xFF071A33))), RoundedCornerShape(24.dp))
                    .border(1.5.dp, MotionLine, RoundedCornerShape(24.dp))
                    .padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(current.label(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Text("앉을 사람을 선택해 주세요.", color = MotionMuted, fontSize = 13.sp)
                    }
                    RoundIconButton("✕", onDismiss)
                }
                occupant?.let { person ->
                    Surface(
                        color = MotionCyan.copy(.10f),
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, MotionCyan.copy(.42f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PersonAvatar(person, Modifier.size(46.dp))
                                Text(person.displayName, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryAction("프로필", { onProfile(person.id) }, Modifier.weight(1f))
                                SecondaryAction("자리 비우기", { onClear(current) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
                state.people.filter { it.id != occupant?.id }.forEach { person ->
                    val blocked = current == SeatPosition.DRIVER && person.kind == PersonKind.CHILD
                    val elsewhere = state.seating.assignments.entries.firstOrNull { it.value == person.id }?.key
                    Surface(
                        onClick = { onPick(person.id, current) },
                        enabled = !blocked,
                        color = Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).alpha(if (blocked) .38f else 1f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            PersonAvatar(person, Modifier.size(42.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    when {
                                        blocked -> "어린이는 운전석을 선택할 수 없어요."
                                        elsewhere != null -> elsewhere.label()
                                        person.kind == PersonKind.CHILD -> "어린이"
                                        else -> "성인"
                                    },
                                    color = if (elsewhere != null) MotionCyan else MotionMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            Icon(MotionIcons.ChevronRight, contentDescription = null, tint = MotionCyan, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                SecondaryAction("＋  사람 추가", onAdd, Modifier.fillMaxWidth())
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
                    Text("사람 추가", Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Medium)
                    seat?.let { Text("→ ${it.label()}", color = MotionCyan, fontSize = 22.sp, fontWeight = FontWeight.Medium) }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().heightIn(min = 76.dp), singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall, placeholder = { Text("이름", fontSize = 22.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onAdd(name.trim(), kind) }))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KindTile("성인", kind == PersonKind.ADULT, true, Modifier.weight(1f)) { kind = PersonKind.ADULT }
                    KindTile("어린이", kind == PersonKind.CHILD, childAllowed, Modifier.weight(1f)) { kind = PersonKind.CHILD }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SecondaryAction("취소", onDismiss, Modifier.weight(1f))
                    PrimaryAction(if (seat != null) "추가하고 앉히기" else "추가", { onAdd(name.trim(), kind) }, Modifier.weight(1f), enabled = name.isNotBlank())
                }
            }
        }
    }
}

@Composable private fun KindTile(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(22.dp),
        color = if (selected) MotionSelected else MotionPanel, border = BorderStroke(1.dp, if (selected) MotionCyan.copy(.5f) else MotionLine),
        modifier = modifier.height(88.dp).alpha(if (enabled) 1f else .35f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = if (selected) MotionText else MotionSubtle)
        }
    }
}

private fun SeatPosition.shortLabel() = when (this) { SeatPosition.DRIVER -> "운전석"; SeatPosition.FRONT_PASSENGER -> "조수석"; SeatPosition.REAR_LEFT -> "뒤 왼쪽"; SeatPosition.REAR_CENTER -> "뒤 가운데"; SeatPosition.REAR_RIGHT -> "뒤 오른쪽" }
private fun SeatPosition.label() = shortLabel()
