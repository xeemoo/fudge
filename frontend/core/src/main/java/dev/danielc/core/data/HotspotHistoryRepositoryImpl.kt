package dev.danielc.core.data

import dev.danielc.core.db.dao.HotspotHistoryDao
import dev.danielc.core.db.entity.HotspotHistoryEntity
import kotlinx.coroutines.flow.Flow

class HotspotHistoryRepositoryImpl(
  private val dao: HotspotHistoryDao
) : HotspotHistoryRepository {

  override fun observeHistory(): Flow<List<HotspotHistoryEntity>> = dao.observeAll()

  override suspend fun markConnected(ssid: String, atEpochMillis: Long) {
    val existing = dao.getBySsid(ssid)
    val nextConnectCount = (existing?.connectCount ?: 0) + 1
    dao.upsert(
      HotspotHistoryEntity(
        ssid = ssid,
        lastConnectedAtEpochMillis = atEpochMillis,
        connectCount = nextConnectCount
      )
    )
  }
}
