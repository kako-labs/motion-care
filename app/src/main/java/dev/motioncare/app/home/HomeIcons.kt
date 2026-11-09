package dev.motioncare.app.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons drawn from the SVG paths of the Home2 board, on a 24-unit grid. Tinted where they are used. */
internal object HomeIcons {
    val Home = filled("M12 3l9 8h-3v9h-4v-6h-4v6H6v-9H3z")
    val Play = filled("M8 5v14l11-7z")
    val Sparkle = filled("M12 2l2.2 7.8L22 12l-7.8 2.2L12 22l-2.2-7.8L2 12l7.8-2.2z")
    val Clock = outlined("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M12 7v5l3.5 2")
    val Person = outlined("M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "M4 21a8 8 0 0 1 16 0")
    val Air = outlined("M3 9h11a3 3 0 1 0-3-3M3 14h16a3 3 0 1 1-3 3M3 19h7")
    val Cloud = outlined("M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9.5 4.3 4.3 0 0 1 17.5 18z")
    val Thermometer = outlined("M14 14.5V5a2 2 0 1 0-4 0v9.5a4 4 0 1 0 4 0z", "M12 9v8")
    val Recirculate = outlined("M4 12a8 8 0 0 1 13.7-5.6L20 9M20 4v5h-5M20 12a8 8 0 0 1-13.7 5.6L4 15M4 20v-5h5")
    val Chevron = outlined("M9 5l7 7-7 7", width = 3f)
    // Home2's vehicle tab icon, on its own 34 x 26 grid.
    val Car = ImageVector.Builder(defaultWidth = 34.dp, defaultHeight = 26.dp, viewportWidth = 34f, viewportHeight = 26f)
        .addPath(addPathNodes("M3 15l3-8c.6-1.6 1.6-3 4-3h14c2.4 0 3.4 1.4 4 3l3 8v7H3z"), stroke = SolidColor(Color.White),
            strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round)
        .addPath(addPathNodes("M10.8 17a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0M26.8 17a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0"),
            fill = SolidColor(Color.White))
        .build()

    private fun filled(path: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.White))
        .build()

    private fun outlined(vararg paths: String, width: Float = 2f) = ImageVector.Builder(defaultWidth = 24.dp,
        defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach {
                addPath(addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }
        .build()
}
