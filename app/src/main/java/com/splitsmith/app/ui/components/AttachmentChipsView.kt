package com.splitsmith.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
                        })
                    }
                )
            }
        }
    }

    // Full-screen Image Preview Modal
    if (previewImageUrl != null || previewImageUri != null) {
        Dialog(onDismissRequest = {
            previewImageUrl = null
            previewImageUri = null
            previewIndex = -1
        }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Receipt Preview",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.inkPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEditable && previewIndex >= 0 && onEditAttachment != null) {
                                TextButton(onClick = {
                                    val idx = previewIndex
                                    previewImageUrl = null
                                    previewImageUri = null
                                    previewIndex = -1
                                    onEditAttachment(idx)
                                }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = colors.inkPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit & Crop", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.inkPrimary, fontSize = 13.sp)
                                }
                            }
                            IconButton(onClick = {
                                previewImageUrl = null
                                previewImageUri = null
                                previewIndex = -1
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.inkPrimary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AsyncImage(
                        model = previewImageUrl ?: previewImageUri,
                        contentDescription = "Receipt photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    )
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
    val accentColor = if (item.isPdf) Color(0xFF0284C7) else Color(0xFFD97706)
    val chipBg = accentColor.copy(alpha = 0.08f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = chipBg,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isPdf) Icons.Outlined.Description else Icons.Outlined.Image,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
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
            if (item.isPending) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "• Pending",
                    fontFamily = OutfitFamily,
                    fontSize = 11.sp,
                    color = colors.inkMuted
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(14.dp)
            )

            if (isEditable) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceCard.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onRemove)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(15.dp)
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
            if (item.url.isNotBlank()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                context.startActivity(intent)
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
