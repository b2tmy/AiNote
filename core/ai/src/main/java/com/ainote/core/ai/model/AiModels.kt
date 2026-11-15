package com.ainote.core.ai.model

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT;

    val wireName: String
        get() = when (this) {
            SYSTEM -> "system"
            USER -> "user"
            ASSISTANT -> "assistant"
        }
}

/**
 * 一条对话消息。[images] 里放的是**不含 data: 前缀**的 base64 图片，
 * 由客户端在序列化时包成 OpenAI 的 image_url content part。
 */
data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val images: List<String> = emptyList()
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    val maxTokens: Int? = null,
    val stream: Boolean = false
)

data class ChatUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

data class ChatResponse(
    val content: String,
    val model: String? = null,
    val usage: ChatUsage? = null
)

sealed interface ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent
    data class Completed(val usage: ChatUsage? = null) : ChatStreamEvent
    data class Failed(val error: AiError) : ChatStreamEvent
}

/**
 * 统一错误类型。UI 只认这一套，不用去猜 OkHttp / 序列化的异常语义。
 */
sealed class AiError(message: String) : Exception(message) {

    class MissingProvider : AiError("还没有配置模型服务")

    class MissingApiKey : AiError("还没有填写 API Key")

    class SecretStoreUnavailable : AiError("系统密钥库不可用，无法读取 API Key")

    class Unauthorized(detail: String? = null) :
        AiError("API Key 无效或无权限" + detail.suffix())

    class RateLimited(detail: String? = null) :
        AiError("请求过于频繁或额度已用尽" + detail.suffix())

    class ServerError(val code: Int, detail: String? = null) :
        AiError("服务返回 $code" + detail.suffix())

    class Network(detail: String? = null) : AiError("网络不通" + detail.suffix())

    class Timeout : AiError("请求超时，稍后再试")

    class BadResponse(detail: String? = null) : AiError("返回内容无法解析" + detail.suffix())
}

private fun String?.suffix() = if (isNullOrBlank()) "" else "：$this"
