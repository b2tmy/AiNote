package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainote.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query(
        """
        SELECT * FROM notes
        WHERE notebook_id = :notebookId AND deleted_at IS NULL AND archived = 0
        ORDER BY pinned DESC, updated_at DESC
        """
    )
    fun observeByNotebook(notebookId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeRecent(): Flow<List<NoteEntity>>

    /** 导出用：所有未删除的笔记 */
    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY notebook_id ASC, updated_at DESC")
    suspend fun getAllActive(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE deleted_at IS NULL AND (title LIKE '%' || :q || '%' OR content_text LIKE '%' || :q || '%')
        ORDER BY updated_at DESC
        LIMIT :limit
        """
    )
    suspend fun search(q: String, limit: Int = 100): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET deleted_at = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE created_at BETWEEN :from AND :to AND deleted_at IS NULL")
    suspend fun countCreatedInRange(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM notes WHERE updated_at BETWEEN :from AND :to AND deleted_at IS NULL")
    suspend fun countUpdatedInRange(from: Long, to: Long): Int
}
