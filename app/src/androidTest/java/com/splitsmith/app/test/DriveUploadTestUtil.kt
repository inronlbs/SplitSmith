package com.splitsmith.app.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitsmith.app.data.GoogleDriveManager
import com.splitsmith.app.migration.FolderMigration
import com.splitsmith.app.util.DriveUploadLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveUploadTestUtil {

    @Test
    fun testLoggerCreatesFile() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        DriveUploadLogger.logError(appContext, "Test", "This is a test error log")
        
        val logFile = java.io.File(appContext.filesDir, "drive_upload_errors.log")
        assertNotNull("Log file should be created", logFile)
        assert(logFile.exists()) { "Log file does not exist" }
    }

    @Test
    fun testFolderMigrationRunsWithoutCrash() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            // Should just return early if no Google Account is signed in during tests
            FolderMigration.runFolderMigration(appContext)
        } catch (e: Exception) {
            assert(false) { "Migration threw an exception: ${e.message}" }
        }
    }
}
