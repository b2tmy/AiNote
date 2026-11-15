package com.ainote.core.data.repository

import com.ainote.core.database.dao.ScreenshotRecordDao
import com.ainote.core.database.entity.AnalyzeStatus
import com.ainote.core.database.entity.ScreenshotRecordEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotRepository @Inject constructor(
    private val dao: ScreenshotRecordDao
) {

    fun observeRecent(limit: Int = 200): Flow<List<ScreenshotRecordEntity>> =
        dao.observeRecent(limit)

    fun observeInRange(from: Long, to: Long): Flow<List<ScreenshotRecordEntity>> =
        dao.observeInRange(from, to)

    fun observeById(id: Long): Flow<ScreenshotRecordEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): ScreenshotRecordEntity? = dao.getById(id)

    /** 待分析队列：M4 的分析 Worker 从这里取活 */
    suspend fun pending(): List<ScreenshotRecordEntity> = dao.getByStatus(AnalyzeStatus.PENDING)

    suspend fun countInRange(from: Long, to: Long): Int = dao.countInRange(from, to)

    /** 失败重跑：把状态退回 PENDING 并清掉上次的错误信息 */
    suspend fun retry(id: Long) {
        val record = dao.getById(id) ?: return
        dao.update(record.copy(status = AnalyzeStatus.PENDING, error = null))
    }

    suspend fun updateStatus(id: Long, status: AnalyzeStatus) = dao.updateStatus(id, status)

    /** 分析成功：回填摘要、标签、笔记指针与 token 用量 */
    suspend fun markDone(
        id: Long,
        status: AnalyzeStatus,
        summary: String?,
        tagsJson: String?,
        todosJson: String?,
        category: String?,
        noteId: Long?,
        provider: String?,
        model: String?,
        tokensIn: Int?,
        tokensOut: Int?
    ) = dao.markDone(
        id = id,
        status = status,
        summary = summary,
        tagsJson = tagsJson,
        todosJson = todosJson,
        category = category,
        noteId = noteId,
        provider = provider,
        model = model,
        tokensIn = tokensIn,
        tokensOut = tokensOut
    )

    suspend fun markFailed(id: Long, status: AnalyzeStatus, error: String?) =
        dao.markFailed(id, status, error)

    suspend fun updateOcrText(id: Long, text: String?) = dao.updateOcrText(id, text)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.deleteAll()
}
