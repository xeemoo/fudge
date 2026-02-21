package dev.danielc.core.domain.usecase

import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.SessionNotReadyCode
import dev.danielc.core.data.SessionState
import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.PreviewRepository
import dev.danielc.core.domain.SdkErrorCode
import dev.danielc.core.domain.WifiErrorCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FetchPreviewImageUseCase(
  private val repo: PreviewRepository,
  private val session: CameraSessionManager,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  suspend operator fun invoke(photoId: PhotoId): Result<ByteArray> {
    val sessionState = runCatching { session.assertReady() }
      .getOrElse { throwable -> return Result.failure(throwable) }

    if (sessionState is SessionState.NotReady) {
      return Result.failure(
        AppException(
          error = sessionState.toAppError()
        )
      )
    }

    return runCatching {
      withContext(ioDispatcher) {
        repo.openPreview(photoId).use { stream ->
          val bytes = stream.readBytes()
          if (bytes.isEmpty()) {
            throw IllegalStateException("Preview image is empty.")
          }
          bytes
        }
      }
    }
  }
}

private fun SessionState.NotReady.toAppError(): AppError {
  return when (code) {
    SessionNotReadyCode.CHECKING -> {
      AppError.Wifi(
        code = WifiErrorCode.UNKNOWN,
        message = reason
      )
    }
    SessionNotReadyCode.WIFI_DISCONNECTED -> {
      AppError.Wifi(
        code = WifiErrorCode.DISCONNECTED,
        message = reason
      )
    }
    SessionNotReadyCode.SDK_UNREACHABLE -> {
      AppError.Wifi(
        code = WifiErrorCode.TIMEOUT,
        message = reason
      )
    }
    SessionNotReadyCode.SDK_NATIVE_LIBRARY_MISSING -> {
      AppError.Sdk(
        code = SdkErrorCode.UNKNOWN,
        message = reason
      )
    }
  }
}
