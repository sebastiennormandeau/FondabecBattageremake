package com.fondabec.battage.data

import com.fondabec.battage.cloud.CloudIds
import com.fondabec.battage.cloud.CloudSyncHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PileRepository(
    private val dao: PileDao,
    private val projectDao: ProjectDao
) {

    fun observePilesForProject(projectId: Long): Flow<List<PileEntity>> =
        dao.observePilesForProject(projectId)

    fun observePile(pileId: Long): Flow<PileEntity?> =
        dao.observePile(pileId)

    private fun nextNoForGauge(gaugeIn: String, currentCount: Int): String {
        return "${gaugeIn}-${currentCount + 1}"
    }

    suspend fun addPile(projectId: Long): Long = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId) ?: return@withContext 0L
        if (!CloudSyncHolder.canWrite(project.ownerUid)) return@withContext 0L

        val now = System.currentTimeMillis()
        val uid = CloudIds.currentUidOrEmpty()
        val remoteId = CloudIds.newRemoteId()

        val id = dao.insert(
            PileEntity(
                projectId = projectId,
                pileNo = "",
                gaugeIn = "",
                depthFt = 0.0,
                implanted = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                remoteId = remoteId,
                ownerUid = uid
            )
        )

        CloudSyncHolder.sync()?.pushPile(id)
        id
    }

    suspend fun addPiles(projectId: Long, count: Int, gaugeIn: String): Int =
        withContext(Dispatchers.IO) {
            val project = projectDao.getById(projectId) ?: return@withContext 0
            if (!CloudSyncHolder.canWrite(project.ownerUid)) return@withContext 0

            val safeCount = count.coerceIn(0, 500)
            if (safeCount == 0) return@withContext 0

            val now = System.currentTimeMillis()
            val uid = CloudIds.currentUidOrEmpty()

            val g = gaugeIn.trim()
            if (g.isBlank()) {
                val list = List(safeCount) {
                    PileEntity(
                        projectId = projectId,
                        pileNo = "",
                        gaugeIn = "",
                        depthFt = 0.0,
                        implanted = false,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        remoteId = CloudIds.newRemoteId(),
                        ownerUid = uid
                    )
                }
                val ids = dao.insertAll(list)
                ids.forEach { CloudSyncHolder.sync()?.pushPile(it) }
                return@withContext ids.size
            }

            val counts = dao.getCountsByGauge(projectId).associate { it.gaugeIn to it.count }.toMutableMap()
            var current = counts[g] ?: 0

            val list = List(safeCount) {
                val pileNo = nextNoForGauge(g, current)
                current += 1
                PileEntity(
                    projectId = projectId,
                    pileNo = pileNo,
                    gaugeIn = g,
                    depthFt = 0.0,
                    implanted = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    remoteId = CloudIds.newRemoteId(),
                    ownerUid = uid
                )
            }

            val ids = dao.insertAll(list)
            ids.forEach { CloudSyncHolder.sync()?.pushPile(it) }
            ids.size
        }

    suspend fun addPilesByGauge(
        projectId: Long,
        qtyByGauge: Map<String, Int>
    ): Int = withContext(Dispatchers.IO) {
        val project = projectDao.getById(projectId) ?: return@withContext 0
        if (!CloudSyncHolder.canWrite(project.ownerUid)) return@withContext 0

        val gauges = listOf("4 1/2", "5 1/2", "7", "9 5/8")
        val pilesToInsert = mutableListOf<PileEntity>()

        val now = System.currentTimeMillis()
        val uid = CloudIds.currentUidOrEmpty()

        val counts = dao.getCountsByGauge(projectId).associate { it.gaugeIn to it.count }.toMutableMap()

        for (g in gauges) {
            val nToAdd = (qtyByGauge[g] ?: 0).coerceIn(0, 500)
            var current = counts[g] ?: 0

            repeat(nToAdd) {
                val pileNo = nextNoForGauge(g, current)
                current += 1
                pilesToInsert.add(
                    PileEntity(
                        projectId = projectId,
                        pileNo = pileNo,
                        gaugeIn = g,
                        depthFt = 0.0,
                        implanted = false,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        remoteId = CloudIds.newRemoteId(),
                        ownerUid = uid
                    )
                )
            }

            counts[g] = current
        }

        if (pilesToInsert.isEmpty()) return@withContext 0
        val ids = dao.insertAll(pilesToInsert)
        ids.forEach { CloudSyncHolder.sync()?.pushPile(it) }
        ids.size
    }

    suspend fun updatePile(
        projectId: Long,
        pileId: Long,
        pileNo: String,
        gaugeIn: String,
        depthFt: Double,
        implanted: Boolean
    ) = withContext(Dispatchers.IO) {
        val pile = dao.getById(pileId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(pile.ownerUid)) return@withContext

        val g = gaugeIn.trim()
        val no = pileNo.trim()

        val finalNo = when {
            no.isBlank() && g.isNotBlank() -> {
                val count = dao.getCountForGaugeExcluding(projectId, g, pileId)
                nextNoForGauge(g, count)
            }
            no.matches(Regex("^\\d+$")) && g.isNotBlank() -> {
                "${g}-${no}"
            }
            else -> no
        }

        val now = System.currentTimeMillis()
        dao.updatePile(
            pileId = pileId,
            pileNo = finalNo,
            gaugeIn = g,
            depthFt = depthFt,
            implanted = implanted,
            updatedAtEpochMs = now
        )

        CloudSyncHolder.sync()?.pushPile(pileId)
    }

    suspend fun deletePile(pileId: Long) = withContext(Dispatchers.IO) {
        val pile = dao.getById(pileId) ?: return@withContext
        if (!CloudSyncHolder.canWrite(pile.ownerUid)) return@withContext

        dao.deleteById(pileId)
        if (pile.remoteId.isNotBlank()) {
            CloudSyncHolder.sync()?.deletePile(pile.projectId, pile.remoteId)
        }
    }
}
