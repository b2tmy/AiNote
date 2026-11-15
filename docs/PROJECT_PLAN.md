# AI 智能笔记（Android）项目计划书

| 项 | 内容 |
|---|---|
| 文档版本 | v1.0（待审批） |
| 项目代号 | AiNote（暂定，可改） |
| 目标平台 | Android（原生，Kotlin + Jetpack Compose） |
| 阶段 | 计划书评审 → 审批通过 → 开工 |
| 本机环境 | Windows 10.0.26200 / JDK 25 / adb 36.0.2（E:\platform\platform-tools）/ 无 Android Studio / **SDK 缺 platforms、build-tools、cmdline-tools** |
| 工作目录 | `E:\note_work` |

---

## 1. 项目概述

### 1.1 一句话定位

一个**纯本地优先**的 Android 笔记应用：它在你截图的那一刻自动"看懂"这张图，把结论写进专属的 AI 分析笔记本；白天随时记的灵感、待办、日程也由同一个 AI 帮你整理，每天晚上给你一份当日汇报。

### 1.2 核心价值

| 痛点 | 本项目的解法 |
|---|---|
| 截图等于垃圾堆，截完就忘，相册里几百张图再也翻不到 | 截图落入 `Screenshots/` 的瞬间被捕获 → AI 描述/分类/提取要点 → 自动写入"AI 分析笔记" |
| 笔记软件只是"存"，不会"想" | 每条笔记、备忘、日程都能一键让 AI 续写、润色、摘要、拆任务 |
| 一天干了什么没有反馈 | 每日定时汇总当日截图记录 + 笔记 + 待办完成情况，生成当日汇报并推送 |
| 云端笔记怕隐私 | 数据（含图片路径、OCR 文本、AI 结果）全在本机 Room 数据库；API Key 走 Keystore 加密；只有"图片 + 提示词"按需发给你自己配置的模型服务 |
| 被某一家 AI 服务锁定 | 统一 OpenAI 兼容协议，DeepSeek / OpenAI / 通义 / 智谱 / Kimi / 本地 Ollama / one-api 中转都能用，Key 由用户自己填 |

### 1.3 目标用户

- 重度截图党：教程、灵感、聊天记录、商品、PPT、报错信息都靠截图留存
- 需要"第二大脑"的个人用户：希望记录低成本、回顾自动化
- 对数据主权敏感、愿意自备 API Key 的用户

### 1.4 明确不做（第一版）

- 多端云同步 / 账号体系 / 服务端（V1 纯本地）
- 协同编辑、公开分享链接
- iOS / 桌面端
- 复杂排版富文本（V1 为 Markdown 长文本 + 基础富文本工具栏）

---

## 2. 功能需求

### 2.1 FR-1 截图智能分析笔记本（核心）

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-1.1 | 用户截图后，App 在后台自动捕获新增截图（无需用户打开 App） | P0 |
| FR-1.2 | 捕获后做"简短分析"：一句话描述、关键信息提取、分类标签、可选待办提取 | P0 |
| FR-1.3 | 分析结果以一条笔记写入专门的"AI 分析笔记本"，附件保留原图缩略图，可点开看原图 | P0 |
| FR-1.4 | 分析前的图片压缩（长边 ≤ 1024、WebP q80），控制 token 成本 | P0 |
| FR-1.5 | 分析失败自动重试（最多 3 次，指数退避），失败记录可见可手动重跑 | P0 |
| FR-1.6 | 支持"仅 Wi-Fi 分析""仅在充电时分析""分析开关"等策略 | P1 |
| FR-1.7 | 模型不支持视觉时，回退本地 ML Kit OCR → 文本模型分析 | P1 |
| FR-1.8 | 兜底入口：分享菜单收图、快捷设置磁贴（TileService）、桌面快捷方式"分析当前截图" | P1 |
| FR-1.9 | 截图记录列表页：按天分组、按分类筛选、失败态高亮 | P1 |
| FR-1.10 | 图片重复检测（sha256 去重），同一张图不重复分析 | P2 |

### 2.2 FR-2 记事本与备忘录

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-2.1 | 多笔记本（Notebook）管理：新建/重命名/图标/排序/删除 | P0 |
| FR-2.2 | 笔记：标题 + 正文（Markdown 编辑，实时预览）、自动保存、创建/更新时间 | P0 |
| FR-2.3 | 附件：从相册/拍照插入图片，展示九宫格 | P1 |
| FR-2.4 | 置顶、归档、回收站（软删除，30 天清理） | P1 |
| FR-2.5 | 全文搜索（Room FTS4/FTS5 中文分词） | P1 |
| FR-2.6 | 备忘录（轻量待办）：一行一条、勾选完成、优先级、可选截止时间 | P0 |
| FR-2.7 | 备忘录支持"从截图分析结果一键转待办" | P2 |
| FR-2.8 | 标签系统（Tag）+ 按标签筛选 | P2 |

### 2.3 FR-3 日历与提醒

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-3.1 | 月视图日历 + 当日事件列表（Compose 自绘，不依赖系统日历 UI） | P0 |
| FR-3.2 | 事件增删改：标题、开始/结束、全天、地点、备注、颜色 | P0 |
| FR-3.3 | 多提醒：事件前 5min/15min/1h/1d 多档 | P0 |
| FR-3.4 | 重复规则：不重复/每天/每周/每月/每年/自定义 RRULE 子集 | P1 |
| FR-3.5 | 提醒落地：AlarmManager 精确闹钟 + 通知（含"稍后提醒""完成"动作按钮） | P0 |
| FR-3.6 | 重启/时区变化/时间被改后自愈重排（BOOT_COMPLETED + TIME_SET + TIMEZONE_CHANGED） | P0 |
| FR-3.7 | 可选：与系统日历双向同步（CalendarContract） | P2 |
| FR-3.8 | AI：自然语言建日程（"明天下午三点跟老张开会"→ 解析成事件草稿待确认） | P1 |

### 2.4 FR-4 AI 交互层（贯穿全局）

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-4.1 | 全局 AI 助手入口（浮动按钮 / 底栏 Tab）多轮对话 | P0 |
| FR-4.2 | 上下文注入：当前笔记 / 当前截图记录 / 当前日程 可"送给 AI" | P0 |
| FR-4.3 | 场景动作：摘要、续写、润色、纠错、翻译、提取待办、生成标题/标签 | P0 |
| FR-4.4 | 流式输出（SSE，逐字上屏），可中断 | P0 |
| FR-4.5 | 提示词模板本地可编辑（系统提示 + 用户模板 + 变量占位符） | P1 |
| FR-4.6 | 对话历史持久化，可按笔记/截图/独立会话分组 | P1 |
| FR-4.7 | Token 用量统计与预估成本（按用户填的单价） | P2 |
| FR-4.8 | 纯本地降级：无 Key / 无网络时，仅 OCR + 规则摘要可用 | P1 |

### 2.5 FR-5 API Key 与模型接入

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-5.1 | 设置页填写：Provider 名称、Base URL、API Key、模型名、能力开关（vision/stream） | P0 |
| FR-5.2 | 内置厂商预设：DeepSeek、OpenAI、通义千问、智谱 GLM、Kimi、Ollama（本地）、自定义 | P0 |
| FR-5.3 | API Key 用 Android Keystore（AES-GCM）加密存储于 EncryptedSharedPreferences | P0 |
| FR-5.4 | Key 不写日志、不进备份（`allowBackup=false` + 备份规则排除）、不上传第三方 | P0 |
| FR-5.5 | 连通性测试按钮（一键发一条 ping 请求验证 Key/模型/网络） | P0 |
| FR-5.6 | 多 Provider 配置可保存，指定默认；不同场景可用不同模型（截图用便宜的 vision、日报用强的文本） | P1 |
| FR-5.7 | 超时/重试/代理设置（自定义 Host、代理端口） | P2 |

### 2.6 FR-6 每日汇报

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-6.1 | 定时任务（默认 21:30，用户可改）生成当日汇报 | P0 |
| FR-6.2 | 汇总内容：当日截图分析条数 + 分类分布、新建/修改笔记、待办完成率、当日日程 | P0 |
| FR-6.3 | AI 生成"今日回顾"叙事 + 明日建议，写入"日报笔记"（一天一条，可重生成） | P0 |
| FR-6.4 | 通知推送可点开直达；支持手动"立即生成" | P0 |
| FR-6.5 | 数据不足时（无记录）不打扰，可配置"仅在有内容时通知" | P1 |
| FR-6.6 | 周报/月报（按同一管道聚合） | P2 |

### 2.7 非功能需求（NFR）

| 类别 | 要求 |
|---|---|
| 性能 | 冷启动 < 1.5s；截图捕获到落库 < 3s（不含 AI 网络耗时）；列表 1000 条滚动 60fps |
| 功耗 | 常驻前台服务空闲时 CPU ≈ 0；不做轮询（事件驱动 + 15min 兜底） |
| 隐私 | 数据全本地；仅图片/文本按需发往用户自配模型服务；提供"一键清除全部 AI 记录" |
| 兼容 | minSdk 26（Android 8.0），targetSdk 35，覆盖 Android 8–16 |
| 体积 | Release APK ≤ 20MB（不开 ML Kit 时）/ ≤ 35MB（含 ML Kit 中文 OCR） |
| 稳定 | 无崩溃会话率 ≥ 99%；关键路径（截图管道、提醒）有日志环形缓冲便于排查 |
| 可维护 | 模块化分层，单元测试覆盖数据层与 AI 解析层 |

---

## 3. 技术选型

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | Kotlin 2.x | 原生唯一正解 |
| UI | Jetpack Compose + Material 3（动态取色） | 开发快、声明式、易做深色/大屏适配 |
| 导航 | Navigation Compose | 官方标准 |
| 架构 | MVVM + Clean 分层（domain/data/ui） | 可测试、可替换 |
| DI | Hilt | 官方，配合 WorkManager/Compose 集成好 |
| 本地库 | Room 2.6+（含 FTS5） | 结构化 + 全文检索一体 |
| 偏好 | DataStore(Proto/Preferences) | 替代 SharedPreferences |
| 密钥 | androidx.security:security-crypto + Keystore | API Key 加密 |
| 网络 | OkHttp 5 + kotlinx.serialization + Retrofit(可选) | SSE 流式需 OkHttp 原生 body streaming |
| 异步 | Coroutines + Flow | 事件流天然契合 |
| 后台 | Foreground Service（dataSync/specialUse）+ WorkManager | 常驻监听 + 兜底周期任务 |
| 提醒 | AlarmManager.setExactAndAllowWhileIdle + NotificationCompat | 准点提醒 |
| OCR | Google ML Kit Text Recognition v2（中文，离线，可选依赖） | 无 vision 模型时兜底 |
| 图片 | Coil（Compose）/ Bitmap + WebP 压缩 | 列表性能 + 降 token |
| 构建 | Gradle 8.x + AGP 8.x + Version Catalog（libs.versions.toml） | 标准 |
| 测试 | JUnit5 + Turbine + Room 内存库 + MockWebServer | 数据层/AI 层可测 |
| 打包 | assembleDebug / assembleRelease + 自签名 keystore | 本机无 Studio，纯 CLI 出包 |

> 注：本机 JDK 为 25。AGP 8.x 官方支持 JDK 17–21，**JDK 25 有较大概率报 "Unsupported class file major version"**。对策：装一个 JDK 17（Temurin）专供 Gradle（`org.gradle.java.home` 指向它），不污染系统环境。这是 M0 环境准备的第一个动作。

---

## 4. 系统架构

### 4.1 分层

```
┌─────────────────────────────────────────────────────────────┐
│  app 层（Compose UI）                                        │
│  截图笔记本 · 笔记 · 备忘录 · 日历 · AI 对话 · 日报 · 设置    │
├─────────────────────────────────────────────────────────────┤
│  domain 层（UseCase / Model，纯 Kotlin，可单测）              │
│  AnalyzeScreenshotUseCase · GenerateDailyReportUseCase       │
│  CreateNoteUseCase · ParseEventFromTextUseCase ...           │
├─────────────────────────────────────────────────────────────┤
│  data 层                                                      │
│  Room(DAO/Entity) · DataStore · KeystoreKeyStore              │
│  ScreenshotRepository · NoteRepository · CalendarRepository   │
│  AiRepository（Provider 抽象 + OpenAI 兼容实现）              │
├─────────────────────────────────────────────────────────────┤
│  service 层                                                   │
│  ScreenshotWatcherService（前台服务 + ContentObserver）       │
│  ScreenshotFallbackWorker（15min 兜底扫描）                   │
│  DailyReportWorker（定时日报）                                │
│  ReminderScheduler（AlarmManager + 通知）                     │
│  BootReceiver / ShareReceiver / AnalyzeTileService            │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 核心数据流：截图 → AI → 笔记本

```
用户按 音量- + 电源
        │
        ▼
系统写入 MediaStore（DCIM/Screenshots/xxx.png）
        │
        ▼  ContentObserver.onChange()  （前台服务常驻）
ScreenshotWatcherService
        │  ① 查 MediaStore 最新 N 条，过滤 RELATIVE_PATH LIKE '%Screenshot%'
        │  ② DATE_ADDED > lastSeenCursor 且 uri 不在已处理集合
        ▼
ScreenshotIngestor
        │  ③ sha256 去重
        │  ④ 复制到 app 私有目录（避免用户删图导致失效）
        │  ⑤ 压缩：长边 1024 / WebP q80 → ≤ ~150KB
        ▼
Insert screenshot_records(status = PENDING)
        │
        ▼  WorkManager 一次性任务（约束：网络 + 策略允许）
AnalyzeScreenshotUseCase
        │  ⑥ 拼提示词（模板 + 用户偏好 + 当前日期）
        │  ⑦ AiRepository.analyzeImage() → 流式/非流式
        │  ⑧ 解析结构化输出（JSON：summary/tags/todos/category）
        ▼
事务：写 notes（notebook = AI 分析笔记本）+ attachments + 回填 record.status = DONE
        │
        ▼
通知（可选，轻提示）+ 列表刷新（Room Flow 自动）
```

### 4.3 关键代码骨架（示意，非最终实现）

**Room 实体（节选）**

```kotlin
@Entity(tableName = "screenshot_records", indices = [Index("sha256", unique = true)])
data class ScreenshotRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val localPath: String,
    val capturedAt: Long,
    val sha256: String,
    val ocrText: String? = null,
    val summary: String? = null,
    val tagsJson: String? = null,
    val category: String? = null,
    val status: AnalyzeStatus = AnalyzeStatus.PENDING,
    val noteId: Long? = null,
    val provider: String? = null,
    val model: String? = null,
    val tokensIn: Int? = null,
    val tokensOut: Int? = null,
    val error: String? = null,
    val retryCount: Int = 0
)
```

**ContentObserver（节选）**

```kotlin
private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        // 系统会连发多次，做 150ms 去抖
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(150)
            ingestNewScreenshots()
        }
    }
}

private fun registerObserver() {
    contentResolver.registerContentObserver(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        true,
        observer
    )
}

private fun ingestNewScreenshots() {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DISPLAY_NAME
    )
    val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection, selection, arrayOf((lastSeenSeconds - 5).toString()), null
    )?.use { c ->
        while (c.moveToNext()) {
            val path = c.getString(1) ?: continue
            if (!path.contains("Screenshot", ignoreCase = true)) continue
            val id = c.getLong(0)
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            ingestor.enqueue(uri)
        }
    }
}
```

**AI 请求（OpenAI 兼容，含图片）**

```json
POST {baseUrl}/chat/completions
Authorization: Bearer {apiKey}
Content-Type: application/json

{
  "model": "{model}",
  "stream": true,
  "temperature": 0.3,
  "max_tokens": 512,
  "messages": [
    { "role": "system", "content": "{prompt.system}" },
    { "role": "user", "content": [
      { "type": "text", "text": "分析这张截图，按 JSON 输出：summary/tags/todos/category" },
      { "type": "image_url", "image_url": { "url": "data:image/webp;base64,{b64}" } }
    ]}
  ]
}
```

**日报聚合（示意结构）**

```json
{
  "date": "2026-02-14",
  "screenshots": { "total": 23, "categories": {"教程": 6, "聊天": 9, "报错": 3, "其它": 5} },
  "notes": { "created": 4, "updated": 11, "topNotebooks": ["工作", "灵感"] },
  "todos": { "total": 7, "done": 5, "pending": 2, "overdue": 1 },
  "events": [ {"title": "与老张开会", "time": "15:00"} ],
  "narrative": "(由 AI 生成，200~400 字)"
}
```

### 4.4 数据表清单（Room，共 13 张）

| # | 表名 | 用途 | 关键字段 |
|---|---|---|---|
| 1 | `notebooks` | 笔记本 | id, name, type(normal/ai_analysis/daily_report), icon, sortOrder, createdAt |
| 2 | `notes` | 笔记 | id, notebookId, title, contentMd, contentText, pinned, archived, deletedAt, sourceType(manual/screenshot/ai), createdAt, updatedAt |
| 3 | `notes_fts` | FTS5 全文索引 | title, contentText |
| 4 | `attachments` | 附件 | id, noteId, uri, mimeType, width, height, sizeBytes, createdAt |
| 5 | `screenshot_records` | 截图流水 | 见 4.3 |
| 6 | `memos` | 备忘录/待办 | id, content, done, priority, dueAt, remindAt, doneAt, createdAt, sortOrder |
| 7 | `calendar_events` | 日程 | id, title, desc, startAt, endAt, allDay, location, color, rrule, sourceType, externalEventId |
| 8 | `reminders` | 提醒 | id, refType(note/memo/event), refId, triggerAt, repeatRule, fired, firedAt |
| 9 | `ai_providers` | 模型配置 | id, name, baseUrl, keyAlias, model, supportsVision, supportsStream, temperature, maxTokens, isDefault, enabled |
| 10 | `ai_conversations` | 会话 | id, scopeType(screenshot/note/memo/global), scopeRefId, title, createdAt |
| 11 | `ai_messages` | 消息 | id, conversationId, role, content, model, provider, tokensIn, tokensOut, createdAt, error |
| 12 | `prompt_templates` | 提示词模板 | id, key, name, systemPrompt, userTemplate, version, enabled |
| 13 | `daily_summaries` | 日报 | id, date(unique), noteId, statsJson, narrative, createdAt |

配套：Room `Migration` 策略（V1 起即建全表，后续版本迁移脚本 + 导出 schema JSON 到 VCS）；数据库启用 WAL；附件与截图副本存 `filesDir/media/`。

---

## 5. 关键风险与对策（Android 平台坑）

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| R1 | **截图监听在部分 ROM 上失效**（MIUI/HyperOS、ColorOS、EMUI 对 MediaStore 增删监听有延迟或限制） | 核心功能不可用 | 三级方案：A=ContentObserver（主）；B=WorkManager 每 15min 扫描兜底（补偿延迟，仅处理遗漏）；C=手动入口（分享菜单 `ACTION_SEND` / 磁贴 / 桌面快捷方式 / 相册分享）。首次启动做"引导用户加自启动白名单 + 关闭电池优化"向导页 |
| R2 | 前台服务长期运行被系统杀 | 监听中断 | `foregroundServiceType="dataSync"` + 常驻低优先级通知 + 用户可关闭；被杀后 `START_STICKY` 重启 + BOOT_COMPLETED 重启 + 兜底 Worker 补偿 |
| R3 | Android 13+ 需 `READ_MEDIA_IMAGES`，14+ 引入 `READ_MEDIA_VISUAL_USER_SELECTED`（部分授权） | 只能读用户选中的图 | 明确走 `READ_MEDIA_IMAGES` 全量授权请求，权限被拒时功能降级为"手动分享收图"，并在 UI 说明 |
| R4 | **Android 14+ 的 ScreenshotDetection API (`registerScreenCaptureCallback`) 只能感知"自己 App 界面被截"** | 误以为能做系统级监听 | 不在架构中使用；仅可选用于"用户截图本 App 时弹窗提示"。避免走这条路浪费时间 |
| R5 | API Key 泄露 | 用户资产损失 | Keystore AES-GCM 加密；`android:allowBackup="false"`；日志过滤器拦截 Bearer；UI 中 Key 掩码显示；提供"清除所有密钥" |
| R6 | AI 输出非结构化，解析失败 | 笔记串行/异常 | 强约束 JSON（`response_format` 若支持 + 提示词约束 + 正则抽取 ```json 代码块）+ 宽松解析器 + 失败降级为纯文本笔记（不丢数据） |
| R7 | 大图 token 成本高（一张 4K 截图可能上千 token） | 用户账单超预期 | 强制压缩：长边 1024 / WebP q80 / 可选 768；提供"低/中/高三档画质"；统计每次用量并在设置页展示累计 |
| R8 | 无网络 / 模型服务 5xx | 分析堆积 | 本地队列 + WorkManager 约束（NetworkType.CONNECTED）+ 指数退避 + 队列页可见 + 手动重试 |
| R9 | 精确闹钟权限（Android 12+ `SCHEDULE_EXACT_ALARM` / 14+ `USE_EXACT_ALARM`）被拒 | 提醒不准时 | 优先申请 `SCHEDULE_EXACT_ALARM`；被拒时降级 `setWindow`/WorkManager，并在设置页引导授权 |
| R10 | 本机 JDK 25 与 AGP 不兼容 / SDK 组件缺失 | 根本构建不起来 | M0 先解决：装 Temurin JDK 17（Gradle 专用）+ `cmdline-tools` + `platforms;android-35` + `build-tools;35.0.0` + `platform-tools`；用 `sdkmanager` 一次性装齐 |
| R11 | 无 Android Studio，全靠 CLI | 调试体验差 | 用 Gradle 命令行 + `adb logcat` + `scrcpy`（本机已有）做真机可视化调试；需要时补装 Studio（可选） |
| R12 | 用户没有可用的多模态模型 | 截图分析无意义 | 内置 ML Kit 中文 OCR → 文本模型链路；OCR 结果可直接落笔记（完全离线可用） |

---

## 6. 页面清单（UI 结构）

```
底部导航：截图 · 笔记 · 备忘 · 日历 · AI
┌── 截图笔记本 (AI 分析)
│    ├ 时间轴列表（按天分组、分类筛选、失败态标记）
│    └ 详情页：原图 + AI 摘要 + 标签 + 提取的待办 + "继续问 AI" + "转笔记/待办"
├── 笔记
│    ├ 笔记本列表 / 笔记列表（置顶、搜索）
│    └ 编辑器：Markdown 工具栏 + 预览切换 + 附件 + AI 动作面板（摘要/续写/润色/翻译/提取待办）
├── 备忘
│    └ 快速输入条 + 列表（勾选、拖拽排序、优先级色带）+ 截止提醒
├── 日历
│    ├ 月视图（事件圆点）+ 日视图（时间轴）
│    └ 事件编辑（含 AI 自然语言输入 → 草稿确认）
├── AI 助手
│    ├ 全局对话（多轮、流式、可中断）
│    ├ 会话历史（可按笔记/截图分组）
│    └ 当前上下文卡片（"正在针对：这张截图 / 这条笔记"）
└── 设置
     ├ AI 接入：Provider 列表 / Key 输入（掩码）/ 连通性测试 / 默认模型选择
     ├ 截图分析：开关、分析策略（Wi-Fi/充电）、画质档、提示词模板编辑
     ├ 每日汇报：时间、通知开关、立即生成、历史日报入口
     ├ 提醒：通知权限、精确闹钟权限、勿扰
     ├ 数据：导出（JSON/Markdown）、导入、清除 AI 记录、清空回收站
     └ 关于与诊断：日志导出、版本、崩溃记录
```

设计原则：Material 3 动态取色、深色模式、大字号无障碍、单手可达（主要操作在下 1/3）、空状态有引导（不是空白页）。

---

## 7. 里程碑与排期

> 估算为"AI 辅助下的净工时"，实际按审批后的节奏推进。每个里程碑都有可验收的产出物。

| 里程碑 | 内容 | 产出物（验收物） | 预估 |
|---|---|---|---|
| **M0 环境与骨架** | 装 JDK17 + Android SDK 组件；建 Gradle 多模块工程；Compose 骨架 + 底部导航；出 Debug APK 装到真机 | 一个能跑起来的空壳 APK（含 5 个空页面） | 0.5 天 |
| **M1 数据层 + 笔记/备忘** | Room 13 表 + DAO + Migration + FTS；笔记 CRUD、笔记本管理、Markdown 编辑、搜索；备忘录 CRUD | 能完整记笔记/待办的可用 App（无 AI） | 2 天 |
| **M2 截图管道** | 权限流；前台服务 + ContentObserver；MediaStore 查询/去重/复制/压缩；兜底 Worker；手动分享入口 | 截图后 App 内出现一条 PENDING 记录（含缩略图） | 1.5 天 |
| **M3 AI 层** | Provider 抽象 + OpenAI 兼容客户端（SSE 流式）；Keystore Key 管理；连通性测试；设置页；提示词模板 | 设置页能填 Key 并测试通过；AI 对话页能流式聊天 | 1.5 天 |
| **M4 截图→AI→笔记本闭环** | 分析 UseCase、JSON 解析器、失败重试、状态回填、详情页、ML Kit OCR 兜底 | 截一张图 → 自动生成一条 AI 分析笔记 | 1.5 天 |
| **M5 日历与提醒** | 月/日视图；事件 CRUD；RRULE 子集；AlarmManager + 通知动作；开机/时区自愈；AI 自然语言建日程 | 建一个 5 分钟后的事件，准点弹通知 | 1.5 天 |
| **M6 每日汇报 + AI 增强** | DailyReportWorker 定时聚合；统计 + AI 叙事；日报笔记本；通知直达；笔记/备忘/日程的 AI 动作面板 | 21:30 收到当日汇报通知并生成日报笔记 | 1 天 |
| **M7 打磨** | 深色模式、空状态、导出/导入、日志与诊断、异常兜底、性能优化、单元测试补全 | 无崩溃、无阻塞 bug 的 RC 版本 | 1 天 |
| **M8 出包与真机验证** | Release 签名、混淆规则、体积检查、真机全流程回归（含低端机） | 可安装的 Release APK + 验收报告 | 0.5 天 |

**合计约 11 个净工作日**（不含等待审批、设备/网络问题等外部阻塞）。

**关键路径**：M0 → M2（风险最高，优先验证）→ M3 → M4。建议 M2 提前做技术验证（先写一个 30 行的 ContentObserver Demo 装机测试），验证通过再铺开，避免后期返工。

---

## 8. 验收标准（Definition of Done）

功能项：
- [ ] 关闭 App 后截图，30 秒内出现分析笔记（Wi-Fi + 有 Key 条件下）
- [ ] 连续截 5 张，5 条记录各自独立、不串行、不丢失
- [ ] 断网截 3 张 → 联网后自动补分析（队列不丢）
- [ ] API Key 填错 → 明确错误提示 + 可重试，不崩溃
- [ ] 无 vision 模型时 OCR 链路可用，离线也能出文字笔记
- [ ] 笔记支持 Markdown 编辑、自动保存、全文搜索命中
- [ ] 备忘录勾选完成 + 截止提醒有效
- [ ] 建日程 → 准点通知 → 动作按钮（稍后/完成）有效
- [ ] 重启手机后提醒不丢、截图监听自动恢复
- [ ] 每日汇报定时生成并可手动重生成
- [ ] 数据库内容用第三方工具可读、结构符合设计（可导出 JSON）

质量项：
- [ ] `assembleRelease` 通过，APK 体积达标，混淆后不崩
- [ ] 无 `StrictMode` 主线程 IO 违规；启动无 ANR
- [ ] 数据层 + AI 解析层单元测试通过
- [ ] 代码无遗留 TODO、无硬编码 Key、无调试日志泄露敏感信息

---

## 9. 目录结构规划

```
E:\note_work\
├── docs\
│   ├── PROJECT_PLAN.md          ← 本文档
│   ├── DECISIONS.md             ← 审批决策记录（审批后生成）
│   └── ARCHITECTURE.md          ← 开工后补充的架构细节
├── app\                          ← 壳模块（导航、主题、DI 聚合）
├── core\
│   ├── common\                   ← 工具、扩展、Result 类型
│   ├── database\                 ← Room 实体/DAO/迁移
│   ├── datastore\                ← 偏好设置
│   ├── designsystem\             ← 主题、通用组件
│   ├── network\                  ← OkHttp 配置、SSE
│   └── security\                 ← Keystore / 加密存储
├── feature\
│   ├── screenshot\               ← 截图笔记本
│   ├── note\                     ← 笔记
│   ├── memo\                     ← 备忘录
│   ├── calendar\                 ← 日历与提醒
│   ├── ai\                       ← AI 对话与动作
│   ├── report\                   ← 每日汇报
│   └── settings\                 ← 设置
├── service\                      ← 前台服务、Worker、Receiver、Tile
├── gradle\libs.versions.toml
├── build.gradle.kts / settings.gradle.kts
└── keystore\                     ← release 签名（gitignore）
```

---

## 10. 待审批决策项（请逐项确认）

| # | 决策项 | 建议默认 | 你的选择 |
|---|---|---|---|
| D1 | 应用名与包名 | 名称 `AiNote`（可中文名"智记"），包名 `com.ainote.app` | |
| D2 | minSdk / targetSdk | 26 / 35 | |
| D3 | 默认 AI Provider | DeepSeek（便宜且中文好，OpenAI 兼容） | |
| D4 | 是否默认开启常驻前台服务监听截图 | 是（首次启动引导授权） | |
| D5 | 数据策略 | 纯本地，无云同步（V1） | |
| D6 | 是否引入 ML Kit 离线 OCR（+约 15MB） | 引入（无 vision 模型时的生命线） | |
| D7 | UI 风格 | Material 3 动态取色 + 深色模式 | |
| D8 | 语言 | 中文单语（V1） | |
| D9 | 是否要系统日历双向同步 | V1 不做，V2 再议 | |
| D10 | 交付形式 | 源码 + 可安装 Release APK（自签名） | |
| D11 | 是否需要我先做 M2 的"截图监听可行性 Demo"再全量开工 | 建议做（30 分钟，规避最大风险） | |
| D12 | 排期节奏 | 按 M0→M8 顺序，每完成一个里程碑给你验收 | |

---

## 11. 开工前一次性确认

审批通过后，我会立刻执行：

1. **M0 环境准备**（无需你操作，除非需要管理员权限装 JDK）
   - 下载安装 Temurin JDK 17（Gradle 专用，不动系统 JDK 25）
   - 用 `sdkmanager` 装：`platform-tools` `platforms;android-35` `build-tools;35.0.0` `cmdline-tools;latest`
   - 生成 `local.properties` 指向 SDK
2. **M0 工程骨架**：Gradle 多模块 + Compose + Hilt + Room，跑通 `assembleDebug`
3. 之后按 M1→M8 顺序推进，每个里程碑结束给你可验证产物

**请在下方"审批意见"处回复确认，或直接指出要修改的决策项。**

---

### 审批意见

- [ ] 同意，按本计划开工（默认项 D1–D12 全部采纳）
- [ ] 同意，但需调整以下项：______________
- [ ] 需修改计划后重新评审：______________

审批人：__________  日期：__________
