package dev.danielc.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_photos")
data class DownloadedPhotoEntity(
  @PrimaryKey val photoId: String,
  val localUri: String,
  val displayName: String?,
  val relativePath: String,
  val mimeType: String?,
  val downloadedAtEpochMillis: Long
)
