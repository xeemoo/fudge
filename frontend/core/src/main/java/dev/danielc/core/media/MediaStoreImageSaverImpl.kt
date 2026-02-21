package dev.danielc.core.media

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreImageSaverImpl internal constructor(
  private val io: MediaStoreIo,
  private val externalCollectionUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaStoreImageSaver {

  constructor(
    contentResolver: ContentResolver,
    externalCollectionUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
  ) : this(
    io = ContentResolverMediaStoreIo(contentResolver),
    externalCollectionUri = externalCollectionUri,
    ioDispatcher = ioDispatcher
  )

  override suspend fun createPending(request: SaveImageRequest): Uri = withContext(ioDispatcher) {
    val values = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, request.displayName)
      put(MediaStore.MediaColumns.MIME_TYPE, request.mimeType)
      put(MediaStore.MediaColumns.RELATIVE_PATH, request.relativePath)
      put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    io.insert(externalCollectionUri, values)
      ?: throw IOException("Failed to create pending MediaStore item.")
  }

  override suspend fun write(uri: Uri, source: InputStream, onProgress: (Int) -> Unit) {
    return withContext(ioDispatcher) {
      try {
        io.openOutputStream(uri)?.use { sink ->
          source.use { input ->
            copyWithProgress(input, sink, onProgress)
          }
        } ?: throw IOException("Unable to open output stream for uri=$uri")
      } catch (t: Throwable) {
        runCatching { io.delete(uri) }
        throw t
      }
    }
  }

  override suspend fun publish(uri: Uri) = withContext(ioDispatcher) {
    val values = ContentValues().apply {
      put(MediaStore.MediaColumns.IS_PENDING, 0)
    }
    val updated = io.update(uri, values)
    if (updated <= 0) {
      throw IOException("Failed to publish MediaStore item: uri=$uri")
    }
  }

  override suspend fun delete(uri: Uri) {
    withContext(ioDispatcher) {
      io.delete(uri)
    }
  }

  private fun copyWithProgress(
    input: InputStream,
    sink: OutputStream,
    onProgress: (Int) -> Unit
  ) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val totalBytes = input.available().takeIf { it > 0 }?.toLong()
    var bytesWritten = 0L
    var lastProgress = -1
    fun report(progress: Int) {
      val normalized = progress.coerceIn(0, 100)
      if (normalized > lastProgress) {
        lastProgress = normalized
        onProgress(normalized)
      }
    }

    report(0)
    while (true) {
      val count = input.read(buffer)
      if (count <= 0) {
        break
      }
      sink.write(buffer, 0, count)
      bytesWritten += count.toLong()
      if (totalBytes != null) {
        val progress = ((bytesWritten * 100L) / totalBytes).toInt().coerceIn(0, 100)
        report(progress)
      }
    }
    sink.flush()
    report(100)
  }
}

internal interface MediaStoreIo {
  fun insert(collectionUri: Uri, values: ContentValues): Uri?
  fun openOutputStream(uri: Uri): OutputStream?
  fun update(uri: Uri, values: ContentValues): Int
  fun delete(uri: Uri): Int
}

internal class ContentResolverMediaStoreIo(
  private val contentResolver: ContentResolver
) : MediaStoreIo {
  override fun insert(collectionUri: Uri, values: ContentValues): Uri? {
    return contentResolver.insert(collectionUri, values)
  }

  override fun openOutputStream(uri: Uri): OutputStream? {
    return contentResolver.openOutputStream(uri, "w")
  }

  override fun update(uri: Uri, values: ContentValues): Int {
    return contentResolver.update(uri, values, null, null)
  }

  override fun delete(uri: Uri): Int {
    return contentResolver.delete(uri, null, null)
  }
}
