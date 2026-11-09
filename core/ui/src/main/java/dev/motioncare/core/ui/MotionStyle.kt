package dev.motioncare.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Colors of the Home2 board in the second design round, shared by every screen. See docs/design.md "스타일 기준". */
object MotionPalette {
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
    val ButtonBorder = Color(0xA6AAE1FF)
    val ActionBorder = Color(0x73A0D8FF)
}

private val motionColors = darkColorScheme(
    primary = MotionPalette.ButtonTop,
    onPrimary = Color.White,
    primaryContainer = MotionPalette.ButtonBottom,
    onPrimaryContainer = Color.White,
    secondary = MotionPalette.Link,
    onSecondary = Color(0xFF03223F),
    secondaryContainer = Color(0xFF16406E),
    onSecondaryContainer = Color(0xFFDDF2FF),
    background = MotionPalette.Base,
    onBackground = MotionPalette.Text,
    surface = Color(0xFF0A1B33),
    onSurface = MotionPalette.Text,
    surfaceVariant = Color(0xFF13294A),
    onSurfaceVariant = MotionPalette.TextSecondary,
    surfaceContainerHigh = Color(0xFF0E2A48),
    outline = Color(0xFF4A6A90),
    outlineVariant = Color(0xFF2A4568),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF5C1A10),
)

/** Dark glass colors for every screen. Text sizes stay at the Material defaults; screens set their own. */
@Composable
fun MotionTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = motionColors) {
    CompositionLocalProvider(LocalContentColor provides MotionPalette.Text, content = content)
}

/** The screen background: three soft blue glows over the base color. */
fun Modifier.motionBackground() = drawBehind {
    drawRect(MotionPalette.Base)
    fun glow(color: Color, x: Float, y: Float, radius: Float) = drawRect(
        Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), Offset(size.width * x, size.height * y), radius),
    )
    glow(Color(0xFF0B447F), 0.82f, 0.04f, size.width * 0.55f)
    glow(Color(0xFF07365F), 0.04f, 0.58f, size.width * 0.45f)
    glow(Color(0xFF06396B), 0.60f, 1.04f, size.width * 0.45f)
}

/** Brushes of the design's surfaces and buttons. */
object MotionBrushes {
    val Glass = Brush.linearGradient(
        0f to Color(0x297AB0F0),
        0.55f to Color(0x1A28548C),
        1f to Color(0x4D0C2242),
        start = Offset(0f, 0f),
        end = Offset(900f, 1300f),
    )
    val Hero = Brush.linearGradient(
        0f to Color(0xFF06264C),
        0.55f to Color(0xFF04193A),
        1f to Color(0xFF03122B),
        start = Offset(0f, 0f),
        end = Offset(1600f, 900f),
    )
    /** A slightly stronger glass for panels that open something, such as the history screens' tilted ribbons. */
    val Action = Brush.linearGradient(
        0f to Color(0x3D7AB0F0),
        0.55f to Color(0x2B28548C),
        1f to Color(0x590C2242),
        start = Offset(0f, 0f),
        end = Offset(900f, 400f),
    )
    val Primary = Brush.verticalGradient(listOf(MotionPalette.ButtonTop, MotionPalette.ButtonBottom))
    val Start = Brush.radialGradient(0f to Color(0xFF6FD9F8), 0.48f to Color(0xFF1191E2), 1f to Color(0xFF0A4CB0))
    val Title = Brush.verticalGradient(0.2f to Color.White, 1f to Color(0xFF8FD4F5))
}

/** The glass card surface of the design: a faint blue gradient with a light edge. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp)) =
    clip(shape).background(MotionBrushes.Glass).border(1.5.dp, MotionPalette.CardBorder, shape)
