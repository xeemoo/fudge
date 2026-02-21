package dev.danielc.core.domain.usecase

import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.CameraSessionManagerImpl
import dev.danielc.core.data.SessionState
import dev.danielc.core.data.SessionNotReadyCode
import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.PhotoRepository
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.domain.SdkErrorCode
import dev.danielc.core.domain.WifiErrorCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchPhotoListUseCaseTest {

  @Test
  fun invoke_whenSessionNotReady_shortCircuitsWithoutCallingRepository() = runTest {
    val repository = FakePhotoRepository()
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = FakeCameraSessionManager(
        assertReadyResult = SessionState.NotReady(code = SessionNotReadyCode.WIFI_DISCONNECTED)
      )
    )

    val result = useCase()

    assertTrue(result.isFailure)
    assertEquals(0, repository.fetchCount)
    val error = result.exceptionOrNull()
    assertTrue(error is AppException)
    val appError = (error as AppException).error
    assertEquals(
      AppError.Wifi(
        code = WifiErrorCode.DISCONNECTED,
        message = CameraSessionManagerImpl.REASON_WIFI_DISCONNECTED
      ),
      appError
    )
  }

  @Test
  fun invoke_whenSessionSdkUnreachable_mapsToWifiTimeoutError() = runTest {
    val repository = FakePhotoRepository()
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = FakeCameraSessionManager(
        assertReadyResult = SessionState.NotReady(code = SessionNotReadyCode.SDK_UNREACHABLE)
      )
    )

    val result = useCase()

    assertTrue(result.isFailure)
    assertEquals(0, repository.fetchCount)
    val error = result.exceptionOrNull()
    assertTrue(error is AppException)
    assertEquals(
      AppError.Wifi(
        code = WifiErrorCode.TIMEOUT,
        message = CameraSessionManagerImpl.REASON_SDK_UNREACHABLE
      ),
      (error as AppException).error
    )
  }

  @Test
  fun invoke_whenSessionNativeLibraryMissing_mapsToSdkError() = runTest {
    val repository = FakePhotoRepository()
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = FakeCameraSessionManager(
        assertReadyResult = SessionState.NotReady(
          code = SessionNotReadyCode.SDK_NATIVE_LIBRARY_MISSING
        )
      )
    )

    val result = useCase()

    assertTrue(result.isFailure)
    assertEquals(0, repository.fetchCount)
    val error = result.exceptionOrNull()
    assertTrue(error is AppException)
    assertEquals(
      AppError.Sdk(
        code = SdkErrorCode.UNKNOWN,
        message = CameraSessionManagerImpl.REASON_SDK_NATIVE_LIBRARY_MISSING
      ),
      (error as AppException).error
    )
  }

  @Test
  fun invoke_whenSessionReady_returnsRepositoryResult() = runTest {
    val photos = listOf(
      RemotePhoto(
        photoId = PhotoId("photo-1"),
        fileName = "DSCF0001.JPG",
        takenAtEpochMillis = 1000L,
        fileSizeBytes = 2048L,
        mimeType = "image/jpeg"
      )
    )
    val repository = FakePhotoRepository(result = Result.success(photos))
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = FakeCameraSessionManager(assertReadyResult = SessionState.Ready)
    )

    val result = useCase()

    assertTrue(result.isSuccess)
    assertEquals(1, repository.fetchCount)
    assertEquals(photos, result.getOrNull())
  }

  @Test
  fun invoke_whenRepositoryFails_propagatesFailureForUiRetry() = runTest {
    val expected = IllegalStateException("sdk timeout")
    val repository = FakePhotoRepository(result = Result.failure(expected))
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = FakeCameraSessionManager(assertReadyResult = SessionState.Ready)
    )

    val result = useCase()

    assertTrue(result.isFailure)
    assertEquals(1, repository.fetchCount)
    assertSame(expected, result.exceptionOrNull())
  }

  @Test
  fun invoke_withPagingArguments_callsRepositoryPagedFetch() = runTest {
    val page = listOf(
      RemotePhoto(
        photoId = PhotoId("photo-121"),
        fileName = "DSCF0121.JPG",
        takenAtEpochMillis = null,
        fileSizeBytes = null,
        mimeType = "image/jpeg"
      )
    )
    val repository = FakePhotoRepository(
      pageResult = Result.success(page)
    )
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = FakeCameraSessionManager(assertReadyResult = SessionState.Ready)
    )

    val result = useCase(offset = 120, limit = 60)

    assertTrue(result.isSuccess)
    assertEquals(0, repository.fetchCount)
    assertEquals(1, repository.fetchPageCount)
    assertEquals(120, repository.lastFetchOffset)
    assertEquals(60, repository.lastFetchLimit)
    assertEquals(page, result.getOrNull())
  }

  private class FakeCameraSessionManager(
    private val assertReadyResult: SessionState
  ) : CameraSessionManager {
    override val sessionState: Flow<SessionState> = emptyFlow()

    override suspend fun assertReady(): SessionState = assertReadyResult
  }

  private class FakePhotoRepository(
    private val result: Result<List<RemotePhoto>> = Result.success(emptyList()),
    private val pageResult: Result<List<RemotePhoto>>? = null
  ) : PhotoRepository {
    var fetchCount: Int = 0
    var fetchPageCount: Int = 0
    var lastFetchOffset: Int? = null
    var lastFetchLimit: Int? = null

    override suspend fun fetchRemotePhotos(): List<RemotePhoto> {
      fetchCount += 1
      return result.getOrThrow()
    }

    override suspend fun fetchRemotePhotosPage(offset: Int, limit: Int): List<RemotePhoto> {
      fetchPageCount += 1
      lastFetchOffset = offset
      lastFetchLimit = limit
      return pageResult?.getOrThrow() ?: super.fetchRemotePhotosPage(offset, limit)
    }
  }
}
