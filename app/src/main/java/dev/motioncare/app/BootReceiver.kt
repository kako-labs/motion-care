package dev.motioncare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts watching the gear again after the head unit boots or the app is updated, both of which end the recording
 * service. Both broadcasts may start a foreground service from the background on API 34; see docs/auto-record.md.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val graph = (context.applicationContext as MotionCareApplication).graph
        if (graph.autoRecord?.enabled?.value == true && speedPermissionGranted(context)) startRecordingService(context)
    }
}
