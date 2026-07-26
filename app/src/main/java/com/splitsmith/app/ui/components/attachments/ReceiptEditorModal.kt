package com.splitsmith.app.ui.components.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.util.AttachmentCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class EditTool {
    CROP, BRIGHTNESS, CONTRAST, AUTO_CLEAN
}

@Composable
fun ReceiptEditorModal(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onEditedImageSaved: (Uri) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSplitColors.current
    val d = LocalDimens.current
    val coroutineScope = rememberCoroutineScope()

    var activeTool by remember { mutableStateOf(EditTool.BRIGHTNESS) }
    var brightness by remember { mutableFloatStateOf(0f) } // -100f to +100f
    var contrast by remember { mutableFloatStateOf(0f) }   // -100f to +100f
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                previewBitmap = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.canvasChalk
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // ── Header Bar ─────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space16, vertical = d.space12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cancel",
                            tint = colors.inkPrimary
                        )
                    }

                    Text(
                        text = "Edit Receipt",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.inkPrimary
                    )

                    Button(
                        onClick = {
                            if (previewBitmap != null) {
                                isLoading = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        var processed = previewBitmap!!
                                        if (rotationDegrees != 0f) {
                                            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees) }
                                            processed = Bitmap.createBitmap(processed, 0, 0, processed.width, processed.height, matrix, true)
                                        }
                                        val enhancedFile = AttachmentCompressor.compressAndPrepareImage(
                                            context = context,
                                            inputUri = imageUri,
                                            brightness = brightness,
                                            contrast = contrast
                                        )
                                        withContext(Dispatchers.Main) {
                                            if (enhancedFile != null) {
                                                onEditedImageSaved(Uri.fromFile(enhancedFile))
                                            } else {
                                                onDismiss()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) { onDismiss() }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(d.radiusFull),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Done", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = colors.borderWhisper, thickness = 1.dp)

                // ── Preview Viewport ───────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.04f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading || previewBitmap == null) {
                        CircularProgressIndicator(color = colors.inkPrimary)
                    } else {
                        val colorMatrix = remember(brightness, contrast) {
                            val contrastScale = ((contrast + 100f) / 100f).coerceAtLeast(0f)
                            val contrastTranslate = (-0.5f * contrastScale + 0.5f) * 255f
                            val bOffset = brightness
                            ColorMatrix(floatArrayOf(
                                contrastScale, 0f,            0f,            0f, contrastTranslate + bOffset,
                                0f,            contrastScale, 0f,            0f, contrastTranslate + bOffset,
                                0f,            0f,            contrastScale, 0f, contrastTranslate + bOffset,
                                0f,            0f,            0f,            1f, 0f
                            ))
                        }

                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Receipt Preview",
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        )
                    }
                }

                HorizontalDivider(color = colors.borderWhisper, thickness = 1.dp)

                // ── Contextual Slider Panel ─────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space24, vertical = d.space12)
                ) {
                    when (activeTool) {
                        EditTool.BRIGHTNESS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Brightness", fontFamily = OutfitFamily, fontSize = 14.sp, color = colors.inkMuted)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "${if (brightness >= 0) "+" else ""}${brightness.toInt()}%",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = colors.inkPrimary
                                        )
                                        IconButton(onClick = { brightness = 0f }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = "Reset", tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                Slider(
                                    value = brightness,
                                    onValueChange = { brightness = it },
                                    valueRange = -100f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.inkPrimary,
                                        activeTrackColor = colors.inkPrimary,
                                        inactiveTrackColor = colors.borderWhisper
                                    )
                                )
                            }
                        }

                        EditTool.CONTRAST -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Contrast", fontFamily = OutfitFamily, fontSize = 14.sp, color = colors.inkMuted)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "${if (contrast >= 0) "+" else ""}${contrast.toInt()}%",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = colors.inkPrimary
                                        )
                                        IconButton(onClick = { contrast = 0f }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = "Reset", tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                Slider(
                                    value = contrast,
                                    onValueChange = { contrast = it },
                                    valueRange = -100f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.inkPrimary,
                                        activeTrackColor = colors.inkPrimary,
                                        inactiveTrackColor = colors.borderWhisper
                                    )
                                )
                            }
                        }

                        EditTool.CROP -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Freeform Orientation", fontFamily = OutfitFamily, fontSize = 14.sp, color = colors.inkMuted)
                                Button(
                                    onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceCard, contentColor = colors.inkPrimary),
                                    border = BorderStroke(1.dp, colors.borderWhisper)
                                ) {
                                    Icon(Icons.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Rotate 90°", fontFamily = OutfitFamily, fontSize = 13.sp)
                                }
                            }
                        }

                        EditTool.AUTO_CLEAN -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Receipt Paper Optimizer", fontFamily = OutfitFamily, fontSize = 14.sp, color = colors.inkMuted)
                                Button(
                                    onClick = {
                                        contrast = 25f
                                        brightness = 10f
                                    },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
                                ) {
                                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apply Auto Clean", fontFamily = OutfitFamily, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.borderWhisper, thickness = 1.dp)

                // ── Bottom Tool Selection Bar ───────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space16, vertical = d.space12),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolChip(
                        icon = Icons.Outlined.AutoFixHigh,
                        label = "Auto Clean",
                        isSelected = activeTool == EditTool.AUTO_CLEAN,
                        onClick = {
                            activeTool = EditTool.AUTO_CLEAN
                            contrast = 25f
                            brightness = 10f
                        }
                    )
                    ToolChip(
                        icon = Icons.Outlined.Crop,
                        label = "Crop/Rotate",
                        isSelected = activeTool == EditTool.CROP,
                        onClick = { activeTool = EditTool.CROP }
                    )
                    ToolChip(
                        icon = Icons.Outlined.LightMode,
                        label = "Brightness",
                        isSelected = activeTool == EditTool.BRIGHTNESS,
                        onClick = { activeTool = EditTool.BRIGHTNESS }
                    )
                    ToolChip(
                        icon = Icons.Outlined.Tune,
                        label = "Contrast",
                        isSelected = activeTool == EditTool.CONTRAST,
                        onClick = { activeTool = EditTool.CONTRAST }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalSplitColors.current
    val d = LocalDimens.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(d.radiusFull),
        color = if (isSelected) colors.inkPrimary else colors.canvasChalk,
        border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) colors.canvasChalk else colors.inkMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontFamily = OutfitFamily,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                color = if (isSelected) colors.canvasChalk else colors.inkMuted
            )
        }
    }
}
