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
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.driving.DrivingViewModel
import dev.motioncare.feature.driving.ui.ActiveContent
import dev.motioncare.feature.history.departure.ReadyContent
import dev.motioncare.feature.history.navigation.HistoryRoutes
import dev.motioncare.feature.history.navigation.historyGraph
import dev.motioncare.feature.history.report.FinishedContent
import dev.motioncare.feature.history.ui.RecentTripsRoute

private const val HOME_ROUTE = "home"

@Composable
fun MainHost(trips: TripRepository, profiles: ProfileRepository) {
    val navigation = rememberNavController()
    val driving: DrivingViewModel = viewModel(factory = viewModelFactory {
        initializer { DrivingViewModel(trips) }
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
            } else NavHost(navigation, startDestination = HOME_ROUTE, modifier = Modifier.weight(1f)) {
                composable(HOME_ROUTE) {
                    Column(Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        drivingState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        val active = drivingState.active
                        val finished = drivingState.finishedId
                        when {
                            active != null -> ActiveContent(active, drivingState.busy, driving::capture, driving::finish)
                            finished != null -> FinishedContent(
                                onDetail = { navigation.navigate(HistoryRoutes.trip(finished)) },
                                onFeedback = { navigation.navigate(HistoryRoutes.feedback(finished)) },
                                onDone = driving::dismissFinished)
                            else -> ReadyContent(drivingState.busy, driving::start) { navigation.navigate(HistoryRoutes.PROFILES) }
                        }
                        if (active == null) RecentTripsRoute(trips) { navigation.navigate(HistoryRoutes.trip(it)) }
                    }
                }
                historyGraph(
                    navigation = navigation,
                    trips = trips,
                    profiles = profiles,
                    onBackToHome = { navigation.popBackStack(HOME_ROUTE, inclusive = false) },
                )
            }
        }
    }
}
