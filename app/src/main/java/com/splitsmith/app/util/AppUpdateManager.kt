package com.splitsmith.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isNewer: Boolean
)

object AppUpdateManager {
    // Default repository details - modify as needed or configure dynamically
    var githubOwner: String = "inronlbs"
    var githubRepo: String = "SplitSmith"
    const val CURRENT_VERSION_NAME = "v1.2.0"

    /**
     * Checks GitHub Releases API for the latest release.
     * URL format: https://api.github.com/repos/{owner}/{repo}/releases/latest
     */
    suspend fun checkForUpdates(
        owner: String = githubOwner,
        repo: String = githubRepo,
        currentVersion: String = CURRENT_VERSION_NAME
    ): AppReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "SplitSmith-App")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val tagName = json.optString("tag_name", "")
                val body = json.optString("body", "No release notes provided.")
                
                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                    if (downloadUrl.isEmpty() && assets.length() > 0) {
                        downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                    }
                }

                if (downloadUrl.isEmpty()) {
                    downloadUrl = json.optString("html_url", "https://github.com/$owner/$repo/releases")
                }

                val cleanLatest = tagName.trim().removePrefix("v").removePrefix("V")
                val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

                val isNewer = isVersionNewer(cleanLatest, cleanCurrent)

                AppReleaseInfo(
                    tagName = tagName,
                    versionName = cleanLatest,
                    releaseNotes = body,
                    downloadUrl = downloadUrl,
                    isNewer = isNewer
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Compares semantic version strings (e.g. 1.2.1 vs 1.2.0)
     */
    private fun isVersionNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(latestParts.size, currentParts.size)

            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (e: Exception) {
            return latest != current
        }
        return false
    }

    /**
     * Downloads the APK using Android's DownloadManager and triggers package installation upon completion.
     */
    fun downloadAndInstallApk(context: Context, downloadUrl: String, fileName: String = "SplitSmith-Update.apk") {
        try {
            val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Downloading SplitSmith Update")
                setDescription("Downloading latest version from GitHub...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverRoaming(true)
                setAllowedOverMetered(true)
                addRequestHeader("User-Agent", "SplitSmith-App")
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try {
                            ctxt.unregisterReceiver(this)
                        } catch (e: Exception) {}
                        installApk(ctxt, destinationFile)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    onComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    onComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            openInBrowser(context, downloadUrl)
        }
    }

    /**
     * Opens direct URL in system browser as fallback
     */
    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Triggers package installer via FileProvider
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
