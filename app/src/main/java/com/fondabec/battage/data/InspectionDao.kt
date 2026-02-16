package com.fondabec.battage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {

    @Insert
    suspend fun insertReport(report: InspectionReportEntity): Long

    @Insert
    suspend fun insertPoints(points: List<InspectionPointEntity>)

    data class FullInspectionReport(
        @androidx.room.Embedded
        val report: InspectionReportEntity,
        @androidx.room.Relation(
            parentColumn = "id",
            entityColumn = "reportId"
        )
        val points: List<InspectionPointEntity>
    )

    @Transaction
    @Query("SELECT * FROM inspection_reports WHERE projectId = :projectId ORDER BY dateEpochMs DESC")
    fun observeFullReportsForProject(projectId: Long): Flow<List<FullInspectionReport>>

    @Transaction
    @Query("SELECT * FROM inspection_reports WHERE id = :reportId")
    suspend fun getFullReportById(reportId: Long): FullInspectionReport?

    @Query("SELECT * FROM inspection_reports WHERE id = :reportId")
    suspend fun getReportById(reportId: Long): InspectionReportEntity?

    @Query("DELETE FROM inspection_reports WHERE id = :reportId")
    suspend fun deleteById(reportId: Long)

    // --- Cloud Sync ---

    @Query("SELECT id FROM inspection_reports WHERE projectId = :projectId AND remoteId = :remoteId LIMIT 1")
    suspend fun getLocalIdByRemoteId(projectId: Long, remoteId: String): Long?

    @Query("DELETE FROM inspection_reports WHERE projectId = :projectId AND remoteId = :remoteId")
    suspend fun deleteByRemoteId(projectId: Long, remoteId: String)

    @Query("UPDATE inspection_reports SET equipmentType = :equipmentType, dateEpochMs = :dateEpochMs, operatorName = :operatorName, machineHours = :machineHours, notes = :notes, remoteId = :remoteId, ownerUid = :ownerUid, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :reportId")
    suspend fun updateFromRemote(
        reportId: Long,
        equipmentType: String,
        dateEpochMs: Long,
        operatorName: String,
        machineHours: Int,
        notes: String,
        remoteId: String,
        ownerUid: String,
        updatedAtEpochMs: Long
    )
}
