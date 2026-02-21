package dev.danielc.core.work.worker

import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.analytics.DownloadFailReason
import dev.danielc.core.analytics.NoOpAnalyticsTracker
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.media.MediaStoreImageSaver
import dev.danielc.core.media.SaveImageRequest
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.first

class DownloadQueueWorkerDependency {
  constructor()

  constructor(
    taskDao: DownloadTaskDao,
    downloadedDao: DownloadedPhotoDao,
    cameraClient: FujifilmCameraClient,
    saver: MediaStoreImageSaver,
    analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
    clock: () -> Long = System::currentTimeMillis
  ) {
    this.taskDao = taskDao
    this.downloadedDao = downloadedDao
    this.cameraClient = cameraClient
    this.saver = saver
    this.analyticsTracker = analyticsTracker
    this.clock = clock
  }

  private var taskDao: DownloadTaskDao? = null
  private var downloadedDao: DownloadedPhotoDao? = null
  private var cameraClient: FujifilmCameraClient? = null
  private var saver: MediaStoreImageSaver? = null
  private var analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker
  private var clock: () -> Long = System::currentTimeMillis

  @Volatile
  var lastQueueId: String? = null
    private set

  fun onQueueIdReceived(queueId: String?) {
    lastQueueId = queueId
  }

  suspend fun processQueue(
    queueId: String?,
    onTaskStarted: suspend (taskIndex: Int, photoId: String) -> Unit = { _, _ -> }
  ) {
    onQueueIdReceived(queueId)
    val normalizedQueueId = queueId?.takeIf { it.isNotBlank() } ?: return
    val taskDao = taskDao ?: return
    val downloadedDao = downloadedDao ?: return
    val cameraClient = cameraClient ?: return
    val saver = saver ?: return

    var taskIndex = 0
    while (true) {
      val task = taskDao.nextQueuedTask(normalizedQueueId) ?: break
      taskIndex += 1
      onTaskStarted(taskIndex, task.photoId)
      analyticsTracker.track(AnalyticsEvent.DownloadStart)
      val downloadingAt = clock()
      taskDao.updateStatus(
        taskId = task.taskId,
        status = DownloadStatus.DOWNLOADING,
        progressPercent = 0,
        errorCode = null,
        localUri = null,
        updatedAtEpochMillis = downloadingAt
      )

      var pendingUri: android.net.Uri? = null
      val request = task.toSaveRequest()
      try {
        cameraClient.openOriginal(PhotoId(task.photoId)).use { source ->
          pendingUri = saver.createPending(request)
          saver.write(checkNotNull(pendingUri), source) {}
          saver.publish(checkNotNull(pendingUri))
        }
        val localUri = checkNotNull(pendingUri).toString()
        downloadedDao.upsert(
          DownloadedPhotoEntity(
            photoId = task.photoId,
            localUri = localUri,
            displayName = request.displayName,
            relativePath = request.relativePath,
            mimeType = request.mimeType,
            downloadedAtEpochMillis = clock()
          )
        )
        taskDao.updateStatus(
          taskId = task.taskId,
          status = DownloadStatus.SUCCESS,
          progressPercent = 100,
          errorCode = null,
          localUri = localUri,
          updatedAtEpochMillis = clock()
        )
        analyticsTracker.track(AnalyticsEvent.DownloadSuccess)
        analyticsTracker.track(AnalyticsEvent.QueueLengthChange(activeQueueLength(normalizedQueueId)))
      } catch (t: Throwable) {
        pendingUri?.let { uri ->
          runCatching { saver.delete(uri) }
        }
        val downloadErrorCode = t.toDownloadErrorCode()
        taskDao.updateStatus(
          taskId = task.taskId,
          status = DownloadStatus.FAILED,
          progressPercent = 0,
          errorCode = downloadErrorCode,
          localUri = null,
          updatedAtEpochMillis = clock()
        )
        analyticsTracker.track(AnalyticsEvent.DownloadFail(downloadErrorCode.toAnalyticsReason()))
        analyticsTracker.track(AnalyticsEvent.QueueLengthChange(activeQueueLength(normalizedQueueId)))
      }
    }
  }

  private suspend fun activeQueueLength(queueId: String): Int {
    return taskDao?.observeByQueueId(queueId)?.first().orEmpty().count { task ->
      task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.DOWNLOADING
    }
  }
}

private fun dev.danielc.core.db.entity.DownloadTaskEntity.toSaveRequest(): SaveImageRequest {
  val name = photoId.takeIf { it.contains('.') } ?: "$photoId.jpg"
  return SaveImageRequest(
    displayName = name,
    mimeType = inferMimeType(name)
  )
}

private fun inferMimeType(fileName: String): String {
  return when {
    fileName.endsWith(".png", ignoreCase = true) -> "image/png"
    fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
    else -> "image/jpeg"
  }
}

private fun Throwable.toDownloadErrorCode(): DownloadErrorCode {
  return when (this) {
    is AppException -> when (val error = error) {
      is AppError.Wifi -> {
        if (error.code == dev.danielc.core.domain.WifiErrorCode.TIMEOUT) {
          DownloadErrorCode.TIMEOUT
        } else {
          DownloadErrorCode.WIFI_DISCONNECTED
        }
      }
      is AppError.Sdk -> {
        if (error.code == dev.danielc.core.domain.SdkErrorCode.TIMEOUT) {
          DownloadErrorCode.TIMEOUT
        } else {
          DownloadErrorCode.SDK_ERROR
        }
      }
      is AppError.Storage -> {
        if (error.code == dev.danielc.core.domain.StorageErrorCode.NO_SPACE) {
          DownloadErrorCode.STORAGE_FULL
        } else {
          DownloadErrorCode.UNKNOWN
        }
      }
      is AppError.Unknown -> DownloadErrorCode.UNKNOWN
    }
    is SocketTimeoutException,
    is TimeoutException -> DownloadErrorCode.TIMEOUT
    is IOException -> {
      val normalized = message?.lowercase().orEmpty()
      if ("no space" in normalized || "enospc" in normalized || "space left" in normalized) {
        DownloadErrorCode.STORAGE_FULL
      } else {
        DownloadErrorCode.UNKNOWN
      }
    }
    else -> DownloadErrorCode.UNKNOWN
  }
}

private fun DownloadErrorCode.toAnalyticsReason(): DownloadFailReason {
  return when (this) {
    DownloadErrorCode.WIFI_DISCONNECTED,
    DownloadErrorCode.TIMEOUT -> DownloadFailReason.DISCONNECT
    DownloadErrorCode.STORAGE_FULL -> DownloadFailReason.STORAGE_FULL
    DownloadErrorCode.SDK_ERROR -> DownloadFailReason.SDK_ERROR
    DownloadErrorCode.UNKNOWN -> DownloadFailReason.UNKNOWN
  }
}
