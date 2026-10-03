# 聊天滚动与天枢命令面板设计

**日期：** 2026-10-01

## 目标

修复思考卡片展开或流式增长时聊天列表被拉回思考段起点的问题，并在输入区提供原生 Compose `/` 命令面板，让手机端用户发现并调用天枢可经会话 Prompt 接口执行的工作流和当前会话技能。

## 交互

- 聊天列表仅在用户仍停留在底部时跟随新消息和思考增量。
- 用户主动上滑后暂停跟随；回到底部后恢复。自动滚动不会把“用户已离开底部”误判为手动滚动。
- 输入区增加 `/` 按钮，点击打开底部命令面板。
- 面板提供搜索，并按“工作流”“技能”分组。
- 点选工作流或技能只把调用文本填入输入框，不立即执行；用户可补充参数并按发送确认。
- 技能列表来自 `GET /sessions/:id/skills`，只把当前已加载的技能作为可调用项展示。

## 边界

- 不修改 Tianshu Harness 核心；工作流和技能仍通过现有 `POST /sessions/:id/prompt` 及其 slash 解析层执行。
- TUI 专属浮层命令不伪装成手机可执行命令，因此不放入本面板。
- 技能列表失败时面板保留工作流，并显示可重试的错误提示。

## 数据流

```text
Compose / 按钮
  -> ModalBottomSheet 搜索
  -> 选择 MobileCommand 或 TianshuSkill
  -> 输入框预填 /plan ... 或 /skill <name>
  -> XuanjiViewModel.send
  -> TianshuClient POST /sessions/:id/prompt
  -> Tianshu slash resolver / skill loader
```

## 验证

- 纯 Kotlin 单测验证工作流目录过滤、输入文本生成和技能输入生成。
- Gradle 单测、Debug APK 构建。
- 手机验收：思考流中上滑不回跳、回底部恢复；`/` 面板搜索/分组/预填；工作流和技能发送后由天枢执行。
