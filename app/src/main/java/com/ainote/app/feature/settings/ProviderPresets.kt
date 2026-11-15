package com.ainote.app.feature.settings

import com.ainote.core.database.entity.AiProviderEntity

/**
 * 内置厂商预设。全部走 OpenAI 兼容协议，只有 baseUrl 和模型名不同。
 * 用户一键添加后仍可随意改。
 */
data class ProviderPreset(
    val key: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val supportsVision: Boolean,
    val note: String
) {
    fun toEntity(now: Long) = AiProviderEntity(
        name = displayName,
        baseUrl = baseUrl,
        model = model,
        supportsVision = supportsVision,
        supportsStream = true,
        temperature = 0.3,
        maxTokens = 1024,
        enabled = true,
        presetKey = key,
        createdAt = now
    )
}

object ProviderPresets {

    val all: List<ProviderPreset> = listOf(
        ProviderPreset(
            key = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            supportsVision = false,
            note = "中文性价比最高，纯文本"
        ),
        ProviderPreset(
            key = "openai",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
            supportsVision = true,
            note = "官方，支持看图"
        ),
        ProviderPreset(
            key = "qwen",
            displayName = "通义千问",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            model = "qwen-plus",
            supportsVision = false,
            note = "阿里，兼容模式"
        ),
        ProviderPreset(
            key = "qwen-vl",
            displayName = "通义千问 VL",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            model = "qwen-vl-plus",
            supportsVision = true,
            note = "阿里，支持看图"
        ),
        ProviderPreset(
            key = "zhipu",
            displayName = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            model = "glm-4-flash",
            supportsVision = false,
            note = "智谱，免费档可用"
        ),
        ProviderPreset(
            key = "zhipu-v",
            displayName = "智谱 GLM-4V",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            model = "glm-4v-flash",
            supportsVision = true,
            note = "智谱，支持看图"
        ),
        ProviderPreset(
            key = "moonshot",
            displayName = "Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            model = "moonshot-v1-8k",
            supportsVision = false,
            note = "月之暗面，长文本"
        ),
        ProviderPreset(
            key = "custom",
            displayName = "自定义 / 中转",
            baseUrl = "https://",
            model = "",
            supportsVision = true,
            note = "one-api、Ollama、局域网自建等"
        )
    )
}
