package com.splitsmith.app.ui.components.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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

import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo

enum class EditTool {
    CROP, BRIGHTNESS, CONTRAST, AUTO_CLEAN
}

data class EditState(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val rotationDegrees: Float = 0f,
    val cropLeft: Float = 0.05f,
    val cropTop: Float = 0.05f,
    val cropRight: Float = 0.95f,
    val cropBottom: Float = 0.95f
)

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
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Crop box state (normalized 0.0f..1.0f coordinates inside preview bounds)
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.95f) }

    // Undo / Redo history stacks
    var undoStack by remember { mutableStateOf(listOf(EditState())) }
    var redoStack by remember { mutableStateOf(listOf<EditState>()) }

    fun currentState() = EditState(
        brightness = brightness,
        contrast = contrast,
        rotationDegrees = rotationDegrees,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom
    )

    fun pushState(newState: EditState) {
        val curr = currentState()
        if (curr != newState) {
            undoStack = undoStack + newState
            redoStack = emptyList()
            brightness = newState.brightness
            contrast = newState.contrast
            rotationDegrees = newState.rotationDegrees
            cropLeft = newState.cropLeft
            cropTop = newState.cropTop
            cropRight = newState.cropRight
            cropBottom = newState.cropBottom
        }
    }

    fun undo() {
        if (undoStack.size > 1) {
            val current = undoStack.last()
            val previous = undoStack[undoStack.size - 2]
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + current
            brightness = previous.brightness
            contrast = previous.contrast
            rotationDegrees = previous.rotationDegrees
            cropLeft = previous.cropLeft
            cropTop = previous.cropTop
            cropRight = previous.cropRight
            cropBottom = previous.cropBottom
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + nextState
            brightness = nextState.brightness
            contrast = nextState.contrast
            rotationDegrees = nextState.rotationDegrees
            cropLeft = nextState.cropLeft
            cropTop = nextState.cropTop
            cropRight = nextState.cropRight
            cropBottom = nextState.cropBottom
        }
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cancel",
                                tint = colors.inkPrimary
                            )
                        }

                        // Minimal Undo & Redo Icon Buttons
                        IconButton(
                            onClick = { undo() },
                            enabled = undoStack.size > 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = "Undo",
                                tint = if (undoStack.size > 1) colors.inkPrimary else colors.inkMuted.copy(alpha = 0.4f)
                            )
                        }

                        IconButton(
                            onClick = { redo() },
                            enabled = redoStack.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Redo,
                                contentDescription = "Redo",
                                tint = if (redoStack.isNotEmpty()) colors.inkPrimary else colors.inkMuted.copy(alpha = 0.4f)
                            )
                        }
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
                                        if (brightness != 0f || contrast != 0f) {
                                            processed = AttachmentCompressor.applyColorMatrixTransform(processed, brightness, contrast)
                                        }
                                        val tempFile = java.io.File(context.cacheDir, "edited_receipt_${System.currentTimeMillis()}.jpg")
                                        tempFile.outputStream().use { out ->
                                            processed.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                        }

                                        withContext(Dispatchers.Main) {
                                            onEditedImageSaved(Uri.fromFile(tempFile))
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

                // ── Preview Viewport with Crop & Rotation ──────────────
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

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Receipt Preview",
                                colorFilter = ColorFilter.colorMatrix(colorMatrix),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationZ = rotationDegrees
                                    }
                            )

                            // Interactive Crop Overlay Box when CROP tool active
                            if (activeTool == EditTool.CROP) {
                                Canvas(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val leftPx = cropLeft * w
                                    val topPx = cropTop * h
                                    val rightPx = cropRight * w
                                    val bottomPx = cropBottom * h

                                    // Darkened dim background outside crop box
                                    drawRect(Color.Black.copy(alpha = 0.45f))

                                    // Clear crop rectangle
                                    drawRect(
                                        color = Color.Transparent,
                                        topLeft = Offset(leftPx, topPx),
                                        size = Size(rightPx - leftPx, bottomPx - topPx),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                                    )

                                    // Flat 1px crop border
                                    drawRect(
                                        color = colors.inkPrimary,
                                        topLeft = Offset(leftPx, topPx),
                                        size = Size(rightPx - leftPx, bottomPx - topPx),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                    )

                                    // Grid lines inside crop box
                                    val thirdW = (rightPx - leftPx) / 3f
                                    val thirdH = (bottomPx - topPx) / 3f
                                    for (i in 1..2) {
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.6f),
                                            start = Offset(leftPx + thirdW * i, topPx),
                                            end = Offset(leftPx + thirdW * i, bottomPx),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.6f),
                                            start = Offset(leftPx, topPx + thirdH * i),
                                            end = Offset(rightPx, topPx + thirdH * i),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    // Corner handles
                                    val handleSize = 16.dp.toPx()
                                    val handleStroke = 3.dp.toPx()
                                    // Top-Left
                                    drawLine(Color.White, Offset(leftPx, topPx), Offset(leftPx + handleSize, topPx), strokeWidth = handleStroke)
                                    drawLine(Color.White, Offset(leftPx, topPx), Offset(leftPx, topPx + handleSize), strokeWidth = handleStroke)
                                    // Top-Right
                                    drawLine(Color.White, Offset(rightPx, topPx), Offset(rightPx - handleSize, topPx), strokeWidth = handleStroke)
                                    drawLine(Color.White, Offset(rightPx, topPx), Offset(rightPx, topPx + handleSize), strokeWidth = handleStroke)
                                    // Bottom-Left
                                    drawLine(Color.White, Offset(leftPx, bottomPx), Offset(leftPx + handleSize, bottomPx), strokeWidth = handleStroke)
                                    drawLine(Color.White, Offset(leftPx, bottomPx), Offset(leftPx, bottomPx - handleSize), strokeWidth = handleStroke)
                                    // Bottom-Right
                                    drawLine(Color.White, Offset(rightPx, bottomPx), Offset(rightPx - handleSize, bottomPx), strokeWidth = handleStroke)
                                    drawLine(Color.White, Offset(rightPx, bottomPx), Offset(rightPx, bottomPx - handleSize), strokeWidth = handleStroke)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.borderWhisper, thickness = 1.dp)

                // ── Contextual Action / Slider Panel ───────────────────
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
                                IconButton(
                                    onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f }
                                ) {
                                    Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate 90°", tint = colors.inkPrimary)
                                }

                                Text("Crop & Orientation", fontFamily = OutfitFamily, fontSize = 14.sp, color = colors.inkMuted)

                                Button(
                                    onClick = {
                                        if (previewBitmap != null) {
                                            try {
                                                val bmp = previewBitmap!!
                                                val x = (cropLeft * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                                                val y = (cropTop * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                                                val w = ((cropRight - cropLeft) * bmp.width).toInt().coerceIn(1, bmp.width - x)
                                                val h = ((cropBottom - cropTop) * bmp.height).toInt().coerceIn(1, bmp.height - y)
                                                previewBitmap = Bitmap.createBitmap(bmp, x, y, w, h)
                                                cropLeft = 0.05f
                                                cropTop = 0.05f
                                                cropRight = 0.95f
                                                cropBottom = 0.95f
                                            } catch (e: Exception) {
                                                e.printStackTrace()
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
                                        Icon(Icons.Outlined.Check, contentDescription = "Apply Crop", modifier = Modifier.size(16.dp))
                                        Text("Apply Crop", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        EditTool.AUTO_CLEAN -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Paper Optimizer (+25% Contrast, +10% Brightness)", fontFamily = OutfitFamily, fontSize = 13.sp, color = colors.inkMuted)
                                Button(
                                    onClick = {
                                        contrast = 25f
                                        brightness = 10f
                                    },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Apply", fontFamily = OutfitFamily, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.borderWhisper, thickness = 1.dp)

                // ── Icon-Only Bottom Tool Selection Bar ─────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space16, vertical = d.space12),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolIconChip(
                        icon = Icons.Outlined.AutoFixHigh,
                        contentDescription = "Auto Clean",
                        isSelected = activeTool == EditTool.AUTO_CLEAN,
                        onClick = {
                            activeTool = EditTool.AUTO_CLEAN
                            contrast = 25f
                            brightness = 10f
                        }
                    )
                    ToolIconChip(
                        icon = Icons.Outlined.Crop,
                        contentDescription = "Crop & Rotate",
                        isSelected = activeTool == EditTool.CROP,
                        onClick = { activeTool = EditTool.CROP }
                    )
                    ToolIconChip(
                        icon = Icons.Outlined.LightMode,
                        contentDescription = "Brightness",
                        isSelected = activeTool == EditTool.BRIGHTNESS,
                        onClick = { activeTool = EditTool.BRIGHTNESS }
                    )
                    ToolIconChip(
                        icon = Icons.Outlined.Tune,
                        contentDescription = "Contrast",
                        isSelected = activeTool == EditTool.CONTRAST,
                        onClick = { activeTool = EditTool.CONTRAST }
                    )
                }
            }
        }
    }
}

// Icon-Only Tool Chip (Strict DESIGN.md Compliance)
@Composable
private fun ToolIconChip(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalSplitColors.current

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) colors.inkPrimary else colors.canvasChalk,
        border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isSelected) colors.canvasChalk else colors.inkMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
