package dev.danielc.core.domain.usecase

import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.analytics.DownloadEnqueueFailReason
import dev.danielc.core.analytics.NoOpAnalyticsTracker
import dev.danielc.core.data.QueueIdProvider
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.model.DownloadStatus
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.media.MediaUriVerifier
import dev.danielc.core.work.DownloadQueueScheduler
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface EnqueueResult {
  data object Enqueued : EnqueueResult
  data object AlreadyDownloaded : EnqueueResult
  data object AlreadyInQueue : EnqueueResult
  data class Failed(val message: String) : EnqueueResult
}

class EnqueueDownloadUseCase(
  private val taskDao: DownloadTaskDao,
  private val downloadedDao: DownloadedPhotoDao,
  private val mediaUriVerifier: MediaUriVerifier,
  private val queueIdProvider: QueueIdProvider,
  private val scheduler: DownloadQueueScheduler,
  private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
  private val clock: () -> Long = System::currentTimeMillis,
  private val taskIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
  private val enqueueLock = Mutex()

  suspend operator fun invoke(photo: RemotePhoto): EnqueueResult {
    return runCatching { enqueueInternal(photo) }
      .getOrElse { throwable ->
        analyticsTracker.track(AnalyticsEvent.DownloadEnqueueFail(DownloadEnqueueFailReason.UNKNOWN))
        EnqueueResult.Failed(throwable.message ?: "Failed to enqueue download task.")
      }
  }

  private suspend fun enqueueInternal(photo: RemotePhoto): EnqueueResult {
    return enqueueLock.withLock {
      val photoId = photo.photoId.value
      val downloaded = downloadedDao.get(photoId)
      if (downloaded != null && mediaUriVerifier.exists(downloaded.localUri)) {
        analyticsTracker.track(AnalyticsEvent.DownloadEnqueueFail(DownloadEnqueueFailReason.ALREADY_DOWNLOADED))
        return@withLock EnqueueResult.AlreadyDownloaded
      }
      if (taskDao.findExistingActiveTask(photoId) != null) {
        analyticsTracker.track(AnalyticsEvent.DownloadEnqueueFail(DownloadEnqueueFailReason.ALREADY_IN_QUEUE))
        return@withLock EnqueueResult.AlreadyInQueue
      }

      val now = clock()
      val queueId = queueIdProvider.getOrCreateQueueId()
      taskDao.insert(
        DownloadTaskEntity(
          taskId = taskIdFactory(),
          queueId = queueId,
          photoId = photoId,
          status = DownloadStatus.QUEUED,
          progressPercent = 0,
          errorCode = null,
          localUri = null,
          createdAtEpochMillis = now,
          updatedAtEpochMillis = now
        )
      )
      scheduler.kick(queueId)
      analyticsTracker.track(AnalyticsEvent.DownloadEnqueueSuccess)
      analyticsTracker.track(AnalyticsEvent.QueueLengthChange(activeQueueLength(queueId)))
      EnqueueResult.Enqueued
    }
  }

  private suspend fun activeQueueLength(queueId: String): Int {
    return taskDao.observeByQueueId(queueId).first().count { task ->
      task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.DOWNLOADING
    }
  }
}
