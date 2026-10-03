# 璇玑第三方来源、版权与许可说明

复盘日期：2026-10-03。此文件明确区分**实际随 APK 分发的组件、改写 / 复用的上游片段、仅用于调研的参考项目、外部服务和非生产素材**。具体物料以 `docs/open-source/inventory/components.json`、`bom.cdx.json` 和完整原始许可证文本为准。

本文不能证明没有侵权，也不替代权利人的许可证。未核实事项列在末尾与发布清单，**公开源码不代表已经取得公开 APK 分发所需的全部条件**。维护者已选择璇玑原创代码采用 Apache-2.0；具体授权以根目录 `LICENSE` 为准，第三方代码、字体和组件始终保留各自许可。

## 1. 实际包含的天枢

| 项目 | 事实 |
| --- | --- |
| 名称 | `tianshu-harness` 3.27.0 |
| 官方仓库 | https://github.com/huiliyi37/Tianshu-harness |
| 官网 / 概念来源 | https://tianshuharness.com/ 与 /docs/concepts |
| 分发方式 | 完整 npm 生产发行包安装在 APK rootfs 的 `/opt/tianshu/node_modules/tianshu-harness` |
| 许可证 | **Apache License 2.0**，从实际包内 `LICENSE` 与 `package.json` 核实 |
| 原版权 | `Copyright 2025-2026 Tianshu Contributors` |
| 完整许可 | `docs/open-source/license-texts/runtime/opt/tianshu/node_modules/tianshu-harness/LICENSE` |

本地上游源码复盘 commit 为 `c2402fe210f402427fda943327ae2b3013cfe5cf`，package.json 为 3.27.0。npm 包与 git commit 的精确发布对应仍需由发行元数据 / 制作者证据确认，不能只凭同一版本号认为字节相同。实际 npm 包 metadata 的 repository 字段使用历史 `Tianshu-Tui` 名称，完整清单原样记录，不静默改写。

璇玑复用天枢 AgentLoop、工具调度、模型协议、会话、计划 / 目标、技能 / 工作流和官方 HTTP / SSE 合约。Android 原生 UI 不是天枢终端 UI 的直接分发副本。

### 上游代码修改说明

璇玑通过自有 Node ESM 加载模块对 Tianshu 3.27.0 的三个 bundle 入口做内存变换：

- `chunk-QL7XQBU6.js`：在 `buildSystemPrompt` 追加 Android 系统环境说明。
- `chunk-DXY5P57D.js`：增加 Android 待办路由、真实状态事件 / 历史恢复 / 写入事务，以及短斜杠命令展示适配。
- `serve-agent-RW6S3C3G.js`：为当前会话暴露真实 TodoStore 与 todo 工具回调。

适配实现位于 `app/src/main/assets/android-runtime/`，保留 upstream compatibility anchor 的归属注释；`TianshuGoals.kt` 中目标驱动提示按上游实际文本对齐。上游磁盘文件未被覆写，但运行代码存在修改，属于必须披露的改动，不应宣传为完全原版运行。对应集成测试检查原始 bundle 的实际行为。

Apache-2.0 要求保留完整许可、适用的原版权 / attribution、修改说明及上游实际存在的 NOTICE 内容。当前实际 Tianshu 包根目录未观察到独立 NOTICE；不能由此推断所有内嵌依赖没有各自 NOTICE。本文件及完整许可目录用于可读归属，不能替代 release 中的完整文件与 obligation 审查。Apache-2.0 不授予商标使用或 Pro 付费功能授权。

## 2. 随 APK 分发的 Linux / Node / native 环境

| 组件 | 打包版本 / 来源 | 许可证据与处理 |
| --- | --- | --- |
| Ubuntu Base arm64 | 24.04.5，Canonical cdimage | 发行版是多个不同许可包的集合，不存在可覆盖全部内容的单一“Ubuntu 许可证” |
| Node.js linux-arm64 | v24.21.0，nodejs.org | Node 本体 MIT；其 `LICENSE` 包括 V8、libuv、OpenSSL、ICU、zlib 等其它声明，必须完整保留 |
| npm 及它的依赖 | 实际 Node 发行包中的 `lib/node_modules/npm` | 按真实 package.json 与 LICENSE / NOTICE 逐项清点，不能只列 npm 本体 |
| PRoot / loader | Termux `proot_5.1.107.95_aarch64.deb` | 已读取 termux/proot 的 GPL v2 文本及 STMicroelectronics 归属；精确版本 header、对应源码 / Termux 补丁集尚需补齐 |
| libtalloc.so.2 | Termux `libtalloc_2.4.3_aarch64.deb` | talloc 来源族为 Samba；应按精确源码 / deb 确认适用 LGPL 和版权，当前不把来源族推断写成法律结论 |
| libandroid-shmem.so | Termux `libandroid-shmem_0.7_aarch64.deb` | termux/libandroid-shmem 当前 LICENSE 为 BSD-3-Clause，版权 Sergii Pylypenko / Fredrik Fornwall；仍须确认对应版本 |
| ripgrep | Ubuntu `ripgrep_14.1.1-1_arm64.deb` | 实际 `/usr/share/doc/ripgrep/copyright` 包含 MIT / Unlicense 与其它文件 BSD 条款 |
| Node 原生 / WASM 依赖 | rootfs 内真实 `.node` / `.wasm` | 包含 ast-grep、tree-sitter、better-sqlite3 等，散列列表不代表内部静态依赖已全部闭环 |

原始版权文本已从本地 rootfs 提取到 `docs/open-source/license-texts/runtime/`；通用 GPL / LGPL / Apache / BSD 等完整条文保存在 `usr/share/common-licenses/` 对应目录。符号链接归属按归档中的目标记录 / 解析，不从开发电脑任意路径读取。

`scripts/build-runtime.sh` 当前下载现成二进制后组装，没有保存每份 Termux deb 的完整 metadata、源码归档、patchset 和编译指令。Ubuntu 中还含 bash、coreutils、grep、tar、glibc、apt、GnuTLS 等带 GPL / LGPL 或其它条件的组件。**只放一个 GitHub 原仓库链接或 MIT LICENSE 不足以证明满足这些二进制分发义务。**

公开 APK 前应提供与实际二进制对应的源码获取方式 / source bundle、许可证、修改说明和必要重建 / relink 材料，按每份 GPL / LGPL 的具体版本和分发方式处理。PRoot 作为独立程序执行、rootfs 与 Android UI 分开等是技术事实，不自动给出整个项目衍生 / 组合许可结论。应用原创代码是否可选宽松许可，应在具体对应关系核查后由维护者决定。

2026-10-03 起 `scripts/build-runtime.sh` 会为实际下载的输入生成 `runtime-inputs.sha256`；`docs/open-source/CORRESPONDING_SOURCE.md` 与 `scripts/collect-corresponding-source.sh` 提供固定入口、收集脚本与发布手册。它们减少人工工作量，但仍不代替逐项许可证、补丁一致性与 Ubuntu 源包复核。

`ripgrep` 直接由 `dpkg-deb -x` 解包，不在 rootfs 的 dpkg 已安装状态表里；它已另按 copyright 文本记录。不能只运行 dpkg 列表就认为 rootfs 物料已列完。

## 3. Android 直接依赖与传递依赖

版本来自 `gradle/libs.versions.toml`，实际打包版本由 Gradle resolved runtime classpath 决定。BOM 可能影响最终版本。清单任务同时导出 release 和当前 debug APK artifacts；debug 专属 UI tooling 也要按实际 debug 分发处理。

| 依赖族 | 用途 | 当前许可族 / 核查依据 |
| --- | --- | --- |
| AndroidX Core / Activity / Lifecycle / Navigation / Compose / Material 3 | 原生 UI、状态与导航 | Apache-2.0；以 resolved POM / 原始项目许可为证据 |
| Compose Material Icons Extended | 控件图标 | Google Material icons 的相关 Apache-2.0 归属，随依赖核查 |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | 语言运行时、并发与 JSON | Apache-2.0 |
| Shizuku API / Provider 13.1.5（含 aidl / shared） | Binder 权限桥接 | MIT；实际 resolved POM 与 RikkaApps/Shizuku-API LICENSE 均已核实，API 与 Shizuku App / 设备 root 授权分开 |
| OkHttp / okhttp-sse 5.5.0 / Okio 传递依赖 | HTTP、SSE、流式 I/O | Apache-2.0 |
| Apache Commons Compress 1.28.0 与其传递依赖 | rootfs tar 解包 | Apache-2.0；保留原 NOTICE |
| commonmark-java 0.30.0 + GFM tables / strikethrough | Markdown AST | BSD-2-Clause |
| jsoup 1.21.2 | 搜索 HTML / RSS 解析 | MIT |
| JUnit 4.13.2 | JVM 测试，仅 test classpath | EPL-1.0，不按生产依赖计入 APK runtime 清单 |
| Gradle Wrapper / AGP / Kotlin 构建插件 | 构建工具与仓库 wrapper | Gradle / AGP / Kotlin 相应 Apache-2.0；构建工具与 APK runtime 分开 |

上述表是归属索引，不替代每个传递依赖的完整条文。机器清单中每个 artifact 有 group / name / version / variant / SHA-256，并保存本地缓存 POM 的许可声明。没有 POM 许可证的条目保持待核实，不自动标为 Apache。原始 artifact 的 META-INF/LICENSE / NOTICE 与上游 metadata 也需核查，POM 字段不是最终许可结论。

## 4. 字体：8 个真实 OFL 字体的改名子集

所有字体在 APK 中实际存在，不是在线字体。其许可证与原版权保存在 `app/src/main/assets/licenses/` 及 `docs/open-source/license-texts/fonts/`。许可均为 SIL Open Font License 1.1，原字体保留自己的 OFL，不能因项目原创代码采用其它许可就改变字体许可。

| 原字体 / 原版权 | 源码来源 | 璇玑资源 / 改名 family |
| --- | --- | --- |
| LXGW WenKai：2021-2026 LXGW；2020 Klee Project Authors | github.com/lxgw/LxgwWenKai，v1.522 | `stellar_wenkai.ttf` / Stellar Verse |
| ZCOOL XiaoWei：2018 The ZCOOL XiaoWei Project Authors | google/fonts `ofl/zcoolxiaowei` | `stellar_xiaowei.ttf` / Stellar Inscription |
| Ma Shan Zheng：2018 The Ma Shan Zheng Project Authors | google/fonts `ofl/mashanzheng` | `stellar_mashan.ttf` / Stellar Brush |
| Noto Serif SC：2012 Google Inc. | google/fonts `ofl/notoserifsc` | `stellar_notoserif.ttf` / Stellar Serif |
| ZCOOL QingKe HuangYou：2018 对应 Project Authors | google/fonts `ofl/zcoolqingkehuangyou` | `stellar_huangyou.ttf` / Stellar Display |
| Long Cang：2018 The Long Cang Project Authors | google/fonts `ofl/longcang` | `stellar_longcang.ttf` / Stellar Flow |
| Zhi Mang Xing：2018 The Zhi Mang Xing Project Authors | google/fonts `ofl/zhimangxing` | `stellar_zhimang.ttf` / Stellar Gesture |
| Liu Jian Mao Cao：2018 The Liu Jian Mao Cao Project Authors | google/fonts `ofl/liujianmaocao` | `stellar_liujian.ttf` / Stellar Cursive |

变更：依据欢迎场景必要字形做子集；保留 copyright / license records；修改 family / PostScript 名称；Noto Serif SC 固定 regular weight。`tools/subset_welcome_font.py` 固定原输入字体 SHA-256，检验字形完整性；输出散列记录在物料清单。字体原输入、授权和子集再生步骤见 `docs/stellar-welcome-font.md`。

子集改名处理 OFL Reserved Font Name 相关条件，保留版权不是可选项。OFL 不限制用字体排版生成的普通文本作品，但字体文件本身继续适用 OFL 条件。FontTools 是开发期处理工具，不是 APK 运行依赖；公开字体处理工具依赖实体时，须另按实际安装版本保留其原始许可，不能由这里推断任意版本许可。

## 5. 局部改编与其它搜索 / 浏览器 / Android 参考项目

下表来源已检查真实社区实现或许可证。它们没有作为 DSH 插件直接装入 APK。当前源树未观察到这些完整库的 vendored 目录或 Gradle 依赖；从 JavaScript / TypeScript API 合约到 Kotlin 原生实现的接口与调度模式有参考关系。由于早期开发缺少完整提交基线，**不能用“没有 vendor 目录”证明零代码片段复制**；发布前对相似片段复核，复制 / 改写的实质片段保留对应 attribution 和许可。

**已明确的局部改编**：`app/src/main/assets/browser/action.js` 的 contenteditable 富文本处理采用 dsh-browser `extensions/dsh-browser/src/content/actions.ts` 第 305–362 行的原生编辑管线（focus、range.selectNodeContents、execCommand insertText / delete），重组到璇玑自己的快照、防失效与审批防护中。归属：**Copyright (c) 2026 Yuxiang Lin，MIT**，审阅 commit `23895d7292495f665e5676d2f174d022bb3c3605`。这属于源码层参考 / 局部改编，不能只称独立设计思想；原 MIT 全文保留于 `license-texts/research/omdsh-dev--dsh-browser/LICENSE`，根 NOTICE 及源码注释应保留归属。agent-browser / browser-use 当前只作能力、契约和状态机对照，不声称借用了它们的具体代码。

| 来源 | 许可 | 参考范围 |
| --- | --- | --- |
| https://github.com/Zhou-Shilin/Aether | GPL-3.0，精确 only / later 需按实际文件确认 | PRoot、本机 Android 运行环境与 linker64 启动方式；不是本项目整体已取得 GPL 豁免的证据 |
| https://github.com/woaiys3/deepseek-harness-android-app | MIT | Android 封装与 harness 本地生命周期方案 |
| https://github.com/liustack/modsearch | MIT | 多引擎路由、Firecrawl、失败转移、结构化证据 |
| https://github.com/DDDMUC/dsh-free-search | MIT | 免费引擎接口、Bing / DDG / SearXNG / AnySearch / Keenable 请求字段 |
| https://github.com/MochiNek0/dsh-web-search-free | MIT | 业务错误码、日期与截断标记 |
| https://github.com/4cya/jupsearch-mcp | MIT | 多提供商聚合、认证、URL 去重 |
| https://github.com/anysearch-ai/anysearch-mcp-server | Apache-2.0 | AnySearch 官方接口 |
| https://github.com/omdsh-dev/dsh-browser | MIT，Copyright (c) 2026 Yuxiang Lin | DOM / 标签页参考及上述富文本算法局部改编；本轮审阅 commit `23895d7292495f665e5676d2f174d022bb3c3605` |
| https://github.com/KevinnZou/compose-webview | Apache-2.0 | Compose WebView 生命周期与导航状态 |
| https://github.com/vercel-labs/agent-browser | Apache-2.0 | 浏览器 ref / frame 身份与快照；审阅 commit `39a74c70d7759d5a6de7a22c04570bb626bbd081` |
| https://github.com/browser-use/browser-use | MIT | 浏览器动作与模型工具接口；审阅 commit `7be96ed8bafa8dfe1eef228b59cf5c884b8b2431` |
| https://github.com/plateaukao/browser（EinkBro） | 未在本次许可证据抓取中核实 | 工具栏 / 标签页交互参考，不声明其代码已复用或许可已确认 |

调研项目原始许可证的本次抓取副本在 `license-texts/research/`，URL、GitHub blob SHA 与文本 SHA-256 在 `inventory/research-license-evidence.json`。抓取 default branch 只能证明当时该许可文件内容，不能证明实际借用代码对应的历史版本。MIT / Apache 项目也不是“无须任何署名”的素材。

## 6. UI 参考、品牌、图片与文字

产品内部界面的交互、层级和密度参考过 Codex、Gemini、Claude 等用户给出的截图 / 视频，及 Refero / Mobbin / assistant-ui 等真实产品参考站。此类截图及品牌不因可访问就成为开源可复制素材；付费图库账号只提供访问，不自动提供再发布权。参考记录见设计文档，截图不被当作本项目新版本实测证据。

生产恒星、轨道、星环、特效和 launcher icon 当前由本项目 Kotlin / 向量资源程序化生成。中转生图服务产生的 `gpt-image-2` / gateway alias `gpt-image-2.5-sunburst` 图片只用于开发比较，当前未在生产 `res/` 或 `assets/` 发现这些位图。别名不是模型官方版本证明，生成服务条款 / 账号的可转授权尚未核实。

`output/imagegen/` 和 `docs/design/references/` 中的开发图片不能跟随整个目录直接公开。发布前应移除第三方产品截图、敏感手机界面、未经确认的生成素材，或单独取得明确授权。用户提供参考图 / 视频的授权仅为这次开发使用，不能据此宣布对全部截图或品牌拥有版权。

欢迎诗句是本项目针对天枢星域编写的表达；星域名称、职责概念与详情来源于天枢官网与核心概念页。应保留事实来源，不声称这些星域概念由璇玑原创；逐字复用上游较长文字仍须作为复制内容处理。当前艺术字体是真实 OFL 字体，不能将它们宣传为本项目原创字体。

## 7. 外部服务与平台

DeepSeek、OpenAI 兼容模型供应商、Tavily、Bing、Firecrawl、Exa、DuckDuckGo、SearXNG 实例、AnySearch、Keenable 及公开 MCP 入口属于远端服务 / API，不是随 APK 分发的开源代码。搜索 SDK 或 MCP 开源许可不等于服务的免费 / 商业使用许可。API Key、额度、访问规则、网站版权 / 数据使用条款均由相应服务确定。

Android 系统 WebView、Android framework、Root 管理器和用户安装的 Shizuku App 是设备侧环境，不随当前 APK 打包；Shizuku API / Provider 是实际依赖。网页内容、下载、用户文件和模型回复归属各自权利人；本项目开源许可证不能改变它们的许可。

## 8. 完整清单与复现

```powershell
.\gradlew.bat -I tools/open-source-dependencies.init.gradle exportOpenSourceDependencies --max-workers=2
python tools/open-source-inventory.py
python tools/fetch-license-evidence.py
python tools/publication-scan.py
```

`open-source-inventory.py` 不联网，不整包解压到开发电脑，按 archive entries 提取原始文本并计算散列；记录 npm 实际 package.json、Node bundled npm、Ubuntu status、ripgrep 额外文本、Gradle classpath、字体、native / WASM 和 bundle source markers。`fetch-license-evidence.py` 只请求公开许可文件；不得向它加入用户密钥。

`inventory/components.json` 为详细证据，`inventory/bom.cdx.json` 是 CycloneDX 1.6 组件清单；`license-texts/` 是可读完整版权 / 许可。嵌入 bundle 的第三方包名称标记不是精确版本证据，源码里的 package-lock / build inputs 仍需补齐。完整文档和许可证应随公开 APK 一起可离线阅读。

## 9. 公开发布前仍未闭环

1. 将维护者选择的 Apache-2.0 落实到原创代码 LICENSE / NOTICE、真实版权主体和贡献流程，核实所有贡献者 / 生成协作代码的授权。不得把该选择外推为第三方字体、GPL / LGPL 包的重新许可。
2. 保存并发布实际 PRoot / loader / talloc / shmem / Ubuntu copyleft 包对应源码、补丁、构建 / relink 信息，确认每个精确版本的二进制义务。
3. 补齐天枢已静态打进 bundle 的第三方版本、全文 notice、原生库与 WASM grammar 归属，审查其 own / vendored license，不只相信 package.json。
4. 按公开版本完整 Gradle resolved dependencies 核对继承 POM、artifact META-INF 以及 Apache NOTICE；debug APK 也必须覆盖 debug 专属依赖。
5. 做原始参考代码比对，特别是 Aether GPL 代码；不将“只是参考”当作免核查声明。
6. 移除 / 授权设计参考截图和生图比较素材，检查手机截图、日志、视频、EXIF、旧 Git 历史是否泄露 Key / 私人数据；文本扫描没有命中不等于媒体安全。
7. 核实品牌和天枢概念文字使用，明确第三方服务条款、隐私数据流与不隶属声明。
8. 每个公开 release 将 APK 散列、SBOM、完整许可证、源码对应资料和设备验收记录固定下来，不能用旧版本文档替代。

以上缺口是明确的公开发布前工作，不是普通免责声明。首次打开中的摘要用于告知来源，不能替代这里列出的完整归属和义务。
