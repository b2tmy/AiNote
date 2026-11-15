package com.ainote.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ainote.core.database.entity.AiConversationEntity
import com.ainote.core.database.entity.AiMessageEntity
import com.ainote.core.database.entity.ConversationScope
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {

    // ---------- 会话 ----------

    @Query("SELECT * FROM ai_conversations ORDER BY updated_at DESC")
    fun observeConversations(): Flow<List<AiConversationEntity>>

    @Query(
        """
        SELECT * FROM ai_conversations
        WHERE scope_type = :scope AND (scope_ref_id IS :refId)
        ORDER BY updated_at DESC LIMIT 1
        """
    )
    suspend fun findConversation(scope: ConversationScope, refId: Long?): AiConversationEntity?

    @Query("SELECT * FROM ai_conversations WHERE id = :id")
    suspend fun getConversation(id: Long): AiConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: AiConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: AiConversationEntity)

    @Query("UPDATE ai_conversations SET title = :title, updated_at = :now WHERE id = :id")
    suspend fun touchConversation(id: Long, title: String, now: Long)

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    // ---------- 消息 ----------

    @Query("SELECT * FROM ai_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC, id ASC")
    suspend fun getMessages(conversationId: Long): List<AiMessageEntity>

    /** 取最近 N 条作为上下文窗口 */
    @Query(
        """
        SELECT * FROM ai_messages WHERE conversation_id = :conversationId
        ORDER BY created_at DESC, id DESC LIMIT :limit
        """
    )
    suspend fun getRecentMessages(conversationId: Long, limit: Int): List<AiMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiMessageEntity): Long

    @Query("DELETE FROM ai_messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesOf(conversationId: Long)

    @Query("SELECT COALESCE(SUM(tokens_in), 0) FROM ai_messages WHERE created_at BETWEEN :from AND :to")
    suspend fun sumTokensIn(from: Long, to: Long): Int

    @Query("SELECT COALESCE(SUM(tokens_out), 0) FROM ai_messages WHERE created_at BETWEEN :from AND :to")
    suspend fun sumTokensOut(from: Long, to: Long): Int
}
