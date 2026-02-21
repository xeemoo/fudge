package dev.danielc.core.domain

import java.io.InputStream

interface PreviewRepository {
  suspend fun openPreview(photoId: PhotoId): InputStream
}
