package dev.danielc.core.domain.usecase

import dev.danielc.core.data.QueueIdProvider
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.model.DownloadStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class QueuePhotoStatus {
  QUEUED,
  DOWNLOADING
}

class ObserveQueuePhotoStatusUseCase(
  private val taskDao: DownloadTaskDao,
  private val queueIdProvider: QueueIdProvider
) {
  @OptIn(ExperimentalCoroutinesApi::class)
  fun observe(): Flow<Map<String, QueuePhotoStatus>> {
    return flow { emit(queueIdProvider.getOrCreateQueueId()) }
      .flatMapLatest { queueId ->
        taskDao.observeByQueueId(queueId).map { tasks ->
          buildMap<String, QueuePhotoStatus> {
            tasks.asReversed().forEach { task ->
              val mapped = when (task.status) {
                DownloadStatus.QUEUED -> QueuePhotoStatus.QUEUED
                DownloadStatus.DOWNLOADING -> QueuePhotoStatus.DOWNLOADING
                else -> null
              } ?: return@forEach
              if (task.photoId !in this) {
                put(task.photoId, mapped)
              }
            }
          }
        }
      }
      .distinctUntilChanged()
  }
}
