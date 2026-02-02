package com.fondabec.battage.data

import com.fondabec.battage.cloud.CloudSyncHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PhotoRepository(private val dao: PhotoDao) {

    fun observePhotosForProject(projectId: Long): Flow<List<PhotoEntity>> = dao.observeForProject(projectId)

    suspend fun addPhoto(projectId: Long, photoUri: String) = withContext(Dispatchers.IO) {
        CloudSyncHolder.sync()?.uploadPhotoAndSync(projectId, photoUri)
    }

    suspend fun updatePhoto(photoId: Long, includeInReport: Boolean) = withContext(Dispatchers.IO) {
        dao.setIncludeInReport(photoId, includeInReport, System.currentTimeMillis())
        CloudSyncHolder.sync()?.pushPhoto(photoId)
    }

    suspend fun deletePhoto(photoId: Long) = withContext(Dispatchers.IO) {
        CloudSyncHolder.sync()?.deletePhoto(photoId)
    }
}
