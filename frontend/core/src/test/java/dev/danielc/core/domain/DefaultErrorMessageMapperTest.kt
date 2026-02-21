package dev.danielc.core.domain

import android.content.Context
import dev.danielc.core.db.model.DownloadErrorCode
import androidx.test.core.app.ApplicationProvider
import dev.danielc.core.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultErrorMessageMapperTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val mapper = DefaultErrorMessageMapper(context)

  @Test
  fun toDownloadFailMessage_mapsWifiDisconnected() {
    assertEquals(
      context.getString(R.string.error_download_wifi_disconnected),
      mapper.toDownloadFailMessage(DownloadErrorCode.WIFI_DISCONNECTED)
    )
  }

  @Test
  fun toDownloadFailMessage_mapsStorageFull() {
    assertEquals(
      context.getString(R.string.error_download_storage_full),
      mapper.toDownloadFailMessage(DownloadErrorCode.STORAGE_FULL)
    )
  }

  @Test
  fun toDownloadFailMessage_mapsSdkError() {
    assertEquals(
      context.getString(R.string.error_download_sdk),
      mapper.toDownloadFailMessage(DownloadErrorCode.SDK_ERROR)
    )
  }
}
