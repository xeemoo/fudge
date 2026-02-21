package dev.danielc.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

  @Query("SELECT * FROM download_tasks WHERE photoId = :photoId ORDER BY createdAtEpochMillis DESC LIMIT 1")
  fun observeByPhotoId(photoId: String): Flow<DownloadTaskEntity?>

  @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY createdAtEpochMillis ASC")
  fun observeActive(): Flow<List<DownloadTaskEntity>>

  @Query("SELECT * FROM download_tasks WHERE queueId = :queueId ORDER BY createdAtEpochMillis ASC")
  fun observeByQueueId(queueId: String): Flow<List<DownloadTaskEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(entity: DownloadTaskEntity)

  @Query(
    """
      UPDATE download_tasks
      SET status = :status,
          progressPercent = :progressPercent,
          errorCode = :errorCode,
          localUri = :localUri,
          updatedAtEpochMillis = :updatedAtEpochMillis
      WHERE taskId = :taskId
    """
  )
  suspend fun updateStatus(
    taskId: String,
    status: DownloadStatus,
    progressPercent: Int,
    errorCode: DownloadErrorCode?,
    localUri: String?,
    updatedAtEpochMillis: Long
  )

  @Query("SELECT * FROM download_tasks WHERE photoId = :photoId AND status IN ('QUEUED', 'DOWNLOADING') ORDER BY createdAtEpochMillis DESC LIMIT 1")
  suspend fun findExistingActiveTask(photoId: String): DownloadTaskEntity?

  @Query("SELECT * FROM download_tasks WHERE queueId = :queueId AND status = 'QUEUED' ORDER BY createdAtEpochMillis ASC LIMIT 1")
  suspend fun nextQueuedTask(queueId: String): DownloadTaskEntity?
}
