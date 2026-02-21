package dev.danielc.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus

@Entity(
  tableName = "download_tasks",
  indices = [Index("photoId"), Index("queueId")]
)
data class DownloadTaskEntity(
  @PrimaryKey val taskId: String,
  val queueId: String,
  val photoId: String,
  val status: DownloadStatus,
  val progressPercent: Int,
  val errorCode: DownloadErrorCode?,
  val localUri: String?,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long
)
