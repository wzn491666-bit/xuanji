# 璇玑 · Xuanji

将天枢运行引擎连接到 Android 原生对话、手机工具和浏览器的独立开源项目。

璇玑在手机本地通过 PRoot / Ubuntu / Node.js 运行 `tianshu-harness`，以原生 Kotlin + Jetpack Compose 提供交互，通过本机 HTTP / SSE 与 Android MCP 连接执行链路。模型计算仍由你配置的服务商完成。

## 当前能力

- 原生对话、Markdown 与表格、思考展示、历史会话、附件、模型和思考强度配置。
- 天枢技能 / 工作流入口，以及原生计划、目标、待办等会话控制。
- 文件、终端、Root / Shizuku、无障碍等 Android 工具与审批。
- Tavily、Bing、Firecrawl、Exa、DuckDuckGo、SearXNG、AnySearch、Keenable 多引擎搜索。
- 内置 WebView 浏览器、标签页、分页 DOM 快照、需审批的网页操作。
- Cockpit 任务、工具、事件、资源与执行信息，以及离线项目文档与许可证入口。

这些条目描述已有实现，不表示所有天枢终端命令都已接入、所有手机已验收或浏览器可操作所有网站。具体实现、限制和仍需测试的部分见[技术架构](docs/TECHNICAL_ARCHITECTURE.md)。

## 开发构建

当前支持 Android 8.0+（API 26），仅 `arm64-v8a`；`compileSdk=37`、`targetSdk=28`。较低 target API 是当前私有目录执行运行时的限制，不能据此承诺应用商店上架或全部 OEM 兼容。

安装 Android Studio / Android SDK 37 和 Java 17 或更新的受支持 JDK，使用仓库 Gradle Wrapper：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --max-workers=2
```

```bash
bash gradlew :app:testDebugUnitTest :app:assembleDebug --max-workers=2
```

`local.properties` 仅保存本机 SDK 路径，不应提交。源码测试和编译可以在没有 runtime 资产的情况下进行，但这样产出的 APK **不能启动天枢**。可运行 APK 另需 `app/src/main/assets/runtime/` 中的 PRoot、loader、共享库、`rootfs.tgz` 和 `version`。

现有运行环境组装脚本为 `scripts/build-runtime.sh`，需 Linux / WSL、`curl`、`ar`、`tar`、`dpkg-deb` 和外网。脚本会删除其 `WORK` 暂存目录，运行前应审阅脚本并指定专用空目录：

```bash
WORK="$HOME/xuanji-runtime-build" bash scripts/build-runtime.sh
```

当前脚本下载现成二进制，尚未固定全部输入散列、源码和补丁；重建结果不保证与已有 APK 逐字节相同。完整 APK 的公开分发必须先完成发布清单中的对应源码、许可和素材核查。

## 验证

```bash
python tools/release-check.py --self-test
python tools/release-check.py
bash gradlew :app:testDebugUnitTest --max-workers=2
```

在已准备完整 runtime 资产、安装 Node.js 24 后执行真实 bundle 检查：

```bash
node tools/runtime-prompt-check.mjs
node tools/command-runtime-check.mjs
node tools/todo-runtime-check.mjs
```

公开 CI 验证源码、文档和 JVM 测试，不构造或发布可运行 APK，也不替代手机和 WebView 验收。手机测试应使用测试账户、可丢弃测试数据和明确审批；不要在 Issue 中提交 API Key、认证令牌或未经脱敏的日志。

建立独立项目仓库后，核对实际跟踪文件与完整历史，再检查已审阅的发布清单：

```bash
python tools/release-check.py --tracked --history
python tools/release-check.py --tracked --history --release
```

检查只输出路径、类别和计数，不输出命中的凭据；`--release` 会拒绝仍有未完成项目的发布清单。媒体、二进制内容和第三方许可义务仍需单独核查。

## 文档与开源归属

| 文档 | 内容 |
| --- | --- |
| [技术架构](docs/TECHNICAL_ARCHITECTURE.md) | 原生 UI、运行时、协议、执行链、数据流、构建与能力边界 |
| [第三方来源与许可](THIRD_PARTY_NOTICES.md) | 实际分发物料、上游改动、调研参考、字体与媒体归属 |
| [隐私说明](docs/PRIVACY.md) | 本机存储、可能外发的数据与用户控制 |
| [来源证据](docs/open-source/SOURCES.md) | 社区调研与可复核证据入口 |
| [公开发布清单](docs/open-source/RELEASE_CHECKLIST.md) | 对应源码、静态依赖、素材、隐私与验收待办 |
| [对应源码与构建证据](docs/open-source/CORRESPONDING_SOURCE.md) | GPL / LGPL 组件的源码固定与收集方式 |
| [开源发布手册](docs/open-source/PUBLICATION.md) | 源码仓库与 APK 资产的分步发布流程 |
| [贡献指南](CONTRIBUTING.md) | 构建、提交、DCO 与第三方代码要求 |
| [安全报告](SECURITY.md) | 私密报告、凭据与漏洞处理 |

首次配置页面展示简要来源和数据流摘要，配置页保留完整离线文档入口。仓库原创贡献采用 [Apache-2.0](LICENSE)，归属摘要见 [NOTICE](NOTICE)。第三方代码、字体、运行环境和许可证原文保留各自许可，根许可证不覆盖或替换它们。公开源码与公开 APK 的分发条件必须分别核查。

本项目不代表天枢、Google、OpenAI、Shizuku、Termux 或 Ubuntu 官方产品，不声称获得这些项目的品牌背书或付费功能授权。
