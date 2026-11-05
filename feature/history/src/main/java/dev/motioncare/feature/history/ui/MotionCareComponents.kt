package dev.motioncare.feature.history.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.core.model.PersonProfile
import kotlin.math.max

val MotionBackground = Color(0xFF050F20)
val MotionPanel = Color(0xCC102844)
val MotionPanelLight = Color(0xAA1B426A)
val MotionCyan = Color(0xFF7CDEF6)
val MotionBlue = Color(0xFF278DDB)
val MotionText = Color(0xFFEAF4FF)
val MotionSubtle = Color(0xFFAEC3DA)
val MotionMuted = Color(0xFF829BB7)
val MotionLine = Color(0x3DA4CDF5)

private val motionColors = darkColorScheme(
    primary = MotionCyan, onPrimary = Color(0xFF03111F), secondary = MotionBlue,
    background = MotionBackground, onBackground = MotionText, surface = MotionPanel,
    onSurface = MotionText, outline = MotionLine, error = Color(0xFFFFA8A8),
)

@Composable fun MotionCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = motionColors, content = content)
}

@Composable fun MotionCareBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF061325), Color(0xFF0C2F55), Color(0xFF050F20)))), content = content)
}

@Composable fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Brush.linearGradient(listOf(MotionPanelLight, MotionPanel)), RoundedCornerShape(22.dp))
        .border(1.dp, MotionLine, RoundedCornerShape(22.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable fun ScreenTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onBack != null) FilledTonalButton(onClick = onBack, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
            Text("‹", fontSize = 40.sp, lineHeight = 40.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
            subtitle?.let { Text(it, color = MotionSubtle, fontSize = 17.sp) }
        }
        action?.invoke()
    }
}

@Composable fun PersonAvatar(person: PersonProfile, modifier: Modifier = Modifier) {
    val colors = if (person.displayName.hashCode() % 2 == 0) listOf(Color(0xFF52C8E8), Color(0xFF1761A8))
    else listOf(Color(0xFF73A6E8), Color(0xFF304D9A))
    Box(modifier.background(Brush.radialGradient(colors), CircleShape).border(2.dp, MotionCyan.copy(.75f), CircleShape),
        contentAlignment = Alignment.Center) {
        Text(person.displayName.take(1), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

@Composable fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MotionBlue), shape = RoundedCornerShape(18.dp)) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled), shape = RoundedCornerShape(18.dp)) {
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable fun TrendBars(values: List<Double>, modifier: Modifier = Modifier, highlightLast: Boolean = true) {
    val safe = values.ifEmpty { listOf(0.0) }
    val ceiling = max(1.0, safe.maxOrNull() ?: 1.0)
    Canvas(modifier.height(150.dp).fillMaxWidth()) {
        val gap = size.width * .025f
        val width = (size.width - gap * (safe.size + 1)) / safe.size
        safe.forEachIndexed { index, value ->
            val height = (size.height * .8f * (value / ceiling)).toFloat().coerceAtLeast(3f)
            val left = gap + index * (width + gap)
            drawRoundRect(if (highlightLast && index >= safe.size - 3) MotionCyan else MotionMuted.copy(.65f),
                topLeft = Offset(left, size.height - height), size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 3, width / 3))
        }
        drawLine(MotionLine, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
    }
}

@Composable fun LineTrend(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val ceiling = max(1.0, values.maxOrNull() ?: 1.0)
    Canvas(modifier.height(210.dp).fillMaxWidth()) {
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val point = Offset(step * index, size.height - (size.height * .82f * (value / ceiling)).toFloat() - 12f)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            drawCircle(if (index == values.lastIndex) Color.White else MotionCyan, if (index == values.lastIndex) 8f else 5f, point)
        }
        drawPath(path, MotionCyan, style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable fun MetricTile(value: String, label: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Column(modifier.background(if (highlighted) MotionBlue.copy(.34f) else MotionPanel, RoundedCornerShape(18.dp))
        .border(1.dp, if (highlighted) MotionCyan.copy(.8f) else MotionLine, RoundedCornerShape(18.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = MotionSubtle, fontSize = 15.sp)
    }
}