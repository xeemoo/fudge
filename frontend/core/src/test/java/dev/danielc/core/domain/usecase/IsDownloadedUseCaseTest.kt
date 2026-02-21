package dev.danielc.core.domain.usecase

import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.media.MediaUriVerifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IsDownloadedUseCaseTest {

  @Test
  fun invoke_whenIndexExistsButFileMissing_returnsFalse() = runTest {
    val dao = FakeDownloadedPhotoDao().apply {
      upsert(
        DownloadedPhotoEntity(
          photoId = "photo-1",
          localUri = "content://missing/photo-1",
          displayName = "photo-1.jpg",
          relativePath = "Pictures/FujifilmCam",
          mimeType = "image/jpeg",
          downloadedAtEpochMillis = 1L
        )
      )
    }
    val useCase = IsDownloadedUseCase(
      downloadedDao = dao,
      verifier = FakeMediaUriVerifier(existingUris = emptySet())
    )

    val result = useCase(PhotoId("photo-1"))

    assertEquals(false, result)
  }

  @Test
  fun observe_whenFileMissing_emitsFalse() = runTest {
    val dao = FakeDownloadedPhotoDao().apply {
      upsert(
        DownloadedPhotoEntity(
          photoId = "photo-2",
          localUri = "content://missing/photo-2",
          displayName = "photo-2.jpg",
          relativePath = "Pictures/FujifilmCam",
          mimeType = "image/jpeg",
          downloadedAtEpochMillis = 1L
        )
      )
    }
    val useCase = IsDownloadedUseCase(
      downloadedDao = dao,
      verifier = FakeMediaUriVerifier(existingUris = emptySet())
    )

    val result = useCase.observe(PhotoId("photo-2")).first()

    assertEquals(false, result)
  }
}

private class FakeDownloadedPhotoDao : DownloadedPhotoDao {
  private val map = linkedMapOf<String, DownloadedPhotoEntity>()
  private val flows = linkedMapOf<String, MutableStateFlow<DownloadedPhotoEntity?>>()

  override fun observe(photoId: String): Flow<DownloadedPhotoEntity?> {
    return flows.getOrPut(photoId) { MutableStateFlow(map[photoId]) }
  }

  override suspend fun get(photoId: String): DownloadedPhotoEntity? = map[photoId]

  override suspend fun upsert(entity: DownloadedPhotoEntity) {
    map[entity.photoId] = entity
    flows.getOrPut(entity.photoId) { MutableStateFlow(entity) }.value = entity
  }
}

private class FakeMediaUriVerifier(
  private val existingUris: Set<String>
) : MediaUriVerifier {
  override suspend fun exists(uriString: String): Boolean = uriString in existingUris
}
