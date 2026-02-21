package dev.danielc.core.domain

interface PhotoRepository {
  suspend fun fetchRemotePhotos(): List<RemotePhoto>

  suspend fun fetchRemotePhotosPage(offset: Int, limit: Int): List<RemotePhoto> {
    if (offset < 0 || limit <= 0) {
      return emptyList()
    }
    return fetchRemotePhotos()
      .drop(offset)
      .take(limit)
  }
}
