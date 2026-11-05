package com.splitsmith.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    /**
     * Downscales an image from [uri] so its maximum dimension is at most [maxDimension],
     * and compresses it into a WebP Base64 data URI string ("data:image/webp;base64,...").
     */
    fun compressImageUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 180,
        quality: Int = 75
    ): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1f) {
                    val targetW = maxDimension
                    val targetH = (maxDimension / ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                } else {
                    val targetH = maxDimension
                    val targetW = (maxDimension * ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                }
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            scale.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
            val bytes = outputStream.toByteArray()
            "data:image/webp;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
