@file:OptIn(ExperimentalMaterial3Api::class)

package com.heckteck.updatekit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main composable for app update dialogs.
 *
 * This automatically checks for updates on launch and shows appropriate dialogs when needed.
 * For force updates that can't be handled by Play Store, shows a custom non-dismissible dialog.
 *
 * @param packageName The app's package name (e.g., BuildConfig.APPLICATION_ID)
 * @param onDismiss Callback when dialog is dismissed (only for flexible updates)
 */
@Composable
fun AppUpdateDialog(
    packageName: String,
    onDismiss: () -> Unit = {}
) {
    val viewModel: AppUpdateViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkForUpdateOnStart()
    }

    if (uiState.showUpdateDialog) {
        ForceUpdateDialog(packageName = packageName)
    }
}

/**
 * Force update dialog that cannot be dismissed.
 * Shows when a critical update is required but Play Store can't handle it via in-app flow.
 *
 * @param packageName The app's package name for opening Play Store
 * @param modifier Modifier for the dialog
 */
@Composable
internal fun ForceUpdateDialog(
    packageName: String,
    modifier: Modifier = Modifier,
    viewModel: AppUpdateViewModel = koinViewModel()
) {
    val context = LocalContext.current

    BasicAlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdateAlt,
                    contentDescription = "Update Required",
                    modifier = Modifier
                        .height(80.dp)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Update Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This app version is no longer supported. Please update from the Play Store to continue using the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                ActionButton(
                    label = "Update Now",
                    onClick = {
                        viewModel.openPlayStore(context, packageName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Standard in-app update dialog.
 * Can be force (non-dismissible) or flexible (dismissible).
 *
 * @param isForceUpdate If true, dialog cannot be dismissed
 * @param onUpdate Callback when user chooses to update
 * @param onDismiss Callback when user dismisses (only works if not force update)
 */
@Composable
private fun InAppUpdateDialog(
    isForceUpdate: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (isForceUpdate) "Update Required" else "Update Available"
    val message = if (isForceUpdate) {
        "This app version is no longer supported. Please update to continue using the app."
    } else {
        "A new version of the app is available. Update now for the best experience."
    }

    AlertDialog(
        onDismissRequest = {
            if (!isForceUpdate) onDismiss()
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("Update Now")
            }
        },
        dismissButton = if (!isForceUpdate) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        } else null,
        properties = DialogProperties(
            dismissOnBackPress = !isForceUpdate,
            dismissOnClickOutside = !isForceUpdate
        )
    )
}

/**
 * Snackbar-style notification for flexible updates that have been downloaded.
 * Shows a "Restart Now" button to apply the update.
 *
 * @param onCompleteUpdate Callback when user chooses to restart and apply update
 */
@Composable
fun FlexibleUpdateSnackbar(
    onCompleteUpdate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Update downloaded!",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Restart the app to apply the update.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onCompleteUpdate) {
                Text("Restart Now")
            }
        }
    }
}
