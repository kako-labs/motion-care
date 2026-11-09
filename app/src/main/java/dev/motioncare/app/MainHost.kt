package dev.motioncare.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import dev.motioncare.app.home.HomeColors
import dev.motioncare.app.home.HomeScreen
import dev.motioncare.app.home.ReadyHero
import dev.motioncare.app.home.StartButton
import dev.motioncare.app.home.environmentCard
import dev.motioncare.app.home.homeBackground
import dev.motioncare.app.home.peopleSummary
import dev.motioncare.app.home.recentTripItems
import dev.motioncare.data.environment.EnvironmentRepository
import dev.motioncare.data.profile.ProfileRepository
import dev.motioncare.data.trip.GuidanceSettings
import dev.motioncare.data.trip.TripRepository
import dev.motioncare.feature.driving.DrivingViewModel
import dev.motioncare.feature.driving.ui.ActiveContent
import dev.motioncare.feature.driving.ui.GuidanceSwitch
import dev.motioncare.feature.driving.ui.rememberGuidanceCardsAllowed
import dev.motioncare.feature.history.navigation.HistoryRoutes
import dev.motioncare.feature.history.navigation.historyGraph
import dev.motioncare.feature.history.report.FinishedContent
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException

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
    // The home card lists people; a profile store that fails to open leaves the card empty.
    LaunchedEffect(profiles) {
        try {
            profiles.initialize()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
    val activeId = drivingState.active?.id
    LaunchedEffect(activeId) {
        if (vehicleConnected && activeId != null) onRecordingStarted()
    }
    val records by trips.records.collectAsStateWithLifecycle()
    val people by profiles.people.collectAsStateWithLifecycle()
    val snapshot by environment.current.collectAsStateWithLifecycle()
    val zone = remember { TimeZone.getDefault() }
    val startTrip = {
        if (vehicleConnected) {
            speedRationaleBefore = speedRationale(activity)
            permissions.launch(recordingPermissions())
        } else driving.start()
    }
    Surface(Modifier.fillMaxSize().homeBackground(), color = Color.Transparent, contentColor = HomeColors.Text) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (!drivingState.ready) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(drivingState.error ?: "저장된 기록 확인 중", style = MaterialTheme.typography.titleLarge)
                    if (!drivingState.busy) Button(onClick = driving::initialize) { Text("다시 시도") }
                }
            } else NavHost(navigation, startDestination = HOME_ROUTE, modifier = Modifier.fillMaxSize()) {
                composable(HOME_ROUTE) {
                    val active = drivingState.active
                    val finished = drivingState.finishedId
                    val error = drivingState.error
                    HomeScreen(
                        vehicleConnected = vehicleConnected,
                        people = peopleSummary(people),
                        environment = environmentCard(snapshot, zone),
                        recent = remember(records) { recentTripItems(records, zone) },
                        start = StartButton(
                            label = when {
                                active != null -> "기록 중"
                                vehicleConnected -> "운행 시작"
                                else -> "샘플 시작"
                            },
                            enabled = active == null && finished == null && !drivingState.busy,
                            onClick = startTrip,
                        ),
                        onPeople = { navigation.navigate(HistoryRoutes.PROFILES) },
                        onTrip = { navigation.navigate(HistoryRoutes.trip(it)) },
                        notice = if (error != null || speedNotice != SpeedPermissionNotice.NONE) {
                            @Composable {
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                SpeedPermissionMessage(speedNotice, onOpenSettings = { openPermissionSettings(context) })
                            }
                        } else null,
                        guidance = guidance?.let { settings ->
                            @Composable {
                                val enabled by settings.enabled.collectAsStateWithLifecycle()
                                val sound by settings.sound.collectAsStateWithLifecycle()
                                GuidanceSwitch(
                                    enabled = enabled,
                                    sound = sound,
                                    cardsBlocked = !rememberGuidanceCardsAllowed(),
                                    onChange = settings::setEnabled,
                                    onSound = settings::setSound,
                                    onTurnOnNotifications = rememberTurnOnNotifications(),
                                )
                            }
                        },
                        hero = { sizes ->
                            when {
                                active != null ->
                                    ActiveContent(active, drivingState.busy, vehicleConnected, driving::capture, driving::finish)
                                finished != null -> FinishedContent(
                                    onDetail = { navigation.navigate(HistoryRoutes.trip(finished)) },
                                    onFeedback = { navigation.navigate(HistoryRoutes.feedback(finished)) },
                                    onDone = driving::dismissFinished,
                                )
                                else -> ReadyHero(vehicleConnected, sizes)
                            }
                        },
                    )
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
