package com.fondabec.battage.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDocumentDao {

    @Query("SELECT * FROM project_documents WHERE projectId = :projectId ORDER BY addedAtEpochMs DESC")
    fun observeDocumentsByProject(projectId: Long): Flow<List<ProjectDocumentEntity>>

    @Query("SELECT * FROM project_documents WHERE id = :docId")
    suspend fun getById(docId: Long): ProjectDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: ProjectDocumentEntity): Long

    @Delete
    suspend fun delete(document: ProjectDocumentEntity)
}