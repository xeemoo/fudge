package dev.danielc.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hotspot_history")
data class HotspotHistoryEntity(
  @PrimaryKey val ssid: String,
  val lastConnectedAtEpochMillis: Long,
  val connectCount: Int
)
