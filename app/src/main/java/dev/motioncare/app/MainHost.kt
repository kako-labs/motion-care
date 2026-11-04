package dev.motioncare.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.GuidanceSettings
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.driving.DrivingViewModel
import dev.motioncare.feature.driving.ui.ActiveContent
import dev.motioncare.feature.history.departure.DepartureRoute
import dev.motioncare.feature.history.navigation.HistoryRoutes
import dev.motioncare.feature.history.navigation.historyGraph
import dev.motioncare.feature.history.report.FinishedRoute
import dev.motioncare.feature.history.ui.MotionCareBackground
import dev.motioncare.feature.history.ui.MotionCareTheme

private const val HOME_ROUTE = "home"
private const val CAR_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"

private fun recordingPermissions(): Array<String> = buildList {
    add(CAR_SPEED_PERMISSION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun MainHost(
    trips: TripRepository,
    profiles: ProfileRepository,
    vehicleConnected: Boolean,
    guidance: GuidanceSettings?,
    onRecordingStarted: () -> Unit,
) {
    val navigation = rememberNavController()
    val context = LocalContext.current
    val driving: DrivingViewModel = viewModel(factory = viewModelFactory { initializer { DrivingViewModel(trips) } })
    val drivingState by driving.uiState.collectAsStateWithLifecycle()
    var speedPermissionDenied by remember { mutableStateOf(false) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        speedPermissionDenied = results[CAR_SPEED_PERMISSION] != true
        if (!speedPermissionDenied) onRecordingStarted()
    }
    LaunchedEffect(vehicleConnected, drivingState.ready) {
        if (vehicleConnected && drivingState.ready && !permissionRequested) {
            permissionRequested = true
            val missing = recordingPermissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) onRecordingStarted() else permissions.launch(missing.toTypedArray())
        }
    }

    MotionCareTheme {
        Surface(Modifier.fillMaxSize()) {
            NavHost(navigation, startDestination = HOME_ROUTE) {
                composable(HOME_ROUTE) {
                    when {
                        !drivingState.ready -> MotionCareBackground { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        drivingState.active != null -> MotionCareBackground { Column(Modifier.padding(22.dp)) {
                            ActiveContent(requireNotNull(drivingState.active), drivingState.busy, vehicleConnected, driving::capture, driving::finish)
                        } }
                        drivingState.finishedId != null -> {
                            val id = requireNotNull(drivingState.finishedId)
                            FinishedRoute(id, trips, profiles,
                                onDetail = { navigation.navigate(HistoryRoutes.trip(id)) },
                                onFeedback = { navigation.navigate(HistoryRoutes.feedback(id)) },
                                onDone = driving::dismissFinished)
                        }
                        else -> DepartureRoute(trips, profiles, guidance,
                            onProfile = { navigation.navigate(HistoryRoutes.profile(it)) },
                            onAdvice = { navigation.navigate(HistoryRoutes.advice(it)) },
                            onRecords = { navigation.navigate(HistoryRoutes.RECORDS) })
                    }
                    if (speedPermissionDenied) Snackbar(Modifier.padding(18.dp)) { Text("차량 속도 권한이 없어 자동 기록을 시작할 수 없습니다.") }
                }
                historyGraph(navigation, trips, profiles) { navigation.popBackStack(HOME_ROUTE, inclusive = false) }
            }
        }
    }
}
