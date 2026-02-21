package dev.danielc.core.data

import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class ThumbnailRepositoryImpl(
  private val cameraClient: FujifilmCameraClient,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val maxConcurrentFetches: Int = DEFAULT_MAX_CONCURRENT_FETCHES,
  private val maxCacheEntries: Int = DEFAULT_MAX_CACHE_ENTRIES,
  private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
  private val failureRetryBackoffMillis: Long = DEFAULT_FAILURE_RETRY_BACKOFF_MILLIS,
  private val nowProvider: () -> Long = System::currentTimeMillis
) : ThumbnailRepository {
  init {
    require(maxConcurrentFetches > 0) { "maxConcurrentFetches must be > 0" }
    require(maxCacheEntries > 0) { "maxCacheEntries must be > 0" }
    require(maxCacheBytes > 0) { "maxCacheBytes must be > 0" }
    require(failureRetryBackoffMillis >= 0) { "failureRetryBackoffMillis must be >= 0" }
  }

  private val cacheMutex = Mutex()
  private val memoryCache = LinkedHashMap<String, ByteArray>(maxCacheEntries, 0.75f, true)
  private var memoryCacheSizeBytes: Long = 0L
  private val inFlightMutex = Mutex()
  private val inFlight = mutableMapOf<String, CompletableDeferred<Result<ByteArray>>>()
  private val failureMutex = Mutex()
  private val failureByKey = mutableMapOf<String, ThumbnailFailureRecord>()
  private val fetchSemaphore = Semaphore(maxConcurrentFetches)

  override fun observeThumbnail(photoId: PhotoId): Flow<ThumbnailState> = flow {
    val key = photoId.value
    val cached = cacheMutex.withLock { memoryCache[key] }
    if (cached != null) {
      emit(ThumbnailState.Ready(cached))
      return@flow
    }
    failureMessageWithinBackoff(key)?.let { message ->
      emit(ThumbnailState.Error(message))
      return@flow
    }

    emit(ThumbnailState.Loading)

    val (deferred, owner) = inFlightMutex.withLock {
      val existing = inFlight[key]
      if (existing != null) {
        existing to false
      } else {
        val created = CompletableDeferred<Result<ByteArray>>()
        inFlight[key] = created
        created to true
      }
    }

    if (owner) {
      var ownerCancellation: CancellationException? = null
      try {
        val fetchResult = try {
          val bytes = withContext(ioDispatcher) {
            fetchSemaphore.withPermit {
              cameraClient.fetchThumbnail(photoId)
            }
          }
          if (bytes.isEmpty()) {
            Result.failure(IllegalStateException("Thumbnail is empty."))
          } else {
            Result.success(bytes)
          }
        } catch (cancel: CancellationException) {
          ownerCancellation = cancel
          Result.failure(cancel)
        } catch (throwable: Throwable) {
          Result.failure(throwable)
        }

        if (fetchResult.isSuccess) {
          val bytes = fetchResult.getOrThrow()
          cacheMutex.withLock {
            putToMemoryCacheLocked(key, bytes)
            trimToCapacityLocked()
          }
          clearFailureRecord(key)
        } else {
          val throwable = fetchResult.exceptionOrNull()
          if (throwable != null && throwable !is CancellationException) {
            markFailure(key, throwable.message ?: DEFAULT_ERROR_MESSAGE)
          }
        }
        deferred.complete(fetchResult)
      } finally {
        if (!deferred.isCompleted) {
          deferred.complete(Result.failure(IllegalStateException("Thumbnail loading was interrupted.")))
        }
        inFlightMutex.withLock {
          if (inFlight[key] === deferred) {
            inFlight.remove(key)
          }
        }
      }
      ownerCancellation?.let { throw it }
    }

    val result = deferred.await()
    result
      .onSuccess { bytes ->
        emit(ThumbnailState.Ready(bytes))
      }
      .onFailure { throwable ->
        emit(
          ThumbnailState.Error(
            throwable.message ?: DEFAULT_ERROR_MESSAGE
          )
        )
      }
  }

  private suspend fun failureMessageWithinBackoff(key: String): String? {
    return failureMutex.withLock {
      val failure = failureByKey[key] ?: return@withLock null
      val elapsed = nowProvider() - failure.failedAtMillis
      if (elapsed < failureRetryBackoffMillis) {
        failure.message
      } else {
        failureByKey.remove(key)
        null
      }
    }
  }

  private suspend fun markFailure(key: String, message: String) {
    failureMutex.withLock {
      failureByKey[key] = ThumbnailFailureRecord(
        message = message,
        failedAtMillis = nowProvider()
      )
    }
  }

  private suspend fun clearFailureRecord(key: String) {
    failureMutex.withLock {
      failureByKey.remove(key)
    }
  }

  private fun putToMemoryCacheLocked(key: String, bytes: ByteArray) {
    val previous = memoryCache.put(key, bytes)
    if (previous != null) {
      memoryCacheSizeBytes -= previous.size.toLong()
    }
    memoryCacheSizeBytes += bytes.size.toLong()
  }

  private fun trimToCapacityLocked() {
    while (memoryCache.size > maxCacheEntries || memoryCacheSizeBytes > maxCacheBytes) {
      val iterator = memoryCache.entries.iterator()
      if (!iterator.hasNext()) {
        return
      }
      val eldest = iterator.next()
      memoryCacheSizeBytes -= eldest.value.size.toLong()
      iterator.remove()
    }
  }
}

private data class ThumbnailFailureRecord(
  val message: String,
  val failedAtMillis: Long
)

private const val DEFAULT_MAX_CONCURRENT_FETCHES = 4
private const val DEFAULT_MAX_CACHE_ENTRIES = 200
private const val DEFAULT_MAX_CACHE_BYTES = 8L * 1024L * 1024L
private const val DEFAULT_FAILURE_RETRY_BACKOFF_MILLIS = 1_500L
private const val DEFAULT_ERROR_MESSAGE = "Failed to load thumbnail."
