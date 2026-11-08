package dev.motioncare.feature.history.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BText = Color(0xFFE3F3FF)
private val BSubtle = Color(0xFFA0BDD6)
private val BCyan = Color(0xFFB0E6FF)
private val BLine = Color(0x38AACFEE)

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
        modifier.background(Brush.linearGradient(listOf(Color(0x352B547F), Color(0x20172D4B))), RoundedCornerShape(26.dp))
            .border(1.dp, BLine, RoundedCornerShape(26.dp)).padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), content = content,
    )
}

/** The tilted pane carries the surface, while text and touch bounds stay upright. */
@Composable
internal fun BActionRibbon(label: String, title: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = GenericShape { size, _ ->
        val cut = minOf(size.width * .04f, size.height * .2f)
        moveTo(cut * 2, 0f)
        lineTo(size.width - cut, 0f)
        quadraticTo(size.width, 0f, size.width - cut / 2, cut)
        lineTo(size.width - cut * 2, size.height - cut)
        quadraticTo(size.width - cut * 2.5f, size.height, size.width - cut * 3, size.height)
        lineTo(cut, size.height)
        quadraticTo(0f, size.height, cut / 2, size.height - cut)
        lineTo(cut * 1.5f, cut)
        quadraticTo(cut * 1.7f, 0f, cut * 2, 0f)
        close()
    }
    Surface(onClick = onClick, enabled = enabled, shape = shape, color = Color.Transparent,
        border = BorderStroke(1.dp, if (enabled) Color(0xAAB1E6FF) else BLine), modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)) {
        Row(Modifier.background(Brush.linearGradient(listOf(Color(0x554F80AB), Color(0x55395688), Color(0x44233A61)))).padding(30.dp, 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, color = BSubtle, fontSize = 13.sp)
                Text(title, color = if (enabled) BText else BSubtle, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
            Text("↗", color = BCyan, fontSize = 25.sp)
        }
    }
}

@Composable
private fun BMetrics(metrics: List<BMetric>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        metrics.forEach { metric ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(metric.label, color = BSubtle, fontSize = 14.sp)
                Text(metric.value ?: "기록 없음", color = BCyan, fontSize = 29.sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun BReportPanel(state: BReportUi, onFeedback: () -> Unit, modifier: Modifier = Modifier, speedChart: (@Composable () -> Unit)? = null) {
    BGlass(modifier.fillMaxWidth()) {
        Text(state.title, color = BText, fontSize = 29.sp, fontWeight = FontWeight.Medium)
        Text(state.participants, color = BSubtle, fontSize = 15.sp)
        BMetrics(state.metrics)
        HorizontalDivider(color = BLine)
        if (speedChart == null) Text("주행 속도 · km/h", color = BText, fontSize = 19.sp)
        if (speedChart != null) speedChart()
        else if (state.speedSegments.isEmpty()) Text("표시할 유효 속도 구간이 없어요.", color = BSubtle, fontSize = 16.sp)
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
        Text("관찰된 움직임", color = BText, fontSize = 19.sp)
        state.movements.chunked(3).forEach { BMetrics(it) }
        HorizontalDivider(color = BLine)
        BoxWithConstraints {
            if (maxWidth >= 680.dp) Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
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
        Text("동승자의 응답", color = BSubtle, fontSize = 14.sp)
        Text(feedback ?: "미응답", color = BCyan, fontSize = 27.sp)
        OutlinedButton(onClick = onFeedback, modifier = Modifier.heightIn(min = 56.dp)) { Text("평가 남기기 · 수정 ↗") }
    }
}

@Composable
private fun BReportGuidance(guidance: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("주행 안내", color = BSubtle, fontSize = 14.sp)
        Text(guidance ?: "표시된 안내 없음", color = BText, fontSize = 19.sp)
    }
}

@Composable
internal fun BPassengerProfilePanel(
    name: String, role: String, preferences: List<BMetric>, moments: List<BMoment>,
    portrait: @Composable () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(25.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) { portrait() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(role, color = BSubtle, fontSize = 14.sp)
                Text(name, color = BText, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.heightIn(min = 56.dp)) { Text("프로필 편집") }
        }
        Text("${name}에게 편안한 이동을.", color = BText, fontSize = 27.sp)
        preferences.chunked(3).forEach { BMetrics(it) }
        BGlass(Modifier.fillMaxWidth()) {
            Text("조금 더 신경 쓸 순간", color = BText, fontSize = 20.sp)
            if (moments.isEmpty()) Text("아직 등록하거나 응답한 정보가 없어요.", color = BSubtle, fontSize = 16.sp)
            moments.forEachIndexed { index, moment ->
                HorizontalDivider(color = BLine)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("%02d".format(index + 1), color = BCyan, fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(moment.title, color = BText, fontSize = 19.sp)
                        Text(moment.detail, color = BSubtle, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** State is caller-owned. This component neither stores feedback nor turns a skipped answer into a rating. */
@Composable
internal fun BFeedbackPanel(
    name: String, rating: Int?, reasons: List<BReason>, selectedReasons: Set<String>, saving: Boolean,
    onRating: (Int) -> Unit, onReason: (String) -> Unit, onSave: () -> Unit, onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("${name}의 한마디", color = BText, fontSize = 25.sp)
        Text("01 / FEELING", color = BSubtle, fontSize = 12.sp)
        Text(when (rating) { 1 -> "많이 불편했어요"; 2 -> "조금 불편했어요"; 3 -> "보통이었어요"; 4 -> "편안했어요"; 5 -> "아주 편안했어요"; else -> "이동은 어땠나요?" }, color = BCyan, fontSize = 29.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (1..5).forEach { score ->
                val selected = rating != null && score <= rating
                Surface(onClick = { onRating(score) }, enabled = !saving, shape = RoundedCornerShape(20.dp),
                    color = if (selected) Color(0x407BBCE8) else Color(0x184D7FAC), border = BorderStroke(1.dp, if (selected) BCyan else BLine),
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp).semantics { contentDescription = "${score}점" }) {
                    Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) { Text(if (selected) "★" else "☆", color = if (selected) BCyan else BSubtle, fontSize = 38.sp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("많이 불편했어요", color = BSubtle, fontSize = 12.sp)
            Text("아주 편안했어요", color = BSubtle, fontSize = 12.sp)
        }
        HorizontalDivider(color = BLine)
        Text("02 / MOMENTS", color = BSubtle, fontSize = 12.sp)
        Text("불편했던 순간이 있었나요?", color = BText, fontSize = 22.sp)
        reasons.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { reason -> FilterChip(selected = reason.id in selectedReasons, onClick = { onReason(reason.id) }, enabled = !saving,
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0x184D7FAC), labelColor = BSubtle,
                        selectedContainerColor = Color(0x407BBCE8), selectedLabelColor = BText),
                    border = BorderStroke(1.dp, if (reason.id in selectedReasons) BCyan else BLine),
                    label = { Text(reason.label) }, modifier = Modifier.heightIn(min = 56.dp)) }
            }
        }
        BActionRibbon("이번 이동의 기록에", if (saving) "저장 중이에요" else "나의 응답 남기기", enabled = rating in 1..5 && !saving, onClick = onSave)
        OutlinedButton(onClick = onSkip, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("건너뛰기") }
    }
}
