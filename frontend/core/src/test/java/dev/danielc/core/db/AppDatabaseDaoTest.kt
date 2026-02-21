package dev.danielc.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import dev.danielc.core.db.entity.HotspotHistoryEntity
import dev.danielc.core.db.model.DownloadStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppDatabaseDaoTest {

  private lateinit var db: AppDatabase

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java
    ).allowMainThreadQueries().build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun schemaVersion_isV1() {
    assertEquals(1, db.openHelper.writableDatabase.version)
  }

  @Test
  fun hotspotHistoryDao_observeAll_emitsUpdates() = runTest {
    val dao = db.hotspotHistoryDao()
    val initial = dao.observeAll().first()

    dao.upsert(
      HotspotHistoryEntity(
        ssid = "FUJIFILM-X",
        lastConnectedAtEpochMillis = 100L,
        connectCount = 1
      )
    )
    advanceUntilIdle()
    val updated = dao.observeAll().first()

    assertTrue(initial.isEmpty())
    assertEquals("FUJIFILM-X", updated.single().ssid)
  }

  @Test
  fun downloadTaskDao_queriesAndStateTransitions_workAsExpected() = runTest {
    val dao = db.downloadTaskDao()
    val task = DownloadTaskEntity(
      taskId = "task-1",
      queueId = "queue-A",
      photoId = "photo-1",
      status = DownloadStatus.QUEUED,
      progressPercent = 0,
      errorCode = null,
      localUri = null,
      createdAtEpochMillis = 1L,
      updatedAtEpochMillis = 1L
    )

    val initialActive = dao.observeActive().first()

    dao.insert(task)
    val updatedActive = dao.observeActive().first()

    assertTrue(initialActive.isEmpty())
    assertEquals("task-1", updatedActive.single().taskId)

    assertEquals("task-1", dao.observeByPhotoId("photo-1").first()?.taskId)
    assertEquals("task-1", dao.findExistingActiveTask("photo-1")?.taskId)
    assertEquals("task-1", dao.nextQueuedTask("queue-A")?.taskId)

    dao.updateStatus(
      taskId = "task-1",
      status = DownloadStatus.SUCCESS,
      progressPercent = 100,
      errorCode = null,
      localUri = "content://media/external/images/media/1",
      updatedAtEpochMillis = 2L
    )

    assertNull(dao.findExistingActiveTask("photo-1"))
    assertNull(dao.nextQueuedTask("queue-A"))
    assertEquals(DownloadStatus.SUCCESS, dao.observeByPhotoId("photo-1").first()?.status)
  }

  @Test
  fun downloadedPhotoDao_getAndObserve_workAsExpected() = runTest {
    val dao = db.downloadedPhotoDao()
    val entity = DownloadedPhotoEntity(
      photoId = "photo-9",
      localUri = "content://media/external/images/media/9",
      displayName = "DSCF0009.JPG",
      relativePath = "Pictures/FujifilmCam/",
      mimeType = "image/jpeg",
      downloadedAtEpochMillis = 123L
    )

    val initial = dao.observe("photo-9").first()

    dao.upsert(entity)
    val updated = dao.observe("photo-9").first()

    assertNull(initial)
    assertEquals("photo-9", updated?.photoId)
    assertEquals("photo-9", dao.get("photo-9")?.photoId)
  }
}
