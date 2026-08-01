package com.splitsmith.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily

data class DisplayAttachment(
    val uri: Uri? = null,
    val url: String = "",
    val name: String = "Attachment",
    val isPdf: Boolean = false,
    val isPending: Boolean = false
)

@Composable
fun AttachmentChipsView(
    attachments: List<DisplayAttachment>,
    isEditable: Boolean = false,
    onRemoveAttachment: ((Int) -> Unit)? = null,
    onEditAttachment: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    val context = LocalContext.current
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewIndex by remember { mutableIntStateOf(-1) }
    var previewFileName by remember { mutableStateOf("") }

    val colors = LocalSplitColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(attachments) { index, item ->
                AttachmentChip(
                    item = item,
                    isEditable = isEditable,
                    onRemove = { onRemoveAttachment?.invoke(index) },
                    onEdit = { onEditAttachment?.invoke(index) },
                    onClick = {
                        openAttachment(context, item, onShowImagePreview = { url, uri ->
                            previewImageUrl = url
                            previewImageUri = uri
                            previewIndex = index
                            previewFileName = item.name
                        })
                    }
                )
            }
        }
    }

    // Full-screen Zoomable & Draggable Image Viewer Modal
    if (previewImageUrl != null || previewImageUri != null) {
        Dialog(
            onDismissRequest = {
                previewImageUrl = null
                previewImageUri = null
                previewIndex = -1
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            val isDriveUrl = previewImageUrl?.contains("drive.google.com") == true || previewImageUrl?.contains("google.com") == true
            val imageModel = when {
                previewImageUri != null -> previewImageUri
                !isDriveUrl && previewImageUrl != null -> previewImageUrl
                isDriveUrl && previewImageUrl != null -> {
                    val parsed = Uri.parse(previewImageUrl)
                    if (parsed.scheme == "file") parsed else null
                }
                else -> null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.93f))
            ) {
                // Image content with pinch-to-zoom & pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = scale * zoom
                                scale = if (newScale < 1f) 1f else if (newScale > 5f) 5f else newScale
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Receipt photo full screen",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    } else {
                        // Stored in Google Drive fallback
                        Column(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Attachment in Google Drive",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = previewFileName,
                                fontFamily = OutfitFamily,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    previewImageUrl?.let { openInGoogleDrive(context, it) }
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in Google Drive", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Top Bar overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = previewFileName,
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDriveUrl && previewImageUrl != null) {
                            Surface(
                                onClick = {
                                    openInGoogleDrive(context, previewImageUrl!!)
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Cloud, contentDescription = "View in Drive", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View in Drive", fontFamily = OutfitFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (isEditable && previewIndex >= 0 && onEditAttachment != null) {
                            Surface(
                                onClick = {
                                    val idx = previewIndex
                                    previewImageUrl = null
                                    previewImageUri = null
                                    previewIndex = -1
                                    onEditAttachment(idx)
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit & Crop", fontFamily = OutfitFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                previewImageUrl = null
                                previewImageUri = null
                                previewIndex = -1
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    item: DisplayAttachment,
    isEditable: Boolean,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    val colors = LocalSplitColors.current
    val isDriveSynced = item.url.contains("drive.google.com") || item.url.contains("google.com")
    
    // Minimal & subtle color styling
    val accentColor = if (item.isPdf) Color(0xFF0284C7) else Color(0xFF64748B)
    val chipBg = accentColor.copy(alpha = 0.08f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = chipBg,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minimal Leading Icon Replacement (Zero Extra Width)
            val iconVector = when {
                isDriveSynced -> Icons.Outlined.Cloud
                item.isPending -> Icons.Outlined.CloudQueue
                item.isPdf -> Icons.Outlined.Description
                else -> Icons.Outlined.Image
            }
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = if (isDriveSynced) Color(0xFF4F46E5) else accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.name,
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp)
            )

            if (isEditable) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceCard.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onRemove)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun openInGoogleDrive(context: Context, driveUrl: String) {
    try {
        val driveIntent = Intent(Intent.ACTION_VIEW, Uri.parse(driveUrl)).apply {
            setPackage("com.google.android.apps.docs")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (driveIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(driveIntent)
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(driveUrl))
            context.startActivity(browserIntent)
        }
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(driveUrl)))
        } catch (e2: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openAttachment(
    context: Context,
    item: DisplayAttachment,
    onShowImagePreview: (url: String?, uri: Uri?) -> Unit
) {
    if (item.isPdf) {
        try {
            if (item.url.isNotBlank()) {
                openInGoogleDrive(context, item.url)
            } else if (item.uri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "No PDF viewer found on device", Toast.LENGTH_SHORT).show()
        }
    } else {
        if (item.url.isNotBlank() || item.uri != null) {
            onShowImagePreview(item.url.ifBlank { null }, item.uri)
        }
    }
}
