package dev.danielc.core.work.notification

import androidx.work.ForegroundInfo

interface DownloadNotificationFactory {
  fun createForegroundInfo(progressText: String): ForegroundInfo
}
