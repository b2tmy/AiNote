package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainote.core.database.entity.NotebookEntity
import com.ainote.core.database.entity.NotebookType
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {

    @Query("SELECT * FROM notebooks ORDER BY sort_order ASC, created_at ASC")
    fun observeAll(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getById(id: Long): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE type = :type LIMIT 1")
    suspend fun getByType(type: NotebookType): NotebookEntity?

    @Query("SELECT * FROM notebooks ORDER BY sort_order ASC, created_at ASC")
    suspend fun getAllOnce(): List<NotebookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notebook: NotebookEntity): Long

    @Update
    suspend fun update(notebook: NotebookEntity)

    @Delete
    suspend fun delete(notebook: NotebookEntity)

    @Query("SELECT COUNT(*) FROM notebooks")
    suspend fun count(): Int
}
