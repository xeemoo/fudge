package dev.danielc.feature.preview

import dev.danielc.R
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.analytics.NoOpAnalyticsTracker
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.ErrorMessageMapper
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.domain.usecase.EnqueueDownloadUseCase
import dev.danielc.core.domain.usecase.EnqueueResult
import dev.danielc.core.domain.usecase.DownloadButtonState
import dev.danielc.core.domain.usecase.FetchPhotoListUseCase
import dev.danielc.core.domain.usecase.FetchPreviewImageUseCase
import dev.danielc.core.domain.usecase.ObserveDownloadStateUseCase
import dev.danielc.core.mvi.MviViewModel
import dev.danielc.ui.UiText
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PreviewViewModel(
  private val fetchPreviewImageUseCase: FetchPreviewImageUseCase,
  private val observeDownloadStateUseCase: ObserveDownloadStateUseCase,
  private val enqueueDownloadUseCase: EnqueueDownloadUseCase,
  private val errorMessageMapper: ErrorMessageMapper,
  private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
  private val fetchPhotoListUseCase: FetchPhotoListUseCase? = null,
  private val enqueuePhotoAction: (suspend (RemotePhoto) -> EnqueueResult)? = null,
  private val photoId: String
) : MviViewModel<PreviewContract.Intent, PreviewContract.State, PreviewContract.Effect>(
  initialState = PreviewContract.State(photoId = photoId)
) {
  private var lastDownloadButtonState: DownloadButtonState = DownloadButtonState.NOT_DOWNLOADED

  init {
    viewModelScope.launch {
      loadPhotoMeta()
    }
    viewModelScope.launch {
      observeDownloadStateUseCase.observeSnapshot(PhotoId(photoId)).collectLatest { snapshot ->
        setState {
          copy(downloadButtonState = snapshot.buttonState)
        }
        if (snapshot.buttonState == DownloadButtonState.FAILED &&
          lastDownloadButtonState != DownloadButtonState.FAILED
        ) {
          val message = errorMessageMapper.toDownloadFailMessage(
            snapshot.errorCode ?: DownloadErrorCode.UNKNOWN
          )
          postEffect {
            PreviewContract.Effect.ShowToast(UiText.Dynamic(message))
          }
        }
        lastDownloadButtonState = snapshot.buttonState
      }
    }
  }

  private suspend fun loadPhotoMeta() {
    val matchedPhoto = findPhotoMeta() ?: return
    setState {
      copy(
        fileName = matchedPhoto.fileName?.trim()?.takeIf { it.isNotEmpty() },
        takenAtEpochMillis = matchedPhoto.takenAtEpochMillis,
        fileSizeBytes = matchedPhoto.fileSizeBytes,
        mimeType = matchedPhoto.mimeType
      )
    }
  }

  override suspend fun reduce(intent: PreviewContract.Intent) {
    when (intent) {
      PreviewContract.Intent.OnEnter -> loadPreview()
      PreviewContract.Intent.OnRetry -> loadPreview()
      PreviewContract.Intent.OnClickDownload -> enqueueDownload()
    }
  }

  private suspend fun loadPreview() {
    setState {
      copy(
        isLoading = true,
        imageBytes = null,
        errorMessage = null
      )
    }

    val result = fetchPreviewImageUseCase(PhotoId(photoId))
    if (result.isSuccess) {
      setState {
        copy(
          isLoading = false,
          imageBytes = result.getOrNull(),
          errorMessage = null
        )
      }
      return
    }

    val throwable = result.exceptionOrNull()
    setState {
      copy(
        isLoading = false,
        imageBytes = null,
        errorMessage = throwable?.toDisplayMessage(errorMessageMapper) ?: UiText.Res(R.string.preview_load_failed)
      )
    }
  }

  private suspend fun enqueueDownload() {
    analyticsTracker.track(AnalyticsEvent.DownloadClick)
    val enqueuePhoto = resolvePhotoForEnqueue()
    val result = (enqueuePhotoAction ?: enqueueDownloadUseCase::invoke)(enqueuePhoto)
    when (result) {
      EnqueueResult.AlreadyDownloaded -> {
        postEffect {
          PreviewContract.Effect.ShowToast(UiText.Res(R.string.preview_toast_already_downloaded))
        }
      }
      EnqueueResult.AlreadyInQueue -> {
        postEffect {
          PreviewContract.Effect.ShowToast(UiText.Res(R.string.preview_toast_already_in_queue))
        }
      }
      EnqueueResult.Enqueued -> {
        postEffect {
          PreviewContract.Effect.ShowToast(UiText.Res(R.string.preview_toast_enqueued))
        }
      }
      is EnqueueResult.Failed -> {
        postEffect {
          PreviewContract.Effect.ShowToast(UiText.Dynamic(result.message))
        }
      }
    }
  }

  private suspend fun resolvePhotoForEnqueue(): RemotePhoto {
    val statePhoto = state.value.toEnqueuePhotoOrNull(photoId)
    if (statePhoto != null) return statePhoto

    val recoveredPhoto = findPhotoMeta()
    if (recoveredPhoto != null) {
      setState {
        copy(
          fileName = recoveredPhoto.fileName?.trim()?.takeIf { it.isNotEmpty() },
          takenAtEpochMillis = recoveredPhoto.takenAtEpochMillis,
          fileSizeBytes = recoveredPhoto.fileSizeBytes,
          mimeType = recoveredPhoto.mimeType
        )
      }
      return recoveredPhoto
    }

    return RemotePhoto(
      photoId = PhotoId(photoId),
      fileName = null,
      takenAtEpochMillis = null,
      fileSizeBytes = null,
      mimeType = null
    )
  }

  private suspend fun findPhotoMeta(): RemotePhoto? {
    val result = fetchPhotoListUseCase?.invoke() ?: return null
    return result
      .getOrNull()
      ?.firstOrNull { photo -> photo.photoId.value == photoId }
  }
}

private fun Throwable.toDisplayMessage(errorMessageMapper: ErrorMessageMapper): UiText {
  if (this is AppException) {
    return UiText.Dynamic(errorMessageMapper.toUserMessage(error))
  }
  return if (message.isNullOrBlank()) {
    UiText.Res(R.string.preview_load_failed)
  } else {
    UiText.Dynamic(message.orEmpty())
  }
}

private fun PreviewContract.State.toEnqueuePhotoOrNull(targetPhotoId: String): RemotePhoto? {
  val normalizedFileName = fileName?.trim()?.takeIf { it.isNotEmpty() }
  val normalizedMimeType = mimeType?.trim()?.takeIf { it.isNotEmpty() }
  val hasMeta = normalizedFileName != null ||
    takenAtEpochMillis != null ||
    fileSizeBytes != null ||
    normalizedMimeType != null
  if (!hasMeta) {
    return null
  }
  return RemotePhoto(
    photoId = PhotoId(targetPhotoId),
    fileName = normalizedFileName,
    takenAtEpochMillis = takenAtEpochMillis,
    fileSizeBytes = fileSizeBytes,
    mimeType = normalizedMimeType
  )
}
