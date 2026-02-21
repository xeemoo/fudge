package dev.danielc.core.data

import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThumbnailRepositoryImplTest {

  private val dispatcher = StandardTestDispatcher()

  @Test
  fun observeThumbnail_concurrentSamePhotoId_onlyFetchesOnce() = runTest(dispatcher) {
    val client = FakeCameraClient(
      thumbnailResults = mutableListOf(Result.success(byteArrayOf(1, 2, 3))),
      fetchDelayMillis = 50L
    )
    val repository = ThumbnailRepositoryImpl(client, dispatcher)
    val photoId = PhotoId("photo-1")

    val first = async { repository.observeThumbnail(photoId).toList() }
    val second = async { repository.observeThumbnail(photoId).toList() }
    advanceUntilIdle()

    val firstStates = first.await()
    val secondStates = second.await()

    assertEquals(1, client.fetchThumbnailCount)
    assertTrue(firstStates.first() is ThumbnailState.Loading)
    assertTrue(firstStates.last() is ThumbnailState.Ready)
    assertTrue(secondStates.first() is ThumbnailState.Loading)
    assertTrue(secondStates.last() is ThumbnailState.Ready)
  }

  @Test
  fun observeThumbnail_afterFirstSuccess_readsFromMemoryCache() = runTest(dispatcher) {
    val client = FakeCameraClient(
      thumbnailResults = mutableListOf(Result.success(byteArrayOf(7, 8, 9)))
    )
    val repository = ThumbnailRepositoryImpl(client, dispatcher)
    val photoId = PhotoId("photo-2")

    val firstStates = repository.observeThumbnail(photoId).toList()
    val secondStates = repository.observeThumbnail(photoId).toList()

    assertEquals(1, client.fetchThumbnailCount)
    assertTrue(firstStates.first() is ThumbnailState.Loading)
    assertTrue(firstStates.last() is ThumbnailState.Ready)
    assertEquals(1, secondStates.size)
    assertTrue(secondStates.single() is ThumbnailState.Ready)
  }

  @Test
  fun observeThumbnail_whenFetchFails_emitsErrorAndNextRetryCanRefetch() = runTest(dispatcher) {
    val client = FakeCameraClient(
      thumbnailResults = mutableListOf(
        Result.failure(IllegalStateException("thumb failed")),
        Result.success(byteArrayOf(3, 4, 5))
      )
    )
    val repository = ThumbnailRepositoryImpl(
      cameraClient = client,
      ioDispatcher = dispatcher,
      failureRetryBackoffMillis = 0L
    )
    val photoId = PhotoId("photo-3")

    val firstStates = repository.observeThumbnail(photoId).toList()
    val secondStates = repository.observeThumbnail(photoId).toList()

    assertEquals(2, client.fetchThumbnailCount)
    assertTrue(firstStates.first() is ThumbnailState.Loading)
    assertTrue(firstStates.last() is ThumbnailState.Error)
    assertTrue(secondStates.first() is ThumbnailState.Loading)
    assertTrue(secondStates.last() is ThumbnailState.Ready)
  }

  @Test
  fun observeThumbnail_whenBytesEmpty_emitsError() = runTest(dispatcher) {
    val client = FakeCameraClient(
      thumbnailResults = mutableListOf(Result.success(byteArrayOf()))
    )
    val repository = ThumbnailRepositoryImpl(client, dispatcher)

    val states = repository.observeThumbnail(PhotoId("photo-empty")).toList()

    assertEquals(1, client.fetchThumbnailCount)
    assertTrue(states.first() is ThumbnailState.Loading)
    assertTrue(states.last() is ThumbnailState.Error)
  }

  @Test
  fun observeThumbnail_whenOwnerCancelled_retryCanRefetchWithoutStuckInFlight() = runTest(dispatcher) {
    val client = FakeCameraClient(
      thumbnailResults = mutableListOf(Result.success(byteArrayOf(9, 9, 9))),
      fetchDelayMillis = 1_000L
    )
    val repository = ThumbnailRepositoryImpl(client, dispatcher)
    val photoId = PhotoId("photo-cancel")

    val firstCollector = async { repository.observeThumbnail(photoId).toList() }
    advanceTimeBy(100L)
    firstCollector.cancel()
    advanceUntilIdle()

    val retryStates = repository.observeThumbnail(photoId).toList()

    assertEquals(2, client.fetchThumbnailCount)
    assertTrue(retryStates.first() is ThumbnailState.Loading)
    assertTrue(retryStates.last() is ThumbnailState.Ready)
  }

  @Test
  fun observeThumbnail_concurrentDifferentPhotoIds_limitsGlobalFetchConcurrency() = runTest(dispatcher) {
    val client = FakeCameraClient(
      thumbnailResults = MutableList(6) { Result.success(byteArrayOf(1, 2, 3)) },
      fetchDelayMillis = 100L
    )
    val repository = ThumbnailRepositoryImpl(
      cameraClient = client,
      ioDispatcher = dispatcher,
      maxConcurrentFetches = 2
    )

    val collectors = (1..6).map { index ->
      async { repository.observeThumbnail(PhotoId("photo-$index")).toList() }
    }
    advanceUntilIdle()
    collectors.forEach { it.await() }

    assertEquals(6, client.fetchThumbnailCount)
    assertEquals(2, client.maxConcurrentFetchCount)
  }

  @Test
  fun observeThumbnail_whenFailureWithinBackoff_skipsImmediateRefetch() = runTest(dispatcher) {
    var now = 1_000L
    val client = FakeCameraClient(
      thumbnailResults = mutableListOf(
        Result.failure(IllegalStateException("temporary failure")),
        Result.success(byteArrayOf(7, 7, 7))
      )
    )
    val repository = ThumbnailRepositoryImpl(
      cameraClient = client,
      ioDispatcher = dispatcher,
      failureRetryBackoffMillis = 2_000L,
      nowProvider = { now }
    )
    val photoId = PhotoId("photo-backoff")

    val firstStates = repository.observeThumbnail(photoId).toList()
    val immediateRetryStates = repository.observeThumbnail(photoId).toList()

    assertEquals(1, client.fetchThumbnailCount)
    assertTrue(firstStates.first() is ThumbnailState.Loading)
    assertTrue(firstStates.last() is ThumbnailState.Error)
    assertEquals(1, immediateRetryStates.size)
    assertTrue(immediateRetryStates.single() is ThumbnailState.Error)

    now += 2_001L
    val afterBackoffStates = repository.observeThumbnail(photoId).toList()

    assertEquals(2, client.fetchThumbnailCount)
    assertTrue(afterBackoffStates.first() is ThumbnailState.Loading)
    assertTrue(afterBackoffStates.last() is ThumbnailState.Ready)
  }

  @Test
  fun observeThumbnail_whenCacheBytesExceeded_evictsLruEntry() = runTest(dispatcher) {
    val client = FakeCameraClient(
      thumbnailResults = mutableListOf(
        Result.success(byteArrayOf(1, 1, 1)),
        Result.success(byteArrayOf(2, 2, 2)),
        Result.success(byteArrayOf(3, 3, 3))
      )
    )
    val repository = ThumbnailRepositoryImpl(
      cameraClient = client,
      ioDispatcher = dispatcher,
      maxCacheEntries = 10,
      maxCacheBytes = 4L
    )

    repository.observeThumbnail(PhotoId("photo-a")).toList()
    repository.observeThumbnail(PhotoId("photo-b")).toList()
    repository.observeThumbnail(PhotoId("photo-a")).toList()

    assertEquals(3, client.fetchThumbnailCount)
  }

  private class FakeCameraClient(
    private val thumbnailResults: MutableList<Result<ByteArray>>,
    private val fetchDelayMillis: Long = 0L
  ) : FujifilmCameraClient {
    var fetchThumbnailCount: Int = 0
    var maxConcurrentFetchCount: Int = 0
    private var inFlightFetchCount: Int = 0

    override suspend fun isReachable(): Boolean = true

    override suspend fun fetchRemotePhotos(): List<RemotePhoto> = emptyList()

    override suspend fun fetchThumbnail(photoId: PhotoId): ByteArray {
      fetchThumbnailCount += 1
      inFlightFetchCount += 1
      if (inFlightFetchCount > maxConcurrentFetchCount) {
        maxConcurrentFetchCount = inFlightFetchCount
      }
      return try {
        if (fetchDelayMillis > 0) {
          delay(fetchDelayMillis)
        }
        val result = if (thumbnailResults.isNotEmpty()) {
          thumbnailResults.removeAt(0)
        } else {
          Result.failure(IllegalStateException("missing fake thumbnail result"))
        }
        result.getOrThrow()
      } finally {
        inFlightFetchCount -= 1
      }
    }

    override suspend fun openPreview(photoId: PhotoId): InputStream {
      return ByteArrayInputStream(ByteArray(0))
    }

    override suspend fun openOriginal(photoId: PhotoId): InputStream {
      return ByteArrayInputStream(ByteArray(0))
    }
  }
}
