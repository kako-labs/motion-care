package dev.motioncare.feature.history.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.core.model.AirGrade
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.OutdoorAir
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.TripFeedback
import dev.motioncare.feature.history.feedback.isCabinFeeling
import dev.motioncare.feature.history.feedback.label
import dev.motioncare.feature.history.presentation.environmentField
import dev.motioncare.feature.history.ui.GlassPanel
import dev.motioncare.feature.history.ui.MotionMuted
import dev.motioncare.feature.history.ui.MotionSubtle
import dev.motioncare.feature.history.ui.MotionText
import java.util.Locale

internal data class CabinFeeling(val name: String, val feelings: String)

/** [end] is null when the trip kept one value or stored a single capture. */
internal data class CabinValue(val label: String, val start: String, val end: String?)

internal data class CabinSummary(val feelings: List<CabinFeeling>, val values: List<CabinValue>)

/**
 * What riders said about the cabin beside what the car stored for the same trip. The two sit side by side and are
 * never joined into a cause: nausea itself can make people feel warm (docs/environment.md).
 * Null when the trip has neither, so a sample trip shows nothing.
 */
internal fun cabinSummary(
    snapshots: List<EnvironmentSnapshot>,
    feedback: List<TripFeedback>,
    names: Map<PersonId, String>,
): CabinSummary? {
    val feelings = feedback.mapNotNull { answer ->
        val felt = answer.reasons.filter { it.isCabinFeeling }.sortedBy { it.ordinal }
        if (felt.isEmpty()) null
        else CabinFeeling(names[answer.targetPersonId] ?: "동승자", felt.joinToString(" · ") { it.label() })
    }
    val first = snapshots.firstOrNull()
    val last = snapshots.lastOrNull()
    if (first == null && feelings.isEmpty()) return null
    val values = if (first == null || last == null) emptyList() else listOf(
        value("실내 온도", first.cabinTemperature, last.cabinTemperature, ::celsius),
        value("설정 온도 · 운전석", first.cabinSetTemperature, last.cabinSetTemperature, ::celsius),
        value("바깥 온도", first.outsideTemperature, last.outsideTemperature, ::celsius),
        value("공기 흐름", first.recirculation, last.recirculation) { if (it) "내기 순환" else "외기 유입" },
        value("바깥 대기질", first.outdoorAir, last.outdoorAir, ::air),
    )
    return CabinSummary(feelings, values)
}

private fun <T : Any> value(
    label: String,
    start: EnvironmentReading<T>,
    end: EnvironmentReading<T>,
    format: (T) -> String,
): CabinValue {
    // A missing value shows why it is missing, as everywhere else environment values appear.
    fun text(reading: EnvironmentReading<T>) = environmentField(label, reading, format).let { if (reading.value != null) it.value else it.status }
    val from = text(start)
    val to = text(end)
    return CabinValue(label, from, to.takeIf { it != from })
}

private fun celsius(value: Double) = "%.1f°C".format(Locale.KOREAN, value)

private fun air(value: OutdoorAir): String {
    val grade = when (value.grade) {
        AirGrade.GOOD -> "좋음"
        AirGrade.MODERATE -> "보통"
        AirGrade.BAD -> "나쁨"
        AirGrade.VERY_BAD -> "매우 나쁨"
        null -> "등급 없음"
    }
    return value.pm25?.let { "$grade · PM2.5 %.1f".format(Locale.KOREAN, it) } ?: grade
}

@Composable
internal fun CabinPanel(summary: CabinSummary, modifier: Modifier = Modifier) {
    GlassPanel(modifier.fillMaxWidth()) {
        Text("이 운행의 차 안 환경", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        summary.feelings.forEach { feeling ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(feeling.name, fontWeight = FontWeight.Medium)
                Text(feeling.feelings, color = MotionText)
            }
        }
        if (summary.values.isEmpty()) {
            Text("이 운행에 저장된 환경 기록이 없어요.", color = MotionSubtle)
        } else summary.values.forEach { item ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.label, Modifier.weight(1f), color = MotionSubtle)
                Text(item.end?.let { "출발 ${item.start} → 종료 $it" } ?: item.start, color = MotionText)
            }
        }
        Text("느낌과 그때의 차 값을 나란히 보여 줄 뿐, 무엇 때문인지 판단하지 않아요.", color = MotionMuted, fontSize = 14.sp)
        if (summary.values.isNotEmpty()) Text("온도·공조: 차량 신호 · 대기질: Open-Meteo, CAMS 예측", color = MotionMuted, fontSize = 14.sp)
    }
}
