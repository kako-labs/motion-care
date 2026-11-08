package dev.motioncare.feature.history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.motioncare.core.model.Availability
import dev.motioncare.core.model.EnvironmentReading
import dev.motioncare.core.model.EnvironmentSnapshot
import dev.motioncare.core.model.EnvironmentSource
import dev.motioncare.core.model.MissingReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presentation only: never requests a permission, invents a reading, or controls HVAC. */
internal data class EnvironmentFieldUi(
    val label: String,
    val value: String,
    val status: String,
    val source: String,
    val observedAtEpochMs: Long?,
)

internal fun <T : Any> environmentField(
    label: String,
    reading: EnvironmentReading<T>,
    format: (T) -> String,
): EnvironmentFieldUi {
    val status = when (reading.availability) {
        Availability.AVAILABLE -> "확인됨"
        Availability.STALE -> "오래된 값"
        Availability.LOADING -> "확인 중"
        Availability.UNSUPPORTED -> "미지원"
        Availability.UNAVAILABLE -> when (reading.missingReason) {
            MissingReason.NO_PERMISSION -> "권한 없음"
            MissingReason.NO_LOCATION -> "위치 확인 필요"
            MissingReason.NETWORK -> "네트워크 연결 필요"
            MissingReason.NO_DATA, null -> "값 없음"
        }
    }
    val source = when (reading.source) {
        EnvironmentSource.VEHICLE -> "차량 공조·환경 신호"
        EnvironmentSource.OPEN_METEO_CAMS -> "대기질: Open-Meteo, CAMS 예측"
    }
    return EnvironmentFieldUi(label, reading.value?.let(format) ?: "—", status, source, reading.observedAtEpochMs)
}

internal fun environmentFields(snapshot: EnvironmentSnapshot): List<EnvironmentFieldUi> = listOf(
    environmentField("실내 온도", snapshot.cabinTemperature) { "%.1f°C".format(Locale.KOREAN, it) },
    environmentField("설정 온도 · 운전석", snapshot.cabinSetTemperature) { "%.1f°C".format(Locale.KOREAN, it) },
    environmentField("바깥 온도", snapshot.outsideTemperature) { "%.1f°C".format(Locale.KOREAN, it) },
    environmentField("공조 순환 모드", snapshot.recirculation) { if (it) "내기 순환" else "외기 유입" },
    environmentField("공기청정", snapshot.airCleaner) { if (it) "켜짐" else "꺼짐" },
    environmentField("바깥 PM2.5", snapshot.outdoorAir) { it.pm25?.let { v -> "%.1f µg/m³".format(Locale.KOREAN, v) } ?: "값 없음" },
    environmentField("바깥 PM10", snapshot.outdoorAir) { it.pm10?.let { v -> "%.1f µg/m³".format(Locale.KOREAN, v) } ?: "값 없음" },
)

/** Drop into the history-owned departure/report content after repository wiring is agreed. */
@Composable
internal fun EnvironmentPanel(snapshot: EnvironmentSnapshot?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("실내와 바깥, 서로 다른 환경", color = Color(0xFFE2F3FF), fontSize = 24.sp, fontWeight = FontWeight.Medium)
        if (snapshot == null) {
            Text("저장된 환경 기록이 없어요. 다른 주행의 값으로 대신하지 않아요.", color = Color(0xFFA6C2D9), fontSize = 16.sp)
        } else {
            val fields = environmentFields(snapshot)
            BoxWithConstraints {
                if (maxWidth >= 680.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        EnvironmentFields(fields.take(3), Modifier.weight(1f))
                        EnvironmentFields(fields.drop(3), Modifier.weight(1f))
                    }
                } else {
                    EnvironmentFields(fields, Modifier.fillMaxWidth())
                }
            }
        }
        Text("실내 PM2.5·CO₂: 지원 신호 없음", color = Color(0xFF8BAAC5), fontSize = 14.sp)
        Text("대기질: Open-Meteo, CAMS 예측 · 실측이 아닌 모델 예측이에요.", color = Color(0xFF8BAAC5), fontSize = 14.sp)
    }
}

@Composable
private fun EnvironmentFields(fields: List<EnvironmentFieldUi>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fields.forEach { field ->
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF17263C), Color(0xFF142237))), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x24B9D5F0), RoundedCornerShape(20.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(field.label, color = Color(0xFFA6C2D9), fontSize = 15.sp)
                Text(field.value, color = Color(0xFFCCEFFF), fontSize = 30.sp, fontWeight = FontWeight.Normal)
                Text(field.status, color = Color(0xFFA6C2D9), fontSize = 14.sp)
                Text(field.source, color = Color(0xFF88A6C1), fontSize = 12.sp)
                field.observedAtEpochMs?.let { at ->
                    Text("기준 시각 " + SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(Date(at)), color = Color(0xFF88A6C1), fontSize = 12.sp)
                }
            }
        }
    }
}
