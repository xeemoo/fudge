package dev.danielc.core.data.sdk

import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.SdkErrorCode
import dev.danielc.sdk.legacy.FujifilmLegacySdk
import dev.danielc.sdk.legacy.FujifilmLegacySdkException
import dev.danielc.sdk.legacy.LegacyPhotoDto
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.ProtocolException
import java.net.SocketTimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FujifilmCameraClientImplTest {

  private val testDispatcher = StandardTestDispatcher()

  @Test
  fun fetchRemotePhotos_prefersSdkUniqueKeyAsPhotoId() = runTest(testDispatcher) {
    val client = FujifilmCameraClientImpl(
      legacySdk = FakeLegacySdk(
        photos = listOf(
          LegacyPhotoDto(
            photoKey = "sdk-key-001",
            fileName = "DSCF0001.JPG",
            takenAtEpochMillis = 100L,
            fileSizeBytes = 200L,
            mimeType = "image/jpeg"
          )
        )
      ),
      ioDispatcher = testDispatcher
    )

    val photoId = client.fetchRemotePhotos().single().photoId

    assertEquals(PhotoId("sdk-key-001"), photoId)
  }

  @Test
  fun fetchRemotePhotos_generatesStableHashWhenSdkKeyMissing() = runTest(testDispatcher) {
    val dto = LegacyPhotoDto(
      photoKey = "",
      fileName = "DSCF0020.JPG",
      takenAtEpochMillis = 1_700_000_000_000L,
      fileSizeBytes = 3_145_728L,
      mimeType = "image/jpeg"
    )

    val client = FujifilmCameraClientImpl(
      legacySdk = FakeLegacySdk(photos = listOf(dto, dto.copy(fileSizeBytes = 3_145_729L))),
      ioDispatcher = testDispatcher
    )

    val first = client.fetchRemotePhotos()
    val second = client.fetchRemotePhotos()

    assertEquals(first[0].photoId, second[0].photoId)
    assertNotEquals(first[0].photoId, first[1].photoId)
    assertTrue(first[0].photoId.value.matches(Regex("[0-9a-f]{64}")))
  }

  @Test
  fun fetchRemotePhotos_mapsTimeoutToAppError() = runTest(testDispatcher) {
    val client = FujifilmCameraClientImpl(
      legacySdk = FakeLegacySdk(error = SocketTimeoutException("timeout")),
      ioDispatcher = testDispatcher
    )

    val error = captureError { client.fetchRemotePhotos() }

    assertEquals(AppError.Sdk(SdkErrorCode.TIMEOUT, "timeout"), error)
  }

  @Test
  fun fetchRemotePhotos_mapsIoToAppError() = runTest(testDispatcher) {
    val client = FujifilmCameraClientImpl(
      legacySdk = FakeLegacySdk(error = IOException("io failed")),
      ioDispatcher = testDispatcher
    )

    val error = captureError { client.fetchRemotePhotos() }

    assertEquals(AppError.Sdk(SdkErrorCode.IO, "io failed"), error)
  }

  @Test
  fun fetchRemotePhotos_mapsProtocolToAppError() = runTest(testDispatcher) {
    val client = FujifilmCameraClientImpl(
      legacySdk = FakeLegacySdk(error = ProtocolException("protocol mismatch")),
      ioDispatcher = testDispatcher
    )

    val error = captureError { client.fetchRemotePhotos() }

    assertEquals(AppError.Sdk(SdkErrorCode.PROTOCOL, "protocol mismatch"), error)
  }

  @Test
  fun fetchRemotePhotos_mapsLegacyProtocolExceptionToAppError() = runTest(testDispatcher) {
    val client = FujifilmCameraClientImpl(
      legacySdk = FakeLegacySdk(error = FujifilmLegacySdkException("protocol field missing")),
      ioDispatcher = testDispatcher
    )

    val error = captureError { client.fetchRemotePhotos() }

    assertEquals(AppError.Sdk(SdkErrorCode.PROTOCOL, "protocol field missing"), error)
  }

  @Test
  fun fetchRemotePhotosPage_mapsPagedSdkResult() = runTest(testDispatcher) {
    val legacySdk = FakeLegacySdk(
      pagedPhotos = listOf(
        LegacyPhotoDto(
          photoKey = "sdk-key-061",
          fileName = "DSCF0061.JPG",
          takenAtEpochMillis = 123L,
          fileSizeBytes = 456L,
          mimeType = "image/jpeg"
        )
      )
    )
    val client = FujifilmCameraClientImpl(
      legacySdk = legacySdk,
      ioDispatcher = testDispatcher
    )

    val photos = client.fetchRemotePhotosPage(offset = 60, limit = 60)

    assertEquals(1, legacySdk.fetchPhotoListPageCallCount)
    assertEquals(60, legacySdk.lastFetchOffset)
    assertEquals(60, legacySdk.lastFetchLimit)
    assertEquals(PhotoId("sdk-key-061"), photos.single().photoId)
  }

  private suspend fun captureError(block: suspend () -> Unit): AppError {
    return try {
      block()
      fail("Expected AppException")
      throw IllegalStateException("unreachable")
    } catch (appException: AppException) {
      appException.error
    }
  }

  private class FakeLegacySdk(
    private val photos: List<LegacyPhotoDto> = emptyList(),
    private val pagedPhotos: List<LegacyPhotoDto> = photos,
    private val error: Throwable? = null
  ) : FujifilmLegacySdk {

    var fetchPhotoListPageCallCount: Int = 0
    var lastFetchOffset: Int? = null
    var lastFetchLimit: Int? = null

    override suspend fun isReachable(): Boolean = true

    override suspend fun fetchPhotoList(): List<LegacyPhotoDto> {
      error?.let { throw it }
      return photos
    }

    override suspend fun fetchPhotoListPage(offset: Int, limit: Int): List<LegacyPhotoDto> {
      fetchPhotoListPageCallCount += 1
      lastFetchOffset = offset
      lastFetchLimit = limit
      error?.let { throw it }
      return pagedPhotos
    }

    override suspend fun fetchThumbnail(photoKey: String): ByteArray = ByteArray(0)

    override suspend fun openPreviewStream(photoKey: String): InputStream = ByteArrayInputStream(ByteArray(0))

    override suspend fun openOriginalStream(photoKey: String): InputStream = ByteArrayInputStream(ByteArray(0))
  }
}
