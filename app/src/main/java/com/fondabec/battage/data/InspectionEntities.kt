package com.fondabec.battage.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspection_reports",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("remoteId")]
)
data class InspectionReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val equipmentType: String, // "Batteuse", "Excavatrice", etc.
    val dateEpochMs: Long,
    val operatorName: String = "",
    val machineHours: Int = 0,
    val notes: String = "",

    // Cloud sync
    val remoteId: String = "",
    val ownerUid: String = "",
    val updatedAtEpochMs: Long = 0L
)

@Entity(
    tableName = "inspection_points",
    foreignKeys = [
        ForeignKey(
            entity = InspectionReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reportId")]
)
data class InspectionPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val reportId: Long,
    val label: String, // "Niveau d'huile moteur"
    val status: String, // "CONFORME", "NON_CONFORME", "ND"
)
