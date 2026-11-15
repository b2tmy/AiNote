package com.ainote.core.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ainote.core.ai.model.AiError
import com.ainote.core.ai.model.ChatMessage
import com.ainote.core.ai.model.ChatRole
import com.ainote.core.ai.model.ScreenshotAnalysisParser
import com.ainote.core.common.DispatcherProvider
import com.ainote.core.common.TimeFormat
import com.ainote.core.common.image.OcrEngine
import com.ainote.core.data.repository.NoteRepository
import com.ainote.core.data.repository.NotebookRepository
import com.ainote.core.data.repository.ScreenshotRepository
import com.ainote.core.database.dao.PromptTemplateDao
import com.ainote.core.database.entity.AnalyzeStatus
import com.ainote.core.database.entity.NoteSourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单张截图的完整分析链路：
 * 读压缩图 → 拼提示词（含模板变量）→ 调模型 → 解析结构化 JSON → 落成笔记 → 回填记录。
 *
 * 失败不吞：状态置 FAILED 并把错误写进记录，用户在列表里能看到原因并重试。
 */
@Singleton
class ScreenshotAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenshotRepository: ScreenshotRepository,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val promptTemplateDao: PromptTemplateDao,
    private val aiRepository: AiRepository,
    private val ocrEngine: OcrEngine,
    private val dispatchers: DispatcherProvider
) {

    /** 返回生成的笔记 id */
    suspend fun analyze(recordId: Long): Result<Long> = withContext(dispatchers.io) {
        val record = screenshotRepository.getById(recordId)
            ?: return@withContext Result.failure(AiError.BadResponse("截图记录不存在"))

        val provider = aiRepository.getDefaultProvider()
            ?: return@withContext fail(recordId, AiError.MissingProvider())

        if (!aiRepository.hasApiKey(provider)) {
            return@withContext fail(recordId, AiError.MissingApiKey())
        }

        screenshotRepository.updateStatus(recordId, AnalyzeStatus.RUNNING)

        try {
            val images = if (provider.supportsVision) {
                readImageAsBase64(record.localPath)
            } else {
                emptyList()
            }

            // 模型不支持看图时：先用本地 OCR 把字抠出来，再交给文本模型做结构化。
            // 这是「DeepSeek 之类纯文本模型也能用截图分析」的关键一步。
            var ocrText = record.ocrText
            if (images.isEmpty() && ocrText.isNullOrBlank()) {
                ocrText = ocrEngine.recognize(File(record.localPath))
                if (!ocrText.isNullOrBlank()) {
                    screenshotRepository.updateOcrText(recordId, ocrText)
                }
            }

            if (images.isEmpty() && ocrText.isNullOrBlank()) {
                return@withContext fail(
                    recordId,
                    AiError.BadResponse("模型不支持看图，本地 OCR 也没能从这张图里识别出文字")
                )
            }

            val template = promptTemplateDao.getByKey(TEMPLATE_KEY)
            val systemPrompt = template?.systemPrompt ?: DEFAULT_SYSTEM
            val userPrompt = (template?.userTemplate ?: DEFAULT_USER)
                .replace("{date}", TimeFormat.dateTime(System.currentTimeMillis()))
                .replace("{content}", ocrText.orEmpty())

            val messages = buildList {
                add(ChatMessage(ChatRole.SYSTEM, systemPrompt))
                add(ChatMessage(ChatRole.USER, userPrompt, images))
            }

            val response = aiRepository.complete(provider, messages).getOrElse { error ->
                return@withContext fail(recordId, error)
            }

            val analysis = ScreenshotAnalysisParser.parse(response.content)
                ?: return@withContext fail(
                    recordId,
                    AiError.BadResponse("模型没有按 JSON 格式返回：${response.content.take(80)}")
                )

            val notebook = notebookRepository.requireAiAnalysisNotebook()
            val noteId = noteRepository.create(
                notebookId = notebook.id,
                title = analysis.summary.take(30).ifBlank { "截图分析" },
                contentMd = analysis.toMarkdown(extra = sourceLine(record.capturedAt)),
                sourceType = NoteSourceType.SCREENSHOT
            )

            screenshotRepository.markDone(
                id = recordId,
                status = AnalyzeStatus.DONE,
                summary = analysis.summary,
                tagsJson = analysis.tags.takeIf { it.isNotEmpty() }?.joinToString(","),
                todosJson = analysis.todos.takeIf { it.isNotEmpty() }?.joinToString(","),
                category = analysis.category,
                noteId = noteId,
                provider = provider.name,
                model = provider.model,
                tokensIn = response.usage?.promptTokens,
                tokensOut = response.usage?.completionTokens
            )

            Result.success(noteId)
        } catch (t: Throwable) {
            Log.w(TAG, "分析截图失败 id=$recordId", t)
            fail(recordId, t)
        }
    }

    private suspend fun fail(recordId: Long, error: Throwable): Result<Long> {
        val message = error.message ?: error::class.simpleName ?: "未知错误"
        screenshotRepository.markFailed(recordId, AnalyzeStatus.FAILED, message)
        return Result.failure(error)
    }

    private fun sourceLine(capturedAt: Long): String =
        "> 来自截图 · ${TimeFormat.dateTime(capturedAt)}"

    private fun readImageAsBase64(path: String): List<String> = runCatching {
        val file = File(path)
        if (!file.exists()) return emptyList()
        listOf(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
    }.getOrDefault(emptyList())

    companion object {
        const val TEMPLATE_KEY = "screenshot_analyze"
        private const val TAG = "ScreenshotAnalyzer"

        private const val DEFAULT_SYSTEM =
            "你是一个截图分析助手。请只输出严格合法的 JSON，不要任何解释文字。"

        private const val DEFAULT_USER =
            "分析这张截图，输出 JSON：{\"summary\":\"一句话说明这张图是什么\"," +
                "\"tags\":[\"标签\"],\"category\":\"分类\",\"todos\":[\"待办\"],\"keyText\":\"关键文字\"}"
    }
}
