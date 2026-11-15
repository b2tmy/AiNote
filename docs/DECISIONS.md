# 技术决策记录（DECISIONS）

> 记录实际落地的版本与踩坑结论，供后续里程碑参考。计划书 `PROJECT_PLAN.md` 是原始设计，本文档是修正后的实施口径。

## 1. 工具链最终组合（M0 验证通过）

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **17.0.12**（`C:\Program Files\Java`） | 已预装，Gradle 专用；系统 PATH 里的 JDK 25 不参与构建 |
| Gradle | **9.6.1** | 本机已有发行包，无需联网下载 |
| AGP | **9.4.1** | 见下方「AGP 9 内置 Kotlin」 |
| Kotlin | 由 AGP 内置 | 不再声明 `org.jetbrains.kotlin.android` |
| KSP | 2.3.12 | Room + Hilt 注解处理 |
| Hilt | 2.60.1 | 该版本要求 AGP ≥ 9.0.0 |
| compileSdk | **37** + `compileSdkMinor = 2` | 对应 `platforms/android-37.2` |
| targetSdk / minSdk | 36 / 26 | |
| Compose BOM | 2026.09.00 | material3 1.4.x |
| Room | 2.8.5 | |
| Android SDK | `E:\Android\Sdk` | `local.properties` 已指向 |

## 2. 踩坑与结论

### 2.1 AGP 9 内置 Kotlin：不能再声明 kotlin-android 插件

AGP 9.0 起内置 Kotlin 支持。若沿用旧写法会直接构建失败：

```
The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
```

处理：
- 从 version catalog、根 `build.gradle.kts`、所有模块 `build.gradle.kts` 中**移除** `org.jetbrains.kotlin.android`
- 不再需要写 `kotlin { compilerOptions { jvmTarget } }`：内置模式下 jvmTarget 默认跟随 `android.compileOptions.targetCompatibility`
- `kapt` 与内置 Kotlin 不兼容（本项目全程用 KSP，无影响）
- 官方文档：<https://developer.android.google.cn/build/migrate-to-built-in-kotlin>

### 2.2 AGP 8.x 与 Gradle 9.6.1 不兼容

Gradle 9.6.0 移除了 AGP 8.x 依赖的内部 API：

```
Plugin 'com.android.internal.application' relies on 'org.gradle.api.problems.internal.InternalProblems',
a Gradle internal API that was removed in Gradle 9.6.0.
```

结论：**AGP 9.x + Gradle 9.6.1** 是唯一可行组合（AGP 8.13.2 在 Gradle 9.6.1 上无法应用）。

### 2.3 Hilt 2.60.1 强制要求 AGP ≥ 9.0.0

```
The Hilt Android Gradle plugin is only compatible with Android Gradle plugin (AGP) version 9.0.0 or higher (found 8.13.2).
```

进一步确认必须走 AGP 9 路线。

### 2.4 最新 androidx 库要求 compileSdk 37

core-ktx 1.19.1、lifecycle 2.11.0、compose ui 1.12.1 等要求编译目标 ≥ API 37：

```
Dependency 'androidx.core:core-ktx:1.19.1' requires libraries and applications that depend on it
to compile against version 37 or later of the Android APIs.
```

处理：安装 `platforms;android-37.2` 并设置 `compileSdk = 37` + `compileSdkMinor = 2`。
（`platforms;android-37.0` / `37.2` 在磁盘上的目录名带次版本号，因此必须写 `compileSdkMinor`。）

> 计划书原定 compileSdk 36 已上调为 37；`targetSdk` 仍保持 36，避免过早引入 API 37 的运行时行为变更。

### 2.5 Compose 图标库不再随 BOM 提供

`androidx.compose.material:material-icons-core` 已停止更新，Compose BOM 2026.09.00 中不再包含。
处理：显式固定版本 `1.7.8`。`Icons.Filled.List` 建议改用 `Icons.AutoMirrored.Filled.List`。

## 3. 本机环境事实

- 无 Android Studio，全部通过 Gradle CLI + adb + 模拟器完成
- `E:\platform\platform-tools`（adb 36）与 `E:\Android\Sdk\platform-tools`（adb 37）并存 → 统一使用 SDK 内那份
- 模拟器 AVD：`ainote_api36`（Pixel 7 / API 36 / google_apis / x86_64）
- 无真机连接；截图监听功能最终需真机验证（模拟器行为不代表 MIUI/HyperOS 等 ROM）

## 4. 命名与常量

- 应用名：智记（`AiNote`）
- applicationId：`com.ainote.app`
- 数据库名：`ainote.db`
- 模块前缀：`com.ainote.core.*` / `com.ainote.feature.*`

---

## 5. M1 实施记录（数据层 + 笔记/备忘）

### 5.1 模块结构（实际落地）

```
app                       UI + 导航（Hilt / Compose）
core:common               调度器、时间格式化、字符串工具
core:designsystem         主题、配色、字体
core:database             Room 12 张表 + 11 个 DAO + 种子数据
core:data                 仓库层（NotebookRepository / NoteRepository / MemoRepository）
```

`core:data` 目前直接返回 Room 实体，未引入独立 domain model —— 个人笔记场景下多一层映射只增加样板，等出现真正的跨源聚合需求再补。

### 5.2 表结构（12 张，均已在 M1 落地）

`notebooks` `notes` `attachments` `screenshot_records` `memos` `calendar_events`
`reminders` `ai_providers` `ai_conversations` `ai_messages` `prompt_templates` `daily_summaries`

### 5.3 全文搜索的取舍

计划书写的是 FTS。实际先用 `LIKE '%q%'`（`NoteDao.search`）：个人笔记量级（万条以内）响应仍在百毫秒级，而 FTS4 对中文需要额外造字级索引、FTS5 又不被 Room 直接支持，收益不划算。
**当单笔记本笔记数超过约 1 万条时再引入 FTS4 + 字符切分索引。** 这是有意识的延后，不是遗漏。

### 5.4 踩坑

- Room `@Query` 里必须写**列名**（`tags_json`），不能写 Kotlin 属性名（`tagsJson`）。KSP 会在编译期报 `no such column`。
- Kotlin 2.4 对 `arrayOf(a, b, null, c)` 这类混合类型推断收紧，`SupportSQLiteDatabase.execSQL` 的参数数组要显式写 `arrayOf<Any?>(...)`。
- `hiltViewModel` 已从 `androidx.hilt.navigation.compose` 迁移到 `androidx.hilt.lifecycle.viewmodel.compose`。
- 种子数据（默认笔记本 + 7 条提示词模板）在建库回调里用原生 SQL 写入，避免在建库时依赖尚未就绪的 DAO 注入。

### 5.5 界面行为约定

- 笔记编辑器 **700ms 防抖自动保存**，离开页面时 `flush()` 立即落盘。
- 标题与正文都为空时视为「从未写过」，返回时物理删除，列表里不会堆"无标题"。
- 备忘勾选完成时会同时撤销该条未触发的提醒，避免做完还弹通知。

### 5.6 模拟器验收手法（可复用）

```powershell
adb shell uiautomator dump /sdcard/ui.xml   # 拿控件真实坐标，别靠肉眼估
adb shell input tap <x> <y>
adb shell input text "Hello%sworld"          # 空格写作 %s
adb shell run-as com.ainote.app sqlite3 /data/data/com.ainote.app/databases/ainote.db "select count(*) from notes;"
```

最后一条能直接查库验证数据真的落盘了，比只看界面可靠。

---

## 6. M2 实施记录（截图管道）

### 6.1 模块

新增 `service` 模块（`com.ainote.service`），承担所有后台组件：

| 文件 | 职责 |
|---|---|
| `ScreenshotWatcherService` | 前台服务 + ContentObserver，实时收图 |
| `ScreenshotFallbackWorker` | 15 分钟兜底扫描，补偿 ROM 拦截或服务被杀 |
| `ScreenshotQuery` | MediaStore 查询（兼容 API 26–37） |
| `ScreenshotIngestor` | 去重 → 压缩 → 落库 |
| `WatcherScheduler` | 统一开关 + 权限判定 + 电池优化判定 |
| `BootReceiver` | 开机 / 应用更新后恢复监听 |
| `ShareReceiverActivity` | 分享菜单收图（保底通道） |
| `ScanTileService` | 快捷设置磁贴，手动触发扫描 |

### 6.2 关键决策：前台服务用 specialUse 而不是 dataSync

Android 15 起 `dataSync` 类型的前台服务有 **6 小时/天** 的累计上限，常驻截图监听会被系统掐掉。
因此声明为 `foregroundServiceType="specialUse"` 并附 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 说明用途。

代价：上架 Google Play 需要申报该类型。本项目本地安装为主，可接受。

### 6.3 三级捕获策略

1. **实时**：`ContentObserver` 监听 `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`，400ms 去抖（系统截图会连发多次 onChange）
2. **兜底**：15 分钟一次 WorkManager 周期扫描，靠 `lastSeenSeconds` 水位线增量补偿
3. **手动**：状态栏刷新按钮 / 快捷设置磁贴 / 分享菜单收图

判定「是不是截图」用的是路径里是否含 `screenshot` 字样，而不是固定目录名 —— 各家 ROM 的截图目录并不统一（Pictures/Screenshots、DCIM/Screenshots、ScreenShot…）。

### 6.4 图片处理参数

长边压到 1024、WebP 有损 q80（可在设置里切 LOW 768/70、HIGH 1536/90）。
实测一张 1080×2400 的手机截图压完约 **10KB**，原图为百 KB 量级，对多模态 token 成本是数量级的削减。

去重用文件流 sha256，落库时有唯一索引兜底并发。

### 6.5 验收证据（模拟器，API 36）

| 验收项 | 结果 |
|---|---|
| 前台服务常驻 | `dumpsys activity services` 显示 `isForeground=true foregroundId=1001 types=0x40000000`（specialUse） |
| 真实系统截图被捕获 | `input keyevent 120`（KEYCODE_SYSRQ）触发 3 次，库里 3 条 PENDING |
| 压缩落盘 | `files/media/screenshots/` 下 2 个 WebP，9752 / 10184 字节 |
| sha256 去重 | 重启 App 触发全量扫描后条数不变（仍为 2） |
| 兜底任务已排 | logcat 出现 `Scheduling work ... ScreenshotFallbackWorker` |
| 权限回收即停 | 撤销 READ_MEDIA_IMAGES 后服务不再常驻；恢复后自动回来 |
| 无崩溃 | logcat 无 FATAL EXCEPTION |

### 6.6 尚未验证的部分（真机才能确认）

- MIUI / HyperOS / ColorOS 对 MediaStore 变更通知的延迟与拦截行为
- 分享菜单收图（`ShareReceiverActivity`）与快捷设置磁贴的实际交互
- 后台被系统清理后的 `START_STICKY` 恢复效果
- 电池优化白名单对服务存活的实际影响

这些都需要插上真机跑一遍，模拟器给不出结论。

---

## 7. M3 实施记录（AI 接入层）

### 7.1 新增模块

| 模块 | 职责 |
|---|---|
| `core:security` | `SecretStore`：API Key 走 Android Keystore（AES256-GCM）+ EncryptedSharedPreferences |
| `core:ai` | OpenAI 兼容客户端（非流式 / SSE 流式 / 连通性测试）+ `AiRepository` |

分层意图：上层（设置页、AI 助手、后续的截图分析）只跟 `AiRepository` 打交道，不直接碰 OkHttp 或 Keystore。

### 7.2 「兼容」的边界

只依赖三件事：`POST {baseUrl}/chat/completions`、`Authorization: Bearer`、SSE `data:` 帧。
DeepSeek / OpenAI / 通义 / 智谱 / Kimi / Ollama / one-api 中转都落在这个契约里，无需逐个适配。

刻意**不引 Retrofit**：SSE 需要自己按行读 body，OkHttp 原生更直接，还省一层依赖。

### 7.3 Key 的处理约定

- 明文 Key 只存在于 Keystore 加密后的存储里；数据库只存别名（`ai_providers.key_alias`）
- 别名按 Provider 名字生成（`apikey_<name>`），重复保存时覆盖而不是堆积
- UI 只提供掩码显示（`sk-xx••••0001`），永不回显完整值
- 日志过滤器不打印 Authorization 头（客户端未接 logging-interceptor，天然没有）

### 7.4 本轮抓到的两个真 bug（真实用户都会踩）

**① edge-to-edge 下漏了 IME insets**
现象：软键盘弹出后输入框被完全遮住，用户看不见自己打的字。
修复：聊天页外层 Column 补 `.imePadding()`。
影响面：所有带输入框且贴屏幕底部的页面。截图页/备忘页的输入框在顶部不受影响，笔记编辑页的正文框同样需要（M7 统一复查）。

**② Android 默认禁止明文 HTTP**
现象：把接口指向 `http://` 的本地服务时，请求被系统静默拦掉，界面只显示「网络不通」，看不出真实原因。
修复：新增 `res/xml/network_security_config.xml`，`cleartextTrafficPermitted="true"`。
取舍：用户接局域网自建服务（Ollama / one-api）通常没有 HTTPS，禁掉明文等于砍功能；面向公网的云厂商 API 本来就是 HTTPS，不受影响。风险是用户把 Key 填给 http 地址时明文上链 —— 这是用户自己的选择，设置页已注明。

### 7.5 验收证据

| 验收项 | 结果 | 依据 |
|---|---|---|
| OpenAI 兼容协议拼装 | 通过 | 指向真实 DeepSeek 接口，服务端返回 401 并在 UI 上显示原始错误：`Authentication Fails, Your api key: ****0001 is invalid (request_id: 258f…)` |
| 服务端错误映射 | 通过 | 401 → 「API Key 无效或无权限」+ 服务端原文 |
| SSE 流式解析 | 通过 | 本地 mock 服务分段发 5 个 chunk，App 端拼接为完整句子并入库 |
| 多轮消息持久化 | 通过 | `ai_messages` 落 `USER` + `ASSISTANT` 两条，assistant 记录带 model 名 |
| Key 加密存储 | 通过 | 数据库只有别名；掩码显示正常；`pm clear` 后 Key 一并失效 |
| 对话 UI | 通过 | 用户/助手气泡、时间戳、模型名、流式追加 |

### 7.6 验证手法：本地 mock 服务

没有真实 Key 的情况下，用 `tools/mock_openai.py` 起一个 OpenAI 兼容的假服务，模拟器通过 `10.0.2.2:8999` 访问宿主机。

好处：**成功路径可以被完整验证**（流式分块、拼接、入库），而不是只能验证错误路径。
这条通路在 M4 分析截图时还能继续复用。

```powershell
python E:\note_work\tools\mock_openai.py
# 模拟器里把 Provider 的 baseUrl 改成 http://10.0.2.2:8999/v1
```

### 7.7 尚未验证

- 真实厂商 Key 的成功路径（结构与 mock 一致，风险低）
- `supportsVision` 打开后图片是否被正确编码为 image_url 多模态 content（M4 会验）
- 长对话的 token 成本控制（当前窗口取最近 20 条）

---

## 8. M4 实施记录（截图 → AI → 笔记本闭环）

### 8.1 闭环结构

```
ScreenshotIngestor  ──(新记录入库)──▶  AnalyzeScheduler.enqueue
                                             │
AnalyzeScreenshotWorker ◀────────────────────┘
   └─ 取 PENDING（每次最多 5 条）
        └─ ScreenshotAnalyzer.analyze(id)
             ├─ 读压缩图 → base64（仅当 provider.supportsVision）
             ├─ 拼提示词（screenshot_analyze 模板 + {date}/{content} 变量）
             ├─ AiRepository.complete（多模态 content parts）
             ├─ ScreenshotAnalysisParser.parse（结构化 JSON）
             ├─ NotebookRepository.requireAiAnalysisNotebook()
             ├─ NoteRepository.create(sourceType = SCREENSHOT)
             └─ ScreenshotRepository.markDone（摘要/标签/笔记指针/token）
```

失败路径同样明确：状态置 `FAILED`，错误原因写进记录，列表和详情页都能看到并一键重试。

### 8.2 结构化输出的解析策略

提示词里写「只输出 JSON」并不保证模型听话。解析器做三步兜底：

1. **括号配平扫描**抠出第一个完整 JSON 对象 —— 正确处理字符串内的 `{}` 与转义，
   因此模型包 ```json 围栏、或在前后加「好的，这是分析结果：」都不影响；
2. 宽松反序列化（`ignoreUnknownKeys` + `isLenient` + `coerceInputValues`）；
3. 所有字段都为空则判定失败，交给上层做降级，而不是写一条空笔记进库。

### 8.3 本轮踩的两个 WorkManager 坑

**① NetworkType 约束导致任务永不执行**
现象：Worker 入队成功，但 logcat 里 `WM-GreedyScheduler: Constraints not met`，
`onBlockedStatusChanged` 直接回调 false，任务被取消且不再尝试。
处理：去掉 WorkManager 的网络约束，改由 Worker 自己用 ConnectivityManager 判断，
无网时返回 `Result.retry()`。行为更可控，也避开了设备侧网络能力判定异常。

**② ExistingWorkPolicy.KEEP 复用了带旧约束的任务**
现象：改完代码重新入队，日志里出现的仍是**同一个 workSpecId**，约束也还是旧的。
根因：KEEP 策略遇到同名任务时会复用它（连同当初的约束），新任务永远排在旧任务后面。
处理：改为 `REPLACE`。Worker 本身幂等（每次都重扫全部 PENDING），替换无副作用。

> 这两条都是真实设备上同样会发生的坑（VPN、多网卡、厂商省电策略都会触发），不是模拟器特有。

### 8.4 验收证据

| 验收项 | 结果 | 依据 |
|---|---|---|
| 自动触发分析 | 通过 | 截图入库后 `AnalyzeScheduler.enqueue` 被调用，Worker 启动 |
| 多模态请求 | 通过 | mock 日志 `image=True` × 3，请求体带 image_url data URI |
| 结构化解析 | 通过 | 摘要 / 标签 / 待办 / 关键信息全部解析入库 |
| 写入 AI 笔记本 | 通过 | `notes.source_type = SCREENSHOT`，`notebook_id = 1`（AI 截图分析） |
| 状态回填 | 通过 | 3 条记录 `PENDING → DONE`，tokens_in=1120 记录在案 |
| Worker 汇总日志 | 通过 | `AnalyzeScreenshot: 本轮分析完成：成功 3，失败 0，剩余 0` |
| 详情页 | 通过 | 原图 / 状态 / 时间 / token / 摘要 / 标签 / 「查看生成的笔记」 |

生成的笔记正文（真实产出）：

```markdown
一张手机应用设置界面的截图，显示了截图监听开关与电池优化提示

**关键信息**：截图监听已开启 / 建议关闭电池优化

#设置 #界面 #教程

**待办**
- [ ] 把应用加入电池优化白名单
- [ ] 确认截图监听开关处于开启状态

---
> 来自截图 · 2026-10-16 00:34
```

### 8.5 M4 未做完的部分（明确留白，不是遗漏）

- **ML Kit 本地 OCR 兜底**：计划里是 P1。目前只用 `DeepSeek` 这类纯文本模型时，
  截图分析会直接失败并提示「当前模型不支持图片理解，且在本地 OCR 也拿不到文字」——
  代码路径已就位（`record.ocrText`），缺的是 OCR 引擎本体（约 +15MB 体积）。
- **待办一键转成备忘**：详情页目前只展示解析出的待办，还不能一键落进 `memos` 表。

两项都排进 M7。

---

## 9. M5 实施记录（日历与提醒）

### 9.1 新增

| 文件 | 职责 |
|---|---|
| `core:data/CalendarRepository` | 日程 CRUD + 提醒的增删改查 |
| `service/ReminderScheduler` | AlarmManager 调度与重排 |
| `service/ReminderReceiver` | 到点触发、通知动作（稍后 / 完成） |
| `service/ReminderNotifications` | 提醒通知渠道与带动作的通知 |
| `app/feature/calendar/*` | 月视图、日列表、事件编辑 |

`PlaceholderScreens.kt` 已删除 —— 五个 Tab 现在全是真实界面，占位页没有存在意义了。

### 9.2 提醒的设计原则：库里是唯一真相

内存里不保留任何调度状态，`reminders` 表就是唯一事实来源。
每次触发读库、每次处理写库，因此「App 被杀 → 状态丢失」类问题天然不存在。

配套的三条规则：

1. **精确闹钟降级**：Android 12+ 用 `canScheduleExactAlarms()` 探测权限，
   拿不到就用 `setWindow`（允许系统延迟）—— 不因为缺权限就彻底不响。
2. **过期提醒作废阈值**：开机时超过 1 小时的过期提醒直接标记已处理。
   否则关机一夜，早上会一口气炸出十几个陈旧通知。
3. **启动即重排**：`AlarmManager` 里的闹钟会随 force-stop、重启、改时间丢失，
   所以 `MainActivity.onCreate` 每次都调 `rescheduleAll()` 重排一遍未触发的提醒。

### 9.3 抓到的 bug：snooze 后 fired 没复位

现象：点「稍后 10 分钟」后，`trigger_at` 正确推后了 10 分钟，但 `fired` 仍是 1。
后果：这条提醒从「未触发」列表里消失，**App 一旦重启就不会被重排**，
     而重启会清掉 AlarmManager，于是这条提醒彻底静默失效。

修复：`ReminderDao.snooze()` 在同一条 UPDATE 里把 `fired = 0, fired_at = NULL` 一起复位。
（原先用的是通用的 `reschedule()`，它只改时间 —— 重复提醒顺延时可以只改时间，但 snooze 不行，两者语义不同，故拆成两个方法。）

### 9.4 验收证据

| 验收项 | 结果 | 依据 |
|---|---|---|
| 月视图渲染 | 通过 | 2026-10 网格对齐正确（1 日为周四），今日高亮，有事件的日期带圆点 |
| 日事件列表 | 通过 | 选中日显示「N 项日程」与事件卡片（标题 + 时间段 + 删除） |
| 闹钟真的挂上了 | 通过 | `dumpsys alarm` 出现 `RTC_WAKEUP … tag=*walarm*:…REMINDER_FIRE`，`exactAllowReason=permission` |
| 准点触发 | 通过 | 预定 `…328324`，实际 `fired_at=…328329`，偏差 5ms |
| 通知动作可用 | 通过 | 通知含「稍后 10 分钟 / 完成」；点稍后后 `trigger_at` 推后 596783ms |
| 精确闹钟权限 | 通过 | `appops set … SCHEDULE_EXACT_ALARM allow` 后走精确路径 |

### 9.5 尚未验证

- 真机 ROM（MIUI / HyperOS）对 AlarmManager 的省电拦截强度
- 用户拒绝 SCHEDULE_EXACT_ALARM 时 `setWindow` 降级路径的实际准点率
- 重复提醒（每天/每周/每月/每年）顺延后的第二次触发
- 关机跨天后 `rescheduleAll` 的作废阈值体验

---

## 10. M6 实施记录（每日汇报）

### 10.1 新增

| 文件 | 职责 |
|---|---|
| `core:ai/DailyReportGenerator` | 聚合当日数据 → AI 叙事 → 写笔记 → 落库 |
| `core:ai/model/DailyStats` | 单日统计快照，含 `toPromptText()` |
| `service/DailyReportWorker` | 定时任务 |
| `service/DailyReportScheduler` | 每天 21:30（可调）的周期调度 |
| `service/DailyReportNotifications` | 日报通知 |
| `app/feature/settings/DailyReportSection` | 设置页里的日报区块 |

### 10.2 核心设计取舍：AI 是增强，不是依赖

没配模型、没网、调用失败时，都会退回**纯统计文本**，保证「今天发生了什么」这件事永远有产出 ——
只有叙事段落的质量会下降。这是刻意的：日报是用户每天都要看的东西，
不应该因为一个外部服务的可用性而整块消失。

配套两条规则：

1. **同一天覆盖而非追加**：反复生成不会在笔记本里堆一串重复条目，靠 `daily_summaries.summary_date` 的唯一索引定位。
2. **空的一天不打扰**：`DailyStats.isEmpty` 为真时直接返回失败，不写笔记、不推通知。
   否则每天深夜都会收到一条「今天你什么都没干」。

### 10.3 聚合口径

| 指标 | 来源 |
|---|---|
| 截图分析条数 / 分类分布 | `screenshot_records` 中 `status='DONE'` 且 `captured_at` 落在当日 |
| 高频标签 | 当日截图 `tags_json` 逗号拆分后计数，取前 6 |
| 笔记新建 / 修改 | `notes.created_at` / `notes.updated_at` 落在当日 |
| 待办新增 / 完成 | `memos.created_at` / `memos.done_at` 落在当日 |
| 日程 | `calendar_events` 与当日区间有交集 |

### 10.4 验收证据

| 验收项 | 结果 | 依据 |
|---|---|---|
| 手动生成 | 通过 | 设置页「现在生成一次」触发，mock 收到 `image=False` 的文本请求 |
| AI 叙事落库 | 通过 | `daily_summaries.narrative` 为模型返回文本 |
| 写入「每日汇报」笔记本 | 通过 | `notes.id=4`，`notebook_id=2`，`source_type=AI` |
| 统计口径正确 | 通过 | `stats_json` 中 `screenshots:3 / categories:{教程:3} / events:["00:58 该喝水了"]` 与实际数据一致 |
| 覆盖而非追加 | 通过 | 重复生成走 `upsert` + 复用同一 `noteId` |
| 定时排定 | 通过 | 界面显示「每天 10-16 21:30」，与 `nextRunAt()` 一致 |

生成的日报正文（真实产出）：

```markdown
今天你主要在处理这个笔记应用本身：几条截图都围绕设置界面，注意力集中在把工具调顺手这件事上。
日程里有一项提醒已经响过。整体节奏偏轻，明天可以挑一条截图里提取出来的待办真正做掉，
比如把应用加进电池优化白名单——那件事拖久了会让截图监听掉线。

---

**当日数据**

- 截图分析：3 条
  - 教程：3
- 笔记：新建 3 · 修改 3
- 待办：新增 0 · 完成 0
- 日程：
  - 00:58 该喝水了

#设置 #界面 #教程
```

### 10.5 M6 未做完的部分（转入 M7）

- **备忘的到点提醒 UI**：数据层与调度路径都通了（`MemoRepository.add` 会插 `reminders`，
  `rescheduleAll` 会排闹钟），但备忘页还没有「设个时间提醒我」的入口，目前只能靠日程提醒。
- **AI 动作面板**：笔记编辑器里的摘要 / 续写 / 润色 / 提取待办还没接。
  `prompt_templates` 里对应的四条模板早已写好（M1 种下的种子数据），缺的只是 UI 与调用。

用户最初提出的四项主功能（截图分析笔记本、记事本备忘、日历提醒、AI 赋能交互）到此**均已落地**，
剩余的是打磨与出包。

---

## 11. M7 实施记录（打磨）

### 11.1 ML Kit 本地中文 OCR

补上了最大的功能缺口：**没有视觉能力的模型也能用截图分析**。

```
ScreenshotAnalyzer
  ├─ provider.supportsVision == true  → 图片 base64 → 多模态请求
  └─ provider.supportsVision == false → OcrEngine 本地识别 → 纯文本请求
                                        （识别结果写回 record.ocr_text，下次不重复跑）
```

代价：APK 约 +15MB（中文识别模型打包进包内，换取完全离线可用）。
引擎懒加载，只用文本模型的用户不会白白付出初始化开销。

**验收**：`supports_vision=0` 时分析成功，`ocr_text` 长度 121，mock 收到的请求为 `image=False`，
结构化解析与 `todos_json` 落库均正常。

### 11.2 待办一键转备忘

- `screenshot_records` 增加 `todos_json`，分析出的待办不再只存在于笔记正文里
- 详情页新增「提取的待办」区块与「全部加入备忘」按钮
- **验收**：点击后 `memos` 表新增 2 条（`把应用加入电池优化白名单` / `确认截图监听开关处于开启状态`）

### 11.3 修掉一个会静默丢数据的 bug：种子数据

**现象**：数据库版本从 2 升到 3 之后，`notebooks` 查出来是 0 ——
默认笔记本（AI 截图分析 / 每日汇报）和 7 条提示词模板全部消失。

**根因**：种子数据原本挂在 `RoomDatabase.Callback.onCreate` 上。
而 `onCreate` **只在数据库「全新创建」时触发**，走 upgrade 路径（包括 destructive migration）时不会调用。

**修复**：
1. 种子逻辑从 `onCreate` 迁到 `onOpen`（每次打开都跑），配合「存在则跳过」的幂等检查；
2. 同时补上显式 `MIGRATION_2_3`（`ALTER TABLE screenshot_records ADD COLUMN todos_json TEXT`），
   以后版本升级保住用户数据，不再依赖 destructive 重建。

**验收**：修复后重启，`notebooks` 恢复为 `1|AI 截图分析 / 2|每日汇报`，`prompt_templates` 7 条。

> 这个 bug 在开发期只是丢了些测试数据，但如果发出去，用户升级一次 App 就会发现
> 自己所有笔记的归属笔记本全没了 —— 属于必须在上线前抓住的那类问题。

### 11.4 IME insets 复查

M3 发现的「键盘遮挡输入框」问题做了全量复查：

| 页面 | 结论 |
|---|---|
| AI 助手（输入框贴底） | M3 已修 |
| 笔记编辑器（正文框贴底） | 补 `.imePadding()` |
| 备忘页（输入框在顶部） | 不受影响 |
| 笔记列表（搜索框在顶部） | 不受影响 |
| 事件编辑 / 提醒选择（ModalBottomSheet） | 框架自带 IME 处理 |

### 11.5 备忘的提醒入口

备忘输入框下方新增「设置提醒时间」入口：今天 / 明天 / 下周 + 时间选择器。
设置后由 `MemoRepository.add(dueAt=…)` 写入 `reminders`，走与日程完全相同的提醒链路。

### 11.6 AI 动作面板

编辑器右上角「AI」按钮 → 摘要 / 续写 / 润色 / 提取待办，各自对应库里的一条提示词模板
（`note_summarize` / `note_continue` / `note_polish` / `note_extract_todos`）。

关键设计：**结果不直接写进正文**。先弹出预览，由用户选择「替换正文」还是「追加到末尾」——
AI 一句话把人家写的东西覆盖掉，是最容易让人卸载应用的那类体验。

**验收**：正文输入后点「摘要」，mock 返回内容正确显示在预览对话框，「替换正文 / 追加到末尾 / 取消」三个动作齐全。

### 11.7 数据导出

设置页「导出数据」→ SAF（`ActivityResultContracts.CreateDocument`）让用户自选保存位置 →
导出一份 Markdown，按笔记本分组、逐条列出标题、更新时间与正文。
数据库文件与 API Key 不参与导出。

### 11.8 单元测试

`core:ai` 下 16 个用例，覆盖两处最容易出错、又最难靠手测覆盖的逻辑：

- `ScreenshotAnalysisParserTest`（10 例）：裸 JSON、代码块围栏、前后带解释文字、字符串内的花括号、
  转义反斜杠、全空字段判定失败、非 JSON 输入、未知字段被忽略、标签数量上限、正文渲染
- `DailyStatsTest`（6 例）：空判定、单项非空、分类分布拼装、省略零值、日程逐条列出

**验收**：`:core:ai:testDebugUnitTest` 总数 16，失败 0。

### 11.9 M7 明确留白

- **回收站清理**：`notes.deleted_at` 软删除已就位，但还没有回收站页面与 30 天自动清理任务。
- **真机回归**：所有验证都在模拟器上完成，ROM 相关行为（MIUI / HyperOS 的省电与自启动策略）
  仍需真机确认 —— 这一点会写进 M8 的验收报告。

---

## 12. M8 实施记录（出包与验收）

### 12.1 签名

`keystore/ainote-release.jks`（RSA 2048，有效期 10000 天，别名 `ainote`），
签名信息从 `keystore.properties` 读取，该文件与整个 `keystore/` 目录都已进 `.gitignore`。

一个细节：`app/build.gradle.kts` 里做了「找不到 keystore.properties 就退回 debug 签名」的兜底，
这样别人 clone 下来不需要密钥也能直接构建。

> 路径踩坑：`keystore.properties` 里的 `storeFile` 不能用 `../keystore/...` ——
> `rootProject.file()` 是相对于项目根解析的，`..` 会直接跳到盘符根目录（`E:\keystore\`），
> 报「Keystore file not found」。写成 `keystore/ainote-release.jks` 才对。

### 12.2 混淆规则

R8 开启（`isMinifyEnabled = true` + `isShrinkResources = true`）。规则集中在 `app/proguard-rules.pro`，
每一段都对应一个「混淆后必崩」的已知模式：

| 段 | 保什么 | 不保会怎样 |
|---|---|---|
| kotlinx.serialization | `Companion` 与 `$$serializer` | 解析模型返回的 JSON 时抛 `SerializationException` |
| Room | `RoomDatabase` 子类、`@Entity`、`@Dao` | 数据库打不开 |
| Hilt / Dagger | `dagger.hilt.**`、`javax.inject.**` | 依赖注入失败，启动即崩 |
| 枚举 | `values()` / `valueOf()` 与 `com.ainote.**` 枚举名 | Room 用枚举 name 存库，名字被混淆后历史数据读不出来 |
| 数据模型 | `core.database.entity.**`、`core.ai.model.**` | 同时进 Room 和序列化的类，整体保留最稳 |
| ML Kit / OkHttp | 各自包名 + `-dontwarn` | OCR 或网络层运行时找不到类 |

### 12.3 体积

| 包 | 体积 |
|---|---|
| Release | **44.16 MB** |
| Debug | 57.81 MB |

超出计划书估算的 ≤35 MB，主因是 ML Kit 中文识别模型（约 15 MB）随包分发。
可选优化（本次未做，理由见下）：改 unbundled 版本（首次使用时下载，但离线不可用）、
ABI 拆分（老设备兼容性下降）。对个人工具类应用而言 44 MB 可接受，优先保离线可用。

### 12.4 Release 冒烟测试

Release 包**不可 `run-as`**（非 debuggable，这是正确行为），因此改用 UI 完整配置 mock 服务来验证。

| 检查项 | 结果 |
|---|---|
| 安装与启动 | 通过，进程存活，无 `FATAL EXCEPTION` |
| 签名校验 | 通过，`apksigner verify` 显示 v2 签名与 keystore SHA-256 一致 |
| 通过 UI 完成 provider 配置 | 通过（改 BaseUrl / 模型名 / Key 后保存成功） |
| 截图监听 | 通过，系统截图被捕获 |
| **AI 链路（混淆风险最高）** | 通过，`AnalyzeScreenshot: 本轮分析完成：成功 1，失败 0，剩余 0` |
| 走的是 OCR 路径 | 通过，mock 侧 `image=False` |
| 数据渲染 | 通过，列表显示「已分析」与摘要 |

这一次冒烟同时覆盖了 R8 最容易破坏的四类反射依赖：
序列化、Room、Hilt、ML Kit。

### 12.5 交付物

见 `docs/ACCEPTANCE_REPORT.md`。核心是 `app/build/outputs/apk/release/app-release.apk`。

### 12.6 验收报告中最重要的一条

报告第 4 节列出了 8 项未验证内容，其中**真机 ROM 的截图监听兼容性**必须在交付后补验 ——
这是整个项目唯一无法在模拟器上得出结论的部分，也是用户最可能「装了发现不工作」的地方。








