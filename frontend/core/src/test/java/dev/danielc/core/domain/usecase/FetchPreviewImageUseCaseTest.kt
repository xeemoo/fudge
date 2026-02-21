package dev.danielc.core.domain.usecase

import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.CameraSessionManagerImpl
import dev.danielc.core.data.SessionNotReadyCode
import dev.danielc.core.data.SessionState
import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.PreviewRepository
import dev.danielc.core.domain.WifiErrorCode
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchPreviewImageUseCaseTest {

  @Test
  fun invoke_whenSessionNotReady_shortCircuitsWithoutCallingRepository() = runTest {
    val repository = FakePreviewRepository()
    val useCase = FetchPreviewImageUseCase(
      repo = repository,
      session = FakeCameraSessionManager(
        assertReadyResult = SessionState.NotReady(code = SessionNotReadyCode.WIFI_DISCONNECTED)
      )
    )

    val result = useCase(PhotoId("photo-1"))

    assertTrue(result.isFailure)
    assertEquals(0, repository.openCount)
    val exception = result.exceptionOrNull()
    assertTrue(exception is AppException)
    assertEquals(
      AppError.Wifi(
        code = WifiErrorCode.DISCONNECTED,
        message = CameraSessionManagerImpl.REASON_WIFI_DISCONNECTED
      ),
      (exception as AppException).error
    )
  }

  @Test
  fun invoke_whenSessionReady_returnsPreviewBytes() = runTest {
    val expected = byteArrayOf(1, 2, 3, 4)
    val repository = FakePreviewRepository(
      results = mutableListOf(Result.success(ByteArrayInputStream(expected)))
    )
    val useCase = FetchPreviewImageUseCase(
      repo = repository,
      session = FakeCameraSessionManager(assertReadyResult = SessionState.Ready)
    )

    val result = useCase(PhotoId("photo-2"))

    assertTrue(result.isSuccess)
    assertEquals(1, repository.openCount)
    assertEquals(expected.toList(), result.getOrNull()?.toList())
  }

  @Test
  fun invoke_whenRepositoryFails_propagatesFailure() = runTest {
    val expected = IllegalStateException("sdk preview failed")
    val repository = FakePreviewRepository(
      results = mutableListOf(Result.failure(expected))
    )
    val useCase = FetchPreviewImageUseCase(
      repo = repository,
      session = FakeCameraSessionManager(assertReadyResult = SessionState.Ready)
    )

    val result = useCase(PhotoId("photo-3"))

    assertTrue(result.isFailure)
    assertEquals(1, repository.openCount)
    val exception = result.exceptionOrNull()
    assertTrue(exception is IllegalStateException)
    assertEquals(expected.message, exception?.message)
  }

  private class FakeCameraSessionManager(
    private val assertReadyResult: SessionState
  ) : CameraSessionManager {
    override val sessionState: Flow<SessionState> = emptyFlow()

    override suspend fun assertReady(): SessionState = assertReadyResult
  }

  private class FakePreviewRepository(
    private val results: MutableList<Result<InputStream>> = mutableListOf(
      Result.success(ByteArrayInputStream(byteArrayOf(1)))
    )
  ) : PreviewRepository {
    var openCount: Int = 0

    override suspend fun openPreview(photoId: PhotoId): InputStream {
      openCount += 1
      val result = if (results.isNotEmpty()) {
        results.removeAt(0)
      } else {
        Result.success(ByteArrayInputStream(byteArrayOf(1)))
      }
      return result.getOrThrow()
    }
  }
}
