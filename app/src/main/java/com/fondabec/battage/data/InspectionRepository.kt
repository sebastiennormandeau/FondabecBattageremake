package com.fondabec.battage.data

import com.fondabec.battage.cloud.CloudIds
import com.fondabec.battage.cloud.CloudSyncHolder
import com.fondabec.battage.ui.InspectionPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InspectionRepository(
    private val inspectionDao: InspectionDao,
    private val projectDao: ProjectDao
) {

    fun observeReportsForProject(projectId: Long) = inspectionDao.observeFullReportsForProject(projectId)

    suspend fun createInspection(
        projectId: Long,
        equipmentType: String,
        operatorName: String,
        machineHours: Int,
        notes: String,
        points: List<InspectionPoint> // On accepte le type de l'UI
    ) = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(project.ownerUid)) return@withContext

        val now = System.currentTimeMillis()
        val userUid = CloudIds.currentUidOrEmpty()

        val report = InspectionReportEntity(
            projectId = projectId,
            equipmentType = equipmentType,
            dateEpochMs = now,
            operatorName = operatorName,
            machineHours = machineHours,
            notes = notes,
            remoteId = CloudIds.newRemoteId(),
            ownerUid = userUid,
            updatedAtEpochMs = now
        )

        val reportId = inspectionDao.insertReport(report)

        // C'est le Repository qui crée les entités
        val pointEntities = points.map { 
            InspectionPointEntity(
                reportId = reportId,
                label = it.label,
                status = it.status.name
            )
        }
        inspectionDao.insertPoints(pointEntities)

        CloudSyncHolder.pushInspectionReport(reportId)
    }

    suspend fun deleteInspection(reportId: Long) = withContext(Dispatchers.IO) {
        val report = inspectionDao.getReportById(reportId) ?: return@withContext
        val project = projectDao.getById(report.projectId) ?: return@withContext

        if (!CloudSyncHolder.canWrite(report.ownerUid)) return@withContext

        if (report.remoteId.isNotBlank() && project.remoteId.isNotBlank()) {
            CloudSyncHolder.sync()?.deleteInspection(project.remoteId, report.remoteId)
        }

        inspectionDao.deleteById(reportId)
    }
}