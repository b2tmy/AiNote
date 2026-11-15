package com.ainote.core.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyStatsTest {

    @Test
    fun `一整天没有任何记录时判定为空`() {
        val stats = DailyStats(date = "2026-10-16")

        assertTrue(stats.isEmpty)
    }

    @Test
    fun `只有截图也算有内容`() {
        val stats = DailyStats(date = "2026-10-16", screenshots = 1)

        assertFalse(stats.isEmpty)
    }

    @Test
    fun `只有日程也算有内容`() {
        val stats = DailyStats(date = "2026-10-16", events = listOf("09:00 站会"))

        assertFalse(stats.isEmpty)
    }

    @Test
    fun `提示文本带上分类分布`() {
        val stats = DailyStats(
            date = "2026-10-16",
            screenshots = 3,
            categories = mapOf("教程" to 2, "聊天" to 1),
            notesCreated = 1,
            todosCreated = 2,
            todosDone = 1
        )

        val text = stats.toPromptText()

        assertTrue(text.contains("截图分析：3 条"))
        assertTrue(text.contains("教程 2"))
        assertTrue(text.contains("待办：新建 2 条，完成 1 条"))
    }

    @Test
    fun `没有完成项时不啰嗦地补零`() {
        val stats = DailyStats(date = "2026-10-16", screenshots = 1, todosCreated = 1)

        val text = stats.toPromptText()

        assertTrue(text.contains("待办：新建 1 条"))
        assertFalse(text.contains("完成 0 条"))
    }

    @Test
    fun `日程逐条列出`() {
        val stats = DailyStats(
            date = "2026-10-16",
            events = listOf("09:00 站会", "15:00 评审")
        )

        val text = stats.toPromptText()

        assertTrue(text.contains("09:00 站会"))
        assertTrue(text.contains("15:00 评审"))
    }
}
