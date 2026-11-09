package dev.motioncare.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Values from the Home2 board of the second design round. See docs/design.md "스타일 기준".
internal object HomeColors {
    val Base = Color(0xFF030E1F)
    val Text = Color(0xFFEAF4FF)
    val TextStrong = Color(0xFFF4FAFF)
    val TextSecondary = Color(0xFFB9D2EC)
    val TextTertiary = Color(0xFFA9C4E0)
    val NavIdle = Color(0xFFC6DAF0)
    val NavActive = Color(0xFF9BE8FB)
    val Link = Color(0xFF8FDDF7)
    val Glow = Color(0xFF5FE0FB)
    val Caution = Color(0xFFFFD3B3)
    val ChipText = Color(0xFFA9EDFC)
    val CardBorder = Color(0x42A0CDFF)
    val HeroBorder = Color(0x4DA0CDFF)
    val ButtonTop = Color(0xFF2F9BEA)
    val ButtonBottom = Color(0xFF0B5FC4)
}

private val motionColors = darkColorScheme(
    primary = HomeColors.ButtonTop,
    onPrimary = Color.White,
    primaryContainer = HomeColors.ButtonBottom,
    onPrimaryContainer = Color.White,
    secondary = HomeColors.Link,
    onSecondary = Color(0xFF03223F),
    secondaryContainer = Color(0xFF16406E),
    onSecondaryContainer = Color(0xFFDDF2FF),
    background = HomeColors.Base,
    onBackground = HomeColors.Text,
    surface = Color(0xFF0A1B33),
    onSurface = HomeColors.Text,
    surfaceVariant = Color(0xFF13294A),
    onSurfaceVariant = HomeColors.TextSecondary,
    outline = Color(0xFF4A6A90),
    outlineVariant = Color(0xFF2A4568),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF5C1A10),
)

/** Dark glass colors for every screen. Sizes stay at the Material defaults outside the home screen. */
@Composable
fun MotionCareTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = motionColors, content = content)

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

/** The screen background: three soft blue glows over the base color. */
internal fun Modifier.homeBackground() = drawBehind {
    drawRect(HomeColors.Base)
    fun glow(color: Color, x: Float, y: Float, radius: Float) = drawRect(
        Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), Offset(size.width * x, size.height * y), radius),
    )
    glow(Color(0xFF0B447F), 0.82f, 0.04f, size.width * 0.55f)
    glow(Color(0xFF07365F), 0.04f, 0.58f, size.width * 0.45f)
    glow(Color(0xFF06396B), 0.60f, 1.04f, size.width * 0.45f)
}

private val glassFill = Brush.linearGradient(
    0f to Color(0x297AB0F0),
    0.55f to Color(0x1A28548C),
    1f to Color(0x4D0C2242),
    start = Offset(0f, 0f),
    end = Offset(900f, 1300f),
)

/** The glass card surface of the design: a faint blue gradient with a light edge. */
internal fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp)) =
    clip(shape).background(glassFill).border(1.5.dp, HomeColors.CardBorder, shape)

internal val heroFill = Brush.linearGradient(
    0f to Color(0xFF06264C),
    0.55f to Color(0xFF04193A),
    1f to Color(0xFF03122B),
    start = Offset(0f, 0f),
    end = Offset(1600f, 900f),
)

internal val primaryFill = Brush.verticalGradient(listOf(HomeColors.ButtonTop, HomeColors.ButtonBottom))

internal val startFill = Brush.radialGradient(
    0f to Color(0xFF6FD9F8),
    0.48f to Color(0xFF1191E2),
    1f to Color(0xFF0A4CB0),
)

internal val titleBrush = Brush.verticalGradient(0.2f to Color.White, 1f to Color(0xFF8FD4F5))
