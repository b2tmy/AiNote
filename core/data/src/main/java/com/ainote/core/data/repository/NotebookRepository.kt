package com.ainote.core.data.repository

import com.ainote.core.database.dao.NotebookDao
import com.ainote.core.database.entity.NotebookEntity
import com.ainote.core.database.entity.NotebookType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotebookRepository @Inject constructor(
    private val dao: NotebookDao
) {

    fun observeAll(): Flow<List<NotebookEntity>> = dao.observeAll()

    suspend fun getById(id: Long): NotebookEntity? = dao.getById(id)

    /** 内置的「AI 截图分析」笔记本，首次使用时若缺失会自动补建 */
    suspend fun requireAiAnalysisNotebook(): NotebookEntity =
        dao.getByType(NotebookType.AI_ANALYSIS) ?: create(
            name = "AI 截图分析",
            type = NotebookType.AI_ANALYSIS
        ).let { id -> dao.getById(id)!! }

    /** 内置的「每日汇报」笔记本 */
    suspend fun requireDailyReportNotebook(): NotebookEntity =
        dao.getByType(NotebookType.DAILY_REPORT) ?: create(
            name = "每日汇报",
            type = NotebookType.DAILY_REPORT
        ).let { id -> dao.getById(id)!! }

    suspend fun create(
        name: String,
        type: NotebookType = NotebookType.NORMAL,
        icon: String? = null
    ): Long = dao.insert(
        NotebookEntity(
            name = name,
            type = type,
            icon = icon,
            sortOrder = dao.count(),
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun rename(notebook: NotebookEntity, newName: String) =
        dao.update(notebook.copy(name = newName))

    suspend fun update(notebook: NotebookEntity) = dao.update(notebook)

    suspend fun delete(notebook: NotebookEntity) = dao.delete(notebook)
}
