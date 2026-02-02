package com.fondabec.battage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    fun observeForProject(projectId: Long): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity): Long

    @Query("UPDATE photos SET includeInReport = :includeInReport, updatedAtEpochMs = :updatedAt WHERE id = :photoId")
    suspend fun setIncludeInReport(photoId: Long, includeInReport: Boolean, updatedAt: Long)

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deleteById(photoId: Long)

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getById(photoId: Long): PhotoEntity?
    
    // Cloud
    @Query("SELECT id FROM photos WHERE remoteId = :remoteId AND projectId = :projectId LIMIT 1")
    suspend fun getLocalIdByRemoteId(projectId: Long, remoteId: String): Long?
    
    @Query("DELETE FROM photos WHERE remoteId = :remoteId AND projectId = :projectId")
    suspend fun deleteByRemoteId(projectId: Long, remoteId: String)
}
