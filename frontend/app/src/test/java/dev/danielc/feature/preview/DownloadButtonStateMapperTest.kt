package dev.danielc.feature.preview

import dev.danielc.R
import dev.danielc.core.domain.usecase.DownloadButtonState
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadButtonStateMapperTest {

  @Test
  fun map_returnsExpectedTextAndNoActionForEachState() {
    assertMapped(DownloadButtonState.NOT_DOWNLOADED, R.string.preview_download_button_download, true)
    assertMapped(DownloadButtonState.QUEUED, R.string.preview_download_button_queued, false)
    assertMapped(DownloadButtonState.DOWNLOADING, R.string.preview_download_button_downloading, false)
    assertMapped(DownloadButtonState.SUCCESS, R.string.preview_download_button_success, false)
    assertMapped(DownloadButtonState.FAILED, R.string.preview_download_button_retry, true)
  }

  private fun assertMapped(state: DownloadButtonState, textResId: Int, enabled: Boolean) {
    val mapped = DownloadButtonStateMapper.map(state)
    assertEquals(textResId, mapped.textResId)
    assertEquals(enabled, mapped.enabled)
  }
}
