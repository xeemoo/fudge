package dev.danielc.core.data

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface QueueIdProvider {
  suspend fun getOrCreateQueueId(): String
}

class InMemoryQueueIdProvider(
  private val queueIdFactory: () -> String = { UUID.randomUUID().toString() }
) : QueueIdProvider {
  private val lock = Mutex()
  @Volatile
  private var queueId: String? = null

  override suspend fun getOrCreateQueueId(): String {
    val existing = queueId
    if (existing != null) {
      return existing
    }
    return lock.withLock {
      queueId ?: queueIdFactory().also { created ->
        queueId = created
      }
    }
  }
}
