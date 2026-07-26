package com.splitsmith.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily

@Composable
fun DeleteExpenseDialog(
    hasAttachments: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: (deleteFromDrive: Boolean) -> Unit
) {
    val colors = LocalSplitColors.current
    var deleteFromDrive by remember { mutableStateOf(hasAttachments) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        title = {
            Text(
                text = "Delete Expense?",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.inkPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete this expense? This action cannot be undone.",
                    fontFamily = OutfitFamily,
                    fontSize = 14.sp,
                    color = colors.inkMuted
                )
                if (hasAttachments) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = deleteFromDrive,
                            onCheckedChange = { deleteFromDrive = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.inkPrimary,
                                uncheckedColor = colors.inkMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Also delete attached receipts from Google Drive",
                            fontFamily = OutfitFamily,
                            fontSize = 13.sp,
                            color = colors.inkPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmDelete(deleteFromDrive) },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.alertRed)
            ) {
                Text("Delete", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
