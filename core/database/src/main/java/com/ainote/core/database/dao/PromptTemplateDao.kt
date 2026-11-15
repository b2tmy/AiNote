package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ainote.core.database.entity.PromptTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptTemplateDao {

    @Query("SELECT * FROM prompt_templates ORDER BY template_key ASC")
    fun observeAll(): Flow<List<PromptTemplateEntity>>

    @Query("SELECT * FROM prompt_templates WHERE template_key = :key LIMIT 1")
    suspend fun getByKey(key: String): PromptTemplateEntity?

    @Query("SELECT * FROM prompt_templates WHERE enabled = 1")
    suspend fun getEnabled(): List<PromptTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: PromptTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(templates: List<PromptTemplateEntity>)

    @Query("DELETE FROM prompt_templates WHERE template_key = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT COUNT(*) FROM prompt_templates")
    suspend fun count(): Int
}
