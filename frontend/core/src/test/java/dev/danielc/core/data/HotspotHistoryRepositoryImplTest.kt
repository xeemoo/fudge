package dev.danielc.core.data

import dev.danielc.core.db.dao.HotspotHistoryDao
import dev.danielc.core.db.entity.HotspotHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotHistoryRepositoryImplTest {

  @Test
  fun markConnected_newSsid_insertsWithCountOne() = runTest {
    val dao = FakeHotspotHistoryDao()
    val repository = HotspotHistoryRepositoryImpl(dao)

    repository.markConnected(ssid = "FUJIFILM-X", atEpochMillis = 1000L)
    val history = repository.observeHistory().first()

    assertEquals(1, history.size)
    assertEquals("FUJIFILM-X", history.first().ssid)
    assertEquals(1, history.first().connectCount)
    assertEquals(1000L, history.first().lastConnectedAtEpochMillis)
  }

  @Test
  fun markConnected_existingSsid_incrementsCountAndUpdatesLastConnectedAt() = runTest {
    val dao = FakeHotspotHistoryDao(
      initial = listOf(
        HotspotHistoryEntity(
          ssid = "FUJIFILM-X",
          lastConnectedAtEpochMillis = 1000L,
          connectCount = 2
        )
      )
    )
    val repository = HotspotHistoryRepositoryImpl(dao)

    repository.markConnected(ssid = "FUJIFILM-X", atEpochMillis = 2500L)
    val updated = repository.observeHistory().first().first()

    assertEquals(3, updated.connectCount)
    assertEquals(2500L, updated.lastConnectedAtEpochMillis)
  }

  private class FakeHotspotHistoryDao(
    initial: List<HotspotHistoryEntity> = emptyList()
  ) : HotspotHistoryDao {
    private val items = MutableStateFlow(initial.sortedByDescending { it.lastConnectedAtEpochMillis })

    override fun observeAll(): Flow<List<HotspotHistoryEntity>> = items

    override suspend fun getBySsid(ssid: String): HotspotHistoryEntity? {
      return items.value.firstOrNull { it.ssid == ssid }
    }

    override suspend fun upsert(entity: HotspotHistoryEntity) {
      val mutable = items.value.toMutableList()
      val index = mutable.indexOfFirst { it.ssid == entity.ssid }
      if (index >= 0) {
        mutable[index] = entity
      } else {
        mutable.add(entity)
      }
      items.value = mutable.sortedByDescending { it.lastConnectedAtEpochMillis }
    }
  }
}
