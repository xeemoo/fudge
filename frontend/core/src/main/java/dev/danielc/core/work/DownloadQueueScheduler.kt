package dev.danielc.core.work

import androidx.work.ExistingWorkPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.danielc.core.work.worker.DownloadQueueWorker

interface DownloadQueueScheduler {
  fun kick(queueId: String): Operation
}

class DownloadQueueSchedulerImpl(
  private val workManager: WorkManager
) : DownloadQueueScheduler {

  override fun kick(queueId: String): Operation {
    val request = createRequest(queueId)
    return workManager.enqueueUniqueWork(
      UNIQUE_WORK_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request
    )
  }

  internal fun createRequest(queueId: String): OneTimeWorkRequest {
    return OneTimeWorkRequestBuilder<DownloadQueueWorker>()
      .setInputData(createInputData(queueId))
      .build()
  }

  internal fun createInputData(queueId: String): Data {
    return workDataOf(DownloadQueueWorker.KEY_QUEUE_ID to queueId)
  }

  companion object {
    const val UNIQUE_WORK_NAME = "download_queue"
  }
}
