package dev.motioncare.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.motioncare.core.model.TripRecord
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(repository: TripRepository) : ViewModel() {
    val uiState = repository.records.map { HistoryUiState(it) }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
data class HistoryUiState(val records: List<TripRecord> = emptyList())
