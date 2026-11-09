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
import dev.motioncare.app.home.HomeScreen
import dev.motioncare.app.home.AdviceHero
import dev.motioncare.app.home.DepartureGuidanceSwitch
import dev.motioncare.app.home.HomeLinks
import dev.motioncare.app.home.homeAdvice
import dev.motioncare.app.home.StartButton
import dev.motioncare.core.ui.MotionPalette
import dev.motioncare.core.ui.motionBackground
import dev.motioncare.app.home.environmentCard
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
import dev.motioncare.feature.history.DepartureViewModel
import dev.motioncare.feature.history.navigation.HistoryRoutes
import dev.motioncare.feature.history.navigation.historyGraph
import dev.motioncare.feature.history.report.FinishedContent
import dev.motioncare.core.model.TripStatus
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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
    // Seats, people and departure advice come from the history feature, as on its own departure screen.
    val departure: DepartureViewModel = viewModel(factory = viewModelFactory {
        initializer { DepartureViewModel(trips, profiles, environment.current) }
    })
    val departureState by departure.uiState.collectAsStateWithLifecycle()
    val activeId = drivingState.active?.id
    // A new trip keeps who sat where at its start and the environment at that moment, then starts recording.
    LaunchedEffect(activeId) {
        if (activeId != null) {
            withContext(NonCancellable) {
                runCatching {
                    profiles.initialize()
                    if (profiles.participants.value.none { it.tripId == activeId }) {
                        profiles.assignCurrentSeating(activeId)
                    }
                }
                runCatching {
                    if (environment.history(activeId).isEmpty()) environment.capture(activeId)
                }
            }
            currentCoroutineContext().ensureActive()
            val stillRecording = try {
                trips.get(activeId)?.trip?.status == TripStatus.RECORDING
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            currentCoroutineContext().ensureActive()
            if (vehicleConnected && stillRecording) onRecordingStarted()
        }
    }
    val records by trips.records.collectAsStateWithLifecycle()
    val participants by profiles.participants.collectAsStateWithLifecycle()
    val snapshot by environment.current.collectAsStateWithLifecycle()
    val zone = remember { TimeZone.getDefault() }
    val startTrip = {
        if (vehicleConnected) {
            speedRationaleBefore = speedRationale(activity)
            permissions.launch(recordingPermissions())
        } else driving.start()
    }
    Surface(Modifier.fillMaxSize().motionBackground(), color = Color.Transparent, contentColor = MotionPalette.Text) {
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
                        people = peopleSummary(departureState.people, departureState.seating),
                        environment = environmentCard(snapshot, zone),
                        recent = remember(records, participants, departureState.people) {
                            recentTripItems(records, zone, participants = participants, people = departureState.people)
                        },
                        start = StartButton(
                            label = when {
                                active != null -> "기록 중"
                                vehicleConnected -> "운행 시작"
                                else -> "샘플 시작"
                            },
                            enabled = active == null && finished == null && !drivingState.busy,
                            onClick = startTrip,
                        ),
                        links = HomeLinks(
                            onSeats = { navigation.navigate(HistoryRoutes.PROFILES) },
                            onRecords = { navigation.navigate(HistoryRoutes.RECORDS) },
                            onProfile = {
                                val driver = departureState.seating.driverId
                                navigation.navigate(if (driver != null) HistoryRoutes.profile(driver) else HistoryRoutes.PROFILES)
                            },
                            onTrip = { navigation.navigate(HistoryRoutes.trip(it)) },
                        ),
                        notice = if (error != null || speedNotice != SpeedPermissionNotice.NONE) {
                            @Composable {
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                SpeedPermissionMessage(speedNotice, onOpenSettings = { openPermissionSettings(context) })
                            }
                        } else null,
                        guidance = {
                            DepartureGuidanceSwitch(departureState.guidanceEnabled, onChange = { departure.toggleGuidance() })
                            // In-drive cards need the car; devices without one show the departure switch only.
                            guidance?.let { settings ->
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
                                else -> AdviceHero(
                                    homeAdvice(departureState.advice, departureState.seating.driverId), sizes,
                                    onExplain = { navigation.navigate(HistoryRoutes.advice(departureState.seating.driverId)) },
                                    onChooseSeats = { navigation.navigate(HistoryRoutes.PROFILES) },
                                )
                            }
                        },
                    )
                }
                historyGraph(
                    navigation = navigation,
                    trips = trips,
                    profiles = profiles,
                    environment = environment.current,
                    onBackToHome = { navigation.popBackStack(HOME_ROUTE, inclusive = false) },
                )
            }
        }
    }
}
