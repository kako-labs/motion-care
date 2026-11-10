package dev.motioncare.feature.history.presentation

import dev.motioncare.core.ui.motionType
import dev.motioncare.feature.history.insights.patternLabel
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
import kotlin.math.roundToInt

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

internal enum class SpeedMovement(
    val pattern: DrivingPattern,
    val tabLabel: String,
    val sectionLabel: String,
) {
    STOP(DrivingPattern.HARD_STOP, patternLabel(DrivingPattern.HARD_STOP), patternLabel(DrivingPattern.HARD_STOP)),
    START(DrivingPattern.RAPID_START, patternLabel(DrivingPattern.RAPID_START), patternLabel(DrivingPattern.RAPID_START)),
    REPEAT(DrivingPattern.REPEATED_SPEED_CHANGE, patternLabel(DrivingPattern.REPEATED_SPEED_CHANGE), patternLabel(DrivingPattern.REPEATED_SPEED_CHANGE)),
}

internal fun speedMovementGroups(snapshot: TripSnapshot): Map<SpeedMovement, List<Observation>> =
    SpeedMovement.entries.associateWith { movement ->
        snapshot.observations.filter { it.pattern == movement.pattern }.sortedBy { it.startMs }
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
    val groups = remember(snapshot) { speedMovementGroups(snapshot) }
    var movement by rememberSaveable(snapshot.trip.id.value) { mutableStateOf(SpeedMovement.STOP) }
    var selected by rememberSaveable(snapshot.trip.id.value, movement) {
        mutableIntStateOf(if (movement == SpeedMovement.STOP) initialEvent else 0)
    }
    val events = groups.getValue(movement)
    val event = events.getOrNull(selected)
    GlassPanel(Modifier.fillMaxWidth()) {
        SectionTitle("주행 속도와 움직임 구간")
        Text("km/h · 시각", color = MotionSubtle)
        TracePlot(snapshot, null, events)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeedMovement.entries.forEach { item ->
                FilterChip(
                    selected = movement == item,
                    onClick = { movement = item; selected = 0 },
                    label = { Text("${item.tabLabel} ${groups.getValue(item).size}") },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        if (events.isEmpty()) Text("저장된 ${movement.sectionLabel} 구간이 없어요.", color = MotionSubtle)
        else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                events.forEachIndexed { index, item ->
                    FilterChip(selected == index, { selected = index },
                        label = { Text("${index + 1} · ${traceClock(snapshot, item.startMs)}") },
                        modifier = Modifier.heightIn(min = 56.dp))
                }
            }
            if (event != null) {
                HorizontalDivider(color = MotionLine)
                Text("${movement.sectionLabel} ${selected + 1} · ${traceClock(snapshot, event.startMs)}", fontSize = motionType.section, color = MotionCyan)
                Text(eventValue(snapshot, movement, event), color = MotionSubtle)
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
            listOf(ceiling, ceiling / 2, 0.0).forEach { Text("${it.toInt()}", color = MotionSubtle, fontSize = motionType.caption) }
        }
        Canvas(Modifier.weight(1f).height(190.dp)
            .semantics { contentDescription = if (selected == null) "전체 속도 그래프. 기록 공백은 선을 끊어 표시" else "선택한 움직임 전후의 속도 상세 그래프" }
            .pointerInput(trace) {
                detectTapGestures { point ->
                    cursorMs = trace.startMs + ((trace.endMs - trace.startMs) * (point.x / size.width).coerceIn(0f, 1f)).toLong()
                }
            }) {
            fun x(ms: Long) = ((ms - trace.startMs).toFloat() / (trace.endMs - trace.startMs)) * size.width
            fun y(sample: SpeedSample) = size.height * (1 - ((sample.metersPerSecond ?: 0.0) * 3.6 / ceiling).toFloat())
            (0..4).forEach { n -> drawLine(MotionLine, Offset(0f, size.height * n / 4), Offset(size.width, size.height * n / 4), 1f) }
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
        listOf(trace.startMs, trace.startMs + (trace.endMs - trace.startMs) / 2, trace.endMs).forEach { Text(traceClock(snapshot, it), fontSize = motionType.caption, color = MotionSubtle) }
    }
    val cursor = cursorMs
    Text(if (cursor == null) "그래프를 터치해 시각과 속도 확인" else {
        val sample = trace.sampleAt(cursor)
        "${traceClock(snapshot, sample?.elapsedMs ?: cursor)} · ${sample?.metersPerSecond?.let { "%.1f km/h".format(it * 3.6) } ?: "기록 없음"}"
    }, color = MotionCyan, fontSize = motionType.label)
}

private fun eventValue(snapshot: TripSnapshot, movement: SpeedMovement, event: Observation): String {
    val window = snapshot.samples.filter {
        it.elapsedMs in event.startMs..event.endMs && it.quality == Quality.VALID && it.metersPerSecond != null
    }
    val before = snapshot.samples.lastOrNull {
        it.elapsedMs <= event.startMs && event.startMs - it.elapsedMs <= 5_000 && it.quality == Quality.VALID
    }?.metersPerSecond?.times(3.6)
    val after = snapshot.samples.firstOrNull {
        it.elapsedMs >= event.endMs && it.elapsedMs - event.endMs <= 5_000 && it.quality == Quality.VALID
    }?.metersPerSecond?.times(3.6)
    val seconds = (event.endMs - event.startMs) / 1000.0
    return when (movement) {
        SpeedMovement.STOP, SpeedMovement.START ->
            "${before?.roundToInt() ?: "—"} → ${after?.roundToInt() ?: "—"} km/h · ${"%.1f".format(seconds)}초 동안"
        SpeedMovement.REPEAT -> {
            val speeds = window.mapNotNull { it.metersPerSecond?.times(3.6) }
            val low = speeds.minOrNull()?.roundToInt() ?: "—"
            val high = speeds.maxOrNull()?.roundToInt() ?: "—"
            "$low ↔ $high km/h · ${"%.1f".format(seconds)}초 구간"
        }
    }
}

private fun traceClock(snapshot: TripSnapshot, elapsedMs: Long) =
    SimpleDateFormat("HH:mm:ss", Locale.KOREAN).format(Date(snapshot.trip.startedAtEpochMs + elapsedMs))
