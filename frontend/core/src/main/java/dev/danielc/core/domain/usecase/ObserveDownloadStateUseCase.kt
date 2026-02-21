package dev.danielc.core.domain.usecase

import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import dev.danielc.core.domain.PhotoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class DownloadButtonState {
  NOT_DOWNLOADED,
  QUEUED,
  DOWNLOADING,
  SUCCESS,
  FAILED
}

data class DownloadStateSnapshot(
  val buttonState: DownloadButtonState,
  val errorCode: DownloadErrorCode? = null
)

class ObserveDownloadStateUseCase(
  private val taskDao: DownloadTaskDao,
  private val isDownloadedUseCase: IsDownloadedUseCase
) {
  fun observe(photoId: PhotoId): Flow<DownloadButtonState> {
    return observeSnapshot(photoId)
      .map { it.buttonState }
      .distinctUntilChanged()
  }

  fun observeSnapshot(photoId: PhotoId): Flow<DownloadStateSnapshot> {
    return combine(
      isDownloadedUseCase.observe(photoId),
      taskDao.observeByPhotoId(photoId.value)
    ) { isDownloaded, latestTask ->
      when {
        isDownloaded -> DownloadStateSnapshot(buttonState = DownloadButtonState.SUCCESS)
        latestTask?.status == DownloadStatus.DOWNLOADING -> {
          DownloadStateSnapshot(buttonState = DownloadButtonState.DOWNLOADING)
        }
        latestTask?.status == DownloadStatus.QUEUED -> {
          DownloadStateSnapshot(buttonState = DownloadButtonState.QUEUED)
        }
        latestTask?.status == DownloadStatus.FAILED -> {
          DownloadStateSnapshot(
            buttonState = DownloadButtonState.FAILED,
            errorCode = latestTask.errorCode
          )
        }
        else -> DownloadStateSnapshot(buttonState = DownloadButtonState.NOT_DOWNLOADED)
      }
    }.distinctUntilChanged()
  }
}
