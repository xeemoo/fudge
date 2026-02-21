package dev.danielc.core.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.danielc.core.work.notification.DownloadNotificationFactory
import dev.danielc.core.work.worker.DownloadQueueWorker
import dev.danielc.core.work.worker.DownloadQueueWorkerDependency

class AppWorkerFactory(
  private val dummyDependency: DummyWorkerDependency,
  private val downloadQueueDependency: DownloadQueueWorkerDependency,
  private val downloadNotificationFactory: DownloadNotificationFactory
) : WorkerFactory() {

  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters
  ): ListenableWorker? {
    return when (workerClassName) {
      DummyWorker::class.qualifiedName -> DummyWorker(appContext, workerParameters, dummyDependency)
      DownloadQueueWorker::class.qualifiedName -> DownloadQueueWorker(
        appContext,
        workerParameters,
        downloadQueueDependency,
        downloadNotificationFactory
      )
      else -> null
    }
  }
}
