package dev.danielc.core.data

import dev.danielc.core.db.entity.HotspotHistoryEntity
import kotlinx.coroutines.flow.Flow

interface HotspotHistoryRepository {
  fun observeHistory(): Flow<List<HotspotHistoryEntity>>
  suspend fun markConnected(ssid: String, atEpochMillis: Long)
}
