package com.splitsmith.app.ui.components.attachments

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.ui.components.AttachmentChipsView
import com.splitsmith.app.ui.components.AttachmentPickerBottomSheet
import com.splitsmith.app.ui.components.DisplayAttachment
import com.splitsmith.app.util.AttachmentCompressor

@Composable
fun AttachmentComponent(
    selectedUris: List<Uri>,
    existingUrls: List<String>,
    onUrisChanged: (List<Uri>) -> Unit,
    onExistingUrlsChanged: (List<String>) -> Unit,
    onDriveFileIdsChanged: ((List<String>) -> Unit)? = null,
    existingDriveFileIds: List<String> = emptyList(),
    isEditable: Boolean = true,
    maxLimit: Int = AttachmentCompressor.MAX_ATTACHMENTS,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalSplitColors.current
    val d = LocalDimens.current

    var showPickerSheet by remember { mutableStateOf(false) }
    var editingUri by remember { mutableStateOf<Uri?>(null) }
    var editingIndex by remember { mutableIntStateOf(-1) }

    val displayAttachments = remember(selectedUris, existingUrls) {
        val list = mutableListOf<DisplayAttachment>()
        existingUrls.forEach { url ->
            val name = url.substringAfterLast("/").substringBefore("?").ifBlank { "Drive Attachment" }
            val isPdf = url.contains(".pdf", ignoreCase = true)
            list.add(DisplayAttachment(url = url, name = name, isPdf = isPdf))
        }
        selectedUris.forEach { uri ->
            val name = AttachmentCompressor.getFileName(context, uri)
            val isPdf = AttachmentCompressor.isPdfUri(context, uri)
            list.add(DisplayAttachment(uri = uri, name = name, isPdf = isPdf))
        }
        list
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "ATTACHMENTS",
            fontFamily = OutfitFamily,
            fontSize = d.textLabelSmall,
            color = colors.inkMuted,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(d.space8))

        if (displayAttachments.isNotEmpty()) {
            AttachmentChipsView(
                attachments = displayAttachments,
                isEditable = isEditable,
                onRemoveAttachment = { idx ->
                    if (idx < existingUrls.size) {
                        val updatedUrls = existingUrls.toMutableList().also { it.removeAt(idx) }
                        onExistingUrlsChanged(updatedUrls)
                        if (idx < existingDriveFileIds.size) {
                            val updatedDriveIds = existingDriveFileIds.toMutableList().also { it.removeAt(idx) }
                            onDriveFileIdsChanged?.invoke(updatedDriveIds)
                        }
                    } else {
                        val uriIdx = idx - existingUrls.size
                        val updatedUris = selectedUris.toMutableList().also { it.removeAt(uriIdx) }
                        onUrisChanged(updatedUris)
                    }
                },
                onEditAttachment = { idx ->
                    if (idx >= existingUrls.size) {
                        val uriIdx = idx - existingUrls.size
                        editingIndex = uriIdx
                        editingUri = selectedUris[uriIdx]
                    }
                }
            )
            Spacer(modifier = Modifier.height(d.space8))
        }

        if (isEditable && displayAttachments.size < maxLimit) {
            OutlinedButton(
                onClick = { showPickerSheet = true },
                shape = RoundedCornerShape(d.radiusFull),
                border = BorderStroke(1.dp, colors.borderWhisper),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = colors.inkPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (displayAttachments.isEmpty()) "Attach Invoice / Receipt" else "Add More Files",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkPrimary
                )
            }
        }
    }

    if (showPickerSheet) {
        val totalCount = existingUrls.size + selectedUris.size
        AttachmentPickerBottomSheet(
            currentCount = totalCount,
            maxLimit = maxLimit,
            onDismiss = { showPickerSheet = false },
            onAttachmentsAdded = { newUris ->
                onUrisChanged(selectedUris + newUris)
            }
        )
    }

    if (editingUri != null) {
        ReceiptEditorModal(
            imageUri = editingUri!!,
            onDismiss = { editingUri = null },
            onEditedImageSaved = { newUri ->
                if (editingIndex in selectedUris.indices) {
                    val updatedList = selectedUris.toMutableList().also { it[editingIndex] = newUri }
                    onUrisChanged(updatedList)
                }
                editingUri = null
            }
        )
    }
}
