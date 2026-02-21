package dev.danielc.core.domain.usecase

import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.SessionState
import dev.danielc.core.data.SessionNotReadyCode
import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.PhotoRepository
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.domain.SdkErrorCode
import dev.danielc.core.domain.WifiErrorCode

class FetchPhotoListUseCase(
  private val repo: PhotoRepository,
  private val session: CameraSessionManager
) {
  suspend operator fun invoke(offset: Int = 0, limit: Int? = null): Result<List<RemotePhoto>> {
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
      val pageLimit = limit
      if (pageLimit == null) {
        repo.fetchRemotePhotos()
      } else {
        repo.fetchRemotePhotosPage(offset = offset, limit = pageLimit)
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
