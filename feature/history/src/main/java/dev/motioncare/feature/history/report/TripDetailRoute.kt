package dev.motioncare.feature.history.report

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.TripId
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.TripExperienceViewModel

@Composable
internal fun TripDetailRoute(
    id: TripId,
    trips: TripRepository,
    profiles: ProfileRepository,
    onBack: () -> Unit,
    onBackToHome: () -> Unit,
    onFeedback: () -> Unit,
) {
    val detail: TripExperienceViewModel = viewModel(factory = viewModelFactory {
        initializer { TripExperienceViewModel(id, trips, profiles) }
    })
    val state by detail.uiState.collectAsStateWithLifecycle()
    TripDetailContent(state, onBack, onBackToHome, onFeedback)
}