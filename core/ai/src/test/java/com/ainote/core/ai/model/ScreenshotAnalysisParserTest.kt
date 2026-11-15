package com.ainote.core.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 解析器的价值全在「模型不听话」的那些边角上，所以测试重点放在畸形输入。
 */
class ScreenshotAnalysisParserTest {

    @Test
    fun `解析裸 JSON`() {
        val raw = """{"summary":"一张设置截图","tags":["设置","界面"],"category":"教程","todos":["做点什么"],"keyText":"关键文字"}"""

        val result = ScreenshotAnalysisParser.parse(raw)

        assertEquals("一张设置截图", result?.summary)
        assertEquals(listOf("设置", "界面"), result?.tags)
        assertEquals("教程", result?.category)
        assertEquals(listOf("做点什么"), result?.todos)
        assertEquals("关键文字", result?.keyText)
    }

    @Test
    fun `解析被 markdown 代码块包裹的 JSON`() {
        val raw = "```json\n{\"summary\":\"摘要内容\"}\n```"

        assertEquals("摘要内容", ScreenshotAnalysisParser.parse(raw)?.summary)
    }

    @Test
    fun `解析前后带解释文字的 JSON`() {
        val raw = "好的，这是分析结果：{\"summary\":\"摘要内容\"} 希望对你有帮助"

        assertEquals("摘要内容", ScreenshotAnalysisParser.parse(raw)?.summary)
    }

    @Test
    fun `字符串内的花括号不会破坏括号配对`() {
        val raw = """{"summary":"包含 {大括号} 和 \"引号\" 的文本"}"""

        assertEquals("包含 {大括号} 和 \"引号\" 的文本", ScreenshotAnalysisParser.parse(raw)?.summary)
    }

    @Test
    fun `转义反斜杠不会提前结束字符串`() {
        val raw = """{"summary":"路径 C:\\temp\\x 后面的 {内容}"}"""

        val summary = ScreenshotAnalysisParser.parse(raw)?.summary
        assertEquals("路径 C:\\temp\\x 后面的 {内容}", summary)
    }

    @Test
    fun `全部字段为空时判定为解析失败`() {
        assertNull(ScreenshotAnalysisParser.parse("""{"summary":"","tags":[],"todos":[],"keyText":null}"""))
    }

    @Test
    fun `完全不是 JSON 时返回 null`() {
        assertNull(ScreenshotAnalysisParser.parse("今天天气不错，我截了一张图。"))
    }

    @Test
    fun `未声明的字段被忽略而不是报错`() {
        val raw = """{"summary":"摘要内容","confidence":0.87,"nested":{"a":1}}"""

        assertEquals("摘要内容", ScreenshotAnalysisParser.parse(raw)?.summary)
    }

    @Test
    fun `标签数量被限制在上限内`() {
        val tags = (1..20).joinToString(",") { "\"t$it\"" }
        val raw = """{"summary":"摘要","tags":[$tags]}"""

        assertEquals(8, ScreenshotAnalysisParser.parse(raw)?.tags?.size)
    }

    @Test
    fun `markdown 正文包含待办与标签`() {
        val analysis = ScreenshotAnalysis(
            summary = "摘要行",
            tags = listOf("设置", "教程"),
            category = "教程",
            todos = listOf("做 A", "做 B"),
            keyText = "关键文字"
        )

        val markdown = analysis.toMarkdown()

        assertEquals(true, markdown.contains("摘要行"))
        assertEquals(true, markdown.contains("#设置 #教程"))
        assertEquals(true, markdown.contains("- [ ] 做 A"))
        assertEquals(true, markdown.contains("关键文字"))
    }
}
