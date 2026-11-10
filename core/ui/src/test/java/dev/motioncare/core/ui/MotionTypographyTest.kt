package dev.motioncare.core.ui

import androidx.compose.ui.text.style.LineBreak
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTypographyTest {
    // Every Material style, so a Text without its own style still wraps Korean between words.
    @Test fun `every style wraps between words`() {
        val typography = motionTypography(WideType)
        val styles = listOf(
            typography.displayLarge, typography.displayMedium, typography.displaySmall,
            typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
            typography.titleLarge, typography.titleMedium, typography.titleSmall,
            typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
            typography.labelLarge, typography.labelMedium, typography.labelSmall,
        )
        styles.forEach { assertEquals(LineBreak.WordBreak.Phrase, it.lineBreak.wordBreak) }
    }
}
