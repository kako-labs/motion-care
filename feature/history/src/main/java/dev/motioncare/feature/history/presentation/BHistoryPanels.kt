package dev.motioncare.feature.history.presentation

import dev.motioncare.core.ui.motionType
import dev.motioncare.core.ui.MotionSecondaryButton
import dev.motioncare.core.ui.MotionChip
import dev.motioncare.core.ui.MotionSpace
import dev.motioncare.feature.history.ui.SectionTitle
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import dev.motioncare.core.ui.MotionIcons
import dev.motioncare.feature.history.ui.MotionSelected
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import dev.motioncare.core.ui.MotionBrushes
import dev.motioncare.core.ui.MotionPalette
import dev.motioncare.core.ui.glass
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.feature.history.ui.MotionText
import dev.motioncare.feature.history.ui.MotionSubtle
import dev.motioncare.feature.history.ui.MotionCyan
import dev.motioncare.feature.history.ui.MotionLine

private val BText = MotionText
private val BSubtle = MotionSubtle
private val BCyan = MotionCyan
private val BLine = MotionLine

/** Presentation values only. The existing history ViewModel remains the source of truth. */
internal data class BMetric(val label: String, val value: String?)
internal data class BMoment(val title: String, val detail: String)
internal data class BReason(val id: String, val label: String)
internal data class BReportUi(
    val title: String,
    val participants: String,
    val sourceLabel: String,
    val metrics: List<BMetric>,
    val movements: List<BMetric>,
    val feedback: String?,
    val guidance: String?,
    /** Already-normalized segments from the caller; gaps must remain separate. No analysis is performed here. */
    val speedSegments: List<List<Offset>> = emptyList(),
)

@Composable
internal fun BGlass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.glass(RoundedCornerShape(24.dp)).padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), content = content,
    )
}

/**
 * An action card that opens something. It was a tilted pane in the B design; the tilt had uneven sides and
 * corners and read as a warped box, so it now uses Home2's rounded card with a stronger glass tint.
 */
@Composable
internal fun BActionRibbon(label: String, title: String, enabled: Boolean = true, compact: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (compact) 20.dp else 24.dp)
    Surface(onClick = onClick, enabled = enabled, shape = shape, color = Color.Transparent,
        border = BorderStroke(1.5.dp, if (enabled) MotionPalette.ActionBorder else BLine), modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
        Row(Modifier.background(MotionBrushes.Action).padding(horizontal = if (compact) 20.dp else 28.dp, vertical = if (compact) 18.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, color = BSubtle, fontSize = motionType.label)
                Text(title, color = if (enabled) BText else BSubtle, fontSize = motionType.section, fontWeight = FontWeight.Medium)
            }
            Icon(MotionIcons.ArrowUpRight, contentDescription = null, tint = BCyan, modifier = Modifier.size(26.dp))
        }
    }
}

/** One row of a grid; [columns] keeps a short last row in the same columns as the rows above it. */
@Composable
private fun BMetrics(metrics: List<BMetric>, modifier: Modifier = Modifier, columns: Int = metrics.size) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MotionSpace.Large)) {
        metrics.forEach { metric ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MotionSpace.Small)) {
                Text(metric.label, color = BSubtle, fontSize = motionType.label)
                Text(metric.value ?: "기록 없음", color = BText, fontSize = motionType.headline, fontWeight = FontWeight.Bold)
            }
        }
        repeat(columns - metrics.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
internal fun BReportPanel(state: BReportUi, onFeedback: () -> Unit, modifier: Modifier = Modifier, speedChart: (@Composable () -> Unit)? = null) {
    BGlass(modifier.fillMaxWidth()) {
        Text(state.title, color = BText, fontSize = motionType.headline, fontWeight = FontWeight.Medium)
        Text(state.participants, color = BSubtle, fontSize = motionType.caption)
        BMetrics(state.metrics)
        HorizontalDivider(color = BLine)
        if (speedChart == null) SectionTitle("주행 속도 · km/h")
        if (speedChart != null) speedChart()
        else if (state.speedSegments.isEmpty()) Text("표시할 유효 속도 구간이 없어요.", color = BSubtle, fontSize = motionType.label)
        else Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "유효한 속도 구간의 흐름. 신호 공백은 이어 그리지 않음" }) {
            (0..2).forEach { line ->
                val y = size.height * line / 2
                drawLine(BLine, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            state.speedSegments.filter { it.size >= 2 && it.all { point -> point.x.isFinite() && point.y.isFinite() } }.forEach { points ->
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = point.x.coerceIn(0f, 1f) * size.width
                    val y = (1 - point.y.coerceIn(0f, 1f)) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, BCyan, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        SectionTitle("관찰된 움직임")
        state.movements.chunked(3).forEach { BMetrics(it, columns = 3) }
        HorizontalDivider(color = BLine)
        BoxWithConstraints {
            if (maxWidth >= 680.dp) Row(horizontalArrangement = Arrangement.spacedBy(MotionSpace.XLarge)) {
                BReportResponse(state.feedback, onFeedback, Modifier.weight(1f))
                BReportGuidance(state.guidance, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                BReportResponse(state.feedback, onFeedback, Modifier.fillMaxWidth())
                BReportGuidance(state.guidance, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BReportResponse(feedback: String?, onFeedback: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("동승자의 응답", color = BSubtle, fontSize = motionType.label)
        Text(feedback ?: "미응답", color = BText, fontSize = motionType.headline, fontWeight = FontWeight.Bold)
        MotionSecondaryButton("평가 남기기 · 수정", onFeedback, icon = MotionIcons.ArrowUpRight)
    }
}

@Composable
private fun BReportGuidance(guidance: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("주행 안내", color = BSubtle, fontSize = motionType.label)
        Text(guidance ?: "표시된 안내 없음", color = BText, fontSize = motionType.body)
    }
}

@Composable
internal fun BPassengerProfilePanel(
    name: String, role: String, preferences: List<BMetric>, moments: List<BMoment>,
    portrait: @Composable () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier,
    showIdentity: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(25.dp)) {
        if (showIdentity) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) { portrait() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(role, color = BSubtle, fontSize = motionType.caption)
                Text(name, color = BText, fontSize = motionType.headline, fontWeight = FontWeight.Medium)
            }
            MotionSecondaryButton("프로필 편집", onEdit)
        }
        Text("${name}에게 편안한 이동을.", color = BText, fontSize = motionType.headline)
        preferences.chunked(3).forEach { BMetrics(it, columns = 3) }
        BGlass(Modifier.fillMaxWidth()) {
            SectionTitle("조금 더 신경 쓸 순간")
            if (moments.isEmpty()) Text("아직 등록하거나 응답한 정보가 없어요.", color = BSubtle, fontSize = motionType.label)
            moments.forEachIndexed { index, moment ->
                HorizontalDivider(color = BLine)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("%02d".format(index + 1), color = BCyan, fontSize = motionType.caption)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(moment.title, color = BText, fontSize = motionType.body)
                        Text(moment.detail, color = BSubtle, fontSize = motionType.caption)
                    }
                }
            }
        }
    }
}

/** State is caller-owned. This component neither stores feedback nor turns a skipped answer into a rating. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BFeedbackPanel(
    name: String, rating: Int?, reasons: List<BReason>, selectedReasons: Set<String>, saving: Boolean,
    onRating: (Int) -> Unit, onReason: (String) -> Unit, onSave: () -> Unit, onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("${name}의 한마디", color = BText, fontSize = motionType.section)
        Text(when (rating) { 1 -> "많이 불편했어요"; 2 -> "조금 불편했어요"; 3 -> "보통이었어요"; 4 -> "편안했어요"; 5 -> "아주 편안했어요"; else -> "이동은 어땠나요?" }, color = BCyan, fontSize = motionType.headline)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val starWidth = if (maxWidth < 320.dp) 56.dp else (maxWidth - 40.dp) / 5
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..5).forEach { score ->
                    val selected = rating != null && score <= rating
                    Surface(onClick = { onRating(score) }, enabled = !saving, shape = RoundedCornerShape(14.dp),
                        color = if (selected) MotionSelected else Color(0x06FFFFFF), border = BorderStroke(1.dp, if (selected) BCyan.copy(.45f) else BLine),
                        modifier = Modifier.width(starWidth).heightIn(min = 96.dp).semantics { contentDescription = "${score}점" }) {
                        Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) { Text(if (selected) "★" else "☆", color = if (selected) BCyan else BSubtle, fontSize = motionType.title) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("많이 불편했어요", color = BSubtle, fontSize = motionType.label)
            Text("아주 편안했어요", color = BSubtle, fontSize = motionType.label)
        }
        HorizontalDivider(color = BLine)
        SectionTitle("불편했던 순간이 있었나요?")
        // Three car-size choices to a row across the panel's width.
        reasons.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MotionSpace.Medium)) {
                row.forEach { reason ->
                    MotionChip(reason.label, reason.id in selectedReasons, { onReason(reason.id) }, Modifier.weight(1f), enabled = !saving)
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        BActionRibbon("이번 이동의 기록에", if (saving) "저장 중이에요" else "나의 응답 남기기", enabled = rating in 1..5 && !saving, onClick = onSave)
        MotionSecondaryButton("건너뛰기", onSkip, Modifier.fillMaxWidth(), enabled = !saving)
    }
}
