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
    val folderCategoryName: String,
    val dateMillis: Long,
    val expenseId: String,
    val isPersonal: Boolean,
    val groupId: String = ""
)

object PendingDriveUploadsManager {

    private const val PREF_NAME = "splitsmith_pending_drive_uploads"
    private const val KEY_ITEMS = "pending_items_json"

    fun enqueueUpload(
        context: Context,
        localUri: Uri,
        folderCategoryName: String,
        dateMillis: Long,
        expenseId: String,
        isPersonal: Boolean,
        groupId: String = ""
    ) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val existingJson = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
            val array = JSONArray(existingJson)

            val itemObj = JSONObject().apply {
                put("id", java.util.UUID.randomUUID().toString())
                put("localUriPath", localUri.toString())
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

    fun getPendingItems(context: Context): List<PendingUploadItem> {
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

    fun isExpensePending(context: Context, expenseId: String): Boolean {
        if (expenseId.isBlank()) return false
        return getPendingItems(context).any { it.expenseId == expenseId }
    }

    suspend fun processPendingQueue(context: Context) = withContext(Dispatchers.IO) {
        val items = getPendingItems(context)
        if (items.isEmpty()) return@withContext

        var uploadedCount = 0
        val remaining = mutableListOf<PendingUploadItem>()

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

                if (driveResult != null) {
                    uploadedCount++
                    if (item.isPersonal) {
                        FirebaseManager.attachDriveFileToPersonalExpense(
                            expenseId = item.expenseId,
                            driveFileId = driveResult.fileId,
                            webUrl = driveResult.webViewLink
                        )
                    } else if (item.groupId.isNotEmpty()) {
                        FirebaseManager.attachDriveFileToGroupExpense(
                            groupId = item.groupId,
                            expenseId = item.expenseId,
                            driveFileId = driveResult.fileId,
                            webUrl = driveResult.webViewLink
                        )
                    }
                } else {
                    remaining.add(item)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                remaining.add(item)
            }
        }

        saveRemainingItems(context, remaining)

        if (uploadedCount > 0) {
            withContext(Dispatchers.Main) {
                val fileText = if (uploadedCount == 1) "1 file" else "$uploadedCount files"
                Toast.makeText(context, "Google Drive backup complete ($fileText uploaded)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveRemainingItems(context: Context, remaining: List<PendingUploadItem>) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            remaining.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("localUriPath", item.localUriPath)
                    put("folderCategoryName", item.folderCategoryName)
                    put("dateMillis", item.dateMillis)
                    put("expenseId", item.expenseId)
                    put("isPersonal", item.isPersonal)
                    put("groupId", item.groupId)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
