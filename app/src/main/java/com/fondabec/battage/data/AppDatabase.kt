package com.fondabec.battage.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// On passe à la version 11
private const val DB_VERSION = 11

private val MIGRATION_6_7_ADD_MAP_POINTS = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS map_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                addressLine TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                avgDepthFt REAL NOT NULL,
                createdAtEpochMs INTEGER NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_map_points_updatedAtEpochMs ON map_points(updatedAtEpochMs)")
    }
}

private val MIGRATION_7_8_ADD_CLOUD_FIELDS = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // projects
        db.execSQL("ALTER TABLE projects ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE projects ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_remoteId ON projects(remoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_ownerUid ON projects(ownerUid)")

        // piles
        db.execSQL("ALTER TABLE piles ADD COLUMN createdAtEpochMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE piles ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE piles ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE piles ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_piles_remoteId ON piles(remoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_piles_ownerUid ON piles(ownerUid)")

        // pile_hotspots
        db.execSQL("ALTER TABLE pile_hotspots ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pile_hotspots ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE pile_hotspots ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE pile_hotspots ADD COLUMN pileRemoteId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pile_hotspots_remoteId ON pile_hotspots(remoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pile_hotspots_ownerUid ON pile_hotspots(ownerUid)")

        // map_points
        db.execSQL("ALTER TABLE map_points ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE map_points ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_map_points_remoteId ON map_points(remoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_map_points_ownerUid ON map_points(ownerUid)")
    }
}

// Nouvelle migration pour ajouter la table des documents
private val MIGRATION_10_11_ADD_DOCUMENTS = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS project_documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                projectId INTEGER NOT NULL,
                title TEXT NOT NULL,
                storagePath TEXT NOT NULL,
                downloadUrl TEXT NOT NULL,
                mimeType TEXT NOT NULL DEFAULT 'application/pdf',
                addedAtEpochMs INTEGER NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL,
                remoteId TEXT NOT NULL DEFAULT '',
                ownerUid TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_project_documents_projectId ON project_documents(projectId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_project_documents_remoteId ON project_documents(remoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_project_documents_ownerUid ON project_documents(ownerUid)")
    }
}

@Database(
    entities = [
        ProjectEntity::class,
        PileEntity::class,
        PileHotspotEntity::class,
        MapPointEntity::class,
        PhotoEntity::class,
        ProjectDocumentEntity::class // Ajouté
    ],
    version = DB_VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun pileDao(): PileDao
    abstract fun pileHotspotDao(): PileHotspotDao
    abstract fun mapPointDao(): MapPointDao
    abstract fun photoDao(): PhotoDao
    abstract fun projectDocumentDao(): ProjectDocumentDao // Ajouté

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fondabec_battage.db"
                )
                    .addMigrations(
                        MIGRATION_6_7_ADD_MAP_POINTS,
                        MIGRATION_7_8_ADD_CLOUD_FIELDS,
                        MIGRATION_10_11_ADD_DOCUMENTS // Ajouté
                    )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}