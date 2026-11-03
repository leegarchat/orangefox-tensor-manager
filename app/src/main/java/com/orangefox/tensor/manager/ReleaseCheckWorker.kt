package com.orangefox.tensor.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class ReleaseCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPrefs(applicationContext)
        val mode = ReleaseNotifyMode.fromOrdinal(prefs.releaseNotifyMode)
        if (mode == ReleaseNotifyMode.None) return Result.success()

        return try {
            val client = OkHttpClient()
            val req = Request.Builder()
                .url("https://api.github.com/repos/leegarchat/twrp_device_google_pixels/releases")
                .header("User-Agent", "PixelOFoxApp")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Result.retry()
                val arr = JSONArray(resp.body?.string() ?: return Result.success())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optBoolean("draft", false)) continue
                    val isPre = o.optBoolean("prerelease", false)
                    if (mode == ReleaseNotifyMode.Main && isPre) continue

                    val tag = o.optString("tag_name", "")
                    val name = o.optString("name", tag).ifEmpty { tag }
                    if (tag.isNotEmpty()) {
                        val lastSeen = prefs.lastNotifiedReleaseTag
                        if (lastSeen.isNotEmpty() && lastSeen != tag) {
                            sendReleaseNotification(name, tag)
                        }
                        prefs.lastNotifiedReleaseTag = tag
                        break
                    }
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun sendReleaseNotification(releaseName: String, tag: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ofox_releases_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.notify_channel_releases),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            nm.createNotificationChannel(ch)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(applicationContext.getString(R.string.notify_new_release_title))
            .setContentText("$releaseName ($tag)")
            .setAutoCancel(true)
            .build()
        nm.notify(tag.hashCode(), notification)
    }

    companion object {
        private const val WORK_NAME = "ofox_periodic_release_check"

        fun schedule(context: Context, mode: ReleaseNotifyMode) {
            val wm = WorkManager.getInstance(context)
            if (mode == ReleaseNotifyMode.None) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val req = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }
    }
}
