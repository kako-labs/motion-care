package dev.motioncare.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Spacing and size steps for the car window, on the 8 dp grid of Google's Android Automotive design system. The touch
 * size and list row heights are that system's values; how each step is used is a product judgement. See
 * docs/design.md#여백과-크기.
 */
object MotionSpace {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Base = 16.dp
    val Large = 24.dp
    val XLarge = 32.dp
    val Huge = 48.dp

    /** The smallest touch target in a car. */
    val Touch = 76.dp
    /** A list row with one line. */
    val RowSingle = 96.dp
    /** A list row with a title and a second line. */
    val RowDouble = 116.dp

    val AvatarSmall = 56.dp
    val AvatarMedium = 76.dp
    val AvatarLarge = 96.dp
    val IconSmall = 28.dp
    val IconMedium = 36.dp
    val IconLarge = 44.dp
}

/**
 * A list row at car size: a title, an optional second line, something before and after, and a divider below. The whole
 * row is the touch target when [onClick] is given, and a chevron says it opens something.
 */
@Composable
fun MotionListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = if (subtitle != null) MotionSpace.RowDouble else MotionSpace.RowSingle)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = MotionSpace.Large, vertical = MotionSpace.Base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MotionSpace.Large),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MotionSpace.Small)) {
                Text(title, fontSize = motionType.body, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, fontSize = motionType.label, color = MotionPalette.TextSecondary) }
            }
            trailing?.invoke(this)
            if (onClick != null) {
                Icon(MotionIcons.ChevronRight, contentDescription = null, tint = MotionPalette.TextSecondary,
                    modifier = Modifier.size(MotionSpace.IconMedium))
            }
        }
        if (divider) HorizontalDivider(color = MotionPalette.CardBorder)
    }
}

/** A switch whose whole row, label included, is the touch target, as small switches are hard to hit in a car. */
@Composable
fun MotionSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = MotionSpace.Touch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MotionSpace.Base),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MotionSpace.Tiny)) {
            Text(label, fontSize = motionType.body, fontWeight = FontWeight.SemiBold)
            description?.let { Text(it, fontSize = motionType.label, color = MotionPalette.TextSecondary) }
        }
        // The row handles the touch; the switch only shows the state, a quarter larger to read at a glance.
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.scale(1.25f).padding(end = MotionSpace.Small))
    }
}

/** A choice chip at car size. */
@Composable
fun MotionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0x592F9BEA) else Color(0x297AB0F0),
        border = BorderStroke(1.5.dp, if (selected) MotionPalette.Link.copy(alpha = .6f) else MotionPalette.CardBorder),
        modifier = modifier.heightIn(min = MotionSpace.Touch),
    ) {
        Row(Modifier.padding(horizontal = MotionSpace.Large), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Text(text, fontSize = motionType.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MotionPalette.TextStrong else MotionPalette.TextSecondary)
        }
    }
}
