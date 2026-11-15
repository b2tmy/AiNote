package com.ainote.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScreenshotAnalysisDto(
    val summary: String = "",
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val todos: List<String> = emptyList(),
    @SerialName("keyText") val keyText: String? = null
)

/** 归一化后的分析结果 */
data class ScreenshotAnalysis(
    val summary: String,
    val tags: List<String>,
    val category: String?,
    val todos: List<String>,
    val keyText: String?
) {
    /** 落成笔记正文 */
    fun toMarkdown(extra: String? = null): String = buildString {
        appendLine(summary)
        appendLine()
        keyText?.takeIf { it.isNotBlank() }?.let {
            appendLine("**关键信息**：$it")
            appendLine()
        }
        if (tags.isNotEmpty()) {
            appendLine(tags.joinToString(" ") { tag -> "#" + tag.trim().removePrefix("#") })
            appendLine()
        }
        if (todos.isNotEmpty()) {
            appendLine("**待办**")
            todos.forEach { appendLine("- [ ] $it") }
            appendLine()
        }
        extra?.takeIf { it.isNotBlank() }?.let {
            appendLine("---")
            appendLine(it)
        }
    }.trim()
}

/**
 * 解析模型返回的结构化结果。
 *
 * 现实情况是：即使提示词里写了「只输出 JSON」，模型仍可能包一层 ```json 围栏、
 * 或者在前后加一句「好的，这是分析结果：」。所以这里做三步：
 * 1. 用括号配平扫描抠出第一个完整的 JSON 对象（正确处理字符串内的花括号）；
 * 2. 宽松反序列化；
 * 3. 全部字段都空则判定为解析失败，交给上层做降级处理。
 */
object ScreenshotAnalysisParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(raw: String): ScreenshotAnalysis? {
        val candidate = extractJsonObject(raw) ?: return null
        val dto = runCatching {
            json.decodeFromString<ScreenshotAnalysisDto>(candidate)
        }.getOrNull() ?: return null

        val analysis = ScreenshotAnalysis(
            summary = dto.summary.trim(),
            tags = dto.tags.map { it.trim() }.filter { it.isNotEmpty() }.take(8),
            category = dto.category?.trim()?.takeIf { it.isNotEmpty() },
            todos = dto.todos.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
            keyText = dto.keyText?.trim()?.takeIf { it.isNotEmpty() }
        )

        val meaningful = analysis.summary.isNotBlank() ||
            analysis.tags.isNotEmpty() ||
            analysis.todos.isNotEmpty() ||
            analysis.keyText != null
        return analysis.takeIf { meaningful }
    }

    /** 抠出第一个括号配平的 JSON 对象；能找到才返回 */
    internal fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until raw.length) {
            val ch = raw[index]
            when {
                escaped -> escaped = false
                inString && ch == '\\' -> escaped = true
                ch == '"' -> inString = !inString
                !inString && ch == '{' -> depth++
                !inString && ch == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
