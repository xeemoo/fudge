package dev.danielc.core.data

import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoRepository
import dev.danielc.core.domain.RemotePhoto

class PhotoRepositoryImpl(
  private val cameraClient: FujifilmCameraClient
) : PhotoRepository {
  override suspend fun fetchRemotePhotos(): List<RemotePhoto> {
    return cameraClient.fetchRemotePhotos()
  }

  override suspend fun fetchRemotePhotosPage(offset: Int, limit: Int): List<RemotePhoto> {
    return cameraClient.fetchRemotePhotosPage(offset, limit)
  }
}
