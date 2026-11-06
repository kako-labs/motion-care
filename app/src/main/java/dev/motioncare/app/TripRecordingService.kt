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
                    val environment = tripId?.let { id -> launch { captureEnvironment(graph.environment, id) } }
                    recorder.record(NotificationGuidancePresenter(this@TripRecordingService, R.drawable.ic_launcher, audio))
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

    private suspend fun captureEnvironment(environment: EnvironmentRepository, tripId: TripId) {
        while (true) {
            captureOnce(environment, tripId)
            delay(ENVIRONMENT_INTERVAL_MS)
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
