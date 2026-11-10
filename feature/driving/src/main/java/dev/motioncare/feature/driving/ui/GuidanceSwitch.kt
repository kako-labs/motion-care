package dev.motioncare.feature.driving.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.motioncare.data.trip.GuidanceSound
import dev.motioncare.feature.driving.guidance.guidanceCardsAllowed

/**
 * [cardsBlocked] means the system drops the card even though guidance is on. The notice says so next to the switch,
 * as the Android permission guide asks of a feature that a denied permission turns off, and offers to turn
 * notifications on. The sound plays only with a card, so its choice waits until cards can show.
 */
@Composable
fun GuidanceSwitch(
    enabled: Boolean,
    sound: GuidanceSound,
    cardsBlocked: Boolean,
    onChange: (Boolean) -> Unit,
    onSound: (GuidanceSound) -> Unit,
    onTurnOnNotifications: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("주행 중 안내", Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onChange)
        }
        if (enabled && cardsBlocked) {
            Text("알림이 꺼져 있어 주행 중 안내 카드가 뜨지 않아요", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onTurnOnNotifications) { Text("알림 켜기") }
        }
        if (enabled && !cardsBlocked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("안내 소리", Modifier.weight(1f))
                FilterChip(
                    selected = sound == GuidanceSound.CHIME,
                    onClick = { onSound(GuidanceSound.CHIME) },
                    label = { Text("알림음") },
                )
                FilterChip(
                    selected = sound == GuidanceSound.VOICE,
                    onClick = { onSound(GuidanceSound.VOICE) },
                    label = { Text("음성") },
                )
            }
        }
    }
}

/** Read again each time the screen comes back, as it does after the permission prompt or the settings screen. */
@Composable
fun rememberGuidanceCardsAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(guidanceCardsAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = guidanceCardsAllowed(context) }
    return allowed
}
