package dev.motioncare.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.motioncare.core.model.TripId
import dev.motioncare.core.model.TripSnapshot
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripDetailViewModel(private val repository: TripRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<TripDetailUiState>(TripDetailUiState.Loading)
    val uiState = mutableState.asStateFlow()
    private var loading: Job? = null
    private var requestedId: TripId? = null
    private var requestVersion = 0

    fun load(id: TripId) {
        requestedId = id
        val version = ++requestVersion
        loading?.cancel()
        mutableState.value = TripDetailUiState.Loading
        loading = viewModelScope.launch {
            try {
                val snapshot = repository.get(id)
                if (version == requestVersion) mutableState.value = snapshot?.let(TripDetailUiState::Content)
                    ?: TripDetailUiState.NotFound
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (version == requestVersion) mutableState.value = TripDetailUiState.Error
            }
        }
    }
    fun retry() { requestedId?.let(::load) }
}
sealed interface TripDetailUiState {
    data object Loading : TripDetailUiState
    data class Content(val snapshot: TripSnapshot) : TripDetailUiState
    data object NotFound : TripDetailUiState
    data object Error : TripDetailUiState
}
