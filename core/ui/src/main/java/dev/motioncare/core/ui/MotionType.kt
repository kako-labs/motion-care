package dev.motioncare.core.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One type scale for every screen. The wide set is Home2's sizes on the car window (its px become sp at 160 dpi) and
 * never goes below 18, the smallest pixel height the Pleos font guide recommends. The compact set is for phones.
 * See docs/design.md.
 */
@Immutable
data class MotionType(
    /** The one big statement of a screen, such as the home advice. */
    val display: TextUnit,
    /** Screen titles. */
    val title: TextUnit,
    /** Large numbers and short emphasized results. */
    val headline: TextUnit,
    /** Card and section titles. */
    val section: TextUnit,
    /** A sentence that explains the display or headline above it. */
    val lead: TextUnit,
    /** Reading text, list rows and button text. */
    val body: TextUnit,
    /** Field names, chips, secondary lines and small buttons. */
    val label: TextUnit,
    /** Units, sources and footnotes. The smallest size on any screen. */
    val caption: TextUnit,
)

val WideType = MotionType(
    display = 54.sp, title = 44.sp, headline = 30.sp, section = 26.sp, lead = 26.sp, body = 23.sp, label = 20.sp, caption = 18.sp,
)

val CompactType = MotionType(
    display = 28.sp, title = 28.sp, headline = 20.sp, section = 19.sp, lead = 17.sp, body = 16.sp, label = 14.sp, caption = 13.sp,
)

/** The car window is wide; phones and the 1024-wide demo layouts are compact. */
fun motionTypeFor(width: Dp): MotionType = if (width >= 1100.dp) WideType else CompactType

val LocalMotionType = staticCompositionLocalOf { CompactType }

/** The type scale of the current window. */
val motionType: MotionType
    @Composable @ReadOnlyComposable get() = LocalMotionType.current

/**
 * Korean wraps between words, as it is read, instead of between any two letters. Android applies phrase breaking where
 * the locale supports it and ignores it elsewhere.
 */
val WordLineBreak = LineBreak.Simple.copy(wordBreak = LineBreak.WordBreak.Phrase)

/**
 * Material styles mapped onto the scale so components without an explicit size still match. Line heights are left to
 * the font so a larger size never overlaps the line below.
 */
fun motionTypography(type: MotionType): Typography {
    fun style(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = size, fontWeight = weight, lineBreak = WordLineBreak)
    return Typography(
        displayLarge = style(type.display, FontWeight.ExtraBold),
        displayMedium = style(type.display, FontWeight.ExtraBold),
        displaySmall = style(type.display, FontWeight.ExtraBold),
        headlineLarge = style(type.title, FontWeight.ExtraBold),
        headlineMedium = style(type.headline, FontWeight.Bold),
        headlineSmall = style(type.section, FontWeight.Bold),
        titleLarge = style(type.section, FontWeight.Bold),
        titleMedium = style(type.body, FontWeight.SemiBold),
        titleSmall = style(type.label, FontWeight.SemiBold),
        bodyLarge = style(type.body),
        bodyMedium = style(type.label),
        bodySmall = style(type.caption),
        labelLarge = style(type.label, FontWeight.Bold),
        labelMedium = style(type.caption, FontWeight.SemiBold),
        labelSmall = style(type.caption),
    )
}
