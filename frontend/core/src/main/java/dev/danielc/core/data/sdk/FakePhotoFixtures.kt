package dev.danielc.core.data.sdk

import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import java.util.Base64

object FakePhotoFixtures {
  private val tinyPngBytes: ByteArray = Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO5xY7kAAAAASUVORK5CYII="
  )

  val photos: List<RemotePhoto> = listOf(
    RemotePhoto(
      photoId = PhotoId("fake-001.png"),
      fileName = "DSCF0001.png",
      takenAtEpochMillis = 1_701_000_000_000,
      fileSizeBytes = tinyPngBytes.size.toLong(),
      mimeType = "image/png"
    ),
    RemotePhoto(
      photoId = PhotoId("fake-002.png"),
      fileName = "DSCF0002.png",
      takenAtEpochMillis = 1_701_000_100_000,
      fileSizeBytes = tinyPngBytes.size.toLong(),
      mimeType = "image/png"
    ),
    RemotePhoto(
      photoId = PhotoId("fake-003.png"),
      fileName = "DSCF0003.png",
      takenAtEpochMillis = 1_701_000_200_000,
      fileSizeBytes = tinyPngBytes.size.toLong(),
      mimeType = "image/png"
    )
  )

  fun thumbnailBytes(photoId: PhotoId): ByteArray = imageBytes(photoId)

  fun previewBytes(photoId: PhotoId): ByteArray = imageBytes(photoId)

  fun originalBytes(photoId: PhotoId): ByteArray = imageBytes(photoId)

  private fun imageBytes(photoId: PhotoId): ByteArray {
    if (photoId.value !in ids) {
      throw IllegalArgumentException("Unknown fake photoId: ${photoId.value}")
    }
    return tinyPngBytes.copyOf()
  }

  private val ids: Set<String> = photos.map { it.photoId.value }.toSet()
}
