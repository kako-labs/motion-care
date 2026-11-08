package dev.motioncare.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import dev.motioncare.data.environment.EnvironmentRepository
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.GuidanceSettings
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.driving.DrivingViewModel
import dev.motioncare.feature.driving.ui.ActiveContent
import dev.motioncare.feature.driving.ui.GuidanceSwitch
import dev.motioncare.feature.driving.ui.rememberGuidanceCardsAllowed
import dev.motioncare.feature.history.departure.ReadyContent
import dev.motioncare.feature.history.navigation.HistoryRoutes
import dev.motioncare.feature.history.navigation.historyGraph
import dev.motioncare.feature.history.report.FinishedContent
import dev.motioncare.feature.history.ui.RecentTripsRoute

private const val HOME_ROUTE = "home"
// Outdoor air needs the car's position. Recording goes on without it.
private const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

private fun recordingPermissions(): Array<String> = buildList {
    add(CAR_SPEED_PERMISSION)
    add(LOCATION_PERMISSION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun MainHost(
    trips: TripRepository,
    profiles: ProfileRepository,
    environment: EnvironmentRepository,
    vehicleConnected: Boolean,
    guidance: GuidanceSettings?,
    onRecordingStarted: () -> Unit,
) {
    val navigation = rememberNavController()
    val driving: DrivingViewModel = viewModel(factory = viewModelFactory {
        initializer { DrivingViewModel(trips) }
    })
    val drivingState by driving.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    var speedNotice by remember { mutableStateOf(SpeedPermissionNotice.NONE) }
    var speedRationaleBefore by remember { mutableStateOf(false) }
    var permissionsAnswered by remember { mutableIntStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val speedGranted = results[CAR_SPEED_PERMISSION] == true
        speedNotice = speedPermissionNotice(speedGranted, speedRationaleBefore, speedRationale(activity))
        permissionsAnswered += 1
        if (speedGranted) driving.start()
    }
    // Back from settings with the permission granted, the notice goes. Recording waits for the driver to start it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (speedNotice != SpeedPermissionNotice.NONE && speedPermissionGranted(context)) {
            speedNotice = SpeedPermissionNotice.NONE
        }
    }
    // Read once on opening and again after the permission answer, which may allow outdoor air.
    LaunchedEffect(permissionsAnswered) { environment.refresh() }
    val activeId = drivingState.active?.id
    LaunchedEffect(activeId) {
        if (vehicleConnected && activeId != null) onRecordingStarted()
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Motion Care", style = MaterialTheme.typography.headlineMedium)
            Text(if (vehicleConnected) "개발 버전 · 차량 속도 기록" else "개발 뼈대 · 합성 샘플 전용",
                color = MaterialTheme.colorScheme.primary)
            if (!drivingState.ready) {
                Text(drivingState.error ?: "저장된 기록 확인 중")
                if (!drivingState.busy) Button(onClick = driving::initialize) { Text("다시 시도") }
            } else NavHost(navigation, startDestination = HOME_ROUTE, modifier = Modifier.weight(1f)) {
                composable(HOME_ROUTE) {
                    Column(Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        drivingState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        SpeedPermissionMessage(speedNotice, onOpenSettings = { openPermissionSettings(context) })
                        val active = drivingState.active
                        val finished = drivingState.finishedId
                        if (guidance != null && finished == null) {
                            val enabled by guidance.enabled.collectAsStateWithLifecycle()
                            val sound by guidance.sound.collectAsStateWithLifecycle()
                            GuidanceSwitch(
                                enabled = enabled,
                                sound = sound,
                                cardsBlocked = !rememberGuidanceCardsAllowed(),
                                onChange = guidance::setEnabled,
                                onSound = guidance::setSound,
                                onTurnOnNotifications = rememberTurnOnNotifications(),
                            )
                        }
                        when {
                            active != null -> ActiveContent(active, drivingState.busy, vehicleConnected, driving::capture, driving::finish)
                            finished != null -> FinishedContent(
                                onDetail = { navigation.navigate(HistoryRoutes.trip(finished)) },
                                onFeedback = { navigation.navigate(HistoryRoutes.feedback(finished)) },
                                onDone = driving::dismissFinished)
                            else -> ReadyContent(
                                busy = drivingState.busy,
                                vehicleConnected = vehicleConnected,
                                onStart = {
                                    if (vehicleConnected) {
                                        speedRationaleBefore = speedRationale(activity)
                                        permissions.launch(recordingPermissions())
                                    } else driving.start()
                                },
                                onProfiles = { navigation.navigate(HistoryRoutes.PROFILES) },
                            )
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
