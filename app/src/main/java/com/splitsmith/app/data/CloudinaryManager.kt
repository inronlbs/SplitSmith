package com.splitsmith.app.data

import com.splitsmith.app.BuildConfig
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume

object CloudinaryManager {
    private const val TAG = "CloudinaryManager"
    
    var cloudName: String = "utxy5ghe"
    var uploadPreset: String = "splitsmith_receipts"
    
    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        val config = mapOf(
                            "cloud_name" to cloudName
                        )
                        MediaManager.init(context.applicationContext, config)
                        isInitialized = true
                        Log.d(TAG, "Cloudinary MediaManager initialized successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing Cloudinary: ${e.message}")
                    }
                }
            }
        }
    }

    private fun deriveKey(userId: String): ByteArray {
        val seed = "SplitSmith_Secret_Receipt_Key_$userId"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    fun xorTransform(data: ByteArray, userId: String): ByteArray {
        val effectiveUserId = if (userId.isBlank()) {
            android.util.Log.w("CloudinaryManager", "xorTransform called with blank userId — using fallback key. File will still be encrypted.")
            "splitsmith_fallback_user"
        } else {
            userId
        }
        val key = deriveKey(effectiveUserId)
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return result
    }

    /**
     * Compresses the image at [imageUri] to max 1280px dimension and ~80% JPEG quality,
     * encrypts bytes via XOR transformation for privacy, and uploads to Cloudinary as raw binary.
     * Returns the secure HTTPS URL of the uploaded asset.
     */
    suspend fun uploadReceipt(
        context: Context,
        imageUri: Uri,
        userId: String = "anonymous",
        category: String = "general"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            init(context)
            
            val isPdf = com.splitsmith.app.util.AttachmentCompressor.isPdfUri(context, imageUri)
            val compressedFile: File = if (isPdf) {
                // High-density 2048px / 85% JPEG compression for PDFs & digital invoices
                com.splitsmith.app.util.AttachmentCompressor.compressPdf(context, imageUri, maxDimension = 2048, quality = 85)
                    ?: run {
                        // Fallback: copy raw PDF stream directly
                        val tempPdf = File.createTempFile("raw_pdf_", ".pdf", context.cacheDir)
                        context.contentResolver.openInputStream(imageUri)?.use { input ->
                            FileOutputStream(tempPdf).use { output -> input.copyTo(output) }
                        }
                        if (tempPdf.exists() && tempPdf.length() > 0) tempPdf else null
                    }
                    ?: return@withContext Result.failure(Exception("Failed to process PDF file"))
            } else {
                createCompressedTempFile(context, imageUri)
                    ?: return@withContext Result.failure(Exception("Failed to process image file"))
            }

            // 2. XOR byte encryption to protect file privacy on Cloudinary
            val rawBytes = compressedFile.readBytes()
            val encBytes = xorTransform(rawBytes, userId)
            
            val encFile = File.createTempFile("receipt_enc_", ".enc", context.cacheDir)
            FileOutputStream(encFile).use { it.write(encBytes) }

            val cleanCategory = category.trim().lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "general" }
            val fileSuffix = if (isPdf) ".pdf.enc" else ".enc"
            val securePublicId = "receipts/$userId/$cleanCategory/${UUID.randomUUID()}$fileSuffix"

            // 3. Execute upload via Cloudinary SDK
            suspendCancellableCoroutine { continuation ->
                try {
                    val requestId = MediaManager.get().upload(Uri.fromFile(encFile))
                        .unsigned(uploadPreset)
                        .option("public_id", securePublicId)
                        .option("resource_type", "raw")
                        .callback(object : UploadCallback {
                            override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                                compressedFile.delete()
                                encFile.delete()
                                val secureUrl = resultData["secure_url"] as? String
                                if (!secureUrl.isNullOrBlank()) {
                                    Log.d(TAG, "Cloudinary upload success: $secureUrl")
                                    continuation.resume(Result.success(secureUrl))
                                } else {
                                    continuation.resume(Result.failure(Exception("Cloudinary returned empty secure_url")))
                                }
                            }

                            override fun onError(requestId: String, error: ErrorInfo) {
                                compressedFile.delete()
                                encFile.delete()
                                Log.e(TAG, "Cloudinary upload error: ${error.description}")
                                continuation.resume(Result.failure(Exception(error.description)))
                            }

                            override fun onStart(requestId: String) {}
                            override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                            override fun onReschedule(requestId: String, error: ErrorInfo) {}
                        })
                        .dispatch()

                    continuation.invokeOnCancellation {
                        try {
                            MediaManager.get().cancelRequest(requestId)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cancel Cloudinary request: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    compressedFile.delete()
                    encFile.delete()
                    continuation.resume(Result.failure(e))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in uploadReceipt: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Resizes and compresses an imageUri to max 1280px width/height and ~80% quality.
     * This strips EXIF GPS tags automatically.
     */
    private fun createCompressedTempFile(context: Context, imageUri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val maxDimension = 1280
            val width = originalBitmap.width
            val height = originalBitmap.height

            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val scale = maxDimension.toFloat() / Math.max(width, height)
                val newW = (width * scale).toInt()
                val newH = (height * scale).toInt()
                Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
            } else {
                originalBitmap
            }

            val tempFile = File.createTempFile("receipt_upload_", ".jpg", context.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.flush()
            outputStream.close()

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image: ${e.message}", e)
            null
        }
    }

    fun extractPublicId(url: String): String? {
        val anchor = "/raw/upload/"
        val index = url.indexOf(anchor)
        if (index == -1) return null
        val path = url.substring(index + anchor.length)
        // Strip the version segment if it exists (e.g. "v1234567890/receipts/...")
        val versionRegex = Regex("^v\\d+/")
        return path.replaceFirst(versionRegex, "")
    }

    suspend fun deleteReceipt(secureUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.CLOUDINARY_API_KEY
        val apiSecret = BuildConfig.CLOUDINARY_API_SECRET
        
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            Log.w(TAG, "Cloudinary API key or secret is blank. Deletion skipped.")
            return@withContext Result.failure(Exception("Cloudinary API Key or Secret is not configured."))
        }
        
        val publicId = extractPublicId(secureUrl) ?: return@withContext Result.failure(Exception("Failed to extract public ID from URL: $secureUrl"))
        
        try {
            val config = mapOf(
                "cloud_name" to cloudName,
                "api_key" to apiKey,
                "api_secret" to apiSecret
            )
            val cloudinary = com.cloudinary.Cloudinary(config)
            val result = cloudinary.uploader().destroy(publicId, mapOf("resource_type" to "raw", "invalidate" to true))
            val resultStatus = result["result"] as? String
            if (resultStatus == "ok" || resultStatus == "not_found") {
                Log.d(TAG, "Successfully deleted or already deleted asset from Cloudinary: $publicId")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Cloudinary destroy returned status: $resultStatus for $publicId")
                Result.failure(Exception("Cloudinary destroy failed: $resultStatus"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Cloudinary asset: ${e.message}", e)
            Result.failure(e)
        }
    }
}
