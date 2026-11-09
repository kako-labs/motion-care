package dev.motioncare.feature.history.ui

import androidx.compose.foundation.Canvas
import dev.motioncare.core.ui.MotionIcons
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.core.model.PersonKind
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.core.ui.MotionBrushes
import dev.motioncare.core.ui.MotionPalette
import dev.motioncare.core.ui.MotionTheme
import dev.motioncare.core.ui.glass
import dev.motioncare.core.ui.motionBackground
import dev.motioncare.feature.history.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Trip times read the same on every screen, whatever the device locale: "11월 5일 (목) 오후 2:48". */
fun tripTime(epochMs: Long): String = SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREAN).format(Date(epochMs))
fun tripDate(epochMs: Long): String = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Date(epochMs))

// Shared tokens follow the app-wide Home2 style in core:ui so the home and history screens match. MotionBlue stays
// the history screens' own chart color.
val MotionBackground = MotionPalette.Base
// Small cards and chips sit on glass, so they take the glass tint instead of a solid fill.
val MotionPanel = Color(0x297AB0F0)
val MotionPanelLight = Color(0x3D7AB0F0)
/** A chosen option. Stronger than the glass tint of unchosen ones so the choice reads at a glance. */
val MotionSelected = Color(0x592F9BEA)
val MotionCyan = MotionPalette.Link
val MotionBlue = Color(0xFF578CB4)
val MotionText = MotionPalette.Text
val MotionSubtle = MotionPalette.TextSecondary
val MotionMuted = MotionPalette.TextTertiary
val MotionLine = MotionPalette.CardBorder

/** The history screens use the app-wide theme; kept so callers inside this feature need not change. */
@Composable fun MotionCareTheme(content: @Composable () -> Unit) = MotionTheme(content)

@Composable fun MotionCareBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    MotionCareTheme {
        Box(modifier.fillMaxSize().motionBackground(), content = content)
    }
}

@Composable fun GlassPanel(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    MotionCareTheme {
        Column(modifier.glass(RoundedCornerShape(if (compact) 20.dp else 28.dp)).padding(if (compact) 18.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp), content = content)
    }
}

@Composable fun ScreenTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 600.dp
        // Home2's screen title is bold; the size steps down on narrow windows.
        val big = maxWidth >= 900.dp
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (onBack != null) RoundIconButton("‹", onBack)
                leading?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = if (big) 36.sp else 28.sp, lineHeight = if (big) 44.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold)
                    subtitle?.let { Text(it, color = MotionMuted, fontSize = if (big) 20.sp else 16.sp, lineHeight = if (big) 28.sp else 24.sp) }
                }
                if (!compact) action?.invoke()
            }
            if (compact && action != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { action() }
        }
    }
}

@Composable fun RoundIconButton(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MotionLine),
        modifier = modifier.size(52.dp).glass(CircleShape)
            .semantics { contentDescription = when (symbol) { "‹" -> "뒤로"; "✕" -> "닫기"; else -> symbol } }) {
        Box(contentAlignment = Alignment.Center) {
            when (symbol) {
                "‹" -> Icon(MotionIcons.Back, contentDescription = null, tint = MotionText, modifier = Modifier.size(26.dp))
                "✕" -> Icon(MotionIcons.Close, contentDescription = null, tint = MotionText, modifier = Modifier.size(24.dp))
                else -> Text(symbol, fontSize = 27.sp, lineHeight = 30.sp, color = MotionSubtle)
            }
        }
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
    Image(painterResource(face), description, modifier.clip(CircleShape),
        contentScale = ContentScale.Crop)
}

/** "더 보기" style toggle. No start padding, so its text lines up with the card text above it. */
@Composable fun ExpandToggle(label: String, expanded: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 56.dp),
        contentPadding = PaddingValues(start = 0.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MotionCyan)
        Spacer(Modifier.width(6.dp))
        Icon(if (expanded) MotionIcons.ChevronUp else MotionIcons.ChevronDown, contentDescription = null,
            tint = MotionCyan, modifier = Modifier.size(20.dp))
    }
}

private val ActionShape = RoundedCornerShape(18.dp)

/** Home2's primary button: a blue gradient with a light edge and white bold text. */
@Composable fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp).alpha(if (enabled) 1f else 0.5f)
            .background(MotionBrushes.Primary, ActionShape).border(1.5.dp, MotionPalette.ButtonBorder, ActionShape),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White,
            disabledContainerColor = Color.Transparent, disabledContentColor = Color.White),
        shape = ActionShape) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** The secondary button sits on glass with the card edge. */
@Composable fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp).glass(ActionShape),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MotionLine), shape = ActionShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MotionText)) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    Column(modifier.padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, color = if (highlighted) MotionCyan else MotionText, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Text(label, color = MotionSubtle, fontSize = 15.sp)
    }
}
