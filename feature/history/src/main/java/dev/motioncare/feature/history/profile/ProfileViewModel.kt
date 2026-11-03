package dev.motioncare.feature.history.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.data.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.people.collect { people -> mutableState.update { it.copy(people = people) } }
        }
        retry()
    }

    fun retry() {
        mutableState.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                repository.initialize()
                mutableState.update { it.copy(loading = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, failed = true) } }
        }
    }
}

data class ProfileUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val people: List<PersonProfile> = emptyList(),
)
