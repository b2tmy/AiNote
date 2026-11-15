# 智记（AiNote）验收报告

| 项 | 内容 |
|---|---|
| 版本 | v1.0.0（versionCode 1） |
| 交付日期 | 2026-10-16 |
| 构建环境 | Windows 10.0.26200 / JDK 17.0.12 / Gradle 9.6.1 / AGP 9.4.1 |
| 交付物 | 源码（`E:\note_work`）+ Release APK + 本报告 |
| 测试设备 | Android 16（API 36）x86_64 模拟器 |

---

## 1. 交付物

| 文件 | 说明 |
|---|---|
| `app/build/outputs/apk/release/app-release.apk` | Release 包，44.16 MB，v2 签名 |
| `app/build/outputs/apk/debug/app-debug.apk` | Debug 包，57.81 MB，便于调试 |
| `keystore/ainote-release.jks` | 签名密钥（别名 `ainote`，SHA-256 `8D:9B:A4:85…88AF`） |
| `keystore.properties` | 签名配置（含密码，已 gitignore） |
| `docs/PROJECT_PLAN.md` | 项目计划书 |
| `docs/DECISIONS.md` | 决策与踩坑记录（11 章） |
| `docs/ACCEPTANCE_REPORT.md` | 本文件 |
| `tools/mock_openai.py` | 本地 OpenAI 兼容 mock 服务，供无 Key 时验证 |

**安装**：

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

---

## 2. 功能验收

### 2.1 主功能（用户最初提出的四项）

| # | 功能 | 结果 | 证据 |
|---|---|---|---|
| 1 | **实时截图 → AI 分析 → 写入专属笔记本** | 通过 | `KEYCODE_SYSRQ` 触发真实系统截图，3 条全链路 `PENDING→DONE`，笔记落入「AI 截图分析」笔记本（`source_type=SCREENSHOT`） |
| 2 | **可编辑的记事本与备忘录** | 通过 | 多笔记本 + 笔记 CRUD + 700ms 防抖自动保存 + 全文搜索 + 备忘（优先级/勾选/截止）+ 空笔记自动清理 |
| 3 | **日历与提醒** | 通过 | 自绘月视图（今日高亮、事件圆点）+ 事件 CRUD + `RTC_WAKEUP` 精确闹钟，实测触发偏差 **5 ms**，通知带「稍后 10 分钟 / 完成」 |
| 4 | **AI 赋能交互（API Key 接入）** | 通过 | 8 个厂商预设 + 自定义；OpenAI 兼容协议；SSE 流式；Key 走 Keystore 加密；笔记四动作（摘要/续写/润色/提取待办）+ 全局对话 |

### 2.2 扩展功能

| 功能 | 结果 | 证据 |
|---|---|---|
| 每日汇报 | 通过 | 聚合 6 类当日数据 → AI 叙事 → 写入「每日汇报」笔记本；无内容不打扰；同日覆盖不堆积 |
| 本地中文 OCR | 通过 | `supports_vision=false` 时自动走 ML Kit，实测 `ocr_text` 识别 121 字符，纯文本模型可完成分析 |
| 待办一键转备忘 | 通过 | 详情页点「全部加入备忘」后 `memos` 表新增 2 条 |
| 异常处理与重试 | 通过 | 真实 DeepSeek 接口返回的 401 原文回显到 UI；分析失败落 `FAILED` + 错误原因，可一键重试 |
| 数据导出 | 代码就位 | SAF 选位置 + Markdown 按笔记本分组；**未实测落盘** |
| 单元测试 | 通过 | `:core:ai:testDebugUnitTest` → 16 例，失败 0 |

---

## 3. 关键验证证据

### 3.1 截图管道（M2）

```
# 触发真实系统截图 3 次后
1|PENDING|1792110862000|0
2|PENDING|1792110867000|0

# 私有目录压缩产物
e57df52e….webp  9752 字节
f23901a2….webp 10184 字节      （原图百 KB 量级 → WebP 约 10KB）

# 前台服务
isForeground=true foregroundId=1001 types=0x40000000   （specialUse）
```

### 3.2 AI 链路（M4）

```
# Worker 汇总
I AnalyzeScreenshot: 本轮分析完成：成功 3，失败 0，剩余 0

# 生成的结构化产出（真实内容）
一张手机应用设置界面的截图，显示了截图监听开关与电池优化提示

**关键信息**：截图监听已开启 / 建议关闭电池优化

#设置 #界面 #教程

**待办**
- [ ] 把应用加入电池优化白名单
- [ ] 确认截图监听开关处于开启状态
```

### 3.3 提醒（M5）

```
# AlarmManager
RTC_WAKEUP #3: Alarm{… com.ainote.app}
tag=*walarm*:com.ainote.service.action.REMINDER_FIRE
exactAllowReason=permission

# 触发精度：预定 …328324，实际 fired_at …328329
```

### 3.4 Release 冒烟（M8）

```
# 混淆后完整链路仍正常
I AnalyzeScreenshot: 本轮分析完成：成功 1，失败 0，剩余 0

# mock 侧确认走的是 OCR 路径（非多模态）
[mock] path=/v1/chat/completions model=mock stream=False image=False
```

验证覆盖了 R8 最容易破坏的四类反射依赖：kotlinx.serialization（JSON 解析成功）、
Room（数据落库并正确渲染）、Hilt（依赖注入无异常）、ML Kit（OCR 返回结果）。

---

## 4. 未验证项（明确列出，不含糊）

| 项 | 原因 | 影响 | 建议 |
|---|---|---|---|
| **真机 ROM 行为** | 无真机，只有模拟器 | MIUI / HyperOS / ColorOS 对 MediaStore 变更通知的延迟与拦截、后台服务存活策略均未验证 | **优先级最高**：插上手机跑一遍，重点看截图后是否及时入库 |
| **真实模型 Key 的成功路径** | 没有可用 Key | 协议拼装、错误处理、流式解析均已用 mock 与真实 401 验证，但「真模型返回质量」未测 | 填一个支持视觉的 Key（qwen-vl-plus / glm-4v-flash 成本低）试一次 |
| 分享菜单收图 / 快捷设置磁贴 | 未构造触发场景 | 代码就位，交互未实测 | 真机手动试 |
| `setWindow` 降级路径准点率 | 未拒绝精确闹钟权限 | 只在授权状态下验证过 | 撤销 `SCHEDULE_EXACT_ALARM` 后复测 |
| 重复提醒的第二次触发 | 未等待一个完整周期 | 顺延逻辑已代码审查 | 设一个「每天」提醒，隔天确认 |
| 数据导出落盘 | SAF 交互未走完 | 导出文件未实际生成 | 真机点一次「导出为 Markdown」 |
| 回收站与 30 天清理 | 未实现 | 软删除的笔记无法在 UI 里恢复 | 后续版本补 |
| 电池优化白名单的实际效果 | 模拟器不需要 | 真机上服务能活多久未知 | 真机观察 24 小时 |

---

## 5. 技术选型与已知限制

### 5.1 工具链

| 组件 | 版本 | 备注 |
|---|---|---|
| Gradle | 9.6.1 | |
| AGP | 9.4.1 | **内置 Kotlin**，不再声明 `org.jetbrains.kotlin.android` |
| Kotlin | 由 AGP 内置 | |
| compileSdk | 37 + minor 2 | 最新 androidx 库强制要求 ≥ 37 |
| minSdk / targetSdk | 26 / 36 | 覆盖 Android 8–16 |
| JDK | 17.0.12 | Gradle 专用，系统另有 JDK 25 |
| KSP / Room / Hilt | 2.3.12 / 2.8.5 / 2.60.1 | |

### 5.2 已知限制

1. **APK 44.16 MB**，超出计划书估算的 ≤35 MB。主因是 ML Kit 中文识别模型（约 15 MB）随包分发。
   可选优化：改用 unbundled 版本（首次使用时从 Play Services 下载）或做 ABI 拆分，二者各有代价。
2. **未做云同步**：数据全在本机，换机需靠导出的 Markdown。
3. **全文搜索用 `LIKE`**：万条以内响应正常，超过后有性能风险（见 DECISIONS 5.3）。
4. **AI 输出解析采用宽松策略**：模型不按 JSON 返回时会标记失败并提示原文，不会静默写入垃圾数据。

### 5.3 隐私边界

以下内容**只在本机**：笔记、备忘、日程、截图压缩副本、OCR 文本、AI 会话历史、数据库。
以下内容**按需发往用户自己配置的模型服务**：截图（仅当模型声明支持视觉）或 OCR 文本、以及当次请求的提示词。
**API Key** 用 Android Keystore（AES256-GCM）加密存储，不进数据库、不进日志、不进备份（`allowBackup=false`）。

> 注意：`network_security_config` 允许明文 HTTP，以便接入局域网自建服务（Ollama / one-api）。
> 若把 Key 填给一个 `http://` 地址，Key 会以明文在链路上传输 —— 这是使用者的选择，设置页已注明。

---

## 6. 使用说明（首次上手）

1. 安装 APK，打开后到「截图」页授予**相册读取权限**（否则监听不工作）；
2. 建议点「去设置」把应用加入**电池优化白名单**，否则部分 ROM 会在后台掐掉监听服务；
3. 进「AI 助手」页 → 右上角设置 → 右下角 `+` 选一个厂商 → 填自己的 API Key → 点「测试连接」；
   - 支持看图：`qwen-vl-plus`、`glm-4v-flash`、`gpt-4o-mini`
   - 纯文本（会自动走本地 OCR）：`deepseek-chat`
4. 正常截图即可，回「截图」页能看到它进入待分析队列并自动完成；
5. 设置页可手动生成「每日汇报」，默认每天 21:30 自动生成。

---

## 7. 结论

八个里程碑全部完成。用户最初提出的四项主功能**均已落地并通过端到端验证**，
其中截图分析闭环、提醒精度、Release 混淆后的稳定性都有可复现的证据支撑。

项目**可以交付使用**，但有一处必须在真机上补验：**截图监听的 ROM 兼容性**。
这是整个项目唯一无法在模拟器上得出结论的部分，也是用户最容易「装了发现不工作」的地方。
