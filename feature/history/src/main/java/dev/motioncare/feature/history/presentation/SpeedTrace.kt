package dev.motioncare.feature.history.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.core.model.*
import dev.motioncare.feature.history.ui.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

internal data class TraceGap(val startMs: Long, val endMs: Long)
internal data class SpeedTrace(
    val startMs: Long,
    val endMs: Long,
    val segments: List<List<SpeedSample>>,
    val gaps: List<TraceGap>,
) {
    /** Select a stored sample only. Never synthesize a speed inside missing data. */
    fun sampleAt(elapsedMs: Long): SpeedSample? {
        val segment = segments.firstOrNull { elapsedMs in it.first().elapsedMs..it.last().elapsedMs } ?: return null
        return segment.minByOrNull { abs(it.elapsedMs - elapsedMs) }
    }
}

/** Presentation only: observations come from storage; this does not run a detector. */
internal fun speedTrace(snapshot: TripSnapshot, observation: Observation? = null): SpeedTrace {
    val duration = (snapshot.trip.endedAtEpochMs?.minus(snapshot.trip.startedAtEpochMs)
        ?: snapshot.samples.lastOrNull()?.elapsedMs ?: 0L).coerceAtLeast(1)
    val start = observation?.let { (it.startMs - 24_000).coerceAtLeast(0).coerceAtMost(duration) } ?: 0L
    val end = observation?.let { (it.endMs + 36_000).coerceAtMost(duration).coerceAtLeast(start + 1) } ?: duration
    val segments = mutableListOf<MutableList<SpeedSample>>()
    var previous: SpeedSample? = null
    for (sample in snapshot.samples.filter { it.elapsedMs in start..end }) {
        if (sample.quality != Quality.VALID || sample.metersPerSecond == null) { previous = null; continue }
        if (previous == null || sample.elapsedMs - previous.elapsedMs > 5_000) segments.add(mutableListOf())
        segments.last().add(sample)
        previous = sample
    }
    val gaps = mutableListOf<TraceGap>()
    var cursor = start
    segments.forEach { line ->
        if (line.first().elapsedMs > cursor) gaps += TraceGap(cursor, line.first().elapsedMs)
        cursor = line.last().elapsedMs
    }
    if (cursor < end) gaps += TraceGap(cursor, end)
    return SpeedTrace(start, end, segments, gaps)
}

@Composable
internal fun BSpeedTrace(snapshot: TripSnapshot, initialEvent: Int = 0, onOpenDetail: ((Int) -> Unit)? = null) {
    val events = remember(snapshot) { snapshot.observations.filter { it.pattern == DrivingPattern.HARD_STOP } }
    var selected by rememberSaveable(snapshot.trip.id.value) { mutableIntStateOf(initialEvent) }
    val event = events.getOrNull(selected)
    GlassPanel(Modifier.fillMaxWidth()) {
        Text("주행 속도와 감속 구간", fontSize = 24.sp)
        Text("km/h · 시각", color = MotionSubtle)
        TracePlot(snapshot, null, events)
        if (events.isEmpty()) Text("저장된 감속 구간이 없어요.", color = MotionSubtle)
        else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                events.forEachIndexed { index, item ->
                    FilterChip(selected == index, { selected = index },
                        label = { Text("감속 ${index + 1} · ${traceClock(snapshot, item.startMs)}") },
                        modifier = Modifier.heightIn(min = 56.dp))
                }
            }
            if (event != null) {
                HorizontalDivider(color = MotionLine)
                Text("감속 ${selected + 1} · ${traceClock(snapshot, event.startMs)} → ${traceClock(snapshot, event.endMs)}", fontSize = 22.sp, color = MotionCyan)
                val before = snapshot.samples.lastOrNull { it.elapsedMs <= event.startMs && it.quality == Quality.VALID }
                    ?.takeIf { event.startMs - it.elapsedMs <= 5_000 }?.metersPerSecond
                val after = snapshot.samples.firstOrNull { it.elapsedMs >= event.endMs && it.quality == Quality.VALID }
                    ?.takeIf { it.elapsedMs - event.endMs <= 5_000 }?.metersPerSecond
                Text("${before?.let { "%.1f".format(it * 3.6) } ?: "—"} → ${after?.let { "%.1f".format(it * 3.6) } ?: "—"} km/h · ${"%.1f".format((event.endMs - event.startMs) / 1000.0)}초", color = MotionSubtle)
                TracePlot(snapshot, event, listOf(event))
                if (onOpenDetail != null) BActionRibbon("같은 운행 · 같은 구간", "운행 상세에서 보기") { onOpenDetail(selected) }
            }
        }
    }
}

@Composable
private fun TracePlot(snapshot: TripSnapshot, selected: Observation?, markers: List<Observation>) {
    val trace = remember(snapshot, selected) { speedTrace(snapshot, selected) }
    val ceiling = remember(trace) { maxOf(20.0, ceil((trace.segments.flatten().maxOfOrNull { (it.metersPerSecond ?: 0.0) * 3.6 } ?: 0.0) / 20.0) * 20.0) }
    var cursorMs by remember(trace) { mutableStateOf<Long?>(null) }
    if (trace.segments.isEmpty()) { Text("표시할 유효 속도 기록이 없어요.", color = MotionSubtle); return }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(36.dp).height(190.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(ceiling, ceiling / 2, 0.0).forEach { Text("${it.toInt()}", color = MotionSubtle, fontSize = 12.sp) }
        }
        Canvas(Modifier.weight(1f).height(190.dp)
            .semantics { contentDescription = if (selected == null) "전체 속도 그래프. 기록 공백은 끊어 표시" else "감속 전 접근과 정차 후 재출발 상세 그래프" }
            .pointerInput(trace) {
                detectTapGestures { point ->
                    cursorMs = trace.startMs + ((trace.endMs - trace.startMs) * (point.x / size.width).coerceIn(0f, 1f)).toLong()
                }
            }) {
            fun x(ms: Long) = ((ms - trace.startMs).toFloat() / (trace.endMs - trace.startMs)) * size.width
            fun y(sample: SpeedSample) = size.height * (1 - ((sample.metersPerSecond ?: 0.0) * 3.6 / ceiling).toFloat())
            (0..4).forEach { n -> drawLine(MotionLine, Offset(0f, size.height * n / 4), Offset(size.width, size.height * n / 4), 1f) }
            trace.gaps.forEach { gap -> drawRect(MotionMuted.copy(.13f), Offset(x(gap.startMs), 0f), androidx.compose.ui.geometry.Size(x(gap.endMs) - x(gap.startMs), size.height)) }
            selected?.let { drawRect(Color(0xFFFFB77E).copy(.14f), Offset(x(it.startMs), 0f), androidx.compose.ui.geometry.Size((x(it.endMs) - x(it.startMs)).coerceAtLeast(2f), size.height)) }
            trace.segments.forEach { line ->
                val path = Path()
                line.forEachIndexed { index, sample -> if (index == 0) path.moveTo(x(sample.elapsedMs), y(sample)) else path.lineTo(x(sample.elapsedMs), y(sample)) }
                drawPath(path, MotionCyan, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                if (line.size == 1) drawCircle(MotionCyan, 3.dp.toPx(), Offset(x(line[0].elapsedMs), y(line[0])))
            }
            markers.forEach { drawLine(Color(0xFFFFB77E), Offset(x(it.startMs), 0f), Offset(x(it.startMs), size.height), 1.dp.toPx()) }
            cursorMs?.let { ms ->
                drawLine(MotionSubtle, Offset(x(ms), 0f), Offset(x(ms), size.height), 1.dp.toPx())
                trace.sampleAt(ms)?.let { drawCircle(Color.White, 4.dp.toPx(), Offset(x(it.elapsedMs), y(it))) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(trace.startMs, trace.startMs + (trace.endMs - trace.startMs) / 2, trace.endMs).forEach { Text(traceClock(snapshot, it), fontSize = 13.sp, color = MotionSubtle) }
    }
    val cursor = cursorMs
    Text(if (cursor == null) "그래프를 터치해 시각과 속도 확인" else {
        val sample = trace.sampleAt(cursor)
        "${traceClock(snapshot, sample?.elapsedMs ?: cursor)} · ${sample?.metersPerSecond?.let { "%.1f km/h".format(it * 3.6) } ?: "기록 없음"}"
    }, color = MotionCyan, fontSize = 16.sp)
}

private fun traceClock(snapshot: TripSnapshot, elapsedMs: Long) =
    SimpleDateFormat("HH:mm:ss", Locale.KOREAN).format(Date(snapshot.trip.startedAtEpochMs + elapsedMs))
