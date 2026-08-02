package com.splitsmith.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PermissionSnackbar(
    showSnackbar: Boolean,
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (showSnackbar) {
        Snackbar(
            modifier = modifier,
            action = {
                TextButton(onClick = onGrantPermission) {
                    Text("Grant")
                }
            },
            dismissAction = {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "Dismiss")
                }
            }
        ) {
            Text("Google Drive permission needed for uploads.")
        }
    }
}
