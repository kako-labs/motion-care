package dev.motioncare.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The one icon set for every screen, on a 24-unit grid. Home2's icons come from the board's SVG paths; the rest are
 * line icons drawn to match. Vectors sit in the middle of their box, where text glyphs such as "‹" follow the font's
 * baseline and drift off center. Tint where used.
 */
object MotionIcons {
    // Navigation and actions
    val Back = line("M15.5 5l-7 7 7 7", width = 2.6f)
    val ChevronRight = line("M8.5 5l7 7-7 7", width = 2.6f)
    val ChevronDown = line("M5 8.5l7 7 7-7", width = 2.4f)
    val ChevronUp = line("M5 15.5l7-7 7 7", width = 2.4f)
    val Close = line("M6 6l12 12M18 6L6 18", width = 2.4f)
    val ArrowUpRight = line("M7 17L17 7M9 7h8v8", width = 2.2f)
    val ArrowRight = line("M4 12h15M13 6l6 6-6 6", width = 2.2f)
    val ArrowUp = line("M12 20V5M6 11l6-6 6 6", width = 2.4f)
    val ArrowDown = line("M12 4v15M6 13l6 6 6-6", width = 2.4f)
    val Plus = line("M12 5v14M5 12h14", width = 2f)
    val Check = line("M5 12.5l4.5 4.5L19 7.5", width = 2.6f)

    // Home2 board
    val Home = filled("M12 3l9 8h-3v9h-4v-6h-4v6H6v-9H3z")
    val Play = filled("M8 5v14l11-7z")
    val Sparkle = filled("M12 2l2.2 7.8L22 12l-7.8 2.2L12 22l-2.2-7.8L2 12l7.8-2.2z")
    val Star = filled("M12 2.8l2.8 5.9 6.4.8-4.7 4.4 1.2 6.4L12 17.2l-5.7 3.1 1.2-6.4-4.7-4.4 6.4-.8z")
    val Clock = line("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M12 7v5l3.5 2")
    val Person = line("M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "M4 21a8 8 0 0 1 16 0")
    val Air = line("M3 9h11a3 3 0 1 0-3-3M3 14h16a3 3 0 1 1-3 3M3 19h7")
    val Cloud = line("M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9.5 4.3 4.3 0 0 1 17.5 18z")
    val Thermometer = line("M14 14.5V5a2 2 0 1 0-4 0v9.5a4 4 0 1 0 4 0z", "M12 9v8")
    val Recirculate = line("M4 12a8 8 0 0 1 13.7-5.6L20 9M20 4v5h-5M20 12a8 8 0 0 1-13.7 5.6L4 15M4 20v-5h5")

    /** Home2's vehicle tab icon, on its own 34 x 26 grid. */
    val Car: ImageVector = ImageVector.Builder(defaultWidth = 34.dp, defaultHeight = 26.dp, viewportWidth = 34f, viewportHeight = 26f)
        .addPath(addPathNodes("M3 15l3-8c.6-1.6 1.6-3 4-3h14c2.4 0 3.4 1.4 4 3l3 8v7H3z"), stroke = SolidColor(Color.White),
            strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round)
        .addPath(addPathNodes("M10.8 17a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0M26.8 17a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0"),
            fill = SolidColor(Color.White))
        .build()

    // Driving patterns, one shape each so a tile reads at a glance.
    val RapidStart = line("M6 12l6-6 6 6M6 19l6-6 6 6", width = 2.4f)
    val HardStop = line("M6 4l6 6 6-6M6 11l6 6 6-6M5 21h14", width = 2.4f)
    val SpeedChange = line("M2.5 12c2.4-6 4.8-6 7.2 0s4.8 6 7.2 0 3.6-4.5 4.6-4.5", width = 2.4f)
    val FastTurn = line("M20 12a8 8 0 1 1-2.6-5.9M20 3.5V9h-5.5", width = 2.4f)
    val Sway = line("M4 8h15M15.5 4.5L19 8l-3.5 3.5M20 16H5M8.5 12.5L5 16l3.5 3.5", width = 2.2f)

    private fun filled(path: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.White))
        .build()

    private fun line(vararg paths: String, width: Float = 2f) = ImageVector.Builder(defaultWidth = 24.dp,
        defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach {
                addPath(addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }
        .build()
}
