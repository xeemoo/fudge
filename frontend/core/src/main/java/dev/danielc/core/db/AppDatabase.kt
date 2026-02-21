package dev.danielc.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.dao.HotspotHistoryDao
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import dev.danielc.core.db.entity.HotspotHistoryEntity

@Database(
  entities = [
    HotspotHistoryEntity::class,
    DownloadTaskEntity::class,
    DownloadedPhotoEntity::class
  ],
  version = 1,
  exportSchema = true
)
@TypeConverters(DbTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

  abstract fun hotspotHistoryDao(): HotspotHistoryDao

  abstract fun downloadTaskDao(): DownloadTaskDao

  abstract fun downloadedPhotoDao(): DownloadedPhotoDao
}
