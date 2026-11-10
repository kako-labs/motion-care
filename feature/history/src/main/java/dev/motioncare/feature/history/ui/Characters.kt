package dev.motioncare.feature.history.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.motioncare.core.ui.MotionIcons
import dev.motioncare.core.ui.motionType
import dev.motioncare.feature.history.R
import kotlin.random.Random

/**
 * The characters people can pick for themselves. Drawn from DiceBear's "Adventurer" style by Lisa Wischofsky
 * (CC BY 4.0), with sulking, sunglasses and other faces that read badly to a child left out; the credit is shown
 * wherever the picker is. Keys are stored with the person, so never reorder or rename them.
 */
internal val characterAvatars: List<Pair<String, Int>> = listOf(
    R.drawable.mc_character_01, R.drawable.mc_character_02, R.drawable.mc_character_03, R.drawable.mc_character_04,
    R.drawable.mc_character_05, R.drawable.mc_character_06, R.drawable.mc_character_07, R.drawable.mc_character_08,
    R.drawable.mc_character_09, R.drawable.mc_character_10, R.drawable.mc_character_11, R.drawable.mc_character_12,
    R.drawable.mc_character_13, R.drawable.mc_character_14, R.drawable.mc_character_15, R.drawable.mc_character_16,
    R.drawable.mc_character_17, R.drawable.mc_character_18, R.drawable.mc_character_19, R.drawable.mc_character_20,
    R.drawable.mc_character_21, R.drawable.mc_character_22, R.drawable.mc_character_23, R.drawable.mc_character_24,
).mapIndexed { index, drawable -> "character_%02d".format(index + 1) to drawable }

internal const val CHARACTER_CREDIT = "캐릭터 그림: Adventurer, Lisa Wischofsky (CC BY 4.0)"

internal fun characterDrawable(key: String): Int? = characterAvatars.firstOrNull { it.first == key }?.second

/** A character other than [current], so "다른 캐릭터" always changes the picture. */
internal fun randomCharacter(current: String? = null, random: Random = Random.Default): String =
    characterAvatars.map { it.first }.filter { it != current }.random(random)

/** Every character in a wrapping grid, with a shuffle and the credit the licence asks for. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CharacterPicker(selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("캐릭터", Modifier.weight(1f), fontSize = motionType.body, fontWeight = FontWeight.Bold)
            SecondaryAction("다른 캐릭터", { onSelect(randomCharacter(selected)) }, leadingIcon = MotionIcons.Sparkle)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            characterAvatars.forEachIndexed { index, (key, drawable) ->
                val chosen = key == selected
                Surface(
                    onClick = { onSelect(key) }, shape = CircleShape, color = Color.Transparent,
                    border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MotionCyan else MotionLine),
                    modifier = Modifier.size(size).semantics {
                        contentDescription = "캐릭터 ${index + 1}"; this.selected = chosen; role = Role.RadioButton
                    },
                ) {
                    Image(painterResource(drawable), null, Modifier.padding(if (chosen) 4.dp else 2.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop)
                }
            }
        }
        Text(CHARACTER_CREDIT, color = MotionMuted, fontSize = motionType.caption)
    }
}
