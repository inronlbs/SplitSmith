package com.splitsmith.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AttachmentCompressor {

    const val MAX_ATTACHMENTS = 5
    private const val MAX_DIMENSION = 2048
    private const val COMPRESSION_QUALITY = 82

    data class AttachmentItem(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long = 0,
        val isPdf: Boolean = false
    )

    fun createTempImageUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = File(context.cacheDir, "camera_photos")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    fun applyColorMatrixTransform(
        sourceBitmap: Bitmap,
        brightness: Float, // -100f to +100f
        contrast: Float    // -100f to +100f
    ): Bitmap {
        val output = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, sourceBitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Contrast scale
        val contrastScale = ((contrast + 100f) / 100f).coerceAtLeast(0f)
        val contrastTranslate = (-0.5f * contrastScale + 0.5f) * 255f

        // Brightness offset
        val brightnessOffset = brightness

        val cm = ColorMatrix(floatArrayOf(
            contrastScale, 0f,            0f,            0f, contrastTranslate + brightnessOffset,
            0f,            contrastScale, 0f,            0f, contrastTranslate + brightnessOffset,
            0f,            0f,            contrastScale, 0f, contrastTranslate + brightnessOffset,
            0f,            0f,            0f,            1f, 0f
        ))

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)
        return output
    }

    fun compressAndPrepareImage(context: Context, inputUri: Uri, brightness: Float = 0f, contrast: Float = 0f): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(inputUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return null

            val rotatedBitmap = rotateBitmapIfRequired(context, inputUri, bitmap)
            val scaledBitmap = scaleBitmapToMaxDimension(rotatedBitmap, MAX_DIMENSION)

            val finalBitmap = if (brightness != 0f || contrast != 0f) {
                val enhanced = applyColorMatrixTransform(scaledBitmap, brightness, contrast)
                if (enhanced != scaledBitmap && enhanced != rotatedBitmap && enhanced != bitmap) {
                    if (scaledBitmap != rotatedBitmap && scaledBitmap != bitmap) scaledBitmap.recycle()
                }
                enhanced
            } else {
                scaledBitmap
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val compressedFile = File(context.cacheDir, "receipt_$timeStamp.jpg")

            val outputStream = FileOutputStream(compressedFile)
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()

            if (finalBitmap != bitmap && finalBitmap != rotatedBitmap) {
                finalBitmap.recycle()
            }
            if (rotatedBitmap != bitmap) {
                rotatedBitmap.recycle()
            }
            bitmap.recycle()

            compressedFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmapIfRequired(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val ei = ExifInterface(inputStream)
            val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            inputStream.close()

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun scaleBitmapToMaxDimension(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (width > height) {
            targetWidth = maxDim
            targetHeight = (maxDim / ratio).toInt()
        } else {
            targetHeight = maxDim
            targetWidth = (maxDim * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name = "attachment"
        if (uri.scheme == "file") {
            // For file:// URIs, ContentResolver.query doesn't work — use path directly
            val segment = uri.lastPathSegment
            if (!segment.isNullOrBlank()) {
                name = segment
            }
            return name
        }
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } catch (e: Exception) {
            // Fallback to lastPathSegment for unknown URI schemes
            val segment = uri.lastPathSegment
            if (!segment.isNullOrBlank()) {
                name = segment
            }
        }
        return name
    }

    fun isPdfUri(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null && mimeType.contains("pdf", ignoreCase = true)) return true
        val name = getFileName(context, uri)
        return name.endsWith(".pdf", ignoreCase = true)
    }
}
