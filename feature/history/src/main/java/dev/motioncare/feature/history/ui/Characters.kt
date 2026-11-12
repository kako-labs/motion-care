package dev.motioncare.feature.history.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import dev.motioncare.core.ui.MotionIcons
import dev.motioncare.core.ui.MotionSpace
import dev.motioncare.core.ui.motionType
import dev.motioncare.feature.history.R
import kotlin.random.Random

/**
 * The characters people can pick for themselves: a woman and a man for each of five ages, generated to match the app's
 * navy and cyan look (see docs/design.md#고를-수-있는-캐릭터). Keys are stored with the person, so never rename them;
 * a key from the earlier set falls back to the face for the person's kind.
 */
internal val characterAvatars: List<Pair<String, Int>> = listOf(
    "girl_child" to R.drawable.mc_person_girl_child,
    "girl_teen" to R.drawable.mc_person_girl_teen,
    "woman_young" to R.drawable.mc_person_woman_young,
    "woman_middle" to R.drawable.mc_person_woman_middle,
    "woman_senior" to R.drawable.mc_person_woman_senior,
    "boy_child" to R.drawable.mc_person_boy_child,
    "boy_teen" to R.drawable.mc_person_boy_teen,
    "man_young" to R.drawable.mc_person_man_young,
    "man_middle" to R.drawable.mc_person_man_middle,
    "man_senior" to R.drawable.mc_person_man_senior,
)

/** Women on the first row and men on the second, youngest first. */
private const val CHARACTERS_PER_ROW = 5

internal fun characterDrawable(key: String): Int? = characterAvatars.firstOrNull { it.first == key }?.second

/** A character other than [current], so "다른 캐릭터" always changes the picture. */
internal fun randomCharacter(current: String? = null, random: Random = Random.Default): String =
    characterAvatars.map { it.first }.filter { it != current }.random(random)

/** Every character in two rows of five that share the width, so each stays a car-size touch target, and a shuffle. */
@Composable
internal fun CharacterPicker(selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MotionSpace.Base)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("캐릭터", Modifier.weight(1f), fontSize = motionType.body, fontWeight = FontWeight.Bold)
            SecondaryAction("다른 캐릭터", { onSelect(randomCharacter(selected)) }, leadingIcon = MotionIcons.Sparkle)
        }
        characterAvatars.withIndex().chunked(CHARACTERS_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MotionSpace.Base)) {
                row.forEach { (index, character) ->
                    val (key, drawable) = character
                    val chosen = key == selected
                    Surface(
                        onClick = { onSelect(key) }, shape = CircleShape, color = Color.Transparent,
                        border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MotionCyan else MotionLine),
                        modifier = Modifier.weight(1f).aspectRatio(1f).semantics {
                            contentDescription = "캐릭터 ${index + 1}"; this.selected = chosen; role = Role.RadioButton
                        },
                    ) {
                        Image(painterResource(drawable), null, Modifier.padding(if (chosen) 4.dp else 2.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}
