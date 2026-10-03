# 聊天滚动与天枢命令面板实施计划

> **面向 agentic worker：** 必需子技能：使用当前会话内联执行本计划并逐项验证。

**目标：** 修复流式思考滚动回跳，并加入能调用天枢工作流与当前技能的原生命令面板。

**架构：** `TianshuClient` 增加会话技能读取；`XuanjiViewModel` 暴露技能状态；`CommandPaletteModel` 保存手机可执行工作流目录和纯函数过滤；`ChatScreen` 负责底部面板和“预填后确认”交互。自动滚动用底部跟随状态门控，避免覆盖用户手势。

**技术栈：** Kotlin、Jetpack Compose Material 3、OkHttp、Kotlin coroutines、JUnit。

## 全局约束

- 不修改 Tianshu Harness 核心代码。
- 不执行删除、格式化、卸载或清理用户文件的操作。
- TUI 专属浮层命令不放入手机端命令面板。
- 命令点选只预填输入框，必须由用户发送确认。

---

### 任务 1：命令目录纯函数

**文件：**
- 创建：`app/src/main/java/com/xuanji/android/ui/chat/CommandPaletteModel.kt`
- 创建：`app/src/test/java/com/xuanji/android/CommandPaletteModelTest.kt`

**接口：** `MobileCommandCatalog.commands`、`filter(query, skills)`、`MobileCommand.inputText()`、`SkillEntry.inputText()`。

- [ ] 写测试：验证空查询返回工作流和技能两组；查询按名称/描述过滤；参数提示生成尾随空格；技能生成 `/skill name `。
- [ ] 运行 `./gradlew.bat :app:testDebugUnitTest --tests com.xuanji.android.CommandPaletteModelTest`，先确认测试因文件不存在失败。
- [ ] 实现最小目录、分类和过滤函数。
- [ ] 再运行同一测试，确认通过。

### 任务 2：技能 HTTP 接口与 ViewModel 状态

**文件：**
- 修改：`app/src/main/java/com/xuanji/android/TianshuClient.kt`
- 修改：`app/src/main/java/com/xuanji/android/XuanjiViewModel.kt`

**接口：** `TianshuClient.listSkills(sessionId): List<SkillEntry>`；`XuanjiViewModel.skills`、`skillsError`、`refreshSkills()`。

- [ ] 解析 `GET /sessions/:id/skills` 的 `{skills:[...]}`，保留 name、description、source、enabled。
- [ ] 会话建立后刷新技能；面板打开时可显式刷新；失败只更新面板错误，不中断聊天订阅。
- [ ] 运行已有单测，确认编译接口。

### 任务 3：滚动跟随修复

**文件：**
- 修改：`app/src/main/java/com/xuanji/android/ui/chat/ChatScreen.kt`

**接口：** `LazyListState` 的底部跟随状态仅用于本页。

- [ ] 删除无条件 `scrollToItem(items.lastIndex)`。
- [ ] 用布局可见项判断是否在底部；用户离开底部时关闭跟随，回到底部时恢复。
- [ ] 自动滚动期间忽略自身滚动事件，并将最新项滚到可见末端。
- [ ] 运行 Gradle 单测并构建，确认 Compose 编译。

### 任务 4：原生命令面板 UI

**文件：**
- 修改：`app/src/main/java/com/xuanji/android/ui/chat/ChatScreen.kt`

**接口：** `/` 按钮、`CommandPaletteSheet`、工作流/技能两类条目点击回调。

- [ ] 输入区增加 `/` 按钮。
- [ ] 使用 `ModalBottomSheet`、搜索框、分组标题、可滚动条目和技能加载错误/重试。
- [ ] 点选只更新 `input` 并关闭面板；不调用 `vm.send`。
- [ ] 确认空状态、键盘和底部安全区布局。

### 任务 5：版本、清单与验证

**文件：**
- 修改：`app/build.gradle.kts`
- 修改：`清单.txt`

- [ ] 将版本递增到 `0.1.4 / versionCode 5`。
- [ ] 更新清单中的聊天 UI、命令面板、滚动修复和测试状态。
- [ ] 运行 `./gradlew.bat :app:testDebugUnitTest`。
- [ ] 运行 `./gradlew.bat :app:assembleDebug`。
- [ ] 检查 APK 路径和包体，给出手机验收步骤。
