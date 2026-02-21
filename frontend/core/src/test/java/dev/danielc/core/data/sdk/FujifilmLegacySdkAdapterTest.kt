package dev.danielc.core.data.sdk

import dev.danielc.sdk.legacy.FujifilmLegacySdk
import dev.danielc.sdk.legacy.LegacyPhotoDto
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FujifilmLegacySdkAdapterTest {

  @Test
  fun fetchPhotoList_mapsFacadeDto() = runTest {
    val adapter = FujifilmLegacySdkAdapter(
      sdk = FakeLegacySdk(
        photos = listOf(
          LegacyPhotoDto(
            photoKey = "42",
            fileName = "DSCF0042.JPG",
            takenAtEpochMillis = 1_735_700_000_000,
            fileSizeBytes = 2_048_000,
            mimeType = "image/jpeg"
          )
        )
      )
    )

    val result = adapter.fetchPhotoList()

    assertEquals(1, result.size)
    assertEquals("42", result.first().photoKey)
    assertEquals("DSCF0042.JPG", result.first().fileName)
    assertEquals(1_735_700_000_000L, result.first().takenAtEpochMillis)
    assertEquals(2_048_000L, result.first().fileSizeBytes)
    assertEquals("image/jpeg", result.first().mimeType)
  }

  private class FakeLegacySdk(
    private val photos: List<LegacyPhotoDto>
  ) : FujifilmLegacySdk {
    override suspend fun isReachable(): Boolean = true

    override suspend fun fetchPhotoList(): List<LegacyPhotoDto> = photos

    override suspend fun fetchThumbnail(photoKey: String): ByteArray = ByteArray(0)

    override suspend fun openPreviewStream(photoKey: String): InputStream = ByteArrayInputStream(ByteArray(0))

    override suspend fun openOriginalStream(photoKey: String): InputStream = ByteArrayInputStream(ByteArray(0))
  }
}
