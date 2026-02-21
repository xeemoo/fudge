package dev.danielc.core.domain.usecase

import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.media.MediaUriVerifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class IsDownloadedUseCase(
  private val downloadedDao: DownloadedPhotoDao,
  private val verifier: MediaUriVerifier
) {
  suspend operator fun invoke(photoId: PhotoId): Boolean {
    val downloaded = downloadedDao.get(photoId.value) ?: return false
    return verifier.exists(downloaded.localUri)
  }

  fun observe(photoId: PhotoId): Flow<Boolean> {
    return downloadedDao.observe(photoId.value)
      .map { downloaded ->
        if (downloaded == null) {
          false
        } else {
          verifier.exists(downloaded.localUri)
        }
      }
      .distinctUntilChanged()
  }
}
