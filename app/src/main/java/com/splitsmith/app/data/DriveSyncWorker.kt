package com.splitsmith.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (GoogleDriveManager.hasDrivePermission(applicationContext)) {
                PendingDriveUploadsManager.processPendingQueue(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DriveSyncWorker", "WorkManager Drive sync failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "splitsmith_drive_sync_work"

        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncWorkRequest = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncWorkRequest
                )
            } catch (e: Exception) {
                android.util.Log.e("DriveSyncWorker", "Failed to enqueue DriveSyncWorker: ${e.message}")
            }
        }
    }
}
