package dev.danielc.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedPhotoDao {

  @Query("SELECT * FROM downloaded_photos WHERE photoId = :photoId LIMIT 1")
  fun observe(photoId: String): Flow<DownloadedPhotoEntity?>

  @Query("SELECT * FROM downloaded_photos WHERE photoId = :photoId LIMIT 1")
  suspend fun get(photoId: String): DownloadedPhotoEntity?

  @Upsert
  suspend fun upsert(entity: DownloadedPhotoEntity)
}
