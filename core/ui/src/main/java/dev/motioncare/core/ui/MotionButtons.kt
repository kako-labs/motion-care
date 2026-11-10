package dev.motioncare.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ButtonShape = RoundedCornerShape(20.dp)

/** Home2's primary button for every screen: a blue gradient with a light edge and white bold text. */
@Composable
fun MotionPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = MotionSpace.Touch).alpha(if (enabled) 1f else 0.5f)
            .background(MotionBrushes.Primary, ButtonShape).border(1.5.dp, MotionPalette.ButtonBorder, ButtonShape),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, contentColor = Color.White,
            disabledContainerColor = Color.Transparent, disabledContentColor = Color.White,
        ),
        shape = ButtonShape,
    ) { ButtonLabel(text, icon, leadingIcon) }
}

/** The secondary button for every screen: glass with the card edge. */
@Composable
fun MotionSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = MotionSpace.Touch).glass(ButtonShape),
        border = BorderStroke(1.5.dp, MotionPalette.CardBorder), shape = ButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MotionPalette.Text),
    ) { ButtonLabel(text, icon, leadingIcon) }
}

@Composable
private fun RowScope.ButtonLabel(text: String, icon: ImageVector?, leadingIcon: ImageVector?) {
    if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(MotionSpace.IconSmall))
        Spacer(Modifier.width(MotionSpace.Medium))
    }
    Text(text, fontSize = motionType.body, fontWeight = FontWeight.Bold)
    if (icon != null) {
        Spacer(Modifier.width(MotionSpace.Medium))
        Icon(icon, contentDescription = null, modifier = Modifier.size(MotionSpace.IconSmall))
    }
}
