package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainote.core.database.entity.AiProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiProviderDao {

    @Query("SELECT * FROM ai_providers ORDER BY is_default DESC, created_at ASC")
    fun observeAll(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers WHERE enabled = 1")
    suspend fun getEnabled(): List<AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE is_default = 1 AND enabled = 1 LIMIT 1")
    suspend fun getDefault(): AiProviderEntity?

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun getById(id: Long): AiProviderEntity?

    @Query("SELECT * FROM ai_providers WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): AiProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: AiProviderEntity): Long

    @Update
    suspend fun update(provider: AiProviderEntity)

    @Delete
    suspend fun delete(provider: AiProviderEntity)

    @Query("UPDATE ai_providers SET is_default = 0")
    suspend fun clearDefault()

    @Query("UPDATE ai_providers SET is_default = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("SELECT COUNT(*) FROM ai_providers")
    suspend fun count(): Int
}
