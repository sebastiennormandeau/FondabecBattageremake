package com.fondabec.battage.data

import com.fondabec.battage.cloud.CloudIds
import com.fondabec.battage.cloud.CloudSyncHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProjectRepository(private val dao: ProjectDao) {

    fun observeProjectSummaries(): Flow<List<ProjectSummary>> = dao.observeProjectSummaries()
    fun observeProject(projectId: Long): Flow<ProjectEntity?> = dao.observeProject(projectId)
    fun observeMapProjects() = dao.observeMapProjects()

    suspend fun createProject(name: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val uid = CloudIds.currentUidOrEmpty()
        val remoteId = CloudIds.newRemoteId()

        val id = dao.insert(
            ProjectEntity(
                name = name.trim().ifBlank { "Nouveau projet" },
                city = "",
                startDateEpochMs = now,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                remoteId = remoteId,
                ownerUid = uid
            )
        )

        CloudSyncHolder.sync()?.pushProject(id)
        id
    }

    suspend fun updateProject(projectId: Long, name: String, city: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(projectId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(existing.ownerUid)) return@withContext

        val now = System.currentTimeMillis()
        dao.updateProject(projectId, name.trim(), city.trim(), now)
        CloudSyncHolder.sync()?.pushProject(projectId)
    }

    suspend fun setPlanPdfPath(projectId: Long, planPdfPath: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(projectId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(existing.ownerUid)) return@withContext

        val now = System.currentTimeMillis()
        dao.setPlanPdfPath(projectId, planPdfPath, now)
        CloudSyncHolder.sync()?.pushProject(projectId)
    }

    suspend fun updateProjectLocation(
        projectId: Long,
        street: String,
        city: String,
        province: String,
        postalCode: String,
        country: String,
        latitude: Double,
        longitude: Double
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getById(projectId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(existing.ownerUid)) return@withContext

        val now = System.currentTimeMillis()
        dao.updateLocation(
            projectId = projectId,
            street = street.trim(),
            city = city.trim(),
            province = province.trim(),
            postalCode = postalCode.trim(),
            country = country.trim(),
            latitude = latitude,
            longitude = longitude,
            updatedAt = now
        )
        CloudSyncHolder.sync()?.pushProject(projectId)
    }

    suspend fun deleteProject(projectId: Long) = withContext(Dispatchers.IO) {
        val existing = dao.getById(projectId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(existing.ownerUid)) return@withContext

        dao.deleteById(projectId)
        if (existing.remoteId.isNotBlank()) {
            CloudSyncHolder.sync()?.deleteProject(existing.remoteId)
        }
    }
}
