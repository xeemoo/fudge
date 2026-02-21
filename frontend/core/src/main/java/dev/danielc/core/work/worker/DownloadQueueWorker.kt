package dev.danielc.core.work.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.danielc.core.R
import dev.danielc.core.work.notification.DownloadNotificationFactory

class DownloadQueueWorker(
  appContext: Context,
  workerParams: WorkerParameters,
  private val dependency: DownloadQueueWorkerDependency,
  private val downloadNotificationFactory: DownloadNotificationFactory
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val queueId = inputData.getString(KEY_QUEUE_ID)
    if (!queueId.isNullOrBlank()) {
      setForeground(
        downloadNotificationFactory.createForegroundInfo(
          applicationContext.getString(R.string.download_notification_text_default)
        )
      )
    }
    dependency.processQueue(queueId) { taskIndex, _ ->
      setForeground(
        downloadNotificationFactory.createForegroundInfo(
          applicationContext.getString(R.string.download_notification_text_with_index, taskIndex)
        )
      )
    }
    return Result.success()
  }

  companion object {
    const val KEY_QUEUE_ID = "queueId"
  }
}
