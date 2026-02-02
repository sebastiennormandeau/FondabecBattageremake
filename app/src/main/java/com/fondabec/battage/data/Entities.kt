package com.fondabec.battage.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [
        Index("remoteId"),
        Index("ownerUid")
    ]
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,

    // Chemin vers le plan PDF dans Firebase Storage
    val planPdfPath: String = "",

    // Localisation (auto)
    val street: String = "",
    val city: String,
    val province: String = "",
    val postalCode: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    val startDateEpochMs: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,

    // Cloud
    val remoteId: String = "",
    val ownerUid: String = ""
)

@Entity(
    tableName = "piles",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("remoteId"),
        Index("ownerUid")
    ]
)
data class PileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val pileNo: String = "",
    val gaugeIn: String = "",
    val depthFt: Double = 0.0,
    val implanted: Boolean = false,

    // Sync timestamps
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,

    // Cloud
    val remoteId: String = "",
    val ownerUid: String = ""
)

@Entity(
    tableName = "pile_hotspots",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PileEntity::class,
            parentColumns = ["id"],
            childColumns = ["pileId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("projectId"),
        Index(value = ["projectId", "pageIndex"]),
        Index("pileId"),
        Index("remoteId"),
        Index("ownerUid")
    ]
)
data class PileHotspotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val pageIndex: Int,
    val pileId: Long? = null,
    val xNorm: Float,
    val yNorm: Float,
    val createdAtEpochMs: Long,

    // Sync
    val updatedAtEpochMs: Long = 0L,

    // Cloud
    val remoteId: String = "",
    val ownerUid: String = "",

    // lien cloud pile (pour relier entre appareils)
    val pileRemoteId: String? = null
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("remoteId"),
        Index("ownerUid")
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val storagePath: String,
    val includeInReport: Boolean = true,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val remoteId: String,
    val ownerUid: String
)

// --- NOUVELLE ENTITÉ POUR LES DOCUMENTS TECHNIQUES ---
@Entity(
    tableName = "project_documents",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("remoteId"),
        Index("ownerUid")
    ]
)
data class ProjectDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,

    val title: String,
    val storagePath: String,    // Chemin Firebase Storage
    val downloadUrl: String,    // URL publique ou signée
    val mimeType: String = "application/pdf",

    val addedAtEpochMs: Long,
    val updatedAtEpochMs: Long,

    // Cloud
    val remoteId: String = "",
    val ownerUid: String = ""
)