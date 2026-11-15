package com.ainote.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ainote.core.database.dao.AiChatDao
import com.ainote.core.database.dao.AiProviderDao
import com.ainote.core.database.dao.AttachmentDao
import com.ainote.core.database.dao.CalendarEventDao
import com.ainote.core.database.dao.DailySummaryDao
import com.ainote.core.database.dao.MemoDao
import com.ainote.core.database.dao.NoteDao
import com.ainote.core.database.dao.NotebookDao
import com.ainote.core.database.dao.PromptTemplateDao
import com.ainote.core.database.dao.ReminderDao
import com.ainote.core.database.dao.ScreenshotRecordDao
import com.ainote.core.database.entity.AiConversationEntity
import com.ainote.core.database.entity.AiMessageEntity
import com.ainote.core.database.entity.AiProviderEntity
import com.ainote.core.database.entity.AttachmentEntity
import com.ainote.core.database.entity.CalendarEventEntity
import com.ainote.core.database.entity.DailySummaryEntity
import com.ainote.core.database.entity.MemoEntity
import com.ainote.core.database.entity.NoteEntity
import com.ainote.core.database.entity.NotebookEntity
import com.ainote.core.database.entity.PromptTemplateEntity
import com.ainote.core.database.entity.ReminderEntity
import com.ainote.core.database.entity.ScreenshotRecordEntity

@Database(
    entities = [
        NotebookEntity::class,
        NoteEntity::class,
        AttachmentEntity::class,
        ScreenshotRecordEntity::class,
        MemoEntity::class,
        CalendarEventEntity::class,
        ReminderEntity::class,
        AiProviderEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
        PromptTemplateEntity::class,
        DailySummaryEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AiNoteDatabase : RoomDatabase() {

    abstract fun notebookDao(): NotebookDao
    abstract fun noteDao(): NoteDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun screenshotRecordDao(): ScreenshotRecordDao
    abstract fun memoDao(): MemoDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun reminderDao(): ReminderDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun promptTemplateDao(): PromptTemplateDao
    abstract fun dailySummaryDao(): DailySummaryDao

    companion object {
        const val DB_NAME = "ainote.db"

        /**
         * 2 → 3：截图记录增加 todos_json，用于「提取的待办」一键转备忘。
         * 用显式迁移而不是 destructive —— 用户数据和种子数据都得保住。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshot_records ADD COLUMN todos_json TEXT")
            }
        }

        fun build(context: Context): AiNoteDatabase =
            Room.databaseBuilder(context, AiNoteDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_2_3)
                // 兜底：遇到没写迁移的版本变化时直接重建，避免开发期卡死
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(SeedCallback)
                .build()
    }
}

/**
 * 种子数据的补齐。
 *
 * 注意这里挂在 **onOpen** 而不是 onCreate：
 * onCreate 只在数据库「全新创建」时触发，走 upgrade / destructive 路径时不会调用，
 * 结果就是版本升级后默认笔记本和提示词模板全部消失（实测踩到过）。
 * onOpen 每次打开都会跑，配合幂等检查既安全又不会重复插入。
 */
private object SeedCallback : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        runCatching {
            ensureNotebooks(db)
            ensureTemplates(db)
        }
    }

    private fun ensureNotebooks(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        insertIfAbsent(
            db,
            checkSql = "SELECT COUNT(*) FROM notebooks WHERE type = ?",
            checkArgs = arrayOf("AI_ANALYSIS"),
            insertSql = "INSERT INTO notebooks (name, type, icon, sort_order, created_at) VALUES (?, ?, ?, ?, ?)",
            insertArgs = arrayOf<Any?>("AI 截图分析", "AI_ANALYSIS", null, 0, now)
        )

        insertIfAbsent(
            db,
            checkSql = "SELECT COUNT(*) FROM notebooks WHERE type = ?",
            checkArgs = arrayOf("DAILY_REPORT"),
            insertSql = "INSERT INTO notebooks (name, type, icon, sort_order, created_at) VALUES (?, ?, ?, ?, ?)",
            insertArgs = arrayOf<Any?>("每日汇报", "DAILY_REPORT", null, 1, now)
        )

        // 「我的笔记」没有唯一特征，只在整库为空时补一条
        val total = db.query("SELECT COUNT(*) FROM notebooks").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (total == 0) {
            db.execSQL(
                "INSERT INTO notebooks (name, type, icon, sort_order, created_at) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>("我的笔记", "NORMAL", null, 2, now)
            )
        }
    }

    private fun ensureTemplates(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        templates.forEach { template ->
            insertIfAbsent(
                db,
                checkSql = "SELECT COUNT(*) FROM prompt_templates WHERE template_key = ?",
                checkArgs = arrayOf(template.key),
                insertSql = """
                    INSERT INTO prompt_templates
                        (template_key, name, system_prompt, user_template, version, enabled, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                insertArgs = arrayOf<Any?>(
                    template.key, template.name, template.system, template.user, 1, 1, now
                )
            )
        }
    }

    private fun insertIfAbsent(
        db: SupportSQLiteDatabase,
        checkSql: String,
        checkArgs: Array<Any?>,
        insertSql: String,
        insertArgs: Array<Any?>
    ) {
        val exists = db.query(checkSql, checkArgs).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) > 0
        }
        if (!exists) db.execSQL(insertSql, insertArgs)
    }

    private data class TemplateSeed(
        val key: String,
        val name: String,
        val system: String,
        val user: String
    )

    private val templates = listOf(
        TemplateSeed(
            key = "screenshot_analyze",
            name = "截图分析",
            system = "你是一个截图分析助手。用户会给你一张手机截图，请只输出严格合法的 JSON，不要任何解释性文字或代码块标记。",
            user = "分析这张截图，输出如下 JSON：{\"summary\":\"一句话说明这张图是什么\",\"tags\":[\"标签\"],\"category\":\"教程/聊天/灵感/报错/购物/文档/其它 中的一个\",\"todos\":[\"可执行的待办，没有则空数组\"],\"keyText\":\"图中最关键的文字，最多100字\"}"
        ),
        TemplateSeed(
            key = "note_summarize",
            name = "笔记摘要",
            system = "你是笔记整理助手，用简洁的中文回答，不寒暄。",
            user = "请总结下面这段笔记，输出 3-5 条要点：\n\n{content}"
        ),
        TemplateSeed(
            key = "note_continue",
            name = "续写笔记",
            system = "你是写作助手，延续用户原有的语气与风格继续写，不要重复已有内容。",
            user = "请顺着下面的内容继续写下去：\n\n{content}"
        ),
        TemplateSeed(
            key = "note_polish",
            name = "润色笔记",
            system = "你是文字编辑，只做润色，不改变原意，不添加新信息。",
            user = "润色下面这段文字，让它更通顺清晰：\n\n{content}"
        ),
        TemplateSeed(
            key = "note_extract_todos",
            name = "提取待办",
            system = "你是一个任务提取助手，只输出 JSON 数组，不要解释。",
            user = "从下面内容中提取所有可执行的待办事项，输出 JSON 数组，每项形如 {\"content\":\"...\",\"priority\":\"HIGH/NORMAL/LOW\"}，没有则输出 []：\n\n{content}"
        ),
        TemplateSeed(
            key = "daily_report",
            name = "每日汇报",
            system = "你是日程回顾助手。根据用户当日数据写一段 200-400 字的中文回顾，具体、自然，不写空洞的鼓励。",
            user = "这是我今天的数据：\n{stats}\n\n请生成今日回顾：今天主要做了什么、有什么值得注意的、明天的一个具体动作。"
        ),
        TemplateSeed(
            key = "event_parse",
            name = "自然语言建日程",
            system = "你是日程解析助手，只输出严格 JSON。",
            user = "把下面这句话解析成日程，输出 JSON：{\"title\":\"\",\"startAt\":\"ISO8601 本地时间\",\"endAt\":\"ISO8601\",\"allDay\":false,\"location\":null}。\n参考当前时间：{date}\n句子：{content}"
        )
    )
}
