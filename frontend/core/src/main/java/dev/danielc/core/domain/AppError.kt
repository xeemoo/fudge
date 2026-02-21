package dev.danielc.core.domain

sealed interface AppError {
  data class Wifi(val code: WifiErrorCode, val message: String? = null) : AppError
  data class Sdk(val code: SdkErrorCode, val message: String? = null) : AppError
  data class Storage(val code: StorageErrorCode, val message: String? = null) : AppError
  data class Unknown(val throwable: Throwable) : AppError
}

enum class WifiErrorCode {
  DISCONNECTED,
  TIMEOUT,
  UNKNOWN
}

enum class SdkErrorCode {
  TIMEOUT,
  IO,
  PROTOCOL,
  UNKNOWN
}

enum class StorageErrorCode {
  NO_SPACE,
  NO_PERMISSION,
  IO,
  UNKNOWN
}
