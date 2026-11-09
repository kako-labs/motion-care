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

/**
 * The main screen after the Home2 board. Every card shows what the app records today; parts of the board that
 * have no feature yet (advice, seating, vehicle, AI summary, full history) are left out rather than faked.
 * The hero card holds the trip controls, so recording and the saved summary stay on this screen.
 */
@Composable
internal fun HomeScreen(
    vehicleConnected: Boolean,
    people: PeopleSummary,
    environment: EnvironmentCard,
    recent: List<RecentTripItem>,
    start: StartButton,
    onPeople: () -> Unit,
    onTrip: (TripId) -> Unit,
    notice: (@Composable () -> Unit)?,
    guidance: (@Composable () -> Unit)?,
    hero: @Composable (HomeSizes) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp && maxHeight >= 820.dp
        val sizes = if (wide) WideSizes else CompactSizes
        val header = @Composable { HomeHeader(vehicleConnected, sizes) }
        val noticeStrip = notice?.let { content -> @Composable { NoticeStrip(sizes, content) } }
        if (wide) {
            Column(Modifier.fillMaxSize().padding(sizes.outer), verticalArrangement = Arrangement.spacedBy(sizes.gap)) {
                header()
                noticeStrip?.invoke()
                HeroCard(sizes, Modifier.weight(1f).fillMaxWidth(), showCar = true) { hero(sizes) }
                Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(sizes.gap)) {
                    PeopleCard(people, onPeople, sizes, Modifier.width(420.dp).fillMaxHeight())
                    EnvironmentPanel(environment, sizes, Modifier.weight(1f).fillMaxHeight())
                    guidance?.let { GuidanceCard(sizes, Modifier.width(420.dp).fillMaxHeight(), scrolls = true, it) }
                }
                RecentTrips(recent, onTrip, sizes, columns = 3)
                BottomNav(start, onPeople, sizes)
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
                    guidance?.let { GuidanceCard(sizes, Modifier.fillMaxWidth(), scrolls = false, it) }
                    RecentTrips(recent, onTrip, sizes, columns = 1)
                }
                BottomNav(start, onPeople, sizes)
            }
        }
    }
}

/** The raised round button in the bottom bar. */
internal data class StartButton(val label: String, val enabled: Boolean, val onClick: () -> Unit)

/** The hero while no trip is open. Copy is a product judgment until departure advice is joined in. */
@Composable
internal fun ReadyHero(vehicleConnected: Boolean, sizes: HomeSizes) {
    Column {
        Chip("오늘의 편안한 운전", sizes)
        Spacer(Modifier.height(16.dp))
        Text(
            if (vehicleConnected) "주차한 상태에서\n운행을 시작해 보세요" else "샘플 주행으로\n기록 흐름을 확인해요",
            fontSize = sizes.hero, lineHeight = sizes.hero * 1.16f, fontWeight = FontWeight.ExtraBold, color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (vehicleConnected) {
                "운행을 기록하면 정차·출발 때의 속도 변화를 찾아 저장해요.\n내비를 띄워도 기록은 계속돼요."
            } else {
                "이 기기에는 차량이 연결되지 않아 합성 샘플만 기록해요."
            },
            fontSize = sizes.heroBody, lineHeight = sizes.heroBody * 1.45f, color = Color(0xFFCFE1F4),
        )
    }
}

@Composable
private fun HomeHeader(vehicleConnected: Boolean, sizes: HomeSizes) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.home_logo), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizes.logo).clip(CircleShape).border(1.5.dp, Color(0x739CE1FF), CircleShape),
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("모션케어", style = TextStyle(brush = titleBrush, fontSize = sizes.title, fontWeight = FontWeight.ExtraBold))
            Text("함께 타는 사람까지 편안한 운전", fontSize = sizes.subtitle, color = HomeColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusPill(if (vehicleConnected) "차량 속도 기록" else "합성 샘플 전용", sizes)
    }
}

@Composable
private fun StatusPill(text: String, sizes: HomeSizes) {
    Row(
        Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(HomeColors.Glow))
        Text(text, fontSize = sizes.small, color = HomeColors.TextStrong, fontWeight = FontWeight.SemiBold)
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
        Icon(HomeIcons.Sparkle, contentDescription = null, tint = Color(0xFF7FE6FB), modifier = Modifier.size(20.dp))
        Text(text, fontSize = sizes.small, fontWeight = FontWeight.Bold, color = HomeColors.ChipText)
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
    Box(modifier.clip(shape).background(heroFill).border(1.5.dp, HomeColors.HeroBorder, shape)) {
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
                action, fontSize = sizes.small, fontWeight = FontWeight.SemiBold, color = HomeColors.Link,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PeopleCard(people: PeopleSummary, onPeople: () -> Unit, sizes: HomeSizes, modifier: Modifier) {
    Column(modifier.glass().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardTitle("함께 타는 사람", sizes, action = "관리", onAction = onPeople)
        if (people.shown.isEmpty()) {
            Text("아직 등록한 사람이 없어요.", fontSize = sizes.body, color = HomeColors.TextSecondary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                people.shown.forEach { person ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Initial(person.displayName, sizes.logo)
                        Text(person.displayName, fontSize = sizes.value, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (people.more > 0) Text("외 ${people.more}명", fontSize = sizes.body, color = HomeColors.TextSecondary)
            }
        }
    }
}

/** No profile photos are stored yet, so a person shows as the first letter of their name. */
@Composable
private fun Initial(name: String, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2C6FB0), Color(0xFF0B2A52))))
            .border(2.dp, Color(0xB378DCFA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

private val environmentIcons = listOf(HomeIcons.Air, HomeIcons.Cloud, HomeIcons.Thermometer, HomeIcons.Recirculate)

@Composable
private fun EnvironmentPanel(card: EnvironmentCard, sizes: HomeSizes, modifier: Modifier) {
    Column(modifier.glass().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("지금 차 안팎 환경", fontSize = sizes.section, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            card.updatedAt?.let { Text(it, fontSize = sizes.small, color = HomeColors.TextTertiary) }
        }
        if (card.unsupported) {
            Text("이 기기에서는 차량 환경 값을 읽을 수 없어요.", fontSize = sizes.body, color = HomeColors.TextSecondary)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            card.items.forEachIndexed { index, item ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconBubble(environmentIcons[index], warm = item.warning, size = sizes.logo * 0.8f)
                    Text(item.label, fontSize = sizes.small, color = HomeColors.TextSecondary, textAlign = TextAlign.Center)
                    Text(
                        item.value,
                        fontSize = if (item.hasValue) sizes.value else sizes.body,
                        fontWeight = if (item.hasValue) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (item.warning) HomeColors.Caution else if (item.hasValue) HomeColors.Text else HomeColors.TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                    item.note?.let { Text(it, fontSize = sizes.small, color = HomeColors.TextTertiary) }
                }
            }
        }
        if (card.showsAirSource) {
            Text("대기질: Open-Meteo, CAMS 예측", fontSize = sizes.small, color = HomeColors.TextTertiary)
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
        Text("안내", fontSize = sizes.section, fontWeight = FontWeight.ExtraBold)
        MaterialTheme(typography = heroTypography(sizes).copy(labelLarge = TextStyle(fontSize = sizes.small, fontWeight = FontWeight.Bold))) {
            ProvideTextStyle(TextStyle(fontSize = sizes.small, fontWeight = FontWeight.SemiBold)) { content() }
        }
    }
}

@Composable
private fun RecentTrips(recent: List<RecentTripItem>, onTrip: (TripId) -> Unit, sizes: HomeSizes, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("최근 기록", fontSize = sizes.section, fontWeight = FontWeight.ExtraBold)
        if (recent.isEmpty()) {
            Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text("아직 기록이 없어요. 운행을 시작하면 여기에 쌓여요.", fontSize = sizes.body, color = HomeColors.TextSecondary)
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
                if (item.detail.isNotEmpty()) Text(item.detail, fontSize = sizes.small, color = HomeColors.TextSecondary)
                if (item.synthetic) {
                    Text("샘플", fontSize = sizes.small, color = HomeColors.ChipText, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x3300AAD2))
                            .padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
        Icon(HomeIcons.Chevron, contentDescription = null, tint = HomeColors.NavIdle, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun BottomNav(start: StartButton, onProfile: () -> Unit, sizes: HomeSizes) {
    val barHeight = sizes.navButton * 0.82f
    Box(Modifier.fillMaxWidth().height(sizes.navButton + 4.dp)) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(barHeight)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0x335A91D7), Color(0x6B102E5C))))
                .border(BorderStroke(1.5.dp, HomeColors.CardBorder), RoundedCornerShape(30.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(HomeIcons.Home, "홈", active = true, sizes = sizes, onClick = {}, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(sizes.navButton * 1.8f))
            NavItem(HomeIcons.Person, "프로필", active = false, sizes = sizes, onClick = onProfile, modifier = Modifier.weight(1f))
        }
        StartCircle(start, sizes, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, sizes: HomeSizes, onClick: () -> Unit, modifier: Modifier) {
    val color = if (active) HomeColors.NavActive else HomeColors.NavIdle
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
            .background(startFill)
            .border(2.dp, Color(0xCCBEF0FF), CircleShape)
            .clickable(enabled = start.enabled, role = Role.Button, onClick = start.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(HomeIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(sizes.navButton * 0.28f))
        Text(start.label, fontSize = sizes.small, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
