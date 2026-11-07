package dev.motioncare.feature.driving.guidance

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.EnvironmentAdvice
import dev.motioncare.core.model.GuidanceTopic
import dev.motioncare.data.trip.GuidanceAudio
import dev.motioncare.data.trip.GuidancePresenter

internal const val GUIDANCE_TITLE = "모션케어"

private const val CHANNEL_ID = "guidance"
private const val CHANNEL_NAME = "주행 중 안내"
private const val NOTIFICATION_ID = 2

// The system shows the card for about 9 s. Removing it soon after keeps old notes out of the bell.
private const val CARD_LIFETIME_MS = 15_000L

/** One recommended action for the card's topic. Sources and limits are in docs/guidance-card.md. */
internal fun guidanceCopy(topic: GuidanceTopic): String = when (topic) {
    is GuidanceTopic.Pattern -> when (topic.pattern) {
        DrivingPattern.RAPID_START -> "출발 후 몇 초 동안 천천히 속도를 높여보세요"
        DrivingPattern.HARD_STOP -> "정지 지점보다 조금 일찍 감속해보세요"
        DrivingPattern.REPEATED_SPEED_CHANGE -> "앞차와의 간격을 확보해 속도 변화를 줄여보세요"
        DrivingPattern.FAST_TURN -> "커브에 진입하기 전에 미리 속도를 줄여보세요"
        DrivingPattern.REPEATED_SWAY -> "핸들을 조금 더 부드럽게 돌려보세요"
    }
    is GuidanceTopic.Environment -> when (topic.advice) {
        EnvironmentAdvice.REDUCE_OUTSIDE_AIR -> "외기 유입을 줄이고 공기청정 기능을 확인해보세요"
        EnvironmentAdvice.TRY_OUTSIDE_AIR -> "답답함을 느낀다면 잠시 외기 모드를 사용해보세요"
    }
    GuidanceTopic.LongDrive -> "2시간 넘게 달렸어요. 안전한 곳에서 쉬어가보세요"
}

/**
 * Whether the system lets the card through. Denying the notification permission turns off all of the app's
 * notifications. Pleos settings only have this app-wide switch, with no screen for a single channel.
 */
fun guidanceCardsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

/**
 * Shows a card over navigation. On Pleos only message and navigation categories pop up while another app
 * is in front. The card has no tap action so that it asks nothing of the driver.
 */
class NotificationGuidancePresenter(
    context: Context,
    @param:DrawableRes private val smallIcon: Int,
    private val audio: GuidanceAudio,
) : GuidancePresenter {
    private val context = context.applicationContext

    override fun show(topic: GuidanceTopic): Boolean {
        if (!guidanceCardsAllowed(context)) return false
        val manager = NotificationManagerCompat.from(context)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH))
        val card: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(GUIDANCE_TITLE)
            .setContentText(guidanceCopy(topic))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(CARD_LIFETIME_MS)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, card)
        } catch (_: SecurityException) {
            return false // The permission went away after the check above.
        }
        audio.play(guidanceCopy(topic))
        return true
    }
}
