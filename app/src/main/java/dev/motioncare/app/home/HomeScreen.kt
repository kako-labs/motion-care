package dev.motioncare.app.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.R
import dev.motioncare.core.model.TripId
import dev.motioncare.core.ui.MotionBrushes
import dev.motioncare.core.ui.MotionIcons
import dev.motioncare.core.ui.MotionPrimaryButton
import dev.motioncare.core.ui.MotionPalette
import dev.motioncare.core.ui.glass
import dev.motioncare.feature.history.ui.PersonAvatar

/**
 * The main screen after the Home2 board. Every card shows what the app records today; parts of the board that
 * have no feature yet (vehicle, AI summary) are left out rather than faked. The hero card holds the departure
 * advice and the trip controls, so recording and the saved summary stay on this screen. Seats, records and
 * profiles open the history screens.
 */
@Composable
internal fun HomeScreen(
    status: String,
    people: PeopleSummary,
    environment: EnvironmentCard,
    recent: List<RecentTripItem>,
    start: StartButton?,
    links: HomeLinks,
    notice: (@Composable () -> Unit)?,
    guidance: @Composable () -> Unit,
    hero: @Composable (HomeSizes) -> Unit,
) {
    val onPeople = links.onSeats
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp && maxHeight >= 820.dp
        val sizes = if (wide) WideSizes else CompactSizes
        val header = @Composable { HomeHeader(status, sizes) }
        val noticeStrip = notice?.let { content -> @Composable { NoticeStrip(sizes, content) } }
        if (wide) {
            Column(Modifier.fillMaxSize().padding(sizes.outer), verticalArrangement = Arrangement.spacedBy(sizes.gap)) {
                header()
                noticeStrip?.invoke()
                HeroCard(sizes, Modifier.weight(1f).fillMaxWidth(), showCar = true) { hero(sizes) }
                // Tall enough for the settings card's three switches and sound choice without scrolling.
                Row(Modifier.fillMaxWidth().height(340.dp), horizontalArrangement = Arrangement.spacedBy(sizes.gap)) {
                    PeopleCard(people, onPeople, sizes, Modifier.width(480.dp).fillMaxHeight())
                    EnvironmentPanel(environment, sizes, Modifier.weight(1f).fillMaxHeight())
                    GuidanceCard(sizes, Modifier.width(400.dp).fillMaxHeight(), scrolls = true, guidance)
                }
                RecentTrips(recent, links, sizes, columns = 3)
                BottomNav(start, links, sizes)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = sizes.gap),
                    verticalArrangement = Arrangement.spacedBy(sizes.gap),
                ) {
                    header()
                    noticeStrip?.invoke()
                    HeroCard(sizes, Modifier.fillMaxWidth().heightIn(min = 200.dp), showCar = false) { hero(sizes) }
                    PeopleCard(people, onPeople, sizes, Modifier.fillMaxWidth())
                    EnvironmentPanel(environment, sizes, Modifier.fillMaxWidth())
                    GuidanceCard(sizes, Modifier.fillMaxWidth(), scrolls = false, guidance)
                    RecentTrips(recent, links, sizes, columns = 1)
                }
                BottomNav(start, links, sizes)
            }
        }
    }
}

/** The raised round button in the bottom bar. Devices without a car start sample trips with it. */
internal data class StartButton(val label: String, val enabled: Boolean, val onClick: () -> Unit)

/** Turns automatic recording on and off. In the car, trips start and finish with the gear instead of a button. */
@Composable
internal fun AutoRecordSwitch(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("운행 자동 기록", Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onChange)
        }
        Text("P에서 출발하면 기록을 시작해요", color = MotionPalette.TextSecondary, fontWeight = FontWeight.Normal)
    }
}

/** Turns the departure advice on and off. The setting lives in the profile store of the history feature. */
@Composable
internal fun DepartureGuidanceSwitch(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("출발 안내", Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

/** Where the home screen's links lead. */
internal data class HomeLinks(
    val onSeats: () -> Unit,
    val onRecords: () -> Unit,
    val onProfile: () -> Unit,
    val onTrip: (TripId) -> Unit,
)

/**
 * The hero while no trip is open: the departure advice the history feature works out for the seated driver.
 * Without a driver it asks who drives and opens the seats.
 */
@Composable
internal fun AdviceHero(advice: HomeAdvice, sizes: HomeSizes, onExplain: () -> Unit, onChooseSeats: () -> Unit) {
    Column {
        Chip("오늘의 편안한 운전", sizes)
        Spacer(Modifier.height(16.dp))
        Text(
            advice.headline,
            fontSize = sizes.hero, lineHeight = sizes.hero * 1.16f, fontWeight = FontWeight.ExtraBold, color = Color.White,
        )
        advice.context?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, fontSize = sizes.heroBody, lineHeight = sizes.heroBody * 1.45f, color = Color(0xFFCFE1F4))
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (advice.action) {
                HeroAction.EXPLAIN -> MotionPrimaryButton("왜 이런 조언을 받았나요?", onExplain)
                HeroAction.CHOOSE_SEATS -> MotionPrimaryButton("좌석에서 운전자 고르기", onChooseSeats)
                HeroAction.NONE -> Unit
            }
            if (advice.comparison != null && advice.comparisonLabel != null) {
                Column(
                    Modifier.glass(RoundedCornerShape(22.dp)).padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(advice.comparisonLabel, fontSize = sizes.small, color = MotionPalette.TextSecondary)
                    Text(advice.comparison, fontSize = sizes.body, fontWeight = FontWeight.ExtraBold, color = MotionPalette.TextStrong)
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(status: String, sizes: HomeSizes) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.home_logo), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizes.logo).clip(CircleShape).border(1.5.dp, Color(0x739CE1FF), CircleShape),
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("모션케어", style = TextStyle(brush = MotionBrushes.Title, fontSize = sizes.title, fontWeight = FontWeight.ExtraBold))
            Text("함께 타는 사람까지 편안한 운전", fontSize = sizes.subtitle, color = MotionPalette.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusPill(status, sizes)
    }
}

@Composable
private fun StatusPill(text: String, sizes: HomeSizes) {
    Row(
        Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MotionPalette.Glow))
        Text(text, fontSize = sizes.small, color = MotionPalette.TextStrong, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Chip(text: String, sizes: HomeSizes) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x3300AAD2))
            .border(1.5.dp, Color(0x8C5AD2F0), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(MotionIcons.Sparkle, contentDescription = null, tint = Color(0xFF7FE6FB), modifier = Modifier.size(20.dp))
        Text(text, fontSize = sizes.small, fontWeight = FontWeight.Bold, color = MotionPalette.ChipText)
    }
}

@Composable
private fun NoticeStrip(sizes: HomeSizes, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(horizontal = 22.dp, vertical = 12.dp)) {
        MaterialTheme(typography = heroTypography(sizes)) {
            ProvideTextStyle(TextStyle(fontSize = sizes.body)) { content() }
        }
    }
}

@Composable
private fun HeroCard(sizes: HomeSizes, modifier: Modifier, showCar: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.clip(shape).background(MotionBrushes.Hero).border(1.5.dp, MotionPalette.HeroBorder, shape)) {
        if (showCar) {
            Image(
                painterResource(R.drawable.home_car), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.6f)
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.horizontalGradient(0f to Color(0xFF05203F), 0.5f to Color(0x0005203F)))
                    },
            )
        }
        // On a wide screen the card takes what is left of the height, so its content scrolls rather than clips.
        // A phone scrolls the whole home column instead, and nested vertical scrolling is not allowed.
        val column = Modifier.fillMaxWidth(if (showCar) 0.58f else 1f)
        Column(
            (if (showCar) column.fillMaxHeight().verticalScroll(rememberScrollState()) else column)
                .padding(horizontal = if (showCar) 40.dp else 20.dp, vertical = if (showCar) 30.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MaterialTheme(typography = heroTypography(sizes)) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) { content() }
            }
        }
    }
}

@Composable
private fun CardTitle(text: String, sizes: HomeSizes, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = sizes.section, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, fontSize = sizes.small, fontWeight = FontWeight.SemiBold, color = MotionPalette.Link,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PeopleCard(people: PeopleSummary, onPeople: () -> Unit, sizes: HomeSizes, modifier: Modifier) {
    Column(modifier.glass().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardTitle("오늘 함께 타는 사람", sizes, action = "변경", onAction = onPeople)
        if (people.shown.isEmpty()) {
            Text(
                if (people.registered == 0) "아직 등록한 사람이 없어요. \"변경\"에서 좌석을 눌러 추가해요." else "좌석에 앉은 사람이 없어요.",
                fontSize = sizes.body, color = MotionPalette.TextSecondary,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                people.shown.forEach { seated ->
                    Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(seated.person, Modifier.size(sizes.logo * 0.85f).border(2.dp, Color(0xB378DCFA), CircleShape))
                        Column {
                            Text(seated.person.displayName, fontSize = sizes.value, fontWeight = FontWeight.ExtraBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(seated.role, fontSize = sizes.small, color = MotionPalette.TextSecondary, maxLines = 2)
                        }
                    }
                }
                if (people.more > 0) Text("외 ${people.more}명", fontSize = sizes.body, color = MotionPalette.TextSecondary)
            }
        }
    }
}

private val environmentIcons = listOf(MotionIcons.Air, MotionIcons.Cloud, MotionIcons.Thermometer, MotionIcons.Recirculate)

@Composable
private fun EnvironmentPanel(card: EnvironmentCard, sizes: HomeSizes, modifier: Modifier) {
    Column(modifier.glass().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("지금 차 안팎 환경", fontSize = sizes.section, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            card.updatedAt?.let { Text(it, fontSize = sizes.small, color = MotionPalette.TextTertiary) }
        }
        if (card.unsupported) {
            Text("이 기기에서는 차량 환경 값을 읽을 수 없어요.", fontSize = sizes.body, color = MotionPalette.TextSecondary)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            card.items.forEachIndexed { index, item ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconBubble(environmentIcons[index], warm = item.warning, size = sizes.logo * 0.8f)
                    Text(item.label, fontSize = sizes.small, color = MotionPalette.TextSecondary, textAlign = TextAlign.Center)
                    Text(
                        item.value,
                        fontSize = if (item.hasValue) sizes.value else sizes.body,
                        fontWeight = if (item.hasValue) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (item.warning) MotionPalette.Caution else if (item.hasValue) MotionPalette.Text else MotionPalette.TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                    item.note?.let { Text(it, fontSize = sizes.small, color = MotionPalette.TextTertiary) }
                }
            }
        }
        if (card.showsAirSource) {
            Text("대기질: Open-Meteo, CAMS 예측", fontSize = sizes.small, color = MotionPalette.TextTertiary)
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, warm: Boolean, size: Dp) {
    val fill = if (warm) {
        Brush.radialGradient(listOf(Color(0x73FFBE8C), Color(0x59784628), Color(0x99281814)))
    } else {
        Brush.radialGradient(listOf(Color(0x6B82C8FF), Color(0x61143C6E), Color(0x9906162E)))
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(fill)
            .border(1.5.dp, if (warm) Color(0x80FFC8A0) else Color(0x6B9CCDFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (warm) Color(0xFFFFD9BC) else Color(0xFFBFEBFC),
            modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun GuidanceCard(sizes: HomeSizes, modifier: Modifier, scrolls: Boolean, content: @Composable () -> Unit) {
    // Only the fixed-height card on a wide screen scrolls; a phone already scrolls the whole home column.
    val card = modifier.glass().padding(horizontal = 22.dp, vertical = 20.dp)
    Column(
        if (scrolls) card.verticalScroll(rememberScrollState()) else card,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("설정", fontSize = sizes.section, fontWeight = FontWeight.ExtraBold)
        MaterialTheme(typography = heroTypography(sizes).copy(labelLarge = TextStyle(fontSize = sizes.small, fontWeight = FontWeight.Bold))) {
            ProvideTextStyle(TextStyle(fontSize = sizes.small, fontWeight = FontWeight.SemiBold)) { content() }
        }
    }
}

@Composable
private fun RecentTrips(recent: List<RecentTripItem>, links: HomeLinks, sizes: HomeSizes, columns: Int) {
    val onTrip = links.onTrip
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardTitle("최근 기록", sizes, action = "전체 기록 보기", onAction = links.onRecords)
        if (recent.isEmpty()) {
            Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text("아직 기록이 없어요. 운행을 시작하면 여기에 쌓여요.", fontSize = sizes.body, color = MotionPalette.TextSecondary)
            }
        } else if (columns > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(sizes.gap)) {
                recent.forEach { TripCard(it, onTrip, sizes, Modifier.weight(1f).height(112.dp)) }
                repeat(columns - recent.size) { Spacer(Modifier.weight(1f)) }
            }
        } else {
            recent.forEach { TripCard(it, onTrip, sizes, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun TripCard(item: RecentTripItem, onTrip: (TripId) -> Unit, sizes: HomeSizes, modifier: Modifier) {
    Row(
        modifier.glass(RoundedCornerShape(24.dp)).clickable(role = Role.Button) { onTrip(item.id) }
            .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.startedAt, fontSize = sizes.tripDate, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.detail.isNotEmpty()) Text(item.detail, fontSize = sizes.small, color = MotionPalette.TextSecondary)
                item.people?.let {
                    Text(it, fontSize = sizes.small, color = MotionPalette.NavIdle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.synthetic) {
                    Text("샘플", fontSize = sizes.small, color = MotionPalette.ChipText, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x3300AAD2))
                            .padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
        Icon(MotionIcons.ChevronRight, contentDescription = null, tint = MotionPalette.NavIdle, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun BottomNav(start: StartButton?, links: HomeLinks, sizes: HomeSizes) {
    val barHeight = sizes.navButton * 0.82f
    Box(Modifier.fillMaxWidth().height(if (start != null) sizes.navButton + 4.dp else barHeight)) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(barHeight)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0x335A91D7), Color(0x6B102E5C))))
                .border(BorderStroke(1.5.dp, MotionPalette.CardBorder), RoundedCornerShape(30.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(MotionIcons.Home, "홈", active = true, sizes = sizes, onClick = {}, modifier = Modifier.weight(1f))
            NavItem(MotionIcons.Clock, "기록", active = false, sizes = sizes, onClick = links.onRecords, modifier = Modifier.weight(1f))
            if (start != null) Spacer(Modifier.width(sizes.navButton * 1.8f))
            NavItem(MotionIcons.Person, "프로필", active = false, sizes = sizes, onClick = links.onProfile, modifier = Modifier.weight(1f))
            // Home2's last tab is the vehicle screen, which does not exist yet; the seats take its place.
            NavItem(MotionIcons.Car, "좌석", active = false, sizes = sizes, onClick = links.onSeats, modifier = Modifier.weight(1f))
        }
        start?.let { StartCircle(it, sizes, Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, sizes: HomeSizes, onClick: () -> Unit, modifier: Modifier) {
    val color = if (active) MotionPalette.NavActive else MotionPalette.NavIdle
    Column(
        modifier.fillMaxHeight().clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(sizes.logo * 0.42f))
        Text(label, fontSize = sizes.small, color = color, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StartCircle(start: StartButton, sizes: HomeSizes, modifier: Modifier) {
    Column(
        modifier.size(sizes.navButton)
            .alpha(if (start.enabled) 1f else 0.5f)
            .drawBehind { drawCircle(Color(0x2900AAD2), radius = size.minDimension / 2) }
            .padding(6.dp)
            .clip(CircleShape)
            .background(MotionBrushes.Start)
            .border(2.dp, Color(0xCCBEF0FF), CircleShape)
            .clickable(enabled = start.enabled, role = Role.Button, onClick = start.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(MotionIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(sizes.navButton * 0.28f))
        Text(start.label, fontSize = sizes.small, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
