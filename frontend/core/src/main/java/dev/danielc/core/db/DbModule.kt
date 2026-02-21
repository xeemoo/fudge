package dev.danielc.core.db

import android.content.Context
import androidx.room.Room
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.dao.HotspotHistoryDao
import org.koin.dsl.module

val dbModule = module {
  single<AppDatabase> {
    Room.databaseBuilder(
      get<Context>(),
      AppDatabase::class.java,
      DB_NAME
    ).build()
  }
  single<HotspotHistoryDao> { get<AppDatabase>().hotspotHistoryDao() }
  single<DownloadTaskDao> { get<AppDatabase>().downloadTaskDao() }
  single<DownloadedPhotoDao> { get<AppDatabase>().downloadedPhotoDao() }
}

private const val DB_NAME = "fujifilm_cam.db"
