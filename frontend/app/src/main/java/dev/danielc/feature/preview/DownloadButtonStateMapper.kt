package dev.danielc.feature.preview

import androidx.annotation.StringRes
import dev.danielc.R
import dev.danielc.core.domain.usecase.DownloadButtonState

data class DownloadButtonUi(
  @StringRes val textResId: Int,
  val enabled: Boolean
)

object DownloadButtonStateMapper {
  fun map(state: DownloadButtonState): DownloadButtonUi {
    return when (state) {
      DownloadButtonState.NOT_DOWNLOADED -> DownloadButtonUi(
        textResId = R.string.preview_download_button_download,
        enabled = true
      )
      DownloadButtonState.QUEUED -> DownloadButtonUi(
        textResId = R.string.preview_download_button_queued,
        enabled = false
      )
      DownloadButtonState.DOWNLOADING -> DownloadButtonUi(
        textResId = R.string.preview_download_button_downloading,
        enabled = false
      )
      DownloadButtonState.SUCCESS -> DownloadButtonUi(
        textResId = R.string.preview_download_button_success,
        enabled = false
      )
      DownloadButtonState.FAILED -> DownloadButtonUi(
        textResId = R.string.preview_download_button_retry,
        enabled = true
      )
    }
  }
}
