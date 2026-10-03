# Chat UI and Thinking Stream Implementation Plan

> **面向 agentic worker：** 本计划按任务顺序在当前工作区内联执行，并逐步验证。步骤使用复选框 (`- [ ]`) 语法进行跟踪。

**目标：** 在璇玑原生聊天页显示天枢提供的流式 thinking_delta，并提升手机聊天页可读性和操作层级。

**架构：** 增加纯 Kotlin 时间线折叠器，专门合并与结束思考片段；ViewModel 在现有 SSE 消费点调用它，继续沿用 seq 去重。Compose 按消息类型渲染可折叠思考段，并优化现有状态栏、Cockpit、工具卡片、对话气泡和输入区。

**技术栈：** Kotlin、Jetpack Compose、Material 3、JUnit 4、现有 Tianshu SessionEvent SSE。

## 全局约束

- 使用 Kotlin、Jetpack Compose 和原生 Android API。
- 不修改天枢源码或 HTTP/SSE 协议。
- 仅展示 provider 经天枢明确返回的思考内容；不生成或推断思考文本。
- 流事件仍通过 `SessionEvent.seq` 去重并通过现有 SSE `lastSeq` 续传。
- Android 应用 `targetSdk = 28` 必须保持不变。
- 手机端与本地 DeepSeek harness 测试由用户执行；构建成功不代表真机验收成功。

---

## 文件职责

- `app/src/main/java/com/xuanji/android/ChatTimelineReducer.kt`：纯状态变换，追加流式思考文本、忽略阶段/Todo 等元数据分界，并在回答、工具与回合结束事件处结束当前思考片段。
- `app/src/main/java/com/xuanji/android/XuanjiViewModel.kt`：将 `thinking_delta` 事件送入 reducer，并在用户回合、回答、工具或完成事件处结束思考状态。
- `app/src/main/java/com/xuanji/android/ui/chat/ChatScreen.kt`：渲染思考、消息与工具卡片，调整聊天页密度、状态层级和输入区交互。
- `app/src/test/java/com/xuanji/android/ChatTimelineReducerTest.kt`：验证思考增量、空增量、内容分离与边界结束行为。
- `app/src/test/java/com/xuanji/android/SessionEventTest.kt`：验证 SSE `thinking_delta` 的 `{ text }` 负载解析。

### 任务 1：先写思考事件折叠失败用例

**文件：**
- 创建：`app/src/test/java/com/xuanji/android/ChatTimelineReducerTest.kt`
- 修改：`app/src/test/java/com/xuanji/android/SessionEventTest.kt`
- 新增实现文件预期：`app/src/main/java/com/xuanji/android/ChatTimelineReducer.kt`

- [x] **步骤 1：新增 reducer 契约用例**

测试覆盖：两段 delta 合并成同一个 thinking item；thinking 与 assistant 文本保持不同类型且顺序不变；空 delta 不创建行；结束时最后一个 thinking item 变为非流式；阶段/Todo 元数据不切段；后续 thought delta 创建新片段。

```kotlin
@Test
fun appendsThinkingDeltasWithoutMergingAssistantText() {
    val items = listOf<ChatItem>(ChatItem.Assistant("答案"))
    val withThinking = ChatTimelineReducer.appendThinking(items, "思考片段")

    assertEquals(2, withThinking.size)
    assertEquals(ChatItem.Assistant("答案"), withThinking[0])
    assertEquals(ChatItem.Thinking("思考片段", streaming = true), withThinking[1])
}

@Test
fun consecutiveThinkingDeltasAppendToTheActiveItem() {
    val first = ChatTimelineReducer.appendThinking(emptyList(), "先")
    val second = ChatTimelineReducer.appendThinking(first, "后")

    assertEquals(ChatItem.Thinking("先后", streaming = true), second.single())
}

@Test
fun emptyDeltaIsIgnoredAndFinishedThinkingStartsANewItem() {
    val first = ChatTimelineReducer.appendThinking(emptyList(), "第一段")
    val ignored = ChatTimelineReducer.appendThinking(first, "")
    val finished = ChatTimelineReducer.finishThinking(ignored)
    val next = ChatTimelineReducer.appendThinking(finished, "第二段")

    assertEquals(first, ignored)
    assertEquals(ChatItem.Thinking("第一段", streaming = false), next[0])
    assertEquals(ChatItem.Thinking("第二段", streaming = true), next[1])
}
```

- [x] **步骤 2：运行新用例并确认因缺少 reducer/model 而失败**

运行：`./gradlew.bat :app:testDebugUnitTest --tests com.xuanji.android.ChatTimelineReducerTest`

预期：编译失败，报告 `ChatTimelineReducer` 或 `ChatItem.Thinking` 尚不存在。

- [x] **步骤 3：为 SSE thinking 负载增加解析断言**

在 `SessionEventTest.kt` 增加一个用例，解析 `{"seq":13,"type":"thinking_delta","data":{"text":"考虑步骤"}}`，断言 `type == "thinking_delta"` 且 `str("text") == "考虑步骤"`。

### 任务 2：实现时间线 reducer 并接入 ViewModel

**文件：**
- 创建：`app/src/main/java/com/xuanji/android/ChatTimelineReducer.kt`
- 修改：`app/src/main/java/com/xuanji/android/XuanjiViewModel.kt`
- 测试：`app/src/test/java/com/xuanji/android/ChatTimelineReducerTest.kt`
- 测试：`app/src/test/java/com/xuanji/android/SessionEventTest.kt`

**接口：**
- `ChatItem.Thinking(text: String, streaming: Boolean)` 表示单个 provider 思考片段。
- `ChatTimelineReducer.appendThinking(items: List<ChatItem>, delta: String): List<ChatItem>` 忽略空文本，仅把相邻流式 thinking delta 合并到末项，否则新建 thinking 项。
- `ChatTimelineReducer.finishThinking(items: List<ChatItem>): List<ChatItem>` 只将末尾仍在流式的 thinking 项标为完成。

- [x] **步骤 1：新增纯 Kotlin reducer 和模型**

实现 `ChatItem.Thinking` 与上述两个纯函数；`finishThinking` 对空列表、末项非 thinking、已结束 thinking 返回原列表。

- [x] **步骤 2：运行 reducer 和 SSE 单测**

运行：`./gradlew.bat :app:testDebugUnitTest --tests com.xuanji.android.ChatTimelineReducerTest --tests com.xuanji.android.SessionEventTest`

预期：全部 PASS。

- [x] **步骤 3：在 ViewModel 处理思考事件与状态边界**

`onEvent` 收到 `thinking_delta` 时先取 `e.str("text")`；非空时用 reducer 追加。只有 `user`、`text_delta`、`tool_use`、`tool_result`、`turn_complete`、`error` 或 `done` 结束末尾思考片段；`phase`、`todo_state` 等元数据不切段。保留既有 `lastSeq` 过滤逻辑，并保留 `newSession()` 清空 `items` 的行为。

- [x] **步骤 4：再次运行 Android 单测**

运行：`./gradlew.bat :app:testDebugUnitTest`

预期：所有现有与新增单测 PASS。

### 任务 3：优化聊天页与思考区呈现

**文件：**
- 修改：`app/src/main/java/com/xuanji/android/ui/chat/ChatScreen.kt`
- 必要时修改：`app/src/main/java/com/xuanji/android/ui/theme/Theme.kt`
- 修改：`app/build.gradle.kts`

- [x] **步骤 1：为 Thinking 项添加独立可折叠渲染**

思考段留在时间线实际位置；流式时显示 `正在思考` 并默认展开正文，结束时默认收起，点击标题展开/折叠全文。正文使用 `SelectionContainer`，流式和结束状态都不与 Assistant 文本合并。无 thinking 事件时不显示空容器。空会话展示简洁的首条消息提示。

- [x] **步骤 2：调整消息、工具和 Cockpit 层级**

用户消息保留右对齐强调色气泡；Assistant 正文使用清晰左对齐阅读面；工具卡片突出运行、成功和失败状态并保留参数/结果折叠；Cockpit 压缩垂直间距和次要视觉权重，保留目标、Todo、交付状态与折叠行为。

- [x] **步骤 3：调整顶栏状态和输入区**

让璇玑名称、天枢状态与权限/新建按钮易于扫读；输入框支持多行与 IME Send，发送与停止保持至少 48dp 触控区域；处理键盘弹出时的底部 inset。图标继续提供内容描述。

- [x] **步骤 4：验证 debug 构建和完整单测**

运行：`./gradlew.bat testDebugUnitTest assembleDebug`

预期：单测全部 PASS，生成 `app/build/outputs/apk/debug/app-debug.apk`，manifest 版本为 `0.1.3 / versionCode 4`。

### 任务 4：手机验收交接

- [x] **步骤 1：提供 DeepSeek harness 手测提示词**

提示词应要求模型先进行多步分析再回答，但明确只验收 App 展示 provider 实际返回的思考片段；让用户验证思考流式更新、结束后折叠/展开、答案分离，以及无思考响应时没有占位区。

- [x] **步骤 2：如实报告验证边界**

区分 Gradle 单测与 APK 构建证据，明确手机实测由用户执行，不声称已在手机验收。

## 自审

- 规范中的数据流、顺序、回合边界、折叠行为、手机样式、双主题、无 thinking 时空状态、错误生命周期与测试要求均由任务覆盖。
- reducer 接口和模型字段在所有测试与消费任务中一致。
- 不新增依赖、不改服务端协议、不扩大到其他清单子系统。
