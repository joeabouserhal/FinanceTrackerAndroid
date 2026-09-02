package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Offline-first mutation queue. Payload is serialized JSON. */
@Entity(
  tableName = "outbox",
  indices = [Index("owner_id")],
)
data class OutboxEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(name = "op_id") val opId: String = UUID.randomUUID().toString(),
  @ColumnInfo(name = "owner_id") val ownerId: String,
  @ColumnInfo(name = "table_name") val tableName: String,
  val action: OutboxAction,
  @ColumnInfo(name = "payload_json") val payloadJson: String,
  @ColumnInfo(name = "created_at") val createdAt: String,
  val attempts: Int = 0,
)
