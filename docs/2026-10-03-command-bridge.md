# 璇玑命令面板修复与验收

版本：0.2.2 / versionCode 31。包内天枢：3.27.0。

## 原因与证据

截图选中了“目录参考”。旧界面使用 `tab != 2` 判断是否允许点击，
因此该页连已接入的 `/model`、`/settings`、`/config` 都被禁用。
另外，静态目录收录了大量 TUI 命令，执行路径却仅覆盖一部分；目录收录
不代表 Android 已实现其全部子命令。

技能接口的旧客户端忽略 `loadErrors`，缺少 `skills` 字段时返回空列表。
项目命令发现错误又和技能错误混用同一个状态。截图中的“0 个技能”
无法仅凭图片确定是空注册表、未启用、恢复时机还是加载失败。

## 社区核对

实施前读取官方仓库信息、社区 issue，并检查本地官方 3.27.0 源码和
APK 运行时压缩包内的编译代码。运行时的路由实际位于
`dist/chunk-DXY5P57D.js`，不能按源码路径猜测缺少接口。

- 官方仓库：https://github.com/huiliyi37/Tianshu-harness
- 技能发现讨论：https://github.com/huiliyi37/Tianshu-harness/issues/100
- 命令输入建议：https://github.com/huiliyi37/Tianshu-harness/issues/244
- 技能安装校验：https://github.com/huiliyi37/Tianshu-harness/issues/207
- Android 社区项目：https://github.com/woaiys3/deepseek-harness-android-app

本次直接复用官方 HTTP 接口；没有复制上述 Android 项目的实现。

## 本次实现

| 操作 | 执行路径 |
| --- | --- |
| 完整目录中的已接入命令 | 与“可用”页共用可执行判断，分类不再改变能力 |
| `/domain`、`/domain list` | GET `/sessions/:id/domains`，打开真实星域选择器 |
| `/domain <key或名称>` | POST `/sessions/:id/domain`，校验确认并重新读取列表 |
| `/plan-mode [on/off/status]` | 读取真实会话状态；更新走 `/plan-mode`；不带参数切换 |
| `/ask [on/off/status]` | 读取真实会话状态；更新走 `/ask-mode`；不带参数切换 |
| `/review off/on/status` | GET/POST `/sessions/:id/review-gate`；保留手动审查工作流 |
| `/fast` | POST `/sessions/:id/zen`，action=skip；如实报告是否发生晋升 |
| 技能列表 | 验证响应结构，保留加载错误、启用状态及刷新中状态 |
| 项目命令 | 单独报告发现失败，避免冒充技能加载错误 |

这些控制命令不会被改写为要求模型模拟操作的长提示词。
切换星域和模式受就绪、忙碌、未确认消息等状态限制；读取列表及状态
可以在运行中进行。旧请求不能覆盖新会话的技能状态。

注意：官方当前版本将 `/domain off` 兼容为 Auto；它不表示关闭星域系统。
欢迎页随机星笺属于品牌展示，和实际选中的会话星域是不同状态。

## 仍未接入

`/capsule` 的注入/摘除、`/zen on/off` 的完整手动开关，以及目录中标为
“未接入”的其他命令仍需实现对应桥接。`/fast` 只跳过当前读专注阶段，
不是 `/zen on` 的替代品。

部分已有手机入口只展示对应信息，例如 `/todo` 支持查看，不支持
`/todo add/done`。本次没有宣称完整天枢命令已全部接入。

## 验证

- 新增的三项回归检查在旧实现上失败，修复后通过。
- `:app:testDebugUnitTest :app:assembleDebug`：181 项测试，0 失败、
  0 错误、0 跳过，APK 构建成功。
- HTTP 测试检查真实请求路径与载荷、确认值不符时拒绝成功、
  技能加载错误和禁用状态保留、响应缺字段不能冒充空列表。
- 定向 Android Lint（API、图标路径、内容描述与翻译检查）完成，0 错误，
  1 条已有 API 30 常量警告；这不等于完成全部 Lint 规则检查。
- APK 签名校验通过，证书 SHA256 与上一构建一致；版本信息已从 APK 核对。
- APK：`app/build/outputs/apk/debug/xuanji-0.2.2-debug.apk`。
  SHA256：`F72D1BD747151AC731F94B441C7A7DE27C38D34251E6548E867FE955E56E61FF`。
- 本轮没有安装、操作或验收手机。UI 点击及本地天枢端到端结果待真机确认。

## 真机验收提示词

将下面内容交给手机测试助手：

```text
请验收璇玑 0.2.2 / versionCode 31 的命令面板。
不要修改用户文件，不执行删除、格式化、卸载、清理或更新运行时。
不要泄露、读取或输出 API Key。

1. 先确认已安装构建的 versionName=0.2.2、versionCode=31，天枢运行中。
2. 打开命令面板，切到“全部命令”。检查 /model、/model list、/settings、
   /config 可以点击并打开对应原生页面，关闭后返回聊天。
3. 在全部命令页点击 /domain，应出现从天枢读取的星域列表和当前勾选。
   在空闲的新会话中选“天枢”，重新打开 /domain 确认勾选保持。
   再选择 Auto 并确认恢复。不要把欢迎页星笺当作真实选中星域。
4. 在聊天框发送 /plan-mode status，记录状态；发 /plan-mode on，
   再发 /plan-mode status，确认开启。随后 /plan-mode off，确认关闭。
5. 用同样步骤检查 /ask status、/ask on、/ask off。
   如初始模式开启，检查结束后恢复初始状态。
6. 发送 /review status，记住初始状态。用 /review off、/review status
   检查关闭，再 /review on、/review status 检查开启，最后恢复初始状态。
7. 空闲时发 /fast，应显示“当前不在读专注阶段，无需跳过”或真实晋升结果。
   不得出现伪装成普通聊天的长提示词。
8. 打开技能页，等待刷新完成，记录启用技能数及所有错误提示；
   若显示 0，请记录完整提示及运行时版本，不要据此宣布技能全部正常。
9. /capsule 等未接入命令应有明确原因，不能以可用命令显示。
10. 发普通消息“请只回复：命令面板回归成功，不要调用工具”。确认聊天正常。

请报告每项结果、错误原文、技能实际数量和最终天枢状态。
```
