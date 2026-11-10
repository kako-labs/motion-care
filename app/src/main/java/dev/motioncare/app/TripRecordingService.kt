package dev.motioncare.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import dev.motioncare.R
import dev.motioncare.core.model.TripId
import dev.motioncare.core.model.TripStatus
import dev.motioncare.data.environment.EnvironmentRepository
import dev.motioncare.feature.driving.guidance.NotificationGuidancePresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "TripRecording"
private const val CHANNEL_ID = "trip_recording"
private const val NOTIFICATION_ID = 1
// Matches the outdoor air provider's hourly values. A product judgement, see docs/environment.md.
private const val ENVIRONMENT_INTERVAL_MS = 60 * 60 * 1000L
// How often the air intake is read for cabin air cards. Outdoor air stays cached for an hour, so this adds no
// network requests. An implementation judgement, see docs/guidance-card.md.
private const val ENVIRONMENT_CHECK_MS = 5 * 60 * 1000L
private const val CHECKS_PER_CAPTURE = ENVIRONMENT_INTERVAL_MS / ENVIRONMENT_CHECK_MS

/**
 * Keeps vehicle recording alive while another app such as navigation is in front. While automatic recording is on
 * it also stays in the foreground between trips, following the gear, because a background service stops after
 * 60 seconds on Pleos (docs/pleos-emulator/README.md).
 */
class TripRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = (application as MotionCareApplication).graph
    private var watching: Job? = null
    private var recording: Job? = null
    private var recordingTrip: TripId? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        if (watching?.isActive != true) watching = scope.launch { watch() }
        // A trip already open, such as one the gear started before the screen asked for this service.
        scope.launch {
            try {
                graph.trips.initialize()
                graph.trips.records.value.firstOrNull { it.status == TripStatus.RECORDING }?.let { record(it.id) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Open trip not read", error)
            }
            stopIfIdle()
        }
        // A restarted process marks the trip interrupted, and the app or the next boot starts watching again.
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        val autoRecord = graph.autoRecord ?: return
        val drives = graph.drives ?: return
        autoRecord.enabled.collectLatest { enabled ->
            if (!enabled || !speedPermissionGranted(this)) {
                stopIfIdle()
                return@collectLatest
            }
            updateNotification()
            try {
                drives.startedTrips().collect { record(it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Gear watching stopped", error)
            }
        }
    }

    private fun record(tripId: TripId) {
        if (recordingTrip == tripId && recording?.isActive == true) return
        val previous = recording
        recordingTrip = tripId
        recording = scope.launch {
            // The gear finished the previous trip before starting this one.
            previous?.cancelAndJoin()
            updateNotification()
            recordTrip(tripId)
            if (recordingTrip == tripId) {
                recordingTrip = null
                recording = null
            }
            updateNotification()
            stopIfIdle()
        }
    }

    private fun watchingGear() = graph.autoRecord?.enabled?.value == true && speedPermissionGranted(this)

    private fun stopIfIdle() {
        if (recordingTrip == null && !watchingGear()) stopSelf()
    }

    private suspend fun recordTrip(tripId: TripId) {
        try {
            val recorder = graph.recorder ?: return
            val audio = graph.guidanceAudio ?: return
            val environment = graph.environment
            coroutineScope {
                // Seats and the first environment values are stored next to the samples, not before them.
                val environmentJob = launch {
                    graph.preparation.prepare(tripId)
                    watchEnvironment(environment, tripId)
                }
                recorder.record(
                    NotificationGuidancePresenter(this@TripRecordingService, R.drawable.ic_launcher, audio),
                    environment.current,
                )
                environmentJob.cancelAndJoin()
            }
            // Values at the end of the trip.
            captureOnce(environment, tripId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Vehicle recording stopped", error)
        }
    }

    // Stores values every hour and reads them in between, so cabin air cards see the air intake change. The values
    // at the start come from the preparation.
    private suspend fun watchEnvironment(environment: EnvironmentRepository, tripId: TripId) {
        var check = 0L
        while (true) {
            delay(ENVIRONMENT_CHECK_MS)
            if (++check % CHECKS_PER_CAPTURE == 0L) captureOnce(environment, tripId) else refreshOnce(environment)
        }
    }

    // Environment values are an addition to the trip; failing to store them never stops recording.
    private suspend fun captureOnce(environment: EnvironmentRepository, tripId: TripId) {
        try {
            environment.capture(tripId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Environment not stored", error)
        }
    }

    private suspend fun refreshOnce(environment: EnvironmentRepository) {
        try {
            environment.refresh()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Environment not read", error)
        }
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "운행 기록", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (recordingTrip != null || !watchingGear()) "운행 기록 중" else "운행 자동 기록 켜짐")
            .apply { if (recordingTrip == null && watchingGear()) setContentText("P에서 출발하면 기록을 시작해요") }
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission") // Without the permission the system drops it, as it does for the first one.
    private fun updateNotification() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification())
    }

    // Android 15 limits dataSync services to six hours a day for apps targeting it; the system then asks the service
    // to stop within seconds. Pleos is API 34, so this is a guard only. See docs/auto-record.md.
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground time limit reached")
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/**
 * Starts the service for an open trip or for automatic recording. The system refuses a start from the background
 * outside its exemptions, such as boot; the app or the next boot then tries again.
 */
fun startRecordingService(context: Context) {
    try {
        ContextCompat.startForegroundService(context, Intent(context, TripRecordingService::class.java))
    } catch (error: IllegalStateException) {
        Log.e(TAG, "Recording service not started", error)
    }
}
