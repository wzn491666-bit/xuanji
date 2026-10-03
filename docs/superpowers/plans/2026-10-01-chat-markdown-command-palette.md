# 聊天 Markdown 与官方命令面板实施计划

> **面向 agentic worker：** 必需子技能：使用当前会话内联执行本计划，并逐任务运行验证。步骤使用复选框 (`- [ ]`) 语法进行跟踪。

**目标：** 让璇玑把天枢回复按可读 Markdown 呈现，并让斜杠命令面板覆盖官方目录，同时在聊天中保留用户输入而不展示天枢内部展开的长提示词。

**架构：** 新增纯 Kotlin Markdown block/inline 解析器，Compose 渲染器只负责把解析结果映射为标题、段落、代码块、引用、列表和表格；不引入 WebView 或新的运行时依赖。命令模型从官方 Tianshu `command-catalog.ts` 对齐元数据，移动端区分可通过 `/sessions/:id/prompt` 发送的命令和桌面专用命令。ViewModel 在发送时记录本地原始输入，在收到服务端 `user` 事件时消费对应事件，避免把服务端翻译 prompt 当成用户消息。

**技术栈：** Kotlin、Jetpack Compose Material 3、现有 SSE 客户端、JUnit 4。

## 全局约束

- 不修改 Tianshu Harness 核心代码或 HTTP/SSE 协议。
- 不使用 WebView，不新增 Markdown 运行时依赖。
- 仅把实际由 provider 返回的内容渲染为 Markdown，不生成思考文本。
- 移动端发送的命令保持短输入；技能正文和服务端展开模板不得进入输入框或用户气泡。
- 桌面专用命令必须明确标注为不可从手机发送，不能伪装成可执行命令。
- 保持 `targetSdk = 28` 和 Android API 下限不变。
- 不连接、安装或操作用户手机；手机验收由用户使用 DeepSeek Harness 完成。

---

### 任务 1：Markdown 纯解析模型

**文件：**
- 创建：`app/src/main/java/com/xuanji/android/ui/chat/MarkdownModel.kt`
- 创建：`app/src/test/java/com/xuanji/android/MarkdownModelTest.kt`

**接口：**
- 产出 `sealed interface MarkdownBlock`：`Paragraph(text)`, `Heading(level, text)`, `Code(language, text)`, `Quote(text)`, `BulletList(items)`, `OrderedList(items)`, `Table(headers, rows)`, `Rule`。
- 产出 `sealed interface MarkdownInline`：`Text`, `Strong`, `Emphasis`, `Code`。
- 产出 `MarkdownParser.parse(source: String): List<MarkdownBlock>` 和 `MarkdownParser.inline(source: String): List<MarkdownInline>`。
- 未闭合代码围栏按代码块直到输入末尾处理；空行不生成空段落；表格至少需要分隔行才识别为表格。

- [x] **步骤 1：编写失败测试**

```kotlin
@Test
fun parsesHeadingsEmphasisAndInlineCode() {
    val blocks = MarkdownParser.parse("# 标题\n\n这是 **重点** 和 `id`。")
    assertEquals(MarkdownBlock.Heading(1, "标题"), blocks[0])
    assertEquals(
        listOf(
            MarkdownInline.Text("这是 "),
            MarkdownInline.Strong("重点"),
            MarkdownInline.Text(" 和 "),
            MarkdownInline.Code("id"),
            MarkdownInline.Text("。"),
        ),
        MarkdownParser.inline((blocks[1] as MarkdownBlock.Paragraph).text),
    )
}

@Test
fun parsesTableOnlyWhenSeparatorRowExists() {
    val blocks = MarkdownParser.parse("| 能力 | 结果 |\n| --- | --- |\n| 读屏 | 通过 |")
    assertEquals(MarkdownBlock.Table(listOf("能力", "结果"), listOf(listOf("读屏", "通过"))), blocks.single())
}

@Test
fun parsesCodeQuoteListsAndRule() {
    val blocks = MarkdownParser.parse("> 说明\n\n- 一\n- 二\n\n---\n\n```kotlin\nval x = 1\n```")
    assertEquals(MarkdownBlock.Quote("说明"), blocks[0])
    assertEquals(MarkdownBlock.BulletList(listOf("一", "二")), blocks[1])
    assertEquals(MarkdownBlock.Rule, blocks[2])
    assertEquals(MarkdownBlock.Code("kotlin", "val x = 1"), blocks[3])
}
```

- [x] **步骤 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests com.xuanji.android.MarkdownModelTest
```

预期：因 `MarkdownParser` 和 `MarkdownBlock` 尚不存在而编译失败。

- [x] **步骤 3：实现最小解析器**

逐行扫描 fenced code、表格、标题、引用、连续列表和水平线；普通连续行合并为段落。inline 解析只处理成对的 `**`、`*`、`` ` ``，保留未知标记为普通文本。

- [x] **步骤 4：运行测试确认 GREEN**

运行同一 Gradle 命令，预期 `MarkdownModelTest` 全部通过。

---

### 任务 2：Compose Markdown 消息渲染

**文件：**
- 创建：`app/src/main/java/com/xuanji/android/ui/chat/MarkdownView.kt`
- 修改：`app/src/main/java/com/xuanji/android/ui/chat/ChatScreen.kt:473-558`

**接口：**
- `@Composable fun MarkdownView(text: String, modifier: Modifier = Modifier)`。
- 使用 `MarkdownParser.parse` 生成 block；段落/引用/列表使用内联样式，代码块使用等宽字体和深灰底，表格使用可横向滚动的统一列宽 Row，标题使用 Material 3 typography。
- `Assistant` 和展开后的 `Thinking` 使用 `MarkdownView`；用户消息继续显示原始纯文本；工具参数/结果继续使用等宽文本。

- [x] **步骤 1：替换 assistant/thinking 的裸 Text**

在 `ItemView` 中保留消息容器和 SelectionContainer，只把内容改为 `MarkdownView(item.text)`；`ThinkingCard` 展开内容也改成同一渲染器。

- [x] **步骤 2：实现 block 样式**

代码块至少提供 10.dp 内边距和 `surfaceVariant` 深色对比；表头加轻微背景，单元格保留 8.dp 间距，超宽表格通过 `horizontalScroll(rememberScrollState())` 浏览；长段落不做固定高度裁剪。

- [x] **步骤 3：运行编译验证**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:compileDebugKotlin
```

预期：Compose 编译通过，未引入新依赖。

---

### 任务 3：保留用户原始命令回显

**文件：**
- 创建：`app/src/main/java/com/xuanji/android/UserPromptLedger.kt`
- 创建：`app/src/test/java/com/xuanji/android/UserPromptLedgerTest.kt`
- 修改：`app/src/main/java/com/xuanji/android/XuanjiViewModel.kt:120-220,340-355`

**接口：**
- `internal class UserPromptLedger`：`record(text: String)`, `consumeServerEvent(serverText: String): Boolean`, `clear()`。
- `consumeServerEvent` 在有待确认本地输入时消费服务端事件并返回 `true`；没有待确认输入时返回 `false`，允许回放/恢复事件正常显示。

- [x] **步骤 1：编写失败测试**

```kotlin
@Test
fun expandedServerPromptIsConsumedAfterLocalSlashInput() {
    val ledger = UserPromptLedger()
    ledger.record("/plan 做一个表情包应用")
    assertTrue(ledger.consumeServerEvent("Run planning workflow with the task: 做一个表情包应用"))
    assertFalse(ledger.consumeServerEvent("另一个回放事件"))
}

@Test
fun clearRemovesPendingPrompt() {
    val ledger = UserPromptLedger()
    ledger.record("/review")
    ledger.clear()
    assertFalse(ledger.consumeServerEvent("expanded review prompt"))
}
```

- [x] **步骤 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests com.xuanji.android.UserPromptLedgerTest
```

预期：因 `UserPromptLedger` 尚不存在而编译失败。

- [x] **步骤 3：实现并接入 ViewModel**

`send(text)` 在调用网络前把非空原文加入 `items` 和 ledger；`onEvent("user")` 先调用 ledger，若返回 `true` 则不再追加服务端长 prompt，否则使用 `promptText/text/prompt` 作为回放消息。`newSession()`、设置变更和清空消息时调用 `ledger.clear()`。

- [x] **步骤 4：运行测试确认 GREEN**

运行同一测试命令，预期全部通过，再运行 `:app:testDebugUnitTest` 确认现有 SSE/Cockpit 测试不受影响。

---

### 任务 4：对齐官方命令目录并改造面板

**文件：**
- 修改：`app/src/main/java/com/xuanji/android/ui/chat/CommandPaletteModel.kt`
- 修改：`app/src/main/java/com/xuanji/android/ui/chat/ChatScreen.kt:315-430`
- 修改：`app/src/test/java/com/xuanji/android/CommandPaletteModelTest.kt`

**接口：**
- `MobileCommand` 增加 `group: String`、`mobileExecutable: Boolean`、`argsHint: String`。
- `MobileCommandCatalog.commands` 对齐官方 `COMMAND_CATALOG` 的命令名/描述/参数提示；生态工作流、`/review` 和 `/skill <name>` 标记可发送，其余官方 TUI 命令展示为“桌面专用”。
- `filter(query, skills)` 保持纯函数，返回按官方目录顺序分组的完整结果；可执行条目点击后只插入 `name + " "`，桌面专用条目不可点击并显示说明。

- [x] **步骤 1：扩展失败测试**

```kotlin
@Test
fun officialCatalogContainsCoreAndWorkflowCommands() {
    assertTrue(MobileCommandCatalog.commands.any { it.name == "/help" })
    assertTrue(MobileCommandCatalog.commands.any { it.name == "/starflow" })
    assertTrue(MobileCommandCatalog.commands.any { it.name == "/plan" && it.mobileExecutable })
}

@Test
fun desktopOnlyCommandsAreSearchableButNotSelectable() {
    val result = MobileCommandCatalog.filter("status", emptyList())
    assertEquals(false, result.workflows.single { it.name == "/status" }.mobileExecutable)
}

@Test
fun skillSelectionKeepsCompactInput() {
    assertEquals("/skill qa ", SkillEntry("qa", "", "builtin", true).inputText())
}
```

- [x] **步骤 2：运行测试确认 RED**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests com.xuanji.android.CommandPaletteModelTest
```

预期：因新字段和完整目录断言尚未实现而失败。

- [x] **步骤 3：实现目录和 UI 分组**

从官方 `command-catalog.ts` 对齐命令元数据；面板分组为“常用工作流、计划与协作、审查与验证、会话与模型、权限与调试、桌面专用、已加载技能”。桌面专用行用低对比文字和“桌面专用”标签，不触发 `onSelect`。保留搜索和重试技能加载。

- [x] **步骤 4：运行测试确认 GREEN**

运行命令模型单测，再运行全量单测，预期所有测试通过。

---

### 任务 5：版本、清单与最终验证

**文件：**
- 修改：`app/build.gradle.kts`
- 修改：`$USERPROFILE/projects/清单.txt`

- [x] **步骤 1：递增版本并记录范围**

将版本从 `0.1.7 / versionCode 8` 递增为 `0.1.8 / versionCode 9`，在清单中记录 Markdown 渲染、原始命令回显、官方目录和“桌面专用”标记。

- [x] **步骤 2：运行完整单测和 APK 构建**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

预期：测试 0 失败，生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [x] **步骤 3：核验 APK**

使用 `aapt2 dump badging` 确认 `versionName=0.1.8`、`versionCode=9`；使用 ZIP 列表确认 `assets/runtime/rootfs.tgz` 仍存在；记录 SHA-256。

- [x] **步骤 4：交付手机验收提示词**

提示词要求 DeepSeek Harness 验证：标题/粗体/代码块/表格渲染，发送 `/plan` 后用户气泡只显示原始命令，命令面板搜索 `/starflow` 和 `/status`，其中 `/starflow` 可发送、`/status` 明确显示桌面专用。

---

## 自审

- Markdown 原始符号问题由任务 1-2 覆盖；表格错位由表格 block + 横向滚动覆盖。
- 长命令 prompt 问题由任务 3 覆盖，服务端协议保持不变。
- 命令数量问题由任务 4 覆盖；不能通过手机 HTTP 路由的 TUI 命令不会被伪装成可执行。
- 每个生产行为都有先失败后通过的 JVM 测试；最终用完整 Gradle 命令和 APK 内容验证。
