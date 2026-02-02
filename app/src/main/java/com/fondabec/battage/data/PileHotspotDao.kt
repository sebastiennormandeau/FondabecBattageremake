package com.fondabec.battage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PileHotspotDao {

    @Query(
        """
        SELECT * FROM pile_hotspots
        WHERE projectId = :projectId AND pageIndex = :pageIndex
        ORDER BY createdAtEpochMs ASC
        """
    )
    fun observeForPage(projectId: Long, pageIndex: Int): Flow<List<PileHotspotEntity>>

    @Query("SELECT * FROM pile_hotspots WHERE id = :hotspotId LIMIT 1")
    suspend fun getById(hotspotId: Long): PileHotspotEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(h: PileHotspotEntity): Long

    @Query("DELETE FROM pile_hotspots WHERE id = :hotspotId")
    suspend fun deleteById(hotspotId: Long)

    @Query("DELETE FROM pile_hotspots WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: Long)

    @Query(
        """
        UPDATE pile_hotspots SET
            pileId = :pileId,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :hotspotId
        """
    )
    suspend fun setPileId(hotspotId: Long, pileId: Long, updatedAtEpochMs: Long)

    @Query(
        """
        SELECT * FROM pile_hotspots
        WHERE projectId = :projectId AND pageIndex = :pageIndex
        ORDER BY createdAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun getLastForPage(projectId: Long, pageIndex: Int): PileHotspotEntity?

    // ---- Cloud helpers

    @Query("SELECT id FROM pile_hotspots WHERE projectId = :projectId AND remoteId = :remoteId LIMIT 1")
    suspend fun getLocalIdByRemoteId(projectId: Long, remoteId: String): Long?

    @Query("DELETE FROM pile_hotspots WHERE projectId = :projectId AND remoteId = :remoteId")
    suspend fun deleteByRemoteId(projectId: Long, remoteId: String)

    @Query(
        """
        UPDATE pile_hotspots SET
            pageIndex = :pageIndex,
            pileId = :pileId,
            xNorm = :xNorm,
            yNorm = :yNorm,
            createdAtEpochMs = :createdAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs,
            remoteId = :remoteId,
            ownerUid = :ownerUid,
            pileRemoteId = :pileRemoteId
        WHERE id = :hotspotId
        """
    )
    suspend fun updateFromRemote(
        hotspotId: Long,
        pageIndex: Int,
        pileId: Long?,
        xNorm: Float,
        yNorm: Float,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        remoteId: String,
        ownerUid: String,
        pileRemoteId: String?
    )
}
