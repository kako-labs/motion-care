package dev.motioncare.feature.history.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.model.TripId
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.history.TripDetailUiState
import dev.motioncare.feature.history.TripDetailViewModel

@Composable
internal fun TripDetailRoute(
    id: TripId,
    repository: TripRepository,
    onBack: () -> Unit,
    onBackToHome: () -> Unit,
    onFeedback: () -> Unit,
) {
    val detail: TripDetailViewModel = viewModel(factory = viewModelFactory {
        initializer { TripDetailViewModel(repository) }
    })
    val state by detail.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { detail.load(id) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (state == TripDetailUiState.Error) {
            Text("기록을 불러오지 못했습니다.")
            Button(onClick = detail::retry) { Text("다시 시도") }
            TextButton(onClick = onBack) { Text("돌아가기") }
        } else TripDetailContent(
            snapshot = (state as? TripDetailUiState.Content)?.snapshot,
            loading = state == TripDetailUiState.Loading,
            onBack = onBackToHome,
            onFeedback = onFeedback,
        )
    }
}
