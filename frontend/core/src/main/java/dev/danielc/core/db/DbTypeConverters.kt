package dev.danielc.core.db

import androidx.room.TypeConverter
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus

class DbTypeConverters {

  @TypeConverter
  fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

  @TypeConverter
  fun fromDownloadStatus(value: DownloadStatus): String = value.name

  @TypeConverter
  fun toDownloadErrorCode(value: String?): DownloadErrorCode? = value?.let { DownloadErrorCode.valueOf(it) }

  @TypeConverter
  fun fromDownloadErrorCode(value: DownloadErrorCode?): String? = value?.name
}
