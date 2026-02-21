package dev.danielc.core.media

import android.net.Uri
import java.io.InputStream

data class SaveImageRequest(
  val displayName: String,
  val mimeType: String,
  val relativePath: String = "Pictures/FujifilmCam/"
)

interface MediaStoreImageSaver {
  suspend fun createPending(request: SaveImageRequest): Uri
  suspend fun write(uri: Uri, source: InputStream, onProgress: (Int) -> Unit)
  suspend fun publish(uri: Uri)
  suspend fun delete(uri: Uri)
}
