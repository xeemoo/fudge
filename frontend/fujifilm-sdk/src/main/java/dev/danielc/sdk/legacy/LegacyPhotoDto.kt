package dev.danielc.sdk.legacy

data class LegacyPhotoDto(
  val photoKey: String,
  val fileName: String?,
  val takenAtEpochMillis: Long?,
  val fileSizeBytes: Long?,
  val mimeType: String?
)
