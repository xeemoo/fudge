package dev.danielc.core.db

import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DbTypeConvertersTest {

  private val converters = DbTypeConverters()

  @Test
  fun downloadStatus_roundTrip() {
    val encoded = converters.fromDownloadStatus(DownloadStatus.DOWNLOADING)
    val decoded = converters.toDownloadStatus(encoded)

    assertEquals("DOWNLOADING", encoded)
    assertEquals(DownloadStatus.DOWNLOADING, decoded)
  }

  @Test
  fun downloadErrorCode_roundTripWithNull() {
    val encoded = converters.fromDownloadErrorCode(DownloadErrorCode.TIMEOUT)
    val decoded = converters.toDownloadErrorCode(encoded)

    assertEquals("TIMEOUT", encoded)
    assertEquals(DownloadErrorCode.TIMEOUT, decoded)
    assertNull(converters.toDownloadErrorCode(null))
    assertNull(converters.fromDownloadErrorCode(null))
  }
}
