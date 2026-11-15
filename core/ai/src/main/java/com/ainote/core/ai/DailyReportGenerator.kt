package com.ainote.core.ai

import com.ainote.core.ai.model.AiError
import com.ainote.core.ai.model.ChatMessage
import com.ainote.core.ai.model.ChatRole
import com.ainote.core.ai.model.DailyStats
import com.ainote.core.common.TimeFormat
import com.ainote.core.data.repository.NoteRepository
import com.ainote.core.data.repository.NotebookRepository
import com.ainote.core.database.dao.CalendarEventDao
import com.ainote.core.database.dao.DailySummaryDao
import com.ainote.core.database.dao.MemoDao
import com.ainote.core.database.dao.NoteDao
import com.ainote.core.database.dao.PromptTemplateDao
import com.ainote.core.database.dao.ScreenshotRecordDao
import com.ainote.core.database.entity.DailySummaryEntity
import com.ainote.core.database.entity.NoteSourceType
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日汇报生成器。
 *
 * 设计取舍：**AI 是增强而非依赖**。
 * 没配模型、没网、调用失败时都会退回纯统计文本，保证「今天发生了什么」这件事
 * 永远有产出 —— 只有叙事段落的质量会下降。
 *
 * 同一天的日报是覆盖而不是追加，避免反复生成后笔记本里堆一串重复条目。
 */
@Singleton
class DailyReportGenerator @Inject constructor(
    private val screenshotDao: ScreenshotRecordDao,
    private val noteDao: NoteDao,
    private val memoDao: MemoDao,
    private val calendarDao: CalendarEventDao,
    private val dailySummaryDao: DailySummaryDao,
    private val promptTemplateDao: PromptTemplateDao,
    private val notebookRepository: NotebookRepository,
    private val noteRepository: NoteRepository,
    private val aiRepository: AiRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 返回生成的笔记 id；[usedAi] 表示叙事是否真的由模型写成 */
    data class Result_(val noteId: Long, val stats: DailyStats, val usedAi: Boolean)

    suspend fun generate(date: LocalDate = LocalDate.now()): Result<Result_> {
        val stats = collectStats(date)
        if (stats.isEmpty) {
            return Result.failure(AiError.BadResponse("这一天没有任何记录，不需要汇报"))
        }

        val (narrative, usedAi) = buildNarrative(stats)
        val dateKey = TimeFormat.dayKey(date)

        val notebook = notebookRepository.requireDailyReportNotebook()
        val existing = dailySummaryDao.getByDate(dateKey)
        val title = "$dateKey 日报"
        val content = buildMarkdown(stats, narrative)

        // 同一天的日报覆盖旧的那条，避免反复生成后笔记本里堆一串重复条目
        val existingNoteId: Long? = existing?.noteId
        val noteId: Long = if (existingNoteId != null && noteRepository.getById(existingNoteId) != null) {
            noteRepository.save(existingNoteId, title, content)
            existingNoteId
        } else {
            noteRepository.create(
                notebookId = notebook.id,
                title = title,
                contentMd = content,
                sourceType = NoteSourceType.AI
            )
        }

        val now = System.currentTimeMillis()
        dailySummaryDao.upsert(
            DailySummaryEntity(
                id = existing?.id ?: 0,
                summaryDate = dateKey,
                noteId = noteId,
                statsJson = json.encodeToString(DailyStats.serializer(), stats),
                narrative = narrative,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )

        return Result.success(Result_(noteId, stats, usedAi))
    }

    private suspend fun collectStats(date: LocalDate): DailyStats {
        val from = TimeFormat.startOfDay(date)
        val to = TimeFormat.endOfDay(date)

        val categories = screenshotDao.categoryCountsInRange(from, to)
            .mapNotNull { entry -> entry.category?.let { it to entry.count } }
            .toMap()

        val topTags = screenshotDao.tagsJsonInRange(from, to)
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }

        val events = calendarDao.getInRange(from, to)
            .map { "${TimeFormat.time(it.startAt)} ${it.title}" }

        return DailyStats(
            date = TimeFormat.dayKey(date),
            screenshots = screenshotDao.countDoneInRange(from, to),
            categories = categories,
            notesCreated = noteDao.countCreatedInRange(from, to),
            notesUpdated = noteDao.countUpdatedInRange(from, to),
            todosCreated = memoDao.countCreatedInRange(from, to),
            todosDone = memoDao.countDoneInRange(from, to),
            events = events,
            topTags = topTags
        )
    }

    private suspend fun buildNarrative(stats: DailyStats): Pair<String, Boolean> {
        val provider = aiRepository.getDefaultProvider()
        if (provider == null || !aiRepository.hasApiKey(provider)) {
            return fallbackNarrative(stats) to false
        }

        val template = promptTemplateDao.getByKey(TEMPLATE_KEY)
        val systemPrompt = template?.systemPrompt
            ?: "你是日程回顾助手，写一段简洁自然的中文回顾。"
        val userPrompt = (template?.userTemplate
            ?: "这是我今天的数据：\n{stats}\n\n请生成今日回顾。")
            .replace("{stats}", stats.toPromptText())
            .replace("{date}", stats.date)
            .replace("{content}", stats.toPromptText())

        val response = aiRepository.complete(
            provider,
            listOf(
                ChatMessage(ChatRole.SYSTEM, systemPrompt),
                ChatMessage(ChatRole.USER, userPrompt)
            )
        )

        val text = response.getOrNull()?.content?.trim()
        return if (text.isNullOrBlank()) {
            fallbackNarrative(stats) to false
        } else {
            text to true
        }
    }

    /** 没有模型时的兜底：至少把事实说清楚 */
    private fun fallbackNarrative(stats: DailyStats): String = buildString {
        append("今天记录了 ${stats.screenshots} 张截图分析")
        if (stats.categories.isNotEmpty()) {
            append("，集中在 " + stats.categories.entries.joinToString("、") { it.key })
        }
        append("。")
        append("新建笔记 ${stats.notesCreated} 条、修改 ${stats.notesUpdated} 条；")
        append("待办新增 ${stats.todosCreated} 条、完成 ${stats.todosDone} 条。")
        if (stats.events.isNotEmpty()) {
            append("日程 ${stats.events.size} 项。")
        }
    }

    private fun buildMarkdown(stats: DailyStats, narrative: String): String = buildString {
        appendLine(narrative)
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("**当日数据**")
        appendLine()
        appendLine("- 截图分析：${stats.screenshots} 条")
        if (stats.categories.isNotEmpty()) {
            stats.categories.forEach { (category, count) ->
                appendLine("  - $category：$count")
            }
        }
        appendLine("- 笔记：新建 ${stats.notesCreated} · 修改 ${stats.notesUpdated}")
        appendLine("- 待办：新增 ${stats.todosCreated} · 完成 ${stats.todosDone}")
        if (stats.events.isNotEmpty()) {
            appendLine("- 日程：")
            stats.events.forEach { appendLine("  - $it") }
        }
        if (stats.topTags.isNotEmpty()) {
            appendLine()
            appendLine(stats.topTags.joinToString(" ") { "#$it" })
        }
    }.trim()

    private companion object {
        const val TEMPLATE_KEY = "daily_report"
    }
}
