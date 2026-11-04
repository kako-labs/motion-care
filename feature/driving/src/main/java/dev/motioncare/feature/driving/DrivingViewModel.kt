package dev.motioncare.feature.driving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.motioncare.core.model.*
import dev.motioncare.data.trip.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns recording UI state only; history queries use their own ViewModel. */
class DrivingViewModel(private val repository: TripRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DrivingUiState())
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.records.collect { records ->
                mutableState.update { state ->
                    val active = records.firstOrNull { trip -> trip.status == TripStatus.RECORDING }
                    val automaticallyFinished = state.active?.takeIf { active == null }?.id
                    state.copy(active = active, finishedId = automaticallyFinished ?: state.finishedId)
                }
            }
        }
        initialize()
    }
    fun initialize() = perform { repository.initialize(); mutableState.update { it.copy(ready = true) } }
    fun start() = perform { handle(repository.start()) }
    fun capture() = perform { handle(repository.captureSample()) }
    fun finish() = perform {
        val result = repository.finish(); handle(result)
        if (result is CommandResult.Success) mutableState.update { it.copy(finishedId = result.tripId) }
    }
    fun dismissFinished() { mutableState.update { it.copy(finishedId = null) } }
    private fun handle(result: CommandResult) {
        if (result is CommandResult.Rejected) mutableState.update { it.copy(error = result.reason) }
    }
    private fun perform(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(error = "처리하지 못했습니다. 다시 시도해 주세요.") } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}

data class DrivingUiState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val active: TripRecord? = null,
    val finishedId: TripId? = null,
    val error: String? = null,
)