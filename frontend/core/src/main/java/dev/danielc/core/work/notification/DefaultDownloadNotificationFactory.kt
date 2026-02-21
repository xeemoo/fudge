package dev.danielc.core.work.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import dev.danielc.core.R

class DefaultDownloadNotificationFactory(
  private val context: Context
) : DownloadNotificationFactory {

  override fun createForegroundInfo(progressText: String): ForegroundInfo {
    ensureNotificationChannel()
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(context.getString(R.string.download_notification_title))
      .setContentText(progressText)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      ForegroundInfo(NOTIFICATION_ID, notification)
    }
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
      CHANNEL_ID,
      context.getString(R.string.download_notification_channel_name),
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = context.getString(R.string.download_notification_channel_description)
    }
    manager.createNotificationChannel(channel)
  }

  companion object {
    const val CHANNEL_ID = "download_queue"
    const val NOTIFICATION_ID = 3001
  }
}
