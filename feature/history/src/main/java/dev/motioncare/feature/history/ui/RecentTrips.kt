package dev.motioncare.feature.history.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.*
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.HistoryViewModel
import java.text.DateFormat
import java.util.Date

@Composable fun RecentTripsRoute(repository: TripRepository, onSelect: (TripId) -> Unit) {
    val history: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
    val state by history.uiState.collectAsStateWithLifecycle()
    RecentTrips(state.records.take(3), onSelect)
}

@Composable fun HistoryListRoute(repository: TripRepository, onBack: () -> Unit, onSelect: (TripId) -> Unit) {
    val history: HistoryViewModel = viewModel(key = "all-history", factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
    val state by history.uiState.collectAsStateWithLifecycle()
    MotionCareBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ScreenTitle("전체 운행 기록", "저장된 원본과 분석 결과", onBack)
            RecentTrips(state.records, onSelect)
        }
    }
}

@Composable fun RecentTrips(records: List<TripRecord>, onSelect: (TripId) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        if (records.isEmpty()) Text("아직 기록이 없습니다.", color = MotionSubtle)
        records.forEach { trip ->
            TextButton(onClick = { onSelect(trip.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(trip.startedAtEpochMs)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(when (trip.status) { TripStatus.COMPLETED -> "완료"; TripStatus.RECORDING -> "기록 중"; TripStatus.INTERRUPTED -> "중단됨" }, color = MotionSubtle)
                }
                Text(if (trip.origin == DataOrigin.VEHICLE) "차량 기록" else "합성 주행", color = MotionCyan)
                Spacer(Modifier.width(12.dp)); Text("›", fontSize = 28.sp)
            }
            HorizontalDivider(color = MotionLine)
        }
    }
}