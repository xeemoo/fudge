package dev.danielc.feature.preview

import dev.danielc.core.domain.usecase.DownloadButtonState
import dev.danielc.ui.UiText

object PreviewContract {

  data class State(
    val photoId: String,
    val fileName: String? = null,
    val takenAtEpochMillis: Long? = null,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val isLoading: Boolean = false,
    val imageBytes: ByteArray? = null,
    val errorMessage: UiText? = null,
    val downloadButtonState: DownloadButtonState = DownloadButtonState.NOT_DOWNLOADED
  )

  sealed interface Intent {
    data object OnEnter : Intent
    data object OnRetry : Intent
    data object OnClickDownload : Intent
  }

  sealed interface Effect {
    data class ShowToast(val message: UiText) : Effect
  }
}
