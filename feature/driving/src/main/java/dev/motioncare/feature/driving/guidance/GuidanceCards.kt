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
import androidx.core.graphics.drawable.toBitmap
import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.EnvironmentAdvice
import dev.motioncare.core.model.GuidanceTopic
import dev.motioncare.data.trip.GuidanceAudio
import dev.motioncare.data.trip.GuidancePresenter
import dev.motioncare.feature.driving.R

internal const val GUIDANCE_TITLE = "모션케어"

private const val CHANNEL_ID = "guidance"
private const val CHANNEL_NAME = "주행 중 안내"
private const val NOTIFICATION_ID = 2

// The system shows the card for about 9 s. Removing it soon after keeps old notes out of the bell.
private const val CARD_LIFETIME_MS = 15_000L

/** One recommended action for the card's topic. Sources and limits are in docs/guidance-card.md. */
internal fun guidanceCopy(topic: GuidanceTopic): String = when (topic) {
    is GuidanceTopic.Pattern -> when (topic.pattern) {
        DrivingPattern.RAPID_START -> "출발 후 몇 초는 천천히 가속해보세요"
        DrivingPattern.HARD_STOP -> "정지 지점보다 조금 일찍 감속해보세요"
        DrivingPattern.REPEATED_SPEED_CHANGE -> "앞차와 간격을 넉넉히 두어보세요"
        DrivingPattern.FAST_TURN -> "커브 전에 미리 속도를 줄여보세요"
        DrivingPattern.REPEATED_SWAY -> "핸들을 조금 더 부드럽게 돌려보세요"
    }
    is GuidanceTopic.Environment -> when (topic.advice) {
        EnvironmentAdvice.REDUCE_OUTSIDE_AIR -> "외기 유입을 줄이고 공기청정을 켜보세요"
        EnvironmentAdvice.TRY_OUTSIDE_AIR -> "답답하다면 잠시 외기 모드를 써보세요"
    }
    GuidanceTopic.LongDrive -> "2시간째, 안전한 곳에서 쉬어가보세요"
}

// Trial copy, shortened so a card with a large icon stays on one line. Not yet recorded in docs/guidance-card.md.

/** The topic's shape for the card's large icon. Whether Pleos draws a large icon is not measured yet. */
@DrawableRes
internal fun guidanceIcon(topic: GuidanceTopic): Int = when (topic) {
    is GuidanceTopic.Pattern -> when (topic.pattern) {
        DrivingPattern.RAPID_START -> R.drawable.guidance_rapid_start
        DrivingPattern.HARD_STOP -> R.drawable.guidance_hard_stop
        DrivingPattern.REPEATED_SPEED_CHANGE -> R.drawable.guidance_speed_change
        DrivingPattern.FAST_TURN -> R.drawable.guidance_fast_turn
        DrivingPattern.REPEATED_SWAY -> R.drawable.guidance_sway
    }
    is GuidanceTopic.Environment -> when (topic.advice) {
        EnvironmentAdvice.REDUCE_OUTSIDE_AIR -> R.drawable.guidance_reduce_outside_air
        EnvironmentAdvice.TRY_OUTSIDE_AIR -> R.drawable.guidance_try_outside_air
    }
    GuidanceTopic.LongDrive -> R.drawable.guidance_long_drive
}

private const val LARGE_ICON_PX = 192

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
            .setLargeIcon(ContextCompat.getDrawable(context, guidanceIcon(topic))?.toBitmap(LARGE_ICON_PX, LARGE_ICON_PX))
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
