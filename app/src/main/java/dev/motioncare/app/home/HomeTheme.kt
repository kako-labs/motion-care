package dev.motioncare.app.home

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colors, surfaces and the theme are shared with the history screens in core:ui (MotionStyle.kt).

/** Text sizes for one width class. The wide set follows Home2 at density 1, the compact set is for phones. */
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
)

internal val WideSizes = HomeSizes(
    outer = 12.dp, gap = 14.dp, title = 44.sp, subtitle = 22.sp, logo = 76.dp, hero = 54.sp, heroBody = 26.sp,
    section = 26.sp, body = 23.sp, small = 20.sp, value = 30.sp, tripDate = 25.sp, navButton = 118.dp,
)

internal val CompactSizes = HomeSizes(
    outer = 0.dp, gap = 12.dp, title = 28.sp, subtitle = 15.sp, logo = 48.dp, hero = 28.sp, heroBody = 17.sp,
    section = 19.sp, body = 16.sp, small = 14.sp, value = 20.sp, tripDate = 18.sp, navButton = 84.dp,
)

/** The trip controls inside the hero card read at the hero's scale. */
internal fun heroTypography(sizes: HomeSizes) = Typography(
    headlineMedium = TextStyle(fontSize = sizes.hero * 0.8f, fontWeight = FontWeight.ExtraBold, lineHeight = sizes.hero),
    titleMedium = TextStyle(fontSize = sizes.section, fontWeight = FontWeight.ExtraBold),
    bodyLarge = TextStyle(fontSize = sizes.heroBody, lineHeight = sizes.heroBody * 1.45f),
    bodyMedium = TextStyle(fontSize = sizes.body),
    labelLarge = TextStyle(fontSize = sizes.body, fontWeight = FontWeight.Bold),
)
