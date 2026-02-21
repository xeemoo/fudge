package dev.danielc.core.data

import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class PhotoRepositoryImplTest {

  @Test
  fun fetchRemotePhotos_delegatesToCameraClient() = runTest {
    val expected = listOf(
      RemotePhoto(
        photoId = PhotoId("photo-1"),
        fileName = "DSCF0001.JPG",
        takenAtEpochMillis = 123L,
        fileSizeBytes = 456L,
        mimeType = "image/jpeg"
      )
    )
    val cameraClient = FakeCameraClient(fetchResult = Result.success(expected))
    val repository = PhotoRepositoryImpl(cameraClient)

    val actual = repository.fetchRemotePhotos()

    assertEquals(1, cameraClient.fetchCount)
    assertEquals(expected, actual)
  }

  @Test
  fun fetchRemotePhotos_propagatesClientFailure() = runTest {
    val expected = IllegalStateException("fetch failed")
    val cameraClient = FakeCameraClient(fetchResult = Result.failure(expected))
    val repository = PhotoRepositoryImpl(cameraClient)

    try {
      repository.fetchRemotePhotos()
      fail("Expected exception")
    } catch (throwable: Throwable) {
      assertEquals(1, cameraClient.fetchCount)
      assertSame(expected, throwable)
    }
  }

  @Test
  fun fetchRemotePhotosPage_delegatesToCameraClient() = runTest {
    val expected = listOf(
      RemotePhoto(
        photoId = PhotoId("photo-61"),
        fileName = "DSCF0061.JPG",
        takenAtEpochMillis = null,
        fileSizeBytes = null,
        mimeType = "image/jpeg"
      )
    )
    val cameraClient = FakeCameraClient(
      fetchResult = Result.success(emptyList()),
      fetchPageResult = Result.success(expected)
    )
    val repository = PhotoRepositoryImpl(cameraClient)

    val actual = repository.fetchRemotePhotosPage(offset = 60, limit = 60)

    assertEquals(1, cameraClient.fetchPageCount)
    assertEquals(60, cameraClient.lastFetchOffset)
    assertEquals(60, cameraClient.lastFetchLimit)
    assertEquals(expected, actual)
  }

  private class FakeCameraClient(
    private val fetchResult: Result<List<RemotePhoto>>,
    private val fetchPageResult: Result<List<RemotePhoto>> = Result.success(emptyList())
  ) : FujifilmCameraClient {
    var fetchCount: Int = 0
    var fetchPageCount: Int = 0
    var lastFetchOffset: Int? = null
    var lastFetchLimit: Int? = null

    override suspend fun isReachable(): Boolean = true

    override suspend fun fetchRemotePhotos(): List<RemotePhoto> {
      fetchCount += 1
      return fetchResult.getOrThrow()
    }

    override suspend fun fetchRemotePhotosPage(offset: Int, limit: Int): List<RemotePhoto> {
      fetchPageCount += 1
      lastFetchOffset = offset
      lastFetchLimit = limit
      return fetchPageResult.getOrThrow()
    }

    override suspend fun fetchThumbnail(photoId: PhotoId): ByteArray = ByteArray(0)

    override suspend fun openPreview(photoId: PhotoId): InputStream {
      return ByteArrayInputStream(ByteArray(0))
    }

    override suspend fun openOriginal(photoId: PhotoId): InputStream {
      return ByteArrayInputStream(ByteArray(0))
    }
  }
}
