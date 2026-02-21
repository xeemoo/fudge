package dev.danielc.core.data.sdk

import dev.danielc.core.domain.PhotoId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeFujifilmCameraClientTest {

  @Test
  fun `isReachable returns true`() = runTest {
    val client = FakeFujifilmCameraClient()

    assertTrue(client.isReachable())
  }

  @Test
  fun `fetchRemotePhotosPage respects offset and limit`() = runTest {
    val client = FakeFujifilmCameraClient()

    val photos = client.fetchRemotePhotos()
    val page = client.fetchRemotePhotosPage(offset = 1, limit = 1)

    assertEquals(1, page.size)
    assertEquals(photos[1].photoId, page[0].photoId)
    assertTrue(client.fetchRemotePhotosPage(offset = -1, limit = 1).isEmpty())
    assertTrue(client.fetchRemotePhotosPage(offset = 0, limit = 0).isEmpty())
  }

  @Test
  fun `thumbnail preview and original provide valid fixture bytes`() = runTest {
    val client = FakeFujifilmCameraClient()
    val photoId = PhotoId("fake-001.png")

    val thumbnail = client.fetchThumbnail(photoId)
    val preview = client.openPreview(photoId).use { it.readBytes() }
    val original = client.openOriginal(photoId).use { it.readBytes() }

    assertTrue(thumbnail.isNotEmpty())
    assertArrayEquals(thumbnail, preview)
    assertArrayEquals(preview, original)
    assertPngHeader(thumbnail)
  }

  private fun assertPngHeader(bytes: ByteArray) {
    val expected = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )
    assertTrue(bytes.size >= expected.size)
    assertArrayEquals(expected, bytes.copyOfRange(0, expected.size))
  }
}
