package com.pocketpass.app.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.URL

class AppUpdateRepository(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val GITHUB_REPO = "Hinoaaaaaf212/pocketpass-release"
        private const val GITHUB_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    fun getCurrentVersionCode(): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    fun getCurrentVersionName(): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    }

    suspend fun checkForUpdate(): AppVersion? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(GITHUB_API).openConnection().apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            val json = connection.getInputStream().bufferedReader().readText()
            val release = org.json.JSONObject(json)

            val tagName = release.getString("tag_name") // e.g. "v1.5"
            val body = release.optString("body", "")
            val assets = release.getJSONArray("assets")

            // Find the APK asset
            var downloadUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (downloadUrl == null) return@withContext null

            // Compare by version name since versionCode doesn't match tag scheme
            val versionName = tagName.removePrefix("v")
            val currentVersionName = getCurrentVersionName()

            if (versionName == currentVersionName || !isNewerVersion(versionName, currentVersionName)) {
                return@withContext null
            }

            AppVersion(
                versionCode = getCurrentVersionCode() + 1,
                versionName = versionName,
                downloadUrl = downloadUrl,
                changelog = body
            )
        } catch (e: Exception) {
            Log.e(TAG, "Check for update failed", e)
            null
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun getApkFile(): File {
        val updatesDir = File(context.externalCacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()
        return File(updatesDir, "pocketpass-update.apk")
    }

    fun downloadApk(version: AppVersion): Long {
        val apkFile = getApkFile()
        if (apkFile.exists()) apkFile.delete()

        val request = DownloadManager.Request(Uri.parse(version.downloadUrl))
            .setTitle("PocketPass v${version.versionName}")
            .setDescription("Downloading update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(apkFile))

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun getDownloadProgress(downloadId: Long): DownloadProgress {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()

            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadProgress.Complete
                DownloadManager.STATUS_FAILED -> DownloadProgress.Failed
                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal) else 0f
                    DownloadProgress.Downloading(progress)
                }
                else -> DownloadProgress.Downloading(0f)
            }
        }
        cursor?.close()
        return DownloadProgress.Failed
    }

    fun installApk() {
        val apkFile = getApkFile()
        if (!apkFile.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun canInstallPackages(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

sealed class DownloadProgress {
    data class Downloading(val progress: Float) : DownloadProgress()
    data object Complete : DownloadProgress()
    data object Failed : DownloadProgress()
}
