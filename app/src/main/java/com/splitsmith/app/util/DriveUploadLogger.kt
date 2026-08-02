package com.splitsmith.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DriveUploadLogger {
    private const val TAG = "DriveUploadLogger"
    private const val LOG_FILE_NAME = "drive_upload_errors.log"

    fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        // Log to Logcat
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }

        // Write to local log file
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val errorDetails = throwable?.let { Log.getStackTraceString(it) } ?: ""
            val logMessage = "[$timeStamp] [$tag] $message\n$errorDetails\n\n"

            FileWriter(logFile, true).use { writer ->
                writer.append(logMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to drive_upload_errors.log", e)
        }
    }
}
