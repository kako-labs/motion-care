package dev.motioncare.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.motioncare.feature.driving.guidance.opensSettingsAfterAsking

const val CAR_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"

/** What the start screen says after the speed permission request comes back. */
enum class SpeedPermissionNotice { NONE, DENIED, ASK_IN_SETTINGS }

/**
 * The rationale flags are shouldShowRequestPermissionRationale before and after asking. A denial in a prompt the
 * driver just saw is only explained, as the Android permission guide asks. Settings are offered only when the
 * system did not show its prompt at all, which is the case after "don't ask again".
 */
fun speedPermissionNotice(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): SpeedPermissionNotice =
    when {
        granted -> SpeedPermissionNotice.NONE
        opensSettingsAfterAsking(granted = false, rationaleBefore, rationaleAfter) -> SpeedPermissionNotice.ASK_IN_SETTINGS
        else -> SpeedPermissionNotice.DENIED
    }

fun speedRationale(activity: Activity?): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, CAR_SPEED_PERMISSION)

fun speedPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, CAR_SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED

/**
 * The speed permission belongs to the location group, so the prompt and the settings page both call it 위치.
 */
@Composable
fun SpeedPermissionMessage(notice: SpeedPermissionNotice, onOpenSettings: () -> Unit) {
    if (notice == SpeedPermissionNotice.NONE) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("차량 속도 권한이 있어야 운행을 기록할 수 있습니다.", color = MaterialTheme.colorScheme.error)
        if (notice == SpeedPermissionNotice.ASK_IN_SETTINGS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("설정 > 권한 > 위치에서 허용하면 기록할 수 있어요")
                TextButton(onClick = onOpenSettings) { Text("설정 열기") }
            }
        }
    }
}

/**
 * Opens the app's page in Car Settings, where 권한 > 위치 grants the speed permission again.
 *
 * On Pleos, ACTION_APPLICATION_DETAILS_SETTINGS opens the vehicle settings app, whose app page has only a
 * notification switch. ACTION_AUTO_REVOKE_PERMISSIONS is documented as equivalent to it and opens Car Settings.
 * It opens in its own task: Pleos has no back button, and in the app's task the app icon would bring back the
 * settings page instead of the app.
 */
fun openPermissionSettings(context: Context) {
    val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent.ACTION_AUTO_REVOKE_PERMISSIONS
    } else {
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    }
    val intent = Intent(action, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A device without this screen leaves the notice in place.
    }
}
