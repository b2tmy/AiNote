package com.ainote.core.ai.model

import kotlinx.serialization.Serializable

/** 单日统计快照，日报的输入 */
@Serializable
data class DailyStats(
    val date: String,
    val screenshots: Int = 0,
    val categories: Map<String, Int> = emptyMap(),
    val notesCreated: Int = 0,
    val notesUpdated: Int = 0,
    val todosCreated: Int = 0,
    val todosDone: Int = 0,
    val events: List<String> = emptyList(),
    val topTags: List<String> = emptyList()
) {

    /** 一整天什么都没发生时不打扰用户 */
    val isEmpty: Boolean
        get() = screenshots == 0 &&
            notesCreated == 0 &&
            notesUpdated == 0 &&
            todosCreated == 0 &&
            todosDone == 0 &&
            events.isEmpty()

    /** 拼成给模型看的人类可读文本，比丢 JSON 更省 token 也更稳 */
    fun toPromptText(): String = buildString {
        appendLine("日期：$date")
        append("截图分析：$screenshots 条")
        if (categories.isNotEmpty()) {
            append("（" + categories.entries.joinToString("、") { "${it.key} ${it.value}" } + "）")
        }
        appendLine()
        appendLine("笔记：新建 $notesCreated 条，修改 $notesUpdated 条")
        append("待办：新建 $todosCreated 条")
        if (todosDone > 0) append("，完成 $todosDone 条")
        appendLine()
        if (events.isNotEmpty()) {
            appendLine("日程：${events.joinToString("；")}")
        }
        if (topTags.isNotEmpty()) {
            appendLine("高频标签：${topTags.joinToString("、")}")
        }
    }.trim()
}
