package com.splitsmith.app.data

import android.content.Context
import android.net.Uri
import com.splitsmith.app.util.AttachmentCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LocalStorageManager {

    private const val ATTACHMENTS_DIR = "attachments"

    suspend fun saveAttachmentLocally(
        context: Context,
        inputUri: Uri,
        prefix: String = "att"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, ATTACHMENTS_DIR).apply {
                if (!exists()) mkdirs()
            }
            val originalName = AttachmentCompressor.getFileName(context, inputUri).ifBlank { "file" }
            val cleanName = originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFileName = "${prefix}_${System.currentTimeMillis()}_$cleanName"
            val targetFile = File(dir, targetFileName)

            val isPdf = AttachmentCompressor.isPdfUri(context, inputUri)

            if (isPdf) {
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                val compressedFile = AttachmentCompressor.compressAndPrepareImage(context, inputUri)
                if (compressedFile != null && compressedFile.exists()) {
                    compressedFile.copyTo(targetFile, overwrite = true)
                    compressedFile.delete()
                } else {
                    context.contentResolver.openInputStream(inputUri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                Uri.fromFile(targetFile)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteAttachmentLocally(context: Context, uriPath: String): Boolean {
        return try {
            val uri = Uri.parse(uriPath)
            if (uri.scheme == "file" && uri.path != null) {
                val file = File(uri.path!!)
                if (file.exists()) file.delete() else true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
