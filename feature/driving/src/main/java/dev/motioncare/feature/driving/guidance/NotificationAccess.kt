package dev.motioncare.feature.driving.guidance

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Decides, after the driver asked to turn notifications on and the permission request came back, whether to open
 * the settings screen. The rationale flags are shouldShowRequestPermissionRationale before and after asking.
 *
 * Settings open only when the system did not show its prompt at all: false before and after means it had stopped
 * asking after "don't ask again". A denial in a prompt the driver just saw is respected, as the Android
 * permission guide asks. Dismissing the very first prompt without answering also leaves both false, so settings
 * open then too.
 */
fun opensSettingsAfterAsking(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): Boolean =
    !granted && !rationaleBefore && !rationaleAfter

/**
 * On Pleos this opens the app's page in Car Settings, whose notification switch grants the permission again.
 * It opens in its own task: Pleos has no back button, and in the app's task the app icon would bring back the
 * settings page instead of the app.
 */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A device without this screen leaves the notice in place.
    }
}
