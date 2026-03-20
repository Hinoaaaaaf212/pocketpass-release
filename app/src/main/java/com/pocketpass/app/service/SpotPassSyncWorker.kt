package com.pocketpass.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pocketpass.app.MainActivity
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.SpotPassRepository
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.util.LedController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SpotPassSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SpotPassSyncWorker"
        private const val WORK_NAME = "spotpass_periodic_sync"
        private const val CHANNEL_ID = "pocketpass_spotpass_channel"
        private const val NOTIFICATION_ID = 500

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SpotPassSyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "SpotPass periodic sync enqueued")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val authRepo = AuthRepository()
            if (authRepo.currentUserId == null) return@withContext Result.success()

            val repo = SpotPassRepository(applicationContext)
            val newCount = repo.syncFromServer()

            if (newCount > 0) {
                val prefs = UserPreferences(applicationContext)
                prefs.setSpotPassUnread(newCount)

                showNotification(newCount)

                // Blink LED on Ayn Thor
                val led = LedController()
                if (led.isAvailable) {
                    led.blinkGreen(kotlinx.coroutines.CoroutineScope(Dispatchers.IO), times = 2)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SpotPass sync worker failed: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun showNotification(count: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "WaveLink Content",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "New content delivered via WaveLink"
                }
            )
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_spotpass", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("WaveLink")
            .setContentText("$count new item${if (count > 1) "s" else ""} arrived!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
