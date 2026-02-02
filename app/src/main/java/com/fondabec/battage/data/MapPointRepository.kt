package com.fondabec.battage.data

import com.fondabec.battage.cloud.CloudIds
import com.fondabec.battage.cloud.CloudSyncHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MapPointRepository(private val dao: MapPointDao) {

    fun observeAll() = dao.observeAll()

    suspend fun addPoint(
        name: String,
        addressLine: String,
        latitude: Double,
        longitude: Double,
        avgDepthFt: Double
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val uid = CloudIds.currentUidOrEmpty()
        val remoteId = CloudIds.newRemoteId()

        val id = dao.insert(
            MapPointEntity(
                name = name.trim(),
                addressLine = addressLine.trim(),
                latitude = latitude,
                longitude = longitude,
                avgDepthFt = avgDepthFt,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                remoteId = remoteId,
                ownerUid = uid
            )
        )

        CloudSyncHolder.sync()?.pushMapPoint(id)
        id
    }

    suspend fun deletePoint(id: Long) = withContext(Dispatchers.IO) {
        val p = dao.getById(id) ?: return@withContext
        if (!CloudSyncHolder.canWrite(p.ownerUid)) return@withContext

        dao.deleteById(id)
        if (p.remoteId.isNotBlank()) {
            CloudSyncHolder.sync()?.deleteMapPoint(p.remoteId)
        }
    }
}
