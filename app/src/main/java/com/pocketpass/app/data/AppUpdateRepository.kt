package com.pocketpass.app.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.io.File

class AppUpdateRepository(private val context: Context) {

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

    suspend fun checkForUpdate(): AppVersion? {
        return try {
            val current = getCurrentVersionCode()
            val result = SupabaseClient.client.postgrest
                .from("app_versions")
                .select {
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<AppVersion>()

            val latest = result.firstOrNull()
            if (latest != null && latest.versionCode > current) latest else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
