package com.fondabec.battage.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    // ✅ DTO ProjectSummary = EXACTEMENT les colonnes ci-dessous
    @Query(
        """
        SELECT 
            p.id AS id,
            p.name AS name,
            p.city AS city,
            p.startDateEpochMs AS startDateEpochMs,
            COALESCE(AVG(pi.depthFt), 0.0) AS avgDepthFt,
            p.createdAtEpochMs AS createdAtEpochMs,
            p.updatedAtEpochMs AS updatedAtEpochMs
        FROM projects p
        LEFT JOIN piles pi ON pi.projectId = p.id
        GROUP BY 
            p.id, p.name, p.city, p.startDateEpochMs, p.createdAtEpochMs, p.updatedAtEpochMs
        ORDER BY p.updatedAtEpochMs DESC
        """
    )
    fun observeProjectSummaries(): Flow<List<ProjectSummary>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProject(projectId: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getById(projectId: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: Long)

    @Query(
        """
        UPDATE projects SET 
            name = :name, 
            city = :city, 
            updatedAtEpochMs = :updatedAt 
        WHERE id = :projectId
        """
    )
    suspend fun updateProject(projectId: Long, name: String, city: String, updatedAt: Long)

    @Query(
        """
        UPDATE projects SET 
            planPdfPath = :planPdfPath, 
            updatedAtEpochMs = :updatedAt 
        WHERE id = :projectId
        """
    )
    suspend fun setPlanPdfPath(projectId: Long, planPdfPath: String, updatedAt: Long)

    @Query(
        """
        UPDATE projects SET
            street = :street,
            city = :city,
            province = :province,
            postalCode = :postalCode,
            country = :country,
            latitude = :latitude,
            longitude = :longitude,
            updatedAtEpochMs = :updatedAt
        WHERE id = :projectId
        """
    )
    suspend fun updateLocation(
        projectId: Long,
        street: String,
        city: String,
        province: String,
        postalCode: String,
        country: String,
        latitude: Double,
        longitude: Double,
        updatedAt: Long
    )

    // ---- Cloud helpers

    @Query("SELECT id FROM projects WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getLocalIdByRemoteId(remoteId: String): Long?

    @Query("UPDATE projects SET remoteId = :remoteId, ownerUid = :ownerUid WHERE id = :projectId")
    suspend fun updateCloudIds(projectId: Long, remoteId: String, ownerUid: String)

    @Query(
        """
        UPDATE projects SET
            name = :name,
            planPdfPath = :planPdfPath,
            street = :street,
            city = :city,
            province = :province,
            postalCode = :postalCode,
            country = :country,
            latitude = :latitude,
            longitude = :longitude,
            startDateEpochMs = :startDateEpochMs,
            createdAtEpochMs = :createdAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs,
            remoteId = :remoteId,
            ownerUid = :ownerUid
        WHERE id = :projectId
        """
    )
    suspend fun updateFromRemote(
        projectId: Long,
        name: String,
        planPdfPath: String,
        street: String,
        city: String,
        province: String,
        postalCode: String,
        country: String,
        latitude: Double,
        longitude: Double,
        startDateEpochMs: Long,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        remoteId: String,
        ownerUid: String
    )

    // Map projects
    @Query(
        """
        SELECT 
            p.id AS id,
            p.name AS name,
            p.latitude AS latitude,
            p.longitude AS longitude,
            COALESCE(AVG(pi.depthFt), 0.0) AS avgDepthFt
        FROM projects p
        LEFT JOIN piles pi ON pi.projectId = p.id
        WHERE p.latitude != 0.0 AND p.longitude != 0.0
        GROUP BY p.id, p.name, p.latitude, p.longitude
        """
    )
    fun observeMapProjects(): Flow<List<ProjectMapItem>>
}
