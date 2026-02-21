package dev.danielc.core.domain

import java.io.InputStream

interface FujifilmCameraClient {
  suspend fun isReachable(): Boolean
  suspend fun fetchRemotePhotos(): List<RemotePhoto>
  suspend fun fetchRemotePhotosPage(offset: Int, limit: Int): List<RemotePhoto> {
    if (offset < 0 || limit <= 0) {
      return emptyList()
    }
    return fetchRemotePhotos()
      .drop(offset)
      .take(limit)
  }
  suspend fun fetchThumbnail(photoId: PhotoId): ByteArray
  suspend fun openPreview(photoId: PhotoId): InputStream
  suspend fun openOriginal(photoId: PhotoId): InputStream
}
