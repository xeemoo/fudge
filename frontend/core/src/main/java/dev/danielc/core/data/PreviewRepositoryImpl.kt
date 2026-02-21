package dev.danielc.core.data

import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.PreviewRepository
import java.io.InputStream

class PreviewRepositoryImpl(
  private val cameraClient: FujifilmCameraClient
) : PreviewRepository {
  override suspend fun openPreview(photoId: PhotoId): InputStream {
    return cameraClient.openPreview(photoId)
  }
}
