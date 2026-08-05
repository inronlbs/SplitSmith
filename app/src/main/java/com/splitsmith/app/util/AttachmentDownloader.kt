package com.splitsmith.app.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AttachmentDownloader {

    suspend fun getOrFetchAttachment(
        context: Context,
        urlOrPath: String,
        driveFileId: String = ""
    ): Uri? = withContext(Dispatchers.IO) {
        if (urlOrPath.isBlank()) return@withContext null

        val parsedUri = Uri.parse(urlOrPath)

        // 1. If it's a local file that actually exists on this device's storage
        if (parsedUri.scheme == "file" && parsedUri.path != null) {
            val localFile = File(parsedUri.path!!)
            if (localFile.exists()) {
                return@withContext parsedUri
            }
        }

        // 2. If it's a content:// URI
        if (parsedUri.scheme == "content") {
            return@withContext parsedUri
        }

        // 3. If file path is local to another device, or it's a web/Drive link:
        // Resolve download URL
        val targetDownloadUrl = when {
            driveFileId.isNotBlank() -> "https://drive.google.com/uc?id=$driveFileId&export=download"
            urlOrPath.contains("uc?id=") -> urlOrPath
            urlOrPath.contains("drive.google.com/file/d/") -> {
                val fileId = urlOrPath.substringAfter("/file/d/").substringBefore("/")
                "https://drive.google.com/uc?id=$fileId&export=download"
            }
            urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://") -> urlOrPath
            else -> null
        } ?: return@withContext null

        val cleanId = if (driveFileId.isNotBlank()) driveFileId else urlOrPath.hashCode().toString()
        val cacheFile = File(context.cacheDir, "shared_receipt_$cleanId.jpg")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            return@withContext Uri.fromFile(cacheFile)
        }

        // Stream from web / Google Drive into cache
        try {
            val connection = URL(targetDownloadUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val isEncrypted = targetDownloadUrl.contains(".enc") || urlOrPath.contains(".enc")
                if (isEncrypted) {
                    val userId = if (targetDownloadUrl.contains("/receipts/")) {
                        targetDownloadUrl.substringAfter("/receipts/").substringBefore("/")
                    } else ""
                    val downloadedBytes = connection.inputStream.use { it.readBytes() }
                    val decryptedBytes = com.splitsmith.app.data.CloudinaryManager.xorTransform(downloadedBytes, userId)
                    FileOutputStream(cacheFile).use { it.write(decryptedBytes) }
                } else {
                    connection.inputStream.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    return@withContext Uri.fromFile(cacheFile)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttachmentDownloader", "Failed to fetch shared attachment: ${e.message}")
        }

        null
    }
}
