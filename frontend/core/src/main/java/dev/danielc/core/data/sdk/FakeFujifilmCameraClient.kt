package dev.danielc.core.data.sdk

import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import java.io.ByteArrayInputStream
import java.io.InputStream

class FakeFujifilmCameraClient(
  private val fixtures: List<RemotePhoto> = FakePhotoFixtures.photos
) : FujifilmCameraClient {

  override suspend fun isReachable(): Boolean = true

  override suspend fun fetchRemotePhotos(): List<RemotePhoto> = fixtures

  override suspend fun fetchRemotePhotosPage(offset: Int, limit: Int): List<RemotePhoto> {
    if (offset < 0 || limit <= 0) {
      return emptyList()
    }
    return fixtures.drop(offset).take(limit)
  }

  override suspend fun fetchThumbnail(photoId: PhotoId): ByteArray {
    return FakePhotoFixtures.thumbnailBytes(photoId)
  }

  override suspend fun openPreview(photoId: PhotoId): InputStream {
    return ByteArrayInputStream(FakePhotoFixtures.previewBytes(photoId))
  }

  override suspend fun openOriginal(photoId: PhotoId): InputStream {
    return ByteArrayInputStream(FakePhotoFixtures.originalBytes(photoId))
  }
}
