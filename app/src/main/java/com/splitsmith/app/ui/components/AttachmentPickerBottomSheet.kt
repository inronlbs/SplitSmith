package com.splitsmith.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.util.AttachmentCompressor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerBottomSheet(
    currentCount: Int,
    maxLimit: Int = AttachmentCompressor.MAX_ATTACHMENTS,
    onDismiss: () -> Unit,
    onAttachmentsAdded: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSplitColors.current
    val remainingSlots = maxLimit - currentCount

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && pendingCameraUri != null) {
                onAttachmentsAdded(listOf(pendingCameraUri!!))
                onDismiss()
            }
        }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = if (remainingSlots > 1) remainingSlots else 2),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val selected = uris.take(remainingSlots)
                onAttachmentsAdded(selected)
                onDismiss()
            }
        }
    )

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val selected = uris.take(remainingSlots)
                onAttachmentsAdded(selected)
                onDismiss()
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 36.dp, top = 8.dp)
        ) {
            Text(
                text = "Attach Invoice / Receipt",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add up to $maxLimit files ($remainingSlots remaining)",
                fontFamily = OutfitFamily,
                fontSize = 13.sp,
                color = colors.inkMuted
            )

            Spacer(modifier = Modifier.height(20.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.CameraAlt,
                title = "Take Photo",
                subtitle = "Capture receipt with camera",
                iconColor = colors.inkPrimary,
                onClick = {
                    if (remainingSlots <= 0) {
                        Toast.makeText(context, "Maximum limit of $maxLimit attachments reached", Toast.LENGTH_SHORT).show()
                        return@AttachmentOptionRow
                    }
                    try {
                        val uri = AttachmentCompressor.createTempImageUri(context)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Choose Photos from Gallery",
                subtitle = "Select multiple images from photo library",
                iconColor = Color(0xFFEA580C),
                onClick = {
                    if (remainingSlots <= 0) {
                        Toast.makeText(context, "Maximum limit of $maxLimit attachments reached", Toast.LENGTH_SHORT).show()
                        return@AttachmentOptionRow
                    }
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.Description,
                title = "Attach PDF / Documents",
                subtitle = "Select PDF invoices or e-receipts",
                iconColor = Color(0xFF0284C7),
                onClick = {
                    if (remainingSlots <= 0) {
                        Toast.makeText(context, "Maximum limit of $maxLimit attachments reached", Toast.LENGTH_SHORT).show()
                        return@AttachmentOptionRow
                    }
                    documentLauncher.launch(arrayOf("application/pdf", "image/*"))
                }
            )
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    val colors = LocalSplitColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = colors.canvasChalk,
        border = BorderStroke(1.dp, colors.borderWhisper)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                )
                Text(
                    text = subtitle,
                    fontFamily = OutfitFamily,
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
            }
        }
    }
}
