package com.ainote.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 截图分析状态机 */
enum class AnalyzeStatus {
    /** 已入库，等待分析 */
    PENDING,

    /** 正在请求模型 */
    RUNNING,

    /** 分析完成，已写入笔记 */
    DONE,

    /** 分析失败，可重试 */
    FAILED,

    /** 按策略跳过（如"仅 Wi-Fi"时无网络、用户关闭了分析） */
    SKIPPED
}

@Entity(
    tableName = "screenshot_records",
    indices = [
        Index(value = ["sha256"], unique = true),
        Index("captured_at"),
        Index("status")
    ]
)
data class ScreenshotRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 系统 MediaStore 中的原始 URI */
    @ColumnInfo(name = "media_uri")
    val mediaUri: String,

    /** 复制到 app 私有目录后的路径（用户删原图也不影响） */
    @ColumnInfo(name = "local_path")
    val localPath: String,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long,

    /** 去重指纹 */
    @ColumnInfo(name = "sha256")
    val sha256: String,

    /** 本地 OCR 文本（无 vision 模型时的兜底） */
    @ColumnInfo(name = "ocr_text")
    val ocrText: String? = null,

    /** AI 生成的一句话摘要 */
    @ColumnInfo(name = "summary")
    val summary: String? = null,

    /** 标签数组的 JSON 串 */
    @ColumnInfo(name = "tags_json")
    val tagsJson: String? = null,

    /** 分析结果里提取出的待办（逗号分隔），供「一键转备忘」使用 */
    @ColumnInfo(name = "todos_json")
    val todosJson: String? = null,

    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "status")
    val status: AnalyzeStatus = AnalyzeStatus.PENDING,

    /** 分析结果落成的那条笔记 */
    @ColumnInfo(name = "note_id")
    val noteId: Long? = null,

    @ColumnInfo(name = "provider")
    val provider: String? = null,

    @ColumnInfo(name = "model")
    val model: String? = null,

    @ColumnInfo(name = "tokens_in")
    val tokensIn: Int? = null,

    @ColumnInfo(name = "tokens_out")
    val tokensOut: Int? = null,

    @ColumnInfo(name = "error")
    val error: String? = null,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
