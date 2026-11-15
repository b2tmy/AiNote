package com.ainote.core.ai.openai

import android.util.Base64
import com.ainote.core.ai.model.AiError
import com.ainote.core.ai.model.ChatMessage
import com.ainote.core.ai.model.ChatRequest
import com.ainote.core.ai.model.ChatResponse
import com.ainote.core.ai.model.ChatStreamEvent
import com.ainote.core.ai.model.ChatUsage
import com.ainote.core.common.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容协议的客户端。
 *
 * 「兼容」的范围：POST {baseUrl}/chat/completions，Bearer 鉴权，SSE 流式。
 * DeepSeek / OpenAI / 通义 / 智谱 / Kimi / Ollama / one-api 中转都落在这个契约里。
 *
 * 刻意不引 Retrofit：SSE 需要自己按行读 body，OkHttp 原生反而更直接。
 */
@Singleton
class OpenAiClient @Inject constructor(
    private val dispatchers: DispatcherProvider
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // 流式响应可能很久不出下一个 token，读超时给足
        .readTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ---------------- 非流式 ----------------

    suspend fun complete(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest
    ): Result<ChatResponse> = withContext(dispatchers.io) {
        runCatching {
            val payload = ChatCompletionRequestDto(
                model = request.model,
                messages = buildMessages(request.messages),
                stream = false,
                temperature = request.temperature,
                maxTokens = request.maxTokens
            )

            val httpResponse = client.newCall(buildHttpRequest(baseUrl, apiKey, payload)).execute()
            httpResponse.use { response ->
                if (!response.isSuccessful) throw mapHttpError(response)

                val body = response.body?.string()
                    ?: throw AiError.BadResponse("空响应体")

                val dto = runCatching {
                    json.decodeFromString<ChatCompletionResponseDto>(body)
                }.getOrElse { throw AiError.BadResponse(it.message) }

                ChatResponse(
                    content = dto.choices.firstOrNull()?.message?.content.orEmpty(),
                    model = dto.model,
                    usage = dto.usage?.toUsage()
                )
            }
        }.recoverCatching { throw it.asAiError() }
    }

    // ---------------- 流式 ----------------

    fun streamChat(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest
    ): Flow<ChatStreamEvent> = flow {
        val payload = ChatCompletionRequestDto(
            model = request.model,
            messages = buildMessages(request.messages),
            stream = true,
            temperature = request.temperature,
            maxTokens = request.maxTokens
        )

        var usage: ChatUsage? = null
        var completed = false

        try {
            client.newCall(buildHttpRequest(baseUrl, apiKey, payload)).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ChatStreamEvent.Failed(mapHttpError(response)))
                    return@use
                }

                val source = response.body?.source()
                if (source == null) {
                    emit(ChatStreamEvent.Failed(AiError.BadResponse("空响应体")))
                    return@use
                }

                while (!completed) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data:")) continue

                    val data = line.substring(DATA_PREFIX.length).trim()
                    if (data == "[DONE]") {
                        completed = true
                        break
                    }

                    val chunk = runCatching {
                        json.decodeFromString<ChatCompletionResponseDto>(data)
                    }.getOrNull() ?: continue

                    chunk.usage?.let { usage = it.toUsage() }
                    chunk.choices.firstOrNull()
                        ?.delta
                        ?.content
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { emit(ChatStreamEvent.Delta(it)) }
                }
            }
            emit(ChatStreamEvent.Completed(usage))
        } catch (e: SocketTimeoutException) {
            emit(ChatStreamEvent.Failed(AiError.Timeout()))
        } catch (e: IOException) {
            emit(ChatStreamEvent.Failed(AiError.Network(e.message)))
        } catch (t: Throwable) {
            emit(ChatStreamEvent.Failed(t.asAiError()))
        }
    }.flowOn(dispatchers.io)

    // ---------------- 连通性测试 ----------------

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(dispatchers.io) {
        runCatching {
            val payload = ChatCompletionRequestDto(
                model = model,
                messages = listOf(MessageDto("user", JsonPrimitive("ping"))),
                stream = false,
                temperature = 0.0,
                maxTokens = 8
            )

            client.newCall(buildHttpRequest(baseUrl, apiKey, payload)).execute().use { response ->
                if (!response.isSuccessful) throw mapHttpError(response)
                val body = response.body?.string().orEmpty()
                val dto = runCatching {
                    json.decodeFromString<ChatCompletionResponseDto>(body)
                }.getOrNull()
                val echoed = dto?.choices?.firstOrNull()?.message?.content
                echoed?.takeIf { it.isNotBlank() } ?: "连接正常（模型返回空内容）"
            }
        }.recoverCatching { throw it.asAiError() }
    }

    // ---------------- 内部工具 ----------------

    private fun buildHttpRequest(
        baseUrl: String,
        apiKey: String,
        payload: ChatCompletionRequestDto
    ): Request = Request.Builder()
        .url(endpoint(baseUrl, "chat/completions"))
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .addHeader("Accept", "application/json, text/event-stream")
        .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun endpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return "$base/${path.trimStart('/')}"
    }

    private fun buildMessages(messages: List<ChatMessage>): List<MessageDto> =
        messages.map { message ->
            if (message.images.isEmpty()) {
                MessageDto(message.role.wireName, JsonPrimitive(message.text))
            } else {
                // 多模态：text + image_url 数组
                val parts = buildJsonArray {
                    if (message.text.isNotBlank()) {
                        addJsonObject {
                            put("type", "text")
                            put("text", message.text)
                        }
                    }
                    message.images.forEach { base64 ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:${MIME_WEBP};base64,$base64")
                            }
                        }
                    }
                }
                MessageDto(message.role.wireName, parts)
            }
        }

    private fun mapHttpError(response: Response): AiError {
        val detail = runCatching {
            response.body?.string()?.let { body ->
                json.decodeFromString<OpenAiErrorEnvelope>(body).error?.message
            }
        }.getOrNull()

        return when (response.code) {
            401, 403 -> AiError.Unauthorized(detail)
            429 -> AiError.RateLimited(detail)
            else -> AiError.ServerError(response.code, detail)
        }
    }

    private fun Throwable.asAiError(): AiError =
        this as? AiError ?: when (this) {
            is SocketTimeoutException -> AiError.Timeout()
            is IOException -> AiError.Network(message)
            else -> AiError.BadResponse(message)
        }

    private fun UsageDto.toUsage() = ChatUsage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens
    )

    companion object {
        private const val DATA_PREFIX = "data:"
        private const val MIME_WEBP = "image/webp"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 把原始字节转成多模态接口需要的 base64（不带 data: 前缀） */
        fun encodeImage(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
