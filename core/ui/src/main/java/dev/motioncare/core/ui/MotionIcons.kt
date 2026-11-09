package dev.motioncare.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons on a 24-unit grid, drawn as vectors so they sit in the middle of their box. Text glyphs such as "‹"
 * follow the font's baseline and drift off center. Each path is balanced around x = 12 and y = 12. Tint where used.
 */
object MotionIcons {
    val Back = line("M15.5 5l-7 7 7 7", width = 2.6f)
    val ChevronRight = line("M8.5 5l7 7-7 7", width = 2.6f)
    val Close = line("M6 6l12 12M18 6L6 18", width = 2.4f)
    val ArrowUpRight = line("M7 17L17 7M9 7h8v8", width = 2.2f)
    val Plus = line("M12 5v14M5 12h14", width = 2f)
    val Check = line("M5 12.5l4.5 4.5L19 7.5", width = 2.6f)
    val ChevronDown = line("M5 8.5l7 7 7-7", width = 2.4f)
    val ChevronUp = line("M5 15.5l7-7 7 7", width = 2.4f)

    private fun line(path: String, width: Float) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        addPathNodes(path), stroke = SolidColor(Color.White), strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()
}
