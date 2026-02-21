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

data class QueueStats(
  val done: Int,
  val total: Int,
  val running: Boolean
)

class ObserveQueueStatsUseCase(
  private val taskDao: DownloadTaskDao,
  private val queueIdProvider: QueueIdProvider
) {
  @OptIn(ExperimentalCoroutinesApi::class)
  fun observe(): Flow<QueueStats> {
    return flow { emit(queueIdProvider.getOrCreateQueueId()) }
      .flatMapLatest { queueId ->
        taskDao.observeByQueueId(queueId).map { tasks ->
          val done = tasks.count { it.status == DownloadStatus.SUCCESS }
          val total = tasks.count {
            it.status == DownloadStatus.SUCCESS ||
              it.status == DownloadStatus.QUEUED ||
              it.status == DownloadStatus.DOWNLOADING
          }
          val running = tasks.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
          }
          QueueStats(done = done, total = total, running = running)
        }
      }
      .distinctUntilChanged()
  }
}
