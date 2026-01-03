package com.heckteck.updatekit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Reusable action button component for update dialogs.
 *
 * @param modifier Modifier for the button
 * @param label Text to display on the button
 * @param onClick Callback when button is clicked
 * @param enabled Whether the button is enabled
 * @param outlined If true, renders as outlined button; otherwise as filled button
 */
@Composable
internal fun ActionButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    outlined: Boolean = false
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(20),
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(20),
            modifier = modifier,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if(isSystemInDarkTheme()) Color(0xFF000000) else Color(0xFF333333),
            )
        }
    }
}
