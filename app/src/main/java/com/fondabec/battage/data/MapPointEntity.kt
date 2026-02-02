package com.fondabec.battage.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "map_points",
    indices = [
        Index("updatedAtEpochMs"),
        Index("remoteId"),
        Index("ownerUid")
    ]
)
data class MapPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val addressLine: String,
    val latitude: Double,
    val longitude: Double,
    val avgDepthFt: Double,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,

    // Cloud
    val remoteId: String = "",
    val ownerUid: String = ""
)
