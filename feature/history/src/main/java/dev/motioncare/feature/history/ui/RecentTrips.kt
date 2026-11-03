package dev.motioncare.feature.history.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.*
import dev.motioncare.core.ui.ScaffoldSection
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.HistoryViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun RecentTripsRoute(repository: TripRepository, onSelect: (TripId) -> Unit) {
    val history: HistoryViewModel = viewModel(factory = viewModelFactory {
        initializer { HistoryViewModel(repository) }
    })
    val state by history.uiState.collectAsStateWithLifecycle()
    RecentTrips(state.records, onSelect)
}

@Composable
fun RecentTrips(records: List<TripRecord>, onSelect: (TripId) -> Unit) {
    ScaffoldSection("저장된 샘플 기록") {
        if (records.isEmpty()) Text("아직 기록이 없습니다.")
        records.forEach { trip ->
            TextButton(onClick = { onSelect(trip.id) }) {
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(trip.startedAtEpochMs))
                Text("$time · ${trip.status.name} · ${trip.origin.name}")
            }
        }
    }
}
