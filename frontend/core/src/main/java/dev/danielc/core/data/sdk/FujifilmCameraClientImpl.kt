package dev.danielc.core.data.sdk

import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.domain.SdkErrorCode
import dev.danielc.core.domain.StorageErrorCode
import dev.danielc.core.domain.WifiErrorCode
import dev.danielc.sdk.legacy.FujifilmLegacySdk
import dev.danielc.sdk.legacy.FujifilmLegacySdkException
import dev.danielc.sdk.legacy.LegacyPhotoDto
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FujifilmCameraClientImpl(
  private val legacySdk: FujifilmLegacySdk,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FujifilmCameraClient {

  override suspend fun isReachable(): Boolean = execute {
    legacySdk.isReachable()
  }

  override suspend fun fetchRemotePhotos(): List<RemotePhoto> = execute {
    legacySdk.fetchPhotoList().map { dto -> dto.toRemotePhoto() }
  }

  override suspend fun fetchRemotePhotosPage(offset: Int, limit: Int): List<RemotePhoto> = execute {
    legacySdk.fetchPhotoListPage(offset, limit).map { dto -> dto.toRemotePhoto() }
  }

  override suspend fun fetchThumbnail(photoId: PhotoId): ByteArray = execute {
    legacySdk.fetchThumbnail(photoId.value)
  }

  override suspend fun openPreview(photoId: PhotoId) = execute {
    legacySdk.openPreviewStream(photoId.value)
  }

  override suspend fun openOriginal(photoId: PhotoId) = execute {
    legacySdk.openOriginalStream(photoId.value)
  }

  private suspend fun <T> execute(action: suspend () -> T): T {
    return withContext(ioDispatcher) {
      try {
        action()
      } catch (throwable: Throwable) {
        throw AppException(error = throwable.toAppError(), cause = throwable)
      }
    }
  }

  private fun LegacyPhotoDto.toPhotoId(): PhotoId {
    val sdkKey = photoKey.trim()
    if (sdkKey.isNotEmpty()) return PhotoId(sdkKey)

    val fallbackRaw = buildString {
      append(fileName.orEmpty())
      append('|')
      append(takenAtEpochMillis ?: "null")
      append('|')
      append(fileSizeBytes ?: "null")
    }

    return PhotoId(fallbackRaw.sha256Hex())
  }

  private fun LegacyPhotoDto.toRemotePhoto(): RemotePhoto {
    return RemotePhoto(
      photoId = toPhotoId(),
      fileName = fileName,
      takenAtEpochMillis = takenAtEpochMillis,
      fileSizeBytes = fileSizeBytes,
      mimeType = mimeType
    )
  }

  private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
  }
}

internal fun Throwable.toAppError(): AppError {
  val message = message

  return when (this) {
    is SocketTimeoutException,
    is TimeoutException -> AppError.Sdk(SdkErrorCode.TIMEOUT, message)

    is ProtocolException -> AppError.Sdk(SdkErrorCode.PROTOCOL, message)

    is UnknownHostException,
    is SocketException -> AppError.Wifi(WifiErrorCode.DISCONNECTED, message)

    is IOException -> {
      val normalized = message?.lowercase().orEmpty()
      if ("no space" in normalized || "enospc" in normalized || "space left" in normalized) {
        AppError.Storage(StorageErrorCode.NO_SPACE, message)
      } else {
        AppError.Sdk(SdkErrorCode.IO, message)
      }
    }

    is FujifilmLegacySdkException -> {
      val normalized = message?.lowercase().orEmpty()
      when {
        "timeout" in normalized -> AppError.Sdk(SdkErrorCode.TIMEOUT, message)
        "io" in normalized || "i/o" in normalized || "rc=-5" in normalized -> AppError.Sdk(SdkErrorCode.IO, message)
        "protocol" in normalized || "invalid" in normalized -> AppError.Sdk(SdkErrorCode.PROTOCOL, message)
        "storage" in normalized || "space" in normalized -> AppError.Storage(StorageErrorCode.IO, message)
        else -> AppError.Sdk(SdkErrorCode.UNKNOWN, message)
      }
    }

    else -> AppError.Unknown(this)
  }
}
