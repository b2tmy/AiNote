package com.ainote.core.ai

import com.ainote.core.ai.model.AiError
import com.ainote.core.ai.model.ChatMessage
import com.ainote.core.ai.model.ChatResponse
import com.ainote.core.ai.model.ChatStreamEvent
import com.ainote.core.ai.openai.OpenAiClient
import com.ainote.core.database.dao.AiChatDao
import com.ainote.core.database.dao.AiProviderDao
import com.ainote.core.database.entity.AiProviderEntity
import com.ainote.core.security.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 能力的统一入口：负责把「数据库里的配置 + 密钥库里的 key」拼成一次可用的请求。
 *
 * 上层（设置页、AI 助手、截图分析）只跟这个类打交道，不直接碰 OkHttp 或 Keystore。
 */
@Singleton
class AiRepository @Inject constructor(
    private val providerDao: AiProviderDao,
    private val chatDao: AiChatDao,
    private val secretStore: SecretStore,
    private val client: OpenAiClient
) {

    val secretStoreAvailable: Boolean get() = secretStore.isAvailable

    fun observeProviders(): Flow<List<AiProviderEntity>> = providerDao.observeAll()

    suspend fun getProvider(id: Long): AiProviderEntity? = providerDao.getById(id)

    suspend fun getDefaultProvider(): AiProviderEntity? = providerDao.getDefault()

    suspend fun saveProvider(provider: AiProviderEntity, apiKey: String?): Long {
        // 别名用 provider 名字生成，稳定且与 id 无关，便于重复保存时覆盖而不是堆积
        val alias = provider.keyAlias ?: aliasFor(provider.name)
        if (!apiKey.isNullOrBlank()) {
            secretStore.putApiKey(alias, apiKey.trim())
        }
        return providerDao.insert(provider.copy(keyAlias = alias))
    }

    suspend fun deleteProvider(provider: AiProviderEntity) {
        provider.keyAlias?.let { secretStore.removeApiKey(it) }
        providerDao.delete(provider)
    }

    suspend fun setDefaultProvider(id: Long) {
        providerDao.clearDefault()
        providerDao.setDefault(id)
    }

    suspend fun hasApiKey(provider: AiProviderEntity): Boolean =
        provider.keyAlias?.let { secretStore.hasApiKey(it) } ?: false

    suspend fun maskedKey(provider: AiProviderEntity): String? =
        provider.keyAlias?.let { secretStore.maskedKey(it) }

    // ---------------- 对话 ----------------

    suspend fun complete(
        provider: AiProviderEntity?,
        messages: List<ChatMessage>
    ): Result<ChatResponse> {
        val resolved = resolve(provider) ?: return Result.failure(resolveError(provider))
        return client.complete(resolved.baseUrl, resolved.apiKey, resolved.toRequest(messages))
    }

    fun stream(
        provider: AiProviderEntity?,
        messages: List<ChatMessage>
    ): Flow<ChatStreamEvent> = flow {
        val resolved = resolve(provider)
        if (resolved == null) {
            emit(ChatStreamEvent.Failed(resolveError(provider)))
            return@flow
        }
        emitAll(client.streamChat(resolved.baseUrl, resolved.apiKey, resolved.toRequest(messages)))
    }

    suspend fun testConnection(provider: AiProviderEntity): Result<String> {
        val resolved = resolve(provider)
            ?: return Result.failure(resolveError(provider))
        return client.testConnection(resolved.baseUrl, resolved.apiKey, resolved.model)
    }

    // ---------------- 内部 ----------------

    private class Resolved(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val temperature: Double,
        val maxTokens: Int
    ) {
        fun toRequest(messages: List<ChatMessage>) = com.ainote.core.ai.model.ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    private fun resolve(provider: AiProviderEntity?): Resolved? {
        if (provider == null) return null
        val alias = provider.keyAlias ?: return null
        if (!secretStore.isAvailable) return null
        val key = secretStore.getApiKey(alias)
        if (key.isNullOrBlank()) return null
        if (provider.baseUrl.isBlank() || provider.model.isBlank()) return null

        return Resolved(
            baseUrl = provider.baseUrl,
            apiKey = key,
            model = provider.model,
            temperature = provider.temperature,
            maxTokens = provider.maxTokens
        )
    }

    private fun resolveError(provider: AiProviderEntity?): AiError = when {
        provider == null -> AiError.MissingProvider()
        !secretStore.isAvailable -> AiError.SecretStoreUnavailable()
        provider.keyAlias == null -> AiError.MissingApiKey()
        else -> AiError.MissingApiKey()
    }

    private fun aliasFor(name: String) = "apikey_" + name.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
}
