package com.fondabec.battage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MapPointDao {

    @Query("SELECT * FROM map_points ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<MapPointEntity>>

    @Query("SELECT * FROM map_points WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MapPointEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(p: MapPointEntity): Long

    @Query("DELETE FROM map_points WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- Cloud helpers

    @Query("SELECT id FROM map_points WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getLocalIdByRemoteId(remoteId: String): Long?

    @Query("DELETE FROM map_points WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: String)

    @Query(
        """
        UPDATE map_points SET
            name = :name,
            addressLine = :addressLine,
            latitude = :latitude,
            longitude = :longitude,
            avgDepthFt = :avgDepthFt,
            createdAtEpochMs = :createdAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs,
            remoteId = :remoteId,
            ownerUid = :ownerUid
        WHERE id = :id
        """
    )
    suspend fun updateFromRemote(
        id: Long,
        name: String,
        addressLine: String,
        latitude: Double,
        longitude: Double,
        avgDepthFt: Double,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        remoteId: String,
        ownerUid: String
    )
}
