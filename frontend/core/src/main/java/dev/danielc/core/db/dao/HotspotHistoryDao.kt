package dev.danielc.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.danielc.core.db.entity.HotspotHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HotspotHistoryDao {

  @Query("SELECT * FROM hotspot_history ORDER BY lastConnectedAtEpochMillis DESC")
  fun observeAll(): Flow<List<HotspotHistoryEntity>>

  @Query("SELECT * FROM hotspot_history WHERE ssid = :ssid LIMIT 1")
  suspend fun getBySsid(ssid: String): HotspotHistoryEntity?

  @Upsert
  suspend fun upsert(entity: HotspotHistoryEntity)
}
