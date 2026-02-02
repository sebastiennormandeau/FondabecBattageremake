package com.fondabec.battage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PileDao {

    @Query("SELECT * FROM piles WHERE projectId = :projectId ORDER BY id ASC")
    fun observePilesForProject(projectId: Long): Flow<List<PileEntity>>

    @Query("SELECT * FROM piles WHERE id = :pileId")
    fun observePile(pileId: Long): Flow<PileEntity?>

    @Query("SELECT * FROM piles WHERE id = :pileId LIMIT 1")
    suspend fun getById(pileId: Long): PileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pile: PileEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(piles: List<PileEntity>): List<Long>

    @Query("DELETE FROM piles WHERE id = :pileId")
    suspend fun deleteById(pileId: Long)

    @Query(
        """
        UPDATE piles SET
            pileNo = :pileNo,
            gaugeIn = :gaugeIn,
            depthFt = :depthFt,
            implanted = :implanted,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :pileId
        """
    )
    suspend fun updatePile(
        pileId: Long,
        pileNo: String,
        gaugeIn: String,
        depthFt: Double,
        implanted: Boolean,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        SELECT gaugeIn, COUNT(*) AS count
        FROM piles
        WHERE projectId = :projectId
        GROUP BY gaugeIn
        """
    )
    suspend fun getCountsByGauge(projectId: Long): List<GaugeCount>

    @Query(
        """
        SELECT COUNT(*) 
        FROM piles
        WHERE projectId = :projectId AND gaugeIn = :gaugeIn AND id != :excludePileId
        """
    )
    suspend fun getCountForGaugeExcluding(projectId: Long, gaugeIn: String, excludePileId: Long): Int

    // ---- Cloud helpers

    @Query("SELECT id FROM piles WHERE projectId = :projectId AND remoteId = :remoteId LIMIT 1")
    suspend fun getLocalIdByRemoteId(projectId: Long, remoteId: String): Long?

    @Query("DELETE FROM piles WHERE projectId = :projectId AND remoteId = :remoteId")
    suspend fun deleteByRemoteId(projectId: Long, remoteId: String)

    @Query(
        """
        UPDATE piles SET
            pileNo = :pileNo,
            gaugeIn = :gaugeIn,
            depthFt = :depthFt,
            implanted = :implanted,
            createdAtEpochMs = :createdAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs,
            remoteId = :remoteId,
            ownerUid = :ownerUid
        WHERE id = :pileId
        """
    )
    suspend fun updateFromRemote(
        pileId: Long,
        pileNo: String,
        gaugeIn: String,
        depthFt: Double,
        implanted: Boolean,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        remoteId: String,
        ownerUid: String
    )
}
