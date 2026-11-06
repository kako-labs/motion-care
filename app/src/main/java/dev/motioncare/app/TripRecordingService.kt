package dev.motioncare.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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

/** Keeps vehicle recording alive while another app such as navigation is in front. */
class TripRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recording: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        if (recording?.isActive != true) recording = scope.launch { record() }
        // A restarted process marks the trip interrupted, so there is nothing to resume.
        return START_NOT_STICKY
    }

    private suspend fun record() {
        try {
            val graph = (application as MotionCareApplication).graph
            val recorder = graph.recorder
            val audio = graph.guidanceAudio
            if (recorder != null && audio != null) {
                val tripId = graph.trips.records.value.firstOrNull { it.status == TripStatus.RECORDING }?.id
                coroutineScope {
                    val environment = tripId?.let { id -> launch { watchEnvironment(graph.environment, id) } }
                    recorder.record(
                        NotificationGuidancePresenter(this@TripRecordingService, R.drawable.ic_launcher, audio),
                        graph.environment.current,
                    )
                    environment?.cancelAndJoin()
                }
                // Values at the end of the trip.
                tripId?.let { captureOnce(graph.environment, it) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Vehicle recording stopped", error)
        }
        stopSelf()
    }

    // Stores values every hour and reads them in between, so cabin air cards see the air intake change.
    private suspend fun watchEnvironment(environment: EnvironmentRepository, tripId: TripId) {
        var check = 0L
        while (true) {
            if (check++ % CHECKS_PER_CAPTURE == 0L) captureOnce(environment, tripId) else refreshOnce(environment)
            delay(ENVIRONMENT_CHECK_MS)
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
            .setContentTitle("운행 기록 중")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
