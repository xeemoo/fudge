package dev.danielc.sdk.legacy

import java.io.InputStream

interface FujifilmLegacySdk {
  suspend fun isReachable(): Boolean
  suspend fun fetchPhotoList(): List<LegacyPhotoDto>
  suspend fun fetchPhotoListPage(offset: Int, limit: Int): List<LegacyPhotoDto> {
    if (offset < 0 || limit <= 0) {
      return emptyList()
    }
    return fetchPhotoList()
      .drop(offset)
      .take(limit)
  }
  suspend fun fetchThumbnail(photoKey: String): ByteArray
  suspend fun openPreviewStream(photoKey: String): InputStream
  suspend fun openOriginalStream(photoKey: String): InputStream
}
