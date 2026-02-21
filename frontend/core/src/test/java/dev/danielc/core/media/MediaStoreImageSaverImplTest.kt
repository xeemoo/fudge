package dev.danielc.core.media

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreImageSaverImplTest {

  @Test
  fun createPending_setsMediaStoreColumnsAndReturnsUri() = runTest {
    val expectedUri = Uri.parse("content://media/external/images/media/100")
    val io = FakeMediaStoreIo(insertResult = expectedUri)
    val saver = MediaStoreImageSaverImpl(
      io = io,
      ioDispatcher = StandardTestDispatcher(testScheduler)
    )

    val result = saver.createPending(
      SaveImageRequest(
        displayName = "DSCF1234.JPG",
        mimeType = "image/jpeg"
      )
    )

    assertEquals(expectedUri, result)
    val inserted = checkNotNull(io.lastInsertedValues)
    assertEquals("DSCF1234.JPG", inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
    assertEquals("image/jpeg", inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE))
    assertEquals("Pictures/FujifilmCam/", inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
    assertEquals(1, inserted.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
  }

  @Test
  fun write_reportsMonotonicProgress_andWritesBytes() = runTest {
    val targetUri = Uri.parse("content://media/external/images/media/101")
    val sink = ByteArrayOutputStream()
    val io = FakeMediaStoreIo(
      outputStreamFactory = { uri ->
        if (uri == targetUri) sink else null
      }
    )
    val saver = MediaStoreImageSaverImpl(
      io = io,
      ioDispatcher = StandardTestDispatcher(testScheduler)
    )
    val payload = ByteArray(16_384) { (it % 127).toByte() }
    val progress = mutableListOf<Int>()

    saver.write(targetUri, ByteArrayInputStream(payload)) { p ->
      progress += p
    }

    assertArrayEquals(payload, sink.toByteArray())
    assertTrue(progress.isNotEmpty())
    assertEquals(0, progress.first())
    assertEquals(100, progress.last())
    assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
  }

  @Test
  fun publish_setsPendingToZero() = runTest {
    val targetUri = Uri.parse("content://media/external/images/media/102")
    val io = FakeMediaStoreIo(updateResult = 1)
    val saver = MediaStoreImageSaverImpl(
      io = io,
      ioDispatcher = StandardTestDispatcher(testScheduler)
    )

    saver.publish(targetUri)

    assertEquals(targetUri, io.lastUpdatedUri)
    val updated = checkNotNull(io.lastUpdatedValues)
    assertEquals(0, updated.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
  }

  @Test
  fun write_whenOutputFails_deletesPendingUri() = runTest {
    val targetUri = Uri.parse("content://media/external/images/media/103")
    val io = FakeMediaStoreIo(
      outputStreamFactory = { FailingOutputStream() }
    )
    val saver = MediaStoreImageSaverImpl(
      io = io,
      ioDispatcher = StandardTestDispatcher(testScheduler)
    )

    val throwable = runCatching {
      saver.write(targetUri, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))) {}
    }.exceptionOrNull()

    assertTrue(throwable is IOException)
    assertEquals(listOf(targetUri), io.deletedUris)
  }
}

private class FakeMediaStoreIo(
  private val insertResult: Uri? = Uri.parse("content://media/external/images/media/1"),
  private val outputStreamFactory: (Uri) -> OutputStream? = { ByteArrayOutputStream() },
  private val updateResult: Int = 1
) : MediaStoreIo {
  var lastInsertedValues: ContentValues? = null
  var lastUpdatedUri: Uri? = null
  var lastUpdatedValues: ContentValues? = null
  val deletedUris = mutableListOf<Uri>()

  override fun insert(collectionUri: Uri, values: ContentValues): Uri? {
    lastInsertedValues = ContentValues(values)
    return insertResult
  }

  override fun openOutputStream(uri: Uri): OutputStream? {
    return outputStreamFactory(uri)
  }

  override fun update(uri: Uri, values: ContentValues): Int {
    lastUpdatedUri = uri
    lastUpdatedValues = ContentValues(values)
    return updateResult
  }

  override fun delete(uri: Uri): Int {
    deletedUris += uri
    return 1
  }
}

private class FailingOutputStream : OutputStream() {
  override fun write(b: Int) {
    throw IOException("write failed")
  }
}
