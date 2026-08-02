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

data class DriveFileResult(val fileId: String, val webViewLink: String, val webContentLink: String = "")

enum class DriveError {
    SCOPE_MISSING, TOKEN_EXPIRED, NETWORK_ERROR, FILE_NOT_FOUND, UNKNOWN
}

sealed class DriveUploadResult {
    data class Success(val fileId: String, val webViewLink: String, val webContentLink: String) : DriveUploadResult()
    data class Failure(val error: DriveError, val message: String) : DriveUploadResult()
}

object GoogleDriveManager {

    private const val ROOT_FOLDER_NAME = "SplitSmith"

    fun hasDrivePermission(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
    }

    fun handleDrivePermissionResult(intentData: android.content.Intent?): Boolean {
        if (intentData == null) return false
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intentData)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            account != null && GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun requestDrivePermission(launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>, context: Context) {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val webClientId = if (resId != 0) context.getString(resId) else "545492492856-hi7e0d7su8duvi27s8g0ob61a5pbq94p.apps.googleusercontent.com"

        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .apply {
                if (webClientId.isNotEmpty()) {
                    requestIdToken(webClientId)
                }
            }
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
            .build()
        val intent = GoogleSignIn.getClient(context, gso).signInIntent
        launcher.launch(intent)
    }

    private fun getDriveService(context: Context): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        // Verify the DRIVE_FILE scope was actually granted
        if (!GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))) {
            android.util.Log.w("GoogleDriveManager", "Drive scope not granted – skipping upload")
            return null
        }
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        val accountName = account.email ?: account.account?.name
        if (account.account != null) {
            credential.selectedAccount = account.account
        } else if (accountName != null) {
            credential.selectedAccountName = accountName
        }

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
            }

            val resultList = try {
                driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute()
            } catch (e: Exception) {
                null
            }

            val existingFolder = resultList?.files?.firstOrNull()
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

            return@withContext createdFolder?.id
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
    ): DriveUploadResult = withContext(Dispatchers.IO) {
        try {
            if (!hasDrivePermission(context)) {
                android.util.Log.e("GoogleDriveManager", "uploadAttachment FAILED: DRIVE_FILE scope not granted")
                return@withContext DriveUploadResult.Failure(DriveError.SCOPE_MISSING, "Google Drive permission not granted. Tap to grant access.")
            }

            val driveService = getDriveService(context)
                ?: return@withContext DriveUploadResult.Failure(DriveError.TOKEN_EXPIRED, "Sign-in token expired or account unavailable. Tap to re-authenticate.")

            // 1. Root SplitSmith folder
            val rootFolderId = findOrCreateFolder(driveService, ROOT_FOLDER_NAME)
                ?: return@withContext DriveUploadResult.Failure(DriveError.NETWORK_ERROR, "Could not create Drive folder. Check your internet connection.")

            // 2. Category / Group folder
            val categoryFolderId = findOrCreateFolder(driveService, folderCategoryName, rootFolderId)
                ?: return@withContext DriveUploadResult.Failure(DriveError.NETWORK_ERROR, "Could not create Drive category folder.")

            // 3. Month folder (e.g. 2026-07)
            val monthString = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(if (dateMillis > 0) dateMillis else System.currentTimeMillis()))
            val monthFolderId = findOrCreateFolder(driveService, monthString, categoryFolderId)
                ?: return@withContext DriveUploadResult.Failure(DriveError.NETWORK_ERROR, "Could not create Drive month folder.")

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

            if (uploadFile == null || !uploadFile.exists()) return@withContext DriveUploadResult.Failure(DriveError.FILE_NOT_FOUND, "Could not read attachment file for upload.")

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

            // Make file viewable to anyone with the link (for group members / split partners)
            try {
                val permission = com.google.api.services.drive.model.Permission()
                    .setType("anyone")
                    .setRole("reader")
                driveService.permissions().create(uploadedFile.id, permission).execute()
            } catch (permError: Exception) {
                android.util.Log.w("GoogleDriveManager", "Could not set public view permission: ${permError.message}")
            }

            // Clean up temp local file
            uploadFile.delete()

            if (uploadedFile != null && uploadedFile.id != null) {
                val link = uploadedFile.webViewLink ?: "https://drive.google.com/file/d/${uploadedFile.id}/view"
                val contentLink = uploadedFile.webContentLink ?: "https://drive.google.com/uc?id=${uploadedFile.id}&export=download"
                android.util.Log.i("GoogleDriveManager", "Upload SUCCESS: fileId=${uploadedFile.id}")
                return@withContext DriveUploadResult.Success(uploadedFile.id, link, contentLink)
            }
            return@withContext DriveUploadResult.Failure(DriveError.UNKNOWN, "Drive upload returned no file ID.")
        } catch (e: java.io.IOException) {
            android.util.Log.e("GoogleDriveManager", "uploadAttachment NETWORK_ERROR", e)
            DriveUploadResult.Failure(DriveError.NETWORK_ERROR, "Network error during upload: ${e.message}")
        } catch (e: com.google.api.client.auth.oauth2.TokenResponseException) {
            android.util.Log.e("GoogleDriveManager", "uploadAttachment TOKEN_EXPIRED", e)
            DriveUploadResult.Failure(DriveError.TOKEN_EXPIRED, "Sign-in token expired. Tap to re-authenticate.")
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveManager", "uploadAttachment UNKNOWN", e)
            DriveUploadResult.Failure(DriveError.UNKNOWN, "Unexpected error: ${e.message ?: "Unknown error"}")
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
