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
import dev.motioncare.data.trip.GuidancePresenter

internal const val GUIDANCE_TITLE = "모션케어"

private const val CHANNEL_ID = "guidance"
private const val CHANNEL_NAME = "주행 중 안내"
private const val NOTIFICATION_ID = 2

// The system shows the card for about 9 s. Removing it soon after keeps old notes out of the bell.
private const val CARD_LIFETIME_MS = 15_000L

/** What was observed, not what to do. See docs/guidance-card.md for why cards give no instructions. */
internal fun guidanceCopy(pattern: DrivingPattern): String = when (pattern) {
    DrivingPattern.RAPID_START -> "출발 직후 속도 변화가 반복되고 있어요"
    DrivingPattern.HARD_STOP -> "정차 직전 속도 변화가 반복되고 있어요"
    DrivingPattern.REPEATED_SPEED_CHANGE -> "가속과 감속이 반복되고 있어요"
    DrivingPattern.FAST_TURN -> "회전 구간의 좌우 움직임이 반복되고 있어요"
    DrivingPattern.REPEATED_SWAY -> "좌우 움직임이 반복되고 있어요"
}

/**
 * Shows a card over navigation. On Pleos only message and navigation categories pop up while another app
 * is in front. The card has no tap action so that it asks nothing of the driver.
 */
class NotificationGuidancePresenter(
    context: Context,
    @param:DrawableRes private val smallIcon: Int,
) : GuidancePresenter {
    private val context = context.applicationContext

    override fun show(pattern: DrivingPattern): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH))
        val card: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(GUIDANCE_TITLE)
            .setContentText(guidanceCopy(pattern))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(CARD_LIFETIME_MS)
            .build()
        manager.notify(NOTIFICATION_ID, card)
        return true
    }
}
