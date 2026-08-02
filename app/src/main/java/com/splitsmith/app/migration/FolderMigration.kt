package com.splitsmith.app.migration

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.splitsmith.app.util.DriveUploadLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object FolderMigration {

    private const val TAG = "FolderMigration"
    private const val PREF_NAME = "MigrationPrefs"
    private const val KEY_SPLITSMITH_ORPHAN_MIGRATED = "splitsmith_orphan_migrated"

    suspend fun runFolderMigration(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SPLITSMITH_ORPHAN_MIGRATED, false)) {
            return@withContext
        }

        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext
        if (!GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))) {
            return@withContext
        }

        try {
            val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = account.account ?: return@withContext

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("SplitSmith").build()

            // Find all SplitSmith folders
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = 'SplitSmith' and trashed = false"
            val resultList = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name, parents)")
                .execute()

            val folders = resultList.files ?: emptyList()
            var migratedCount = 0

            for (folder in folders) {
                val parents = folder.parents
                // If it doesn't have 'root' as a parent, move it
                if (parents == null || !parents.contains("root")) {
                    val previousParents = parents?.joinToString(",") ?: ""
                    
                    driveService.files().update(folder.id, null)
                        .setAddParents("root")
                        .setRemoveParents(previousParents)
                        .setFields("id, parents")
                        .execute()
                        
                    migratedCount++
                    DriveUploadLogger.logError(context, TAG, "Migrated orphaned SplitSmith folder ${folder.id} to root.")
                }
            }

            // Mark migration as done
            prefs.edit().putBoolean(KEY_SPLITSMITH_ORPHAN_MIGRATED, true).apply()
            DriveUploadLogger.logError(context, TAG, "Folder migration completed. Migrated $migratedCount folders.")
            
        } catch (e: Exception) {
            DriveUploadLogger.logError(context, TAG, "Folder migration failed", e)
        }
    }
}
