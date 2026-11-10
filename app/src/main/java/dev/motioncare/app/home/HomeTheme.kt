package dev.motioncare.app.home

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.motioncare.core.ui.CompactType
import dev.motioncare.core.ui.MotionType
import dev.motioncare.core.ui.WideType
import dev.motioncare.core.ui.WordLineBreak

// Colors, surfaces, the theme and the type scale are shared with the history screens in core:ui.

/** Sizes for one width class. The wide set follows Home2 at density 1, the compact set is for phones. */
internal data class HomeSizes(
    val outer: Dp,
    val gap: Dp,
    val title: TextUnit,
    val subtitle: TextUnit,
    val logo: Dp,
    val hero: TextUnit,
    val heroBody: TextUnit,
    val section: TextUnit,
    val body: TextUnit,
    val small: TextUnit,
    val value: TextUnit,
    val tripDate: TextUnit,
    val navButton: Dp,
    /** Height of the people and environment cards under the hero; the settings column beside them is as tall. */
    val infoRow: Dp,
    val peopleWidth: Dp,
    val settingsWidth: Dp,
    val avatar: Dp,
    val iconBubble: Dp,
)

// Text sizes come from the shared scale so the home and every other screen read alike.
private fun sizes(type: MotionType, outer: Dp, gap: Dp, logo: Dp, navButton: Dp, infoRow: Dp, peopleWidth: Dp,
    settingsWidth: Dp, avatar: Dp, iconBubble: Dp) = HomeSizes(
    outer = outer, gap = gap, title = type.title, subtitle = type.label, logo = logo, hero = type.display,
    heroBody = type.lead, section = type.section, body = type.body, small = type.label, value = type.headline,
    tripDate = type.section, navButton = navButton, infoRow = infoRow, peopleWidth = peopleWidth,
    settingsWidth = settingsWidth, avatar = avatar, iconBubble = iconBubble,
)

// The wide set fills the 1682 x 1210 car window without scrolling: header 96, hero about 420, cards 312, recent trips
// 168 and the tab bar 88. See docs/design.md#앱에-구현한-홈-화면.
internal val WideSizes = sizes(WideType, outer = 12.dp, gap = 16.dp, logo = 76.dp, navButton = 108.dp, infoRow = 312.dp,
    peopleWidth = 430.dp, settingsWidth = 440.dp, avatar = 56.dp, iconBubble = 64.dp)

internal val CompactSizes = sizes(CompactType, outer = 0.dp, gap = 12.dp, logo = 48.dp, navButton = 84.dp, infoRow = 0.dp,
    peopleWidth = 0.dp, settingsWidth = 0.dp, avatar = 48.dp, iconBubble = 40.dp)

/** The trip controls inside the hero card read at the hero's scale. */
internal fun heroTypography(sizes: HomeSizes) = Typography(
    headlineMedium = TextStyle(fontSize = sizes.hero * 0.8f, fontWeight = FontWeight.ExtraBold, lineHeight = sizes.hero,
        lineBreak = WordLineBreak),
    titleMedium = TextStyle(fontSize = sizes.section, fontWeight = FontWeight.ExtraBold, lineBreak = WordLineBreak),
    bodyLarge = TextStyle(fontSize = sizes.heroBody, lineHeight = sizes.heroBody * 1.45f, lineBreak = WordLineBreak),
    bodyMedium = TextStyle(fontSize = sizes.body, lineBreak = WordLineBreak),
    labelLarge = TextStyle(fontSize = sizes.body, fontWeight = FontWeight.Bold, lineBreak = WordLineBreak),
)
