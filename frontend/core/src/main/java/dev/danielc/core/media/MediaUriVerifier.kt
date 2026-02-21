package dev.danielc.core.media

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MediaUriVerifier {
  suspend fun exists(uriString: String): Boolean
}

class ContentResolverMediaUriVerifier(
  private val contentResolver: ContentResolver,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaUriVerifier {
  override suspend fun exists(uriString: String): Boolean = withContext(ioDispatcher) {
    runCatching {
      val uri = Uri.parse(uriString)
      contentResolver.openInputStream(uri)?.use { stream ->
        stream.read()
      } != null
    }.getOrDefault(false)
  }
}
