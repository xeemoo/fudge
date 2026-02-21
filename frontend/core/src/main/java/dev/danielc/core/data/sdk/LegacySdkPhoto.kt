package dev.danielc.core.data.sdk

data class LegacySdkPhoto(
  val photoKey: String,
  val fileName: String?,
  val takenAtEpochMillis: Long?,
  val fileSizeBytes: Long?,
  val mimeType: String?
)
