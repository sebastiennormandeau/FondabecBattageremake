package com.fondabec.battage.data

import com.fondabec.battage.cloud.CloudIds
import com.fondabec.battage.cloud.CloudSyncHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PileHotspotRepository(
    private val dao: PileHotspotDao,
    private val projectDao: ProjectDao,
    private val pileDao: PileDao
) {

    fun observeHotspotsForPage(projectId: Long, pageIndex: Int): Flow<List<PileHotspotEntity>> =
        dao.observeForPage(projectId, pageIndex)

    suspend fun getHotspot(hotspotId: Long): PileHotspotEntity? = withContext(Dispatchers.IO) {
        dao.getById(hotspotId)
    }

    suspend fun addHotspot(
        projectId: Long,
        pageIndex: Int,
        xNorm: Float,
        yNorm: Float,
        pileId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId) ?: return@withContext 0L
        if (!CloudSyncHolder.canWrite(project.ownerUid)) return@withContext 0L

        val now = System.currentTimeMillis()
        val uid = CloudIds.currentUidOrEmpty()
        val remoteId = CloudIds.newRemoteId()

        val pileRemoteId = pileId?.let { pileDao.getById(it)?.remoteId }

        val id = dao.insert(
            PileHotspotEntity(
                projectId = projectId,
                pageIndex = pageIndex,
                pileId = pileId,
                xNorm = xNorm.coerceIn(0f, 1f),
                yNorm = yNorm.coerceIn(0f, 1f),
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                remoteId = remoteId,
                ownerUid = uid,
                pileRemoteId = pileRemoteId
            )
        )

        CloudSyncHolder.sync()?.pushHotspot(id)
        id
    }

    suspend fun setPileId(hotspotId: Long, pileId: Long) = withContext(Dispatchers.IO) {
        val h = dao.getById(hotspotId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(h.ownerUid)) return@withContext

        val now = System.currentTimeMillis()
        dao.setPileId(hotspotId, pileId, now)
        CloudSyncHolder.sync()?.pushHotspot(hotspotId)
    }

    suspend fun undoLastHotspot(projectId: Long, pageIndex: Int): Long? = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId) ?: return@withContext null
        if (!CloudSyncHolder.canWrite(project.ownerUid)) return@withContext null

        val last = dao.getLastForPage(projectId, pageIndex) ?: return@withContext null
        dao.deleteById(last.id)

        if (last.remoteId.isNotBlank()) {
            CloudSyncHolder.sync()?.deleteHotspot(projectId, last.remoteId)
        }

        last.pileId
    }

    suspend fun clearProject(projectId: Long) = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(project.ownerUid)) return@withContext

        dao.deleteAllForProject(projectId)
        // (cleanup cloud en masse = jalon ultérieur)
    }
}
