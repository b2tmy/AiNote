package com.ainote.core.ai.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<MessageDto>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

/**
 * content 可能是纯字符串，也可能是多模态数组（text + image_url），
 * 所以这里用 JsonElement 而不是 String，交给调用方构造。
 */
@Serializable
data class MessageDto(
    val role: String,
    val content: JsonElement
)

@Serializable
data class ChatCompletionResponseDto(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null
)

@Serializable
data class ChoiceDto(
    val index: Int = 0,
    val message: MessageContentDto? = null,
    val delta: MessageContentDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class MessageContentDto(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

@Serializable
data class OpenAiErrorEnvelope(
    val error: OpenAiErrorBody? = null
)

@Serializable
data class OpenAiErrorBody(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

@Serializable
data class ModelsResponseDto(
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String = "",
    val owned_by: String? = null
)
