package dev.danielc.core.domain.usecase

import dev.danielc.core.data.QueueIdProvider
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveQueueStatsUseCaseTest {

  @Test
  fun observe_countsDoneTotalAndRunningForCurrentQueue() = runTest {
    val taskDao = FakeDownloadTaskDao(
      tasks = listOf(
        task("t1", "queue-A", "p1", DownloadStatus.DOWNLOADING, 1L),
        task("t2", "queue-A", "p2", DownloadStatus.QUEUED, 2L),
        task("t3", "queue-A", "p3", DownloadStatus.SUCCESS, 3L),
        task("t4", "queue-A", "p4", DownloadStatus.FAILED, 4L),
        task("t5", "queue-B", "x1", DownloadStatus.SUCCESS, 1L)
      )
    )
    val useCase = ObserveQueueStatsUseCase(taskDao, FakeQueueIdProvider("queue-A"))

    val result = useCase.observe().first()

    assertEquals(QueueStats(done = 1, total = 3, running = true), result)
  }

  @Test
  fun observeQueuePhotoStatus_returnsOnlyQueuedAndDownloadingByPhoto() = runTest {
    val taskDao = FakeDownloadTaskDao(
      tasks = listOf(
        task("t1", "queue-A", "p1", DownloadStatus.QUEUED, 1L),
        task("t2", "queue-A", "p2", DownloadStatus.DOWNLOADING, 2L),
        task("t3", "queue-A", "p3", DownloadStatus.SUCCESS, 3L),
        task("t4", "queue-A", "p1", DownloadStatus.DOWNLOADING, 4L)
      )
    )
    val useCase = ObserveQueuePhotoStatusUseCase(taskDao, FakeQueueIdProvider("queue-A"))

    val result = useCase.observe().first()

    assertEquals(
      mapOf(
        "p1" to QueuePhotoStatus.DOWNLOADING,
        "p2" to QueuePhotoStatus.DOWNLOADING
      ),
      result
    )
  }

  private fun task(
    taskId: String,
    queueId: String,
    photoId: String,
    status: DownloadStatus,
    createdAt: Long
  ): DownloadTaskEntity {
    return DownloadTaskEntity(
      taskId = taskId,
      queueId = queueId,
      photoId = photoId,
      status = status,
      progressPercent = if (status == DownloadStatus.SUCCESS) 100 else 0,
      errorCode = null,
      localUri = null,
      createdAtEpochMillis = createdAt,
      updatedAtEpochMillis = createdAt
    )
  }
}

private class FakeQueueIdProvider(
  private val queueId: String
) : QueueIdProvider {
  override suspend fun getOrCreateQueueId(): String = queueId
}

private class FakeDownloadTaskDao(
  tasks: List<DownloadTaskEntity>
) : DownloadTaskDao {
  private val tasksFlow = MutableStateFlow(tasks)

  override fun observeByPhotoId(photoId: String): Flow<DownloadTaskEntity?> = flowOf(
    tasksFlow.value.filter { it.photoId == photoId }.maxByOrNull { it.createdAtEpochMillis }
  )

  override fun observeActive(): Flow<List<DownloadTaskEntity>> = flowOf(
    tasksFlow.value.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
  )

  override fun observeByQueueId(queueId: String): Flow<List<DownloadTaskEntity>> = flowOf(
    tasksFlow.value.filter { it.queueId == queueId }.sortedBy { it.createdAtEpochMillis }
  )

  override suspend fun insert(entity: DownloadTaskEntity) = Unit

  override suspend fun updateStatus(
    taskId: String,
    status: DownloadStatus,
    progressPercent: Int,
    errorCode: DownloadErrorCode?,
    localUri: String?,
    updatedAtEpochMillis: Long
  ) = Unit

  override suspend fun findExistingActiveTask(photoId: String): DownloadTaskEntity? = null

  override suspend fun nextQueuedTask(queueId: String): DownloadTaskEntity? = null
}
