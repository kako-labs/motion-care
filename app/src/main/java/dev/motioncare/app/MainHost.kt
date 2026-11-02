package dev.motioncare.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import dev.motioncare.core.model.TripId
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.driving.DrivingViewModel
import dev.motioncare.feature.driving.ui.ActiveContent
import dev.motioncare.feature.history.HistoryViewModel
import dev.motioncare.feature.history.TripDetailViewModel
import dev.motioncare.feature.history.TripDetailUiState
import dev.motioncare.feature.history.departure.ReadyContent
import dev.motioncare.feature.history.feedback.FeedbackSlot
import dev.motioncare.feature.history.profile.ProfileSlot
import dev.motioncare.feature.history.report.FinishedContent
import dev.motioncare.feature.history.report.TripDetailContent
import dev.motioncare.feature.history.ui.RecentTrips

@Composable
fun MainHost(repository: TripRepository) {
    val navigation = rememberNavController()
    val driving: DrivingViewModel = viewModel(factory = viewModelFactory {
        initializer { DrivingViewModel(repository) }
    })
    val drivingState by driving.uiState.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Motion Care", style = MaterialTheme.typography.headlineMedium)
            Text("개발 뼈대 · 합성 샘플 전용", color = MaterialTheme.colorScheme.primary)
            if (!drivingState.ready) {
                Text(drivingState.error ?: "저장된 샘플 확인 중")
                if (!drivingState.busy) Button(onClick = driving::initialize) { Text("다시 시도") }
            } else NavHost(navigation, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") {
                    val history: HistoryViewModel = viewModel(factory = viewModelFactory {
                        initializer { HistoryViewModel(repository) }
                    })
                    val historyState by history.uiState.collectAsStateWithLifecycle()
                    Column(Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        drivingState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        val active = drivingState.active
                        val finished = drivingState.finishedId
                        when {
                            active != null -> ActiveContent(active, drivingState.busy, driving::capture, driving::finish)
                            finished != null -> FinishedContent(
                                onDetail = { navigation.navigate("trip/${finished.value}") },
                                onFeedback = { navigation.navigate("feedback/${finished.value}") },
                                onDone = driving::dismissFinished)
                            else -> ReadyContent(drivingState.busy, driving::start) { navigation.navigate("profiles") }
                        }
                        if (active == null) RecentTrips(historyState.records) { navigation.navigate("trip/${it.value}") }
                    }
                }
                composable("profiles") {
                    ProfileSlot { navigation.popBackStack() }
                }
                composable("trip/{tripId}") { entry ->
                    val id = requireNotNull(entry.arguments?.getString("tripId"))
                    val detail: TripDetailViewModel = viewModel(factory = viewModelFactory {
                        initializer { TripDetailViewModel(repository) }
                    })
                    val state by detail.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(id) { detail.load(TripId(id)) }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (state == TripDetailUiState.Error) {
                            Text("기록을 불러오지 못했습니다.")
                            Button(onClick = detail::retry) { Text("다시 시도") }
                            TextButton(onClick = { navigation.popBackStack() }) { Text("돌아가기") }
                        } else TripDetailContent(
                            snapshot = (state as? TripDetailUiState.Content)?.snapshot,
                            loading = state == TripDetailUiState.Loading,
                            onBack = { navigation.popBackStack("home", inclusive = false) },
                            onFeedback = { navigation.navigate("feedback/$id") })
                    }
                }
                composable("feedback/{tripId}") { entry ->
                    FeedbackSlot(entry.arguments?.getString("tripId")) { navigation.popBackStack() }
                }
            }
        }
    }
}
