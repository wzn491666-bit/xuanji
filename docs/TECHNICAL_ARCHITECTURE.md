# 璇玑技术实现与维护说明

复盘日期：2026-10-03。本文描述当前源码与实际打包运行环境，功能状态以具体实现和验收证据为准。它不是“全部天枢命令已经完成”的声明。第三方来源、许可证和公开发布前的阻塞事项见 `THIRD_PARTY_NOTICES.md` 与 `docs/open-source/RELEASE_CHECKLIST.md`。

## 1. 项目定位

璇玑是天枢的 Android 原生客户端及本机执行桥梁。Android 应用负责交互、生命周期、凭据配置和手机能力；完整 `tianshu-harness` npm 发行包在本机 Ubuntu 用户空间中运行，承担模型请求、AgentLoop、工具调度、会话、目标、计划和交付检查。它不是将天枢替换成一个普通聊天 API，也没有把上游桌面界面直接装入 WebView。

当前打包版本为 Tianshu 3.27.0、Node.js v24.21.0、Ubuntu Base 24.04.5 arm64。版本来自 `scripts/build-runtime.sh` 和实际 `assets/runtime/version`。Android 版本号应从 `app/build.gradle.kts` 和每次交付 APK 读取，本文不固定 Android 版本，避免同一个文档落后于后续构建。

独立品牌“璇玑”不表示天枢、Google、OpenAI、Shizuku、Termux 或 Ubuntu 的官方产品或授权背书。上游开源授权与云服务账号、商业功能授权是不同事项。

## 2. 总体架构

```text
Android 原生 Compose UI
    │ StateFlow / ViewModel / 生命周期
    ├── TianshuClient ── 本机 HTTP + Bearer + SSE ── tianshu serve
    │                                               │
    │                                       AgentLoop / 模型供应商
    │                                       原生天枢工具与持久会话
    │                                               │ MCP HTTP
    ├── TianshuService ── PRoot ── Ubuntu ── Node 24 ──┘
    │
    └── AndroidMcpServer（127.0.0.1 动态端口、独立令牌）
          ├── 手机文件读写、权限状态
          ├── 无障碍读屏与批准后的操作
          ├── Root / Shizuku id 检查
          ├── 多引擎联网搜索
          └── 原生 WebView 标签页、DOM 快照和批准后的操作
```

天枢 HTTP 与 Android MCP 使用不同令牌。前者当前固定端口 3100，后者绑定 loopback 动态端口。Android WebView 负责真实网站浏览；对话正文和应用文档采用原生 Markdown 渲染，不执行模型返回的 HTML。

## 3. 构建、平台和目录

| 项目 | 当前实现 |
| --- | --- |
| 原生语言 / UI | Kotlin、Jetpack Compose、Material 3 |
| SDK | `minSdk=26`、`compileSdk=37`、`targetSdk=28` |
| ABI | 当前仅 `arm64-v8a` |
| JVM | 编译 Java 17；本地构建使用 Android Studio JBR |
| 构建工具 | Gradle Wrapper；AGP、Kotlin 插件与依赖版本见 `gradle/libs.versions.toml` |
| HTTP / SSE | OkHttp 与 okhttp-sse |
| JSON / 并发 | kotlinx.serialization、kotlinx.coroutines |
| 归档 / HTML / Markdown | Commons Compress、jsoup、commonmark-java + GFM tables / strikethrough |

`targetSdk=28` 是当前私有目录可执行运行方式的技术限制，不是现代 Android 安全能力完全对齐的证明。新版 Android 的安装兼容、存储权限、前台服务、电池管理以及应用商店 target API 政策必须独立验收。当前没有 Google Play 上架能力的证明，x86 模拟器也不在受支持 ABI 内。

| 源码位置 | 职责 |
| --- | --- |
| `MainActivity.kt`、`ui/XuanjiApp.kt` | 原生入口、配置引导、主界面路由 |
| `XuanjiViewModel.kt` | 会话连接、发送与停止、SSE 归并、原生命令、模型配置和工作台状态 |
| `Runtime.kt`、`TianshuService.kt` | 安装本机运行环境、启动天枢、状态通知、进程回收与重启 |
| `TianshuClient.kt` | 天枢 HTTP 协议和请求错误处理 |
| `AndroidMcpServer.kt`、`AndroidMcpProtocol.kt` | Android 工具定义、鉴权、JSON-RPC / MCP 调用 |
| `CommandExecutor.kt`、`ShizukuShellService.kt` | 设备 Root 与 Shizuku Binder 命令后端 |
| `XuanjiAccessibilityService.kt` | Android 系统无障碍节点读取和手势 |
| `search/`、`browser/` | 多引擎搜索、原生网页生命周期与模型浏览器工具 |
| `ui/chat/`、`ui/workspace/`、`ui/theme/` | 对话、工作台、主题与设计控件 |
| `app/src/main/assets/android-runtime/` | 璇玑独立维护的 Node 加载适配模块与环境说明 |
| `tools/` | 原始 bundle 集成检查、字体处理、开源清单与发布扫描 |

## 4. 运行环境安装与服务生命周期

APK 包含 PRoot、loader、libtalloc、libandroid-shmem 以及已组装的 `rootfs.tgz`。第一次启动在应用私有目录安装；再次启动按 `installed-version` 判断复用。安装使用互斥锁、暂存目录、目录交换与取消检查，保存旧 rootfs 的 `/root` 数据。大归档解析和 I/O 不在 UI 主线程进行。

`Runtime.command()` 经 `/system/bin/linker64` 启动 PRoot，绑定 `/dev`、`/proc`、`/sys` 与 `/storage/emulated/0:/sdcard`，设置 `/opt/node/bin` 等 PATH。PRoot 的 `-0` 仅模拟 Linux 用户空间 root 身份，**不会授予手机内核 Root 权限**。真正设备 Root / Shizuku 另走 `CommandExecutor`。

`TianshuService` 是非导出的前台服务，提供常驻通知。启动命令为 `tianshu serve --port 3100`，存在文件权限时工作目录为 `/sdcard`，否则退回 `/root`。服务为每次天枢进程生成随机认证令牌，收集有界日志；异常退出后延迟 5 秒重启。销毁时取消协程并回收自己的进程与 Android MCP 资源。

前台服务和 `START_STICKY` 只能提高存活概率；本文不承诺所有手机锁屏期间永不被系统终止。OEM 电池策略、杀进程、内存不足和冷启动恢复仍需手机验收。Android 日志目前保留最近 500 行，超长单行截断；它不是无限期审计日志。

归档解包检查目标路径，并处理权限、符号链接和硬链接。运行环境来自发行方时也必须验证散列和归档路径；当前构建脚本尚未形成完整的源码、补丁、二进制供应链签名闭环，见发布清单。

## 5. 天枢融合与版本适配

璇玑沿用上游运行引擎及官方 HTTP 路由。原生菜单首先解析斜杠命令，能执行的系统操作通过真实路由或本机原生处理；技能、工作流等模型指令交给天枢解析。聊天短命令展示和实际发送内容分离，避免将整段展开提示词重复塞进消息流。

官方 HTTP 未暴露的能力不能通过“回复一段提示词”假装完成。当前 `/todo` 的 Android 桥接复用真正的会话 TodoStore 和上游 todo 工具；不是另建一个与 AgentLoop 无关的手机清单。

### Node 加载适配

`Runtime.prepareAndroidEnvironment()` 将璇玑自有模块复制到 rootfs 的 `/opt/xuanji`。服务通过 `NODE_OPTIONS=--import=/opt/xuanji/register.mjs` 注册 Node 24 同步 `registerHooks`，在模块加载时变换代码。上游磁盘文件不被覆写，**加载得到的上游代码仍然有修改**，不能据此规避 Apache 的修改说明与保留通知要求。

| 适配位置 | 修改用途 |
| --- | --- |
| `dist/chunk-QL7XQBU6.js` 中 `buildSystemPrompt` | 为主控与子代理追加稳定的 Android 环境系统上下文 |
| `dist/chunk-DXY5P57D.js` | 补 Android 待办路由、事务互斥、确认写入后的 todo_state、历史恢复与短命令展示 |
| `dist/serve-agent-RW6S3C3G.js` | 暴露会话独立待办读写 / 恢复方法并复用上游工具回调 |

这些文件名与函数形状被明确锁定于 **3.27.0**。启动前检查版本与锚点唯一性，发现上游不匹配即报错；不会悄悄跳过环境注入。升级天枢前需要重新读上游源码、更新变换、运行原始 bundle 集成测试。源码中的锚点、目标模式驱动提示等上游片段按 Apache-2.0 归属天枢贡献者。

### Android 环境系统上下文

`android-environment.txt` 描述本机 PRoot / Android 路径、真实设备 Root 与模拟 root 的区别、Android MCP 能力、工具选择、审批、浏览器 / 搜索的外部数据边界等。它进入真正模型请求的 system 部分，同时保留天枢原有规则和模型校准。环境说明不应包含 API Key、会话令牌或用户私人内容；用户项目文档和网页内容也不应被提升为 system 指令。

## 6. 会话、消息与重连

原生界面订阅天枢会话 SSE。事件使用会话 ID、服务器令牌、连接 generation 和 seq 检查，减少切换会话后旧请求覆盖新页面、历史状态反复变回 running 等问题。网络错误保留明确状态，认证 / 会话不存在与普通短暂断流走不同恢复路径；重连退避从 2 秒增长，最高 30 秒。

消息分为用户、模型正文、模型返回的 thinking、工具调用 / 结果与审批。`ChatTimelineReducer` 合并 thinking_delta，工具和回合边界结束当前流。`ChatPresentation` 组织每轮执行过程，不改变原始事件顺序。仅显示供应商真实返回的思考字段；未返回时不能编造“思考过程”。

发送使用客户端 requestId 与服务器 run ledger。HTTP 接受、拒绝、结果不明与实际 SSE 到达是不同状态；结果不明时保留请求 ID 重试，避免重复执行。`UserPromptLedger` 消除本地短命令与服务器展开命令的重复展示。停止按钮调用真实中止接口，同时处理停止中的控件状态。

历史保存由天枢负责。欢迎诗句只影响视觉场景，其关联保存在 Android preferences，不能改变真实运行星域。上下文裁剪 / 压缩、服务重新创建和恢复会话是不同事件，不应以恢复页面成功证明完整历史永远不丢失。

## 7. 目标、计划与待办

| 能力 | 技术实现与边界 |
| --- | --- |
| 目标 | Kotlin 解析预算、最大轮数、验收判据；调用官方 goal 路由，之后发送上游真实目标驱动提示。仅创建目标 tracker 不会自动启动模型 |
| 目标恢复 | 官方 resume + 真正模型驱动，复用发送幂等链路；pause / cancel 停止自主继续，不等同于中止当前轮 |
| 判据 | 支持读取；3.27.0 官方 HTTP 尚未提供现有目标的 criteria set，明确提示能力边界 |
| 计划 | 真实目录、全文查看、编辑保存、批准与反馈；批准由上游启动，客户端不重复启动；查看取消自动批准倒计时 |
| 待办 | `/todo` 与原生面板对接会话独立 TodoStore；全表版本 SHA-256 防过期写入；执行中与待办修改互斥 |
| 待办恢复 | 从已确认成功的历史写入 / authoritative todo_state 恢复；不复用进程全局默认 TodoStore，不把错误写入或过期乐观事件当成当前真值 |

待办路由位于 `/sessions/:id/xuanji/todos`，继承上游 Bearer 鉴权。写入先校验操作、唯一 ID、条目状态与 expectedRevision，调用上游 todo.execute 及真实 plan trace 回调，再确认持久化。条目重排或 skip 是待办清单更新，不表示删除用户文件。

星域胶囊、技能完整管理、禅模式所有阶段、信任 / 授权配置及其它只在 TUI 存在的命令仍需逐项桥接与验收。命令目录不等于命令执行覆盖率；当前详细进度记录见 `docs/2026-10-03-command-bridge.md` 和开发计划。

## 8. Android 工具与权限执行

Android MCP 实现 loopback-only、令牌鉴权的 Streamable HTTP JSON-RPC；当前响应为 JSON，不提供 GET SSE。它限制请求头、请求体和连接处理资源。每个工具返回真实结果 / 明确错误，不将权限状态当作执行成功。

当前 Android 工具包括 `android_read_screen`、`android_list_files`、`android_read_file`、`android_write_file`、`android_shell_id`、`android_permission_status`、`android_accessibility_action`、`android_web_search` 和浏览器 tabs / open / control / snapshot / action。

手机文件工具用 canonical path 约束到 `/sdcard`，读取有大小上限；写入由本地审批确认，使用临时文件及完整替换，避免中途写坏现有文件。工作台文件预览保持只读。天枢自带 Linux shell 具有其自身审批机制和访问边界，不能声称 Android 文件工具边界约束了所有上游 shell。

无障碍服务需要用户在 Android 设置中手动开启，读取真实节点。可点击语义结合节点自身 clickable、ACTION_CLICK 与最近可点击祖先，处理 Compose 文字 / 点击语义分层。手势通过系统无障碍 API 执行，并经过本机审批。权限授予本身不是用户授权任意后续破坏性操作。

Root 使用用户实际提供的 su 环境；Shizuku 使用实际 Binder 服务 / UserService，指定 `processNameSuffix`。Root 模式、Shizuku root 模式与 Shizuku adb shell 模式的 UID 不相同，应按 `id` 实际输出判断。璇玑不会自动给 Shizuku 授予 KernelSU 权限，也不能替用户开启无线调试。

## 9. 多引擎搜索

原生 HTTP 适配器支持 Tavily、Bing RSS、Firecrawl、Exa、DuckDuckGo HTML、SearXNG、AnySearch、Keenable。社区插件是接口与调度参考，没有将 DSH 插件直接安装成天枢插件。用户在对话页配置引擎池、多选 / 自动选择和整体深度。

| 深度 | 每引擎结果上限 | 汇总结果上限 | 摘录字符上限 | 自动模式成功源目标 |
| --- | --- | --- | --- | --- |
| 轻量 | 3 | 8 | 350 | 1 |
| 均衡 | 6 | 16 | 650 | 2 |
| 深入 | 10 | 24 | 1000 | 3 |

手动多选并发调用每个选择的引擎，失败独立记录；自动模式继续尝试候选直到满足目标或耗尽池 / 预算。URL 标准化去掉片段与常见追踪参数，用倒数排名融合合并来源，保留各引擎错误、耗时、空结果和截断标记。支持取消实际网络请求。

Exa / Keenable 在未配置 Key 时有社区公开 MCP 入口；公共入口的免费配额、稳定性、条款和持续可用性不由璇玑保证。SearXNG 需用户指定实例。Bing RSS / DuckDuckGo 网页接口也可能变动或出现验证码。开源客户端不能授予商业搜索服务的使用权或豁免其服务条款。

查询、摘要请求等会发送到选中的外部服务；返回结果标记为不可信内容。搜索密钥使用独立 Android Keystore-backed AES-GCM 保存，并在错误 / 日志输出中脱敏。所选引擎不是所选模型，模型选择与搜索路由是不同配置。

## 10. 内置浏览器

浏览器使用 Android 系统 WebView 和原生 Activity，不另打包桌面 Chromium。标签页 WebView 在应用进程内管理，当前最多 8 页；导航、选中、销毁和 JS evaluate 都在 Android 主线程。Activity detach 更换 context 并暂停页面，防止长期持有销毁 Activity。

真实浏览器支持地址输入、导航、后退 / 前进、重载、进度、错误、标题、文件选择与网页对话框。模型工具读取 DOM 语义快照，以快照 ID / 元素编号执行批准后的操作。快照和导航 generation 绑定；页面导航、关闭或过期目标要求重新读取。隐藏、禁用、被遮挡或无效目标会返回明确失败。

本轮原生工具增强的精确接口如下，手机原生生命周期尚待用户验收：

| 工具 | 参数 / 动作 | 真实行为与约束 |
| --- | --- | --- |
| `android_browser_control` | `tabId`、`action`、`confirm=true`；action 为 navigate / back / forward / reload / stop / activate / close | 操作指定标签页；仅 navigate 接受 `url`，只允许无内嵌凭据的 HTTP / HTTPS；其它动作携带 url 会拒绝 |
| `android_browser_snapshot` | 可选 `tabId`、`textOffset=0..500000`、`maxChars=1000..24000`（默认 12000）、`viewportOnly`、`region`（1..512 的 CSS selector） | 分页 / 区域 / 视口读取，返回 page 的 nextOffset / hasMore / scanLimited、viewport 和下拉 options；不会把截断正文当成完整网页 |
| `android_browser_action` | `tabId`、`snapshotId`、`action`、`confirm`；click / type / select / check / submit / scroll | 元素编号 1..100；整页 scroll 可不传编号，其它动作必须指定 |
| type / select | text 最多 16384；value 最多 512；optionBy 为 value / label | 支持普通输入与 contenteditable 替换、单选下拉；敏感输入与多选仍需用户处理 |
| check / submit / scroll | checked 布尔；direction 为 up / down / left / right / top / bottom，amount 可选 1..10000 | check 设置真实勾选；submit 使用 requestSubmit 保留原生表单验证；滚动可指定容器 |

执行网页变更后应重新 snapshot，不能复用旧编号猜测 DOM。标签页只在应用进程内保留，尚未实现 saveState / 进程死亡恢复。没有任意 JS eval、桌面 Chromium / CDP 或全权上传工具；跨域 iframe / shadow DOM 尚不读取，用户选择文件与权限保持原生交互。

安全设置禁用 file / content / universal file access，没有注入 `addJavascriptInterface` 原生桥；禁止混合内容，启用 Safe Browsing，SSL 错误取消，不静默放行。HTTP / HTTPS 的网络访问仍受用户导航、站点和 Android 设备环境约束。浏览器本身能保持站点 cookie / 存储；这不是将登录信息安全导出给模型的能力。

“完美控制浏览器”尚不是已证明状态。基础 DOM 合约和桌面页面脚本测试不能替代 Android WebView 真机验收；跨域 iframe、复杂富文本、下载 / 上传、多窗口与平台能力仍需按实际工具实现逐项验证。本轮增强应在代码完成后同步到此处，不以调研或计划当作已发布功能。

## 11. 原生 UI 与性能策略

对话采用明亮底色、紧凑胶囊输入区、原生历史侧栏、顶部模型 / 思考控制和分组执行过程。Markdown 由 CommonMark AST 转换为原生文本、代码、列表和横向表格，行内 code 使用灰底；HTML 原样文字显示。图片 Markdown 当前显示说明 / 链接，不能声称已支持全部富媒体或数学排版。

### 思考区与真实强度控制

思考展示采用两层独立状态：`ExecutionBlock` 控制整轮执行过程，`ThinkingSection` 控制其中每个思考子段。打开总栏不会强制打开所有子段，子段选择在总栏关闭后再打开时仍保留。设置中的“展开思考总览”默认展开总栏，未单独展开的子段仍保留预览；“半折叠”在总栏显示最新思考片段；“全部折叠”默认隐藏正文。用户可以分别展开总栏和具体子段查看全文。

预览采用最新文本尾部的约四行视口，最多取末尾 8192 字符，在后台解析为纯文本，避免每个 token 都测量完整思考历史。预览及展开的思考正文采用 `13sp` 字号、`21sp` 行高，展开不会变成普通回复的大字号。总览首次打开前准备子段预览与阶段正文；单段全文也先完成解析，再由本次点击启动约 200ms 的高度过渡，并固定本次目标高度，避免空正文突然撑高。后续文本通过串行、合并输入的后台解析更新，保留上次可见正文，不在每个 token 上取消解析或重启展开时钟。用户主动浏览历史时不会被每个流式 token 强行拉回思考起点。各面板独立保存滚动状态，工作台 Dialog 统一管理 insets / IME，避免 sheet 与内部列表同时争夺底部布局。

思考强度控件继续使用原生 Material Slider 的拖动、离散档位、触觉和无障碍语义。拖动只预览星轨能量；松手后通过 `XuanjiViewModel.setReasoningEffort()` 调用天枢真实会话接口，再读取 session snapshot 核对成功，之后更新当前强度和保存配置。支持档位由模型能力决定，提交期间禁用重复操作，不支持的模型不展示可调滑条。外观改变没有替代这一执行链路。

### 星轨脉冲与动画边界

恒星、星环与轨道由 `StellarBrand.kt` 中自有原生绘制生成；启动图标是本项目向量资源。本轮按璇玑恒星品牌原创实现“星轨脉冲”：`StellarOrbitMotion.kt` 用细椭圆弧、沿轨光点和金色日冕组织动效，不再采用大面积多色渐变罩层。输入区下方显示局部轨道，发送后上方轨道原位渐显、下方随之淡出，等待结束后收束；光效不作为模型实际执行进度的数值证明。

滑条采用约 `56dp` 的轨道和 `42dp` 的恒星滑块，按下缩放至 `1.06` 倍并保持在 `48dp` 预留区域内。档位越高，启用的轨道、光点和运动速度越强；进入最高档触发短暂金色日冕。标题区域固定高度，档位名称和“正在应用…”只做淡入淡出，不启用 `AnimatedContent` 的尺寸弹簧，避免文字切换带动控件伸缩。

绘制缓存可复用对象，逐帧状态在 draw / graphicsLayer 中读取，减少父布局和消息测量重组。自定义循环只在前台 `RESUMED`、页面可见且系统动画倍率非零时运行；工作台等遮挡对话时暂停背景星轨。系统“移除动画”或动画倍率为零时停止轨道和光点循环、取消思考高度过渡，保留静态轮廓与等待指示。这里只描述源码策略，帧预算、低端机温升和切换流畅度仍需硬件测量，未宣称完成真机帧率验证；原创实现和视觉调整也不构成法律安全结论。

欢迎文字是按天枢 16 个星域设计的简短诗句，每个新会话选择一个并保持稳定，星域详情可查看。艺术字体只用于欢迎文字；对话、控制和文档采用可读 UI 字体。8 个真实 OFL 字体被按必要汉字子集化并改名，保留原版权和 OFL。

## 12. 工作台与真实观测

工作台整合任务 / todo、文件预览、终端 / 后台任务、配置、事件回放、验证、Hooks、Token / 缓存 / 上下文以及代理执行信号。Reducer 从真实 server snapshot、tool_result、hook_result、job、delegation 等事件提取数据。

缺失的数值显示为未知 / 未返回，不能用 UI 推算冒充上游实测。“Delivery Gate: GREEN”来自实际工具交付结果，不是有回复就自动绿色。失败分类是界面辅助归纳，不等同于根因诊断；Token 总量不能冒充人民币成本。收敛、资源、代理波次等只有上游返回足够字段时才具有对应证据。

## 13. 凭据、数据与外部服务

Android 本地模型 Key 使用 Android Keystore 的 AES-GCM 密文存储；旧配置会尝试迁移。搜索使用独立密钥别名。应用禁用系统备份，通知与日志不显示真实 Key。天枢 3.27.0 自身 `secrets-store.ts` 实现 AES-256-GCM 信封与 0600 权限、OS 密钥库 / 本地文件密钥后端；它与 Android Keystore 是不同层，不能声称 Node 内部凭据已由 Android 硬件密钥托管。最终持久化后端与恢复条件仍要手机验证。

附件从系统 content URI 复制到应用私有 rootfs `/root/.xuanji/attachments/`，保留原文件。图片支持 PNG / JPEG / WebP / GIF；单图片 10 MiB，其它文件 8 MiB；每类最多 4 个，总编码上限另有限制。请求体采用流式 Base64，避免一次保留多份大数组。图片 / 文档内容可能发给用户配置的模型 / vision 服务，一般文件以 runtime 路径交给天枢读取。

| 数据流 | 目的地 |
| --- | --- |
| 对话 / thinking 请求 / 附件 | 用户选择的模型与可选 vision 供应商 |
| 搜索词 | 所选搜索服务 / 公共 MCP / 自建 SearXNG |
| 网页导航 | 目标站点、站点子资源及其存储 |
| Linux shell / 文件工具 | 本机 runtime、批准后的共享存储、命令主动访问的外部端点 |
| 本地会话 / 事件 | 天枢私有目录及其原生持久化系统 |

仅基于源码不能承诺“绝无遥测”或“所有数据完全离线”：上游、npm 依赖和用户工具各有自己的行为。正式发布要提供准确隐私说明，并用网络观测核实外部连接。

## 14. 开发与验证

本机原生构建示例：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --max-workers=2
node tools/runtime-prompt-check.mjs
node tools/command-runtime-check.mjs
node tools/todo-runtime-check.mjs
```

JVM 测试覆盖协议、解析、状态归并、发送、命令路由、搜索适配、路径边界和待办行为等。Node 检查使用实际 APK rootfs 中的天枢 bundle，而不只验证替身接口。桌面网络测试只证明当时服务 / 请求成功；手机原生生命周期、授权、帧率、键盘、WebView 与 OEM 行为由真机验收。

运行环境组装脚本在 Linux / WSL 执行，下载 Ubuntu、Node、Termux deb、ripgrep 和 npm 包。脚本会重建指定 WORK 目录，执行前必须确认它是专用构建目录，不能指向用户文件目录。当前安装时跳过 npm scripts，原生依赖是否有正确 arm64 产物需检查，而不能认为 npm install 成功等于所有工具可用。

公开前生成实际物料与许可证证据：

```powershell
.\gradlew.bat -I tools/open-source-dependencies.init.gradle exportOpenSourceDependencies --max-workers=2
python tools/open-source-inventory.py
python tools/publication-scan.py
```

`components.json` 记录真实 rootfs / 字体 / Gradle artifacts 散列、npm 元数据、Ubuntu 包、native / WASM 和 notice 来源；`bom.cdx.json` 为 CycloneDX 1.6 格式。它们是可复核证据，不是法律许可结论，且不会自动补齐所有静态编译 / minified / WASM 内部依赖的源码对应关系。

## 15. 维护原则与未完成事项

1. 升级 Tianshu / Node / rootfs 时先读社区源码与发行差异，再更新适配和实际 bundle 测试。
2. 命令支持应包括 UI、请求、执行结果、审批、重试、历史恢复与设备验收；只有目录条目不算接入。
3. 优先复用原引擎的数据与执行入口，保持每会话隔离，不用全局 fallback 偷换语义。
4. 核心工具不能为了美观隐藏失败或不确定状态，UI 设计与执行正确性要同时成立。
5. 每个公开 APK 重新生成 SBOM、许可证、散列与测试记录；使用不同 versionCode，保留用户数据。
6. 尚待逐项完成：全部 TUI 专属命令、胶囊与技能管理、浏览器全面操控边界、Android 平台现代化、完整 rootfs/native 源码发布链和公开版本的法律 / 隐私审查。

本技术说明应随实现持续更新。设计参考、计划、部分测试通过与最终用户真机验收必须分开记录。
