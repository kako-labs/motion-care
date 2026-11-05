package dev.motioncare.feature.driving.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.motioncare.data.trip.GuidanceSound

@Composable
fun GuidanceSwitch(
    enabled: Boolean,
    sound: GuidanceSound,
    onChange: (Boolean) -> Unit,
    onSound: (GuidanceSound) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("주행 중 안내")
            Switch(checked = enabled, onCheckedChange = onChange)
        }
        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("안내 소리")
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
