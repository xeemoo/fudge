package dev.danielc.core.data.sdk

import dev.danielc.sdk.legacy.FujifilmLegacySdk

class FujifilmLegacySdkAdapter(
  private val sdk: FujifilmLegacySdk
) {

  suspend fun fetchPhotoList(): List<LegacySdkPhoto> {
    return sdk.fetchPhotoList().map { photo ->
      LegacySdkPhoto(
        photoKey = photo.photoKey,
        fileName = photo.fileName,
        takenAtEpochMillis = photo.takenAtEpochMillis,
        fileSizeBytes = photo.fileSizeBytes,
        mimeType = photo.mimeType
      )
    }
  }
}
