package dev.motioncare.app

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import dev.motioncare.feature.driving.guidance.openNotificationSettings
import dev.motioncare.feature.driving.guidance.opensSettingsAfterAsking

/**
 * What "알림 켜기" under the guidance switch does: the system prompt while the system still shows it, and the
 * settings screen only once it no longer does.
 */
@Composable
fun rememberTurnOnNotifications(): () -> Unit {
    val activity = LocalActivity.current
    val rationaleBefore = remember { BooleanArray(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (activity != null && opensSettingsAfterAsking(granted, rationaleBefore[0], rationaleFor(activity))) {
            openNotificationSettings(activity)
        }
    }
    return remember(activity, launcher) {
        turnOn@{
            if (activity == null) return@turnOn
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // No runtime permission here: the driver turned notifications off in settings.
                openNotificationSettings(activity)
                return@turnOn
            }
            rationaleBefore[0] = rationaleFor(activity)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun rationaleFor(activity: Activity): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
