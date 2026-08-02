package com.splitsmith.app.data

import android.content.Context
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PendingUploadItem(
    val id: String,
    val localUriPath: String,
    val originalLocalUriPath: String = "", // The URI originally stored in Firestore receiptUrls
    val folderCategoryName: String,
    val dateMillis: Long,
    val expenseId: String,
    val isPersonal: Boolean,
    val groupId: String = ""
)

data class PendingDeletionItem(
    val id: String,
    val driveFileId: String
)

object PendingDriveUploadsManager {

    private const val PREF_NAME = "splitsmith_pending_drive_uploads"
    private const val KEY_ITEMS = "pending_items_json"
    private const val KEY_DELETIONS = "pending_deletions_json"
    private val lock = Any()
    @Volatile
    private var isProcessing = false

    // ─── UPLOADS QUEUE ───────────────────────────────────────────────

    fun enqueueUpload(
        context: Context,
        localUri: Uri,
        folderCategoryName: String,
        dateMillis: Long,
        expenseId: String,
        isPersonal: Boolean,
        groupId: String = "",
        originalLocalUriPath: String = ""
    ) {
        synchronized(lock) {
            try {
                val persistentDir = java.io.File(context.filesDir, "pending_drive_uploads").apply { if (!exists()) mkdirs() }

                val originalName = com.splitsmith.app.util.AttachmentCompressor.getFileName(context, localUri)
                val extension = when {
                    originalName.contains(".pdf", ignoreCase = true) -> ".pdf"
                    originalName.contains(".png", ignoreCase = true) -> ".png"
                    originalName.contains(".jpg", ignoreCase = true) || originalName.contains(".jpeg", ignoreCase = true) -> ".jpg"
                    else -> {
                        val mime = try { context.contentResolver.getType(localUri) } catch (_: Exception) { null }
                        when {
                            mime?.contains("pdf") == true -> ".pdf"
                            mime?.contains("png") == true -> ".png"
                            else -> ".jpg"
                        }
                    }
                }
                val fileName = "pending_${System.currentTimeMillis()}_${expenseId.take(6)}$extension"
                val persistentFile = java.io.File(persistentDir, fileName)

                val copied = try {
                    context.contentResolver.openInputStream(localUri)?.use { input ->
                        persistentFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    persistentFile.exists()
                } catch (e: Exception) {
                    if (localUri.scheme == "file" && localUri.path != null) {
                        try {
                            java.io.File(localUri.path!!).copyTo(persistentFile, overwrite = true)
                            persistentFile.exists()
                        } catch (_: Exception) { false }
                    } else false
                }

                val storedUriPath = if (copied) Uri.fromFile(persistentFile).toString() else localUri.toString()
                val effectiveOriginalPath = originalLocalUriPath.ifBlank { localUri.toString() }

                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val existingJson = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
                val array = JSONArray(existingJson)

                val itemObj = JSONObject().apply {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("localUriPath", storedUriPath)
                    put("originalLocalUriPath", effectiveOriginalPath)
                    put("folderCategoryName", folderCategoryName)
                    put("dateMillis", dateMillis)
                    put("expenseId", expenseId)
                    put("isPersonal", isPersonal)
                    put("groupId", groupId)
                }
                array.put(itemObj)

                prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getPendingItems(context: Context): List<PendingUploadItem> {
        synchronized(lock) {
            val list = mutableListOf<PendingUploadItem>()
            try {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val existingJson = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
                val array = JSONArray(existingJson)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        PendingUploadItem(
                            id = obj.optString("id", ""),
                            localUriPath = obj.optString("localUriPath", ""),
                            originalLocalUriPath = obj.optString("originalLocalUriPath", ""),
                            folderCategoryName = obj.optString("folderCategoryName", "General"),
                            dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                            expenseId = obj.optString("expenseId", ""),
                            isPersonal = obj.optBoolean("isPersonal", true),
                            groupId = obj.optString("groupId", "")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }
    }

    fun isExpensePending(context: Context, expenseId: String): Boolean {
        if (expenseId.isBlank()) return false
        return getPendingItems(context).any { it.expenseId == expenseId }
    }

    private fun removeItemById(context: Context, itemId: String) {
        synchronized(lock) {
            try {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val existingJson = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
                val array = JSONArray(existingJson)
                val newArray = JSONArray()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optString("id", "") != itemId) {
                        newArray.put(obj)
                    }
                }
                prefs.edit().putString(KEY_ITEMS, newArray.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ─── DELETIONS QUEUE ─────────────────────────────────────────────

    fun enqueueDeletion(context: Context, fileIds: List<String>) {
        if (fileIds.isEmpty()) return
        synchronized(lock) {
            try {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val existingJson = prefs.getString(KEY_DELETIONS, "[]") ?: "[]"
                val array = JSONArray(existingJson)

                fileIds.filter { it.isNotBlank() }.forEach { fileId ->
                    val obj = JSONObject().apply {
                        put("id", java.util.UUID.randomUUID().toString())
                        put("driveFileId", fileId)
                    }
                    array.put(obj)
                }

                prefs.edit().putString(KEY_DELETIONS, array.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getPendingDeletions(context: Context): List<PendingDeletionItem> {
        synchronized(lock) {
            val list = mutableListOf<PendingDeletionItem>()
            try {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val existingJson = prefs.getString(KEY_DELETIONS, "[]") ?: "[]"
                val array = JSONArray(existingJson)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        PendingDeletionItem(
                            id = obj.optString("id", ""),
                            driveFileId = obj.optString("driveFileId", "")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }
    }

    private fun removeDeletionById(context: Context, itemId: String) {
        synchronized(lock) {
            try {
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val existingJson = prefs.getString(KEY_DELETIONS, "[]") ?: "[]"
                val array = JSONArray(existingJson)
                val newArray = JSONArray()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optString("id", "") != itemId) {
                        newArray.put(obj)
                    }
                }
                prefs.edit().putString(KEY_DELETIONS, newArray.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun processPendingDeletions(context: Context) = withContext(Dispatchers.IO) {
        val items = getPendingDeletions(context)
        if (items.isEmpty()) return@withContext

        for (item in items) {
            try {
                if (item.driveFileId.isNotBlank()) {
                    val success = GoogleDriveManager.deleteFile(context, item.driveFileId)
                    if (success) {
                        removeDeletionById(context, item.id)
                    }
                } else {
                    removeDeletionById(context, item.id)
                }
            } catch (e: Exception) {
                android.util.Log.e("PendingDriveDeletions", "Background deletion failed for ${item.driveFileId}: ${e.message}")
            }
        }
    }

    // ─── QUEUE PROCESSING ────────────────────────────────────────────

    suspend fun processPendingQueue(context: Context) = withContext(Dispatchers.IO) {
        if (isProcessing) return@withContext
        isProcessing = true

        try {
            // Process deletions first
            processPendingDeletions(context)

            if (!GoogleDriveManager.hasDrivePermission(context)) {
                android.util.Log.w("PendingDriveUploads", "Process queue skipped: Drive permission not granted on device")
                isProcessing = false
                return@withContext
            }

            val items = getPendingItems(context)
            if (items.isEmpty()) {
                isProcessing = false
                return@withContext
            }

            var uploadedCount = 0

            for (item in items) {
                try {
                    val uri = Uri.parse(item.localUriPath)
                    val driveResult = GoogleDriveManager.uploadAttachment(
                        context = context,
                        inputUri = uri,
                        folderCategoryName = item.folderCategoryName,
                        dateMillis = item.dateMillis,
                        expenseId = item.expenseId
                    )

                    when (driveResult) {
                        is DriveUploadResult.Success -> {
                            try {
                                val localPath = item.originalLocalUriPath.ifBlank { item.localUriPath }
                                if (item.isPersonal) {
                                    FirebaseManager.attachDriveFileToPersonalExpense(
                                        expenseId = item.expenseId,
                                        driveFileId = driveResult.fileId,
                                        webUrl = driveResult.webViewLink,
                                        localUriPath = localPath
                                    )
                                } else if (item.groupId.isNotEmpty()) {
                                    FirebaseManager.attachDriveFileToGroupExpense(
                                        groupId = item.groupId,
                                        expenseId = item.expenseId,
                                        driveFileId = driveResult.fileId,
                                        webUrl = driveResult.webViewLink,
                                        localUriPath = localPath
                                    )
                                } else {
                                    FirebaseManager.attachDriveFileToDirectSplit(
                                        splitId = item.expenseId,
                                        driveFileId = driveResult.fileId,
                                        webUrl = driveResult.webViewLink,
                                        localUriPath = localPath
                                    )
                                }
                            } catch (attachError: Exception) {
                                android.util.Log.e("PendingDriveUploads", "Firestore attach failed (Drive file exists: ${driveResult.fileId}): ${attachError.message}")
                                continue
                            }

                            uploadedCount++
                            removeItemById(context, item.id)

                            try {
                                if (uri.scheme == "file" && uri.path != null) {
                                    val file = java.io.File(uri.path!!)
                                    if (file.absolutePath.contains("pending_drive_uploads")) {
                                        file.delete()
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        is DriveUploadResult.Failure -> {
                            android.util.Log.e("PendingDriveUploads", "Queue upload failed [${driveResult.error}]: ${driveResult.message}")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (uploadedCount > 0) {
                withContext(Dispatchers.Main) {
                    val fileText = if (uploadedCount == 1) "1 file" else "$uploadedCount files"
                    Toast.makeText(context, "Google Drive backup complete ($fileText uploaded)", Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            isProcessing = false
        }
    }
}
