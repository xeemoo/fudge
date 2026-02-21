package dev.danielc.core.domain

class AppException(
  val error: AppError,
  cause: Throwable? = null
) : RuntimeException(cause?.message, cause)
