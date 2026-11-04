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
import dev.motioncare.core.model.TripStatus
import dev.motioncare.feature.driving.guidance.NotificationGuidancePresenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "TripRecording"
private const val CHANNEL_ID = "trip_recording"
private const val NOTIFICATION_ID = 1

/** Keeps automatic vehicle recording alive while navigation or another app is in front. */
class TripRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recording: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        if (recording?.isActive != true) recording = scope.launch { record() }
        // A restarted process marks the previous trip interrupted, so there is nothing to resume.
        return START_NOT_STICKY
    }

    private suspend fun record() = coroutineScope {
        val graph = (application as MotionCareApplication).graph
        val participantSnapshot = launch {
            graph.trips.records
                .map { records -> records.firstOrNull { it.status == TripStatus.RECORDING }?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { tripId ->
                    try {
                        // Freeze the selected driver, passengers and seats when this trip starts.
                        graph.profiles.assignCurrentSeating(tripId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // Profile metadata must never stop raw vehicle recording.
                        Log.e(TAG, "Failed to snapshot trip participants", error)
                    }
                }
        }
        try {
            graph.recorder?.record(NotificationGuidancePresenter(this@TripRecordingService, R.drawable.ic_launcher))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Vehicle recording stopped", error)
        } finally {
            participantSnapshot.cancelAndJoin()
            stopSelf()
        }
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "자동 운행 감지", NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("모션케어가 차량 움직임을 확인하고 있어요")
            .setContentText("운행 기록은 속도 변화에 따라 자동으로 시작하고 종료돼요.")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
