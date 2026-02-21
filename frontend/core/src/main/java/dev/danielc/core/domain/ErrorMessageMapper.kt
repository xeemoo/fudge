package dev.danielc.core.domain

import android.content.Context
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.R

interface ErrorMessageMapper {
  fun toUserMessage(error: AppError): String
  fun toDownloadFailMessage(code: DownloadErrorCode): String
}

class DefaultErrorMessageMapper(
  private val context: Context
) : ErrorMessageMapper {
  override fun toUserMessage(error: AppError): String {
    return when (error) {
      is AppError.Wifi -> when (error.code) {
        WifiErrorCode.DISCONNECTED -> context.getString(R.string.error_wifi_disconnected)
        WifiErrorCode.TIMEOUT -> context.getString(R.string.error_wifi_timeout)
        WifiErrorCode.UNKNOWN -> context.getString(R.string.error_wifi_unknown)
      }
      is AppError.Sdk -> when (error.code) {
        SdkErrorCode.TIMEOUT -> context.getString(R.string.error_sdk_timeout)
        SdkErrorCode.IO,
        SdkErrorCode.PROTOCOL,
        SdkErrorCode.UNKNOWN -> context.getString(R.string.error_sdk_general)
      }
      is AppError.Storage -> when (error.code) {
        StorageErrorCode.NO_SPACE -> context.getString(R.string.error_storage_no_space)
        StorageErrorCode.NO_PERMISSION -> context.getString(R.string.error_storage_no_permission)
        StorageErrorCode.IO,
        StorageErrorCode.UNKNOWN -> context.getString(R.string.error_storage_unknown)
      }
      is AppError.Unknown -> error.throwable.message ?: context.getString(R.string.error_operation_unknown)
    }
  }

  override fun toDownloadFailMessage(code: DownloadErrorCode): String {
    return when (code) {
      DownloadErrorCode.WIFI_DISCONNECTED -> context.getString(R.string.error_download_wifi_disconnected)
      DownloadErrorCode.SDK_ERROR -> context.getString(R.string.error_download_sdk)
      DownloadErrorCode.STORAGE_FULL -> context.getString(R.string.error_download_storage_full)
      DownloadErrorCode.TIMEOUT -> context.getString(R.string.error_download_timeout)
      DownloadErrorCode.UNKNOWN -> context.getString(R.string.error_download_unknown)
    }
  }
}
