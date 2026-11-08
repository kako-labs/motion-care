package dev.motioncare.feature.history.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.core.model.PersonKind
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.feature.history.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Trip times read the same on every screen, whatever the device locale: "11월 5일 (목) 오후 2:48". */
fun tripTime(epochMs: Long): String = SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREAN).format(Date(epochMs))
fun tripDate(epochMs: Long): String = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Date(epochMs))

val MotionBackground = Color(0xFF050F20)
val MotionPanel = Color(0xCC102844)
val MotionPanelLight = Color(0xAA1B426A)
val MotionCyan = Color(0xFFB0E6FF)
val MotionBlue = Color(0xFF278DDB)
val MotionText = Color(0xFFEAF4FF)
val MotionSubtle = Color(0xFFAEC3DA)
val MotionMuted = Color(0xFF829BB7)
val MotionLine = Color(0x3DA4CDF5)

private val motionColors = darkColorScheme(
    primary = MotionCyan, onPrimary = Color(0xFF03111F), secondary = MotionBlue,
    background = MotionBackground, onBackground = MotionText, surface = MotionPanel,
    onSurface = MotionText, outline = MotionLine, error = Color(0xFFFFA8A8),
    secondaryContainer = Color(0xFF1B426A), onSecondaryContainer = MotionText,
    surfaceVariant = Color(0xFF16365A), onSurfaceVariant = MotionSubtle,
    surfaceContainerHigh = Color(0xFF0E2A48), outlineVariant = MotionLine,
)

/** The host app keeps a light theme, so every history surface brings its own dark scheme and text color. */
@Composable fun MotionCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = motionColors) {
        CompositionLocalProvider(LocalContentColor provides MotionText, content = content)
    }
}

@Composable fun MotionCareBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    MotionCareTheme {
        Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF061325), Color(0xFF0C2F55), Color(0xFF050F20)))), content = content)
    }
}

@Composable fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    MotionCareTheme {
        Column(modifier.background(Brush.linearGradient(listOf(Color(0x352B547F), Color(0x40172D4B))), RoundedCornerShape(22.dp))
            .border(1.dp, MotionLine, RoundedCornerShape(22.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable fun ScreenTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onBack != null) RoundIconButton("‹", onBack)
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, color = MotionSubtle, fontSize = 17.sp) }
        }
        action?.invoke()
    }
}

@Composable fun RoundIconButton(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = MotionPanelLight, border = androidx.compose.foundation.BorderStroke(1.dp, MotionLine),
        modifier = modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(symbol, fontSize = 34.sp, lineHeight = 34.sp, color = MotionText) }
    }
}

private val adultFaces = listOf(R.drawable.mc_avatar_adult, R.drawable.mc_avatar_woman)
private val childFaces = listOf(R.drawable.mc_avatar_child, R.drawable.mc_avatar_boy)

/** Same face for the same person every time; children always get a child face. */
fun avatarFor(person: PersonProfile): Int {
    val faces = if (person.kind == PersonKind.CHILD) childFaces else adultFaces
    return when (person.id.value) { "driver" -> R.drawable.mc_face_man; "doyun" -> R.drawable.mc_face_boy; "jiyoung" -> R.drawable.mc_face_woman; "seoa" -> R.drawable.mc_face_girl; else -> faces[Math.floorMod(person.id.hashCode(), faces.size)] }
}

@Composable fun KindFace(kind: PersonKind, modifier: Modifier = Modifier) {
    FaceImage(if (kind == PersonKind.CHILD) childFaces.first() else adultFaces.first(), modifier)
}

@Composable fun PersonAvatar(person: PersonProfile, modifier: Modifier = Modifier) {
    FaceImage(avatarFor(person), modifier, person.displayName)
}

@Composable private fun FaceImage(face: Int, modifier: Modifier, description: String? = null) {
    Image(painterResource(face), description, modifier.clip(CircleShape).border(2.dp, MotionCyan.copy(.75f), CircleShape),
        contentScale = ContentScale.Crop)
}

@Composable fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MotionBlue, contentColor = Color.White), shape = RoundedCornerShape(18.dp)) {
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
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(minOf(14.dp.toPx(), width / 4), minOf(14.dp.toPx(), width / 4)))
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
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Text(label, color = MotionSubtle, fontSize = 15.sp)
    }
}