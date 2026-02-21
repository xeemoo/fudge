package dev.danielc.core.domain

data class RemotePhoto(
  val photoId: PhotoId,
  val fileName: String?,
  val takenAtEpochMillis: Long?,
  val fileSizeBytes: Long?,
  val mimeType: String?
)
