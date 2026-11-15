package com.ainote.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainote.core.ai.AiRepository
import com.ainote.core.ai.model.ChatMessage
import com.ainote.core.ai.model.ChatRole
import com.ainote.core.ai.model.ChatStreamEvent
import com.ainote.core.database.dao.AiChatDao
import com.ainote.core.database.entity.AiConversationEntity
import com.ainote.core.database.entity.AiMessageEntity
import com.ainote.core.database.entity.AiProviderEntity
import com.ainote.core.database.entity.ConversationScope
import com.ainote.core.database.entity.MessageRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatDao: AiChatDao
) : ViewModel() {

    private var conversationId: Long = 0L

    private val _messages = MutableStateFlow<List<AiMessageEntity>>(emptyList())
    val messages: StateFlow<List<AiMessageEntity>> = _messages.asStateFlow()

    /** 正在流式接收的半成品文本 */
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _provider = MutableStateFlow<AiProviderEntity?>(null)
    val provider: StateFlow<AiProviderEntity?> = _provider.asStateFlow()

    init {
        viewModelScope.launch {
            conversationId = ensureConversation()
            chatDao.observeMessages(conversationId).collect { list ->
                _messages.value = list
            }
        }
        refreshProvider()
    }

    fun refreshProvider() {
        viewModelScope.launch { _provider.value = aiRepository.getDefaultProvider() }
    }

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _isStreaming.value) return

        viewModelScope.launch {
            _error.value = null
            if (conversationId == 0L) conversationId = ensureConversation()

            val provider = aiRepository.getDefaultProvider()
            if (provider == null) {
                _error.value = "还没有配置模型服务，点右上角去设置"
                return@launch
            }
            _provider.value = provider

            chatDao.insertMessage(
                AiMessageEntity(
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    content = content,
                    createdAt = System.currentTimeMillis()
                )
            )

            val history = buildHistory()
            val buffer = StringBuilder()
            var failure: String? = null

            _isStreaming.value = true
            _streamingText.value = ""

            aiRepository.stream(provider, history).collect { event ->
                when (event) {
                    is ChatStreamEvent.Delta -> {
                        buffer.append(event.text)
                        _streamingText.value = buffer.toString()
                    }

                    is ChatStreamEvent.Failed -> {
                        failure = event.error.message
                    }

                    is ChatStreamEvent.Completed -> Unit
                }
            }

            _isStreaming.value = false
            _streamingText.value = ""

            if (buffer.isNotEmpty()) {
                chatDao.insertMessage(
                    AiMessageEntity(
                        conversationId = conversationId,
                        role = MessageRole.ASSISTANT,
                        content = buffer.toString(),
                        provider = provider.name,
                        model = provider.model,
                        createdAt = System.currentTimeMillis()
                    )
                )
                chatDao.touchConversation(
                    id = conversationId,
                    title = history.firstOrNull { it.role == ChatRole.USER }?.text?.take(20) ?: "AI 助手",
                    now = System.currentTimeMillis()
                )
            }

            if (failure != null) {
                _error.value = failure
            } else if (buffer.isEmpty()) {
                _error.value = "模型没有返回任何内容"
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            if (conversationId == 0L) return@launch
            chatDao.deleteMessagesOf(conversationId)
            _error.value = null
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private suspend fun ensureConversation(): Long {
        chatDao.findConversation(ConversationScope.GLOBAL, null)?.let { return it.id }
        val now = System.currentTimeMillis()
        return chatDao.insertConversation(
            AiConversationEntity(
                scopeType = ConversationScope.GLOBAL,
                scopeRefId = null,
                title = "AI 助手",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** 取最近若干轮作为上下文，避免长会话把 token 撑爆 */
    private suspend fun buildHistory(): List<ChatMessage> =
        chatDao.getRecentMessages(conversationId, CONTEXT_WINDOW)
            .sortedBy { it.id }
            .mapNotNull { entity ->
                val role = when (entity.role) {
                    MessageRole.USER -> ChatRole.USER
                    MessageRole.ASSISTANT -> ChatRole.ASSISTANT
                    MessageRole.SYSTEM -> ChatRole.SYSTEM
                }
                entity.content.takeIf { it.isNotBlank() }?.let { ChatMessage(role, it) }
            }

    private companion object {
        const val CONTEXT_WINDOW = 20
    }
}
