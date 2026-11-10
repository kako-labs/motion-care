package dev.motioncare.feature.driving.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.motioncare.core.ui.MotionChip
import dev.motioncare.core.ui.MotionSecondaryButton
import dev.motioncare.core.ui.MotionSpace
import dev.motioncare.core.ui.MotionSwitchRow
import dev.motioncare.core.ui.motionType
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
    Column(verticalArrangement = Arrangement.spacedBy(MotionSpace.Medium)) {
        MotionSwitchRow("주행 중 안내", enabled, onChange)
        if (enabled && cardsBlocked) {
            Text("알림이 꺼져 있어 주행 중 안내 카드가 뜨지 않아요", color = MaterialTheme.colorScheme.error,
                fontSize = motionType.label)
            MotionSecondaryButton("알림 켜기", onTurnOnNotifications, Modifier.fillMaxWidth())
        }
        if (enabled && !cardsBlocked) {
            // The label sits above the two choices, which take the card's width as car-size touch targets.
            Text("안내 소리", fontSize = motionType.body, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MotionSpace.Medium)) {
                MotionChip("알림음", sound == GuidanceSound.CHIME, { onSound(GuidanceSound.CHIME) }, Modifier.weight(1f))
                MotionChip("음성", sound == GuidanceSound.VOICE, { onSound(GuidanceSound.VOICE) }, Modifier.weight(1f))
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
