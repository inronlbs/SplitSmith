package com.splitsmith.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
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
import com.splitsmith.app.util.AttachmentDisplayHelper

data class DisplayAttachment(
    val uri: Uri? = null,
    val url: String = "",
    val name: String = "Attachment",
    val isPdf: Boolean = false,
    val isPending: Boolean = false,
    val isDriveSynced: Boolean = false,
    val driveFileId: String = ""
)

fun isAttachmentAvailableLocally(context: Context, item: DisplayAttachment): Boolean {
    if (item.url.startsWith("http://", ignoreCase = true) || item.url.startsWith("https://", ignoreCase = true)) {
        return true
    }
    val targetUriStr = item.uri?.toString() ?: item.url
    if (targetUriStr.isBlank()) return false
    return try {
        val uri = Uri.parse(targetUriStr)
        if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: "")
            file.exists() && file.length() > 0
        } else if (uri.scheme == "content") {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val exists = pfd != null
            pfd?.close()
            exists
        } else {
            val file = java.io.File(targetUriStr)
            file.exists() && file.length() > 0
        }
    } catch (e: Exception) {
        false
    }
}

@Composable
fun AttachmentChipsView(
    attachments: List<DisplayAttachment>,
    isEditable: Boolean = false,
    onRemoveAttachment: ((Int) -> Unit)? = null,
    onEditAttachment: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val visibleAttachments = remember(attachments, context) {
        if (isEditable) attachments else attachments.filter { isAttachmentAvailableLocally(context, it) }
    }
    if (visibleAttachments.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewIndex by remember { mutableIntStateOf(-1) }
    var previewFileName by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    val colors = LocalSplitColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(visibleAttachments) { index, item ->
                AttachmentChip(
                    item = item,
                    index = index,
                    isEditable = isEditable,
                    onRemove = { onRemoveAttachment?.invoke(index) },
                    onEdit = { onEditAttachment?.invoke(index) },
                    onClick = {
                        coroutineScope.launch {
                            isDownloading = true
                            val fetchedUri = com.splitsmith.app.util.AttachmentDownloader.getOrFetchAttachment(
                                context = context,
                                urlOrPath = item.url.ifBlank { item.uri?.toString() ?: "" },
                                driveFileId = item.driveFileId
                            )
                            isDownloading = false
                            if (fetchedUri != null) {
                                previewImageUri = fetchedUri
                                previewImageUrl = item.url
                                previewIndex = index
                                previewFileName = item.name
                            } else {
                                openAttachment(context, item, onShowImagePreview = { url, uri ->
                                    previewImageUrl = url
                                    previewImageUri = uri
                                    previewIndex = index
                                    previewFileName = item.name
                                })
                            }
                        }
                    }
                )
            }
        }
    }

    if (isDownloading) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = colors.inkPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Fetching shared receipt...", fontFamily = OutfitFamily, color = colors.inkPrimary, fontSize = 14.sp)
                }
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

            val rawModel: Any? = previewImageUri ?: previewImageUrl?.ifBlank { null }
            val isDriveUrl = previewImageUrl?.contains("drive.google.com") == true || previewImageUrl?.contains("google.com") == true

            // Check if model points to a non-existent local file from another device
            val isLocalFileMissing = remember(rawModel) {
                if (rawModel is Uri && rawModel.scheme == "file" && rawModel.path != null) {
                    !java.io.File(rawModel.path!!).exists()
                } else if (rawModel is String && (rawModel.startsWith("file://") || rawModel.startsWith("/data/"))) {
                    val cleanPath = rawModel.removePrefix("file://")
                    !java.io.File(cleanPath).exists()
                } else {
                    false
                }
            }

            val imageModel = if (isLocalFileMissing) null else rawModel

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
                    if (isLocalFileMissing) {
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
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Receipt Stored on Other Phone",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This receipt was saved locally on your other phone. Once Google Drive Sync completes on your other phone, the receipt will be available here.",
                                fontFamily = OutfitFamily,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else if (imageModel != null) {
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
                            )
                        }
                    }
                }

                // Top Bar overlay — dark scrim ensures icons visible on any background
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.40f))
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AttachmentDisplayHelper.cleanAttachmentLabel(previewFileName, previewIndex.coerceAtLeast(0), previewFileName.endsWith(".pdf", ignoreCase = true)),
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
    index: Int,
    isEditable: Boolean,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    val colors = LocalSplitColors.current
    val isDriveSynced = item.isDriveSynced || item.driveFileId.isNotBlank() || item.url.contains("drive.google.com") || item.url.contains("google.com")
    
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
            // Standardized icon mapping per design system
            val iconVector = when {
                isDriveSynced -> Icons.Default.CloudDone
                item.isPending -> Icons.Default.CloudUpload
                item.isPdf -> Icons.Default.Description
                else -> Icons.Default.AttachFile
            }
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = if (isDriveSynced) Color(0xFF34A853) else accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = AttachmentDisplayHelper.cleanAttachmentLabel(item.name, index, item.isPdf),
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

private fun openAttachment(
    context: Context,
    item: DisplayAttachment,
    onShowImagePreview: (url: String?, uri: Uri?) -> Unit
) {
    if (item.isPdf) {
        try {
            val targetUri = if (item.uri != null) item.uri else Uri.parse(item.url)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(targetUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            if (item.url.isNotBlank()) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                } catch (e2: Exception) {
                    Toast.makeText(context, "No PDF viewer found on device", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "No PDF viewer found on device", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        if (item.url.isNotBlank() || item.uri != null) {
            onShowImagePreview(item.url.ifBlank { null }, item.uri)
        }
    }
}
