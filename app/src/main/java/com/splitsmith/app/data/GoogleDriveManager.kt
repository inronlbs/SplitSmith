package com.splitsmith.app.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.splitsmith.app.util.AttachmentCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

data class DriveFileResult(
    val fileId: String,
    val webViewLink: String
)

object GoogleDriveManager {

    private const val ROOT_FOLDER_NAME = "SplitSmith"

    fun hasDrivePermission(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
    }

    fun requestDrivePermission(launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>, context: Context) {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
            .build()
        val intent = GoogleSignIn.getClient(context, gso).signInIntent
        launcher.launch(intent)
    }

    private fun getDriveService(context: Context): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("SplitSmith").build()
    }

    private suspend fun findOrCreateFolder(
        driveService: Drive,
        folderName: String,
        parentId: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            var query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
            if (parentId != null) {
                query += " and '$parentId' in parents"
            } else {
                query += " and 'root' in parents"
            }

            val resultList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val existingFolder = resultList.files.firstOrNull()
            if (existingFolder != null) {
                return@withContext existingFolder.id
            }

            // Folder doesn't exist, create it
            val folderMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
                if (parentId != null) {
                    parents = Collections.singletonList(parentId)
                }
            }

            val createdFolder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()

            return@withContext createdFolder.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun uploadAttachment(
        context: Context,
        inputUri: Uri,
        folderCategoryName: String, // "Personal Expenses" or Group Name
        dateMillis: Long,
        expenseId: String
    ): DriveFileResult? = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(context) ?: return@withContext null

            // 1. Root SplitSmith folder
            val rootFolderId = findOrCreateFolder(driveService, ROOT_FOLDER_NAME) ?: return@withContext null

            // 2. Category / Group folder
            val categoryFolderId = findOrCreateFolder(driveService, folderCategoryName, rootFolderId) ?: return@withContext null

            // 3. Month folder (e.g. 2026-07)
            val monthString = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(if (dateMillis > 0) dateMillis else System.currentTimeMillis()))
            val monthFolderId = findOrCreateFolder(driveService, monthString, categoryFolderId) ?: return@withContext null

            val isPdf = AttachmentCompressor.isPdfUri(context, inputUri)
            val originalName = AttachmentCompressor.getFileName(context, inputUri)

            val uploadFile: java.io.File?
            val mimeType: String

            if (isPdf) {
                // Copy PDF input stream to temp cache file
                val tempPdfFile = java.io.File(context.cacheDir, "upload_$originalName")
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    tempPdfFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                uploadFile = tempPdfFile
                mimeType = "application/pdf"
            } else {
                // Compress Image
                uploadFile = AttachmentCompressor.compressAndPrepareImage(context, inputUri)
                mimeType = "image/jpeg"
            }

            if (uploadFile == null || !uploadFile.exists()) return@withContext null

            val formattedDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(if (dateMillis > 0) dateMillis else System.currentTimeMillis()))
            val cleanExpenseId = if (expenseId.isNotBlank()) expenseId.take(6) else "exp"
            val targetFileName = "${cleanExpenseId}_${formattedDate}_$originalName"

            val fileMetadata = File().apply {
                name = targetFileName
                parents = Collections.singletonList(monthFolderId)
            }

            val mediaContent = InputStreamContent(mimeType, FileInputStream(uploadFile))

            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink, webContentLink")
                .execute()

            // Clean up temp local file
            uploadFile.delete()

            if (uploadedFile != null && uploadedFile.id != null) {
                val link = uploadedFile.webViewLink ?: "https://drive.google.com/file/d/${uploadedFile.id}/view"
                return@withContext DriveFileResult(uploadedFile.id, link)
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteFile(context: Context, fileId: String): Boolean = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext true
        try {
            val driveService = getDriveService(context) ?: return@withContext false
            driveService.files().delete(fileId).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteFiles(context: Context, fileIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (fileIds.isEmpty()) return@withContext true
        var allSuccessful = true
        for (id in fileIds) {
            if (id.isNotBlank()) {
                val success = deleteFile(context, id)
                if (!success) allSuccessful = false
            }
        }
        allSuccessful
    }
}
