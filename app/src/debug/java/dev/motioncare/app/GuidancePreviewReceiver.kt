package dev.motioncare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.motioncare.R
import dev.motioncare.core.model.DrivingPattern
import dev.motioncare.core.model.EnvironmentAdvice
import dev.motioncare.core.model.GuidanceTopic
import dev.motioncare.data.trip.GuidanceAudio
import dev.motioncare.feature.driving.guidance.NotificationGuidancePresenter

/**
 * Shows one guidance card without driving, to check how the system draws its icons and copy:
 * `adb shell am broadcast --user 10 -n dev.motioncare.scaffold/dev.motioncare.app.GuidancePreviewReceiver --es topic HARD_STOP`.
 * Topics are the DrivingPattern and EnvironmentAdvice names and LONG_DRIVE. No sound, and nothing is stored.
 */
class GuidancePreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("topic")
        val topic = DrivingPattern.entries.firstOrNull { it.name == name }?.let { GuidanceTopic.Pattern(it) }
            ?: EnvironmentAdvice.entries.firstOrNull { it.name == name }?.let { GuidanceTopic.Environment(it) }
            ?: GuidanceTopic.LongDrive.takeIf { name == "LONG_DRIVE" }
        if (topic == null) {
            Log.w(TAG, "Unknown topic $name")
            return
        }
        val shown = NotificationGuidancePresenter(context, R.drawable.home_logo, GuidanceAudio { }).show(topic)
        Log.i(TAG, "$name shown=$shown")
    }

    private companion object {
        const val TAG = "GuidancePreview"
    }
}
