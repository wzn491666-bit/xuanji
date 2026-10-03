# 璇玑 0.2.3：搜索、浏览器与 Android 环境融合

版本：0.2.3 / versionCode 32。包内天枢：3.27.0。完整目标仍在推进。

## 本轮实现

- 对话页的搜索入口：八个引擎可多选；自动路由、多引擎并发、轻量/均衡/深入档。
  多引擎模式请求每个选择项，最多三个并发；单源失败不会抹掉其它结果。
- Tavily、Firecrawl、AnySearch 匿名/密钥接口，Exa、Keenable 匿名 MCP/密钥 REST，
  Bing RSS、DuckDuckGo HTML、自建 SearXNG JSON。
- 密钥用独立 Android Keystore 别名加密。取消搜索会取消网络 socket，
  响应限制 1 MiB；验证码、业务失败、协议缺失不会伪装成成功。
- 内置原生 WebView 浏览器：地址、前进/后退、刷新/停止、八个标签页、错误提示、
  外部浏览器入口和用户手动文件选择。聊天及搜索里的网页链接也在此打开。
- 四个实际 Android MCP 工具：browser_tabs、browser_open、browser_snapshot、browser_action。
  正式名称均带 android_ 前缀。快照包含正文、编号元素、snapshotId 与截断标记；
  点击/输入使用独立本地审批，旧快照、隐藏/禁用元素与敏感输入会被拒绝。
- Node 24 的加载钩子在真正的天枢系统提示入口追加 Android 环境信息，覆盖主控与子代理。
  保留原始天枢文件、项目指令、模型校准与会话历史；不依靠聊天消息冒充系统提示。

## 社区调研

实现各项前已查看对应社区项目的实际实现与接口：

- [modsearch](https://github.com/liustack/modsearch)：搜索失败转移与结构化证据。
- [dsh-free-search](https://github.com/DDDMUC/dsh-free-search)：八类引擎、Exa/Keenable MCP 响应。
- [dsh-web-search-free](https://github.com/MochiNek0/dsh-web-search-free)：提供商载荷和业务错误。
- [jupsearch-mcp](https://github.com/4cya/jupsearch-mcp)：合并与去重。
- [dsh-browser](https://github.com/omdsh-dev/dsh-browser)：DOM 编号、可见性、敏感字段、快照预算。
- [compose-webview](https://github.com/KevinnZou/compose-webview)：AndroidView 和 WebView 生命周期。
- [EinkBro](https://github.com/plateaukao/einkbro)：原生浏览器工具栏与标签页模式。
- [DSH Android](https://github.com/woaiys3/deepseek-harness-android-app)：真实 Android 权限与 proot 身份的区分。
- 天枢静态提示、PromptEngine 和社区 issue：核对系统消息构建及主控/子代理的职责。

DSH 插件作为参考，未声称可直接安装到天枢。浏览器实现采用 Android 自带 WebView。

## 已验证与限制

实际 Kotlin 网络探针：Tavily、Exa、AnySearch、Firecrawl、Keenable、Bing 各返回三条结果；
DuckDuckGo 此次连接超时，此前独立桌面探针返回了真实 HTML。
SearXNG 需要用户自己的实例，尚未实际探测。匿名接口的额度与可用性由服务端决定。

`tools/browser-dom-check.cjs` 在真实 Chromium 页面验证了读页、输入事件、点击、
敏感值遮蔽、隐藏/禁用/文件元素限制和导航/快照失效。
该测试不证明 Android WebView 的 Activity 生命周期与手机性能。

`tools/runtime-prompt-check.mjs` 使用随 APK 分发的实际天枢 bundle 与正式 Node 加载钩子，
创建真实 PromptEngine，并检查主控、子代理 buildOaiRequest 的 system 消息。
系统提示保留完整上游内容，重复请求保持稳定，其余历史消息一致。
手机的 proot 启动与在线模型调用仍由真机验收确认。

Android 上下文钩子当前明确适配天枢 3.27.0 的已知入口。升级内核必须重新核验入口；
不兼容时会报错，不能静默漏注入。

浏览器不包含跨域 iframe 的 DOM，不执行任意模型 JavaScript，不自动处理敏感输入、
摄像头/麦克风授权、网站下载或外部应用协议；需要时由用户在浏览器或系统中处理。
标签页在当前 App 进程中保留，尚未实现进程死亡后的标签页/表单恢复。

全部斜杠命令尚未完全接入；目标/计划管理、胶囊及其余终端控制继续按能力矩阵推进。
本版本不是完整目标验收完成。

## 真机验收提示词

```text
请验收璇玑 0.2.3 / versionCode 32 的搜索和内置浏览器。
不修改用户文件，不删除、卸载、清理、锁屏，不把任何凭据写入回复或日志。

1. 确认天枢能正常启动，聊天和原有 Root/Shizuku 检查仍正常。
2. 打开对话页搜索设置，选择多引擎、Tavily/Exa/AnySearch/Keenable/Bing，设为深入。
   测试“Android WebView 官方文档”，记录每个引擎是否真正执行、结果数或具体失败。
   再切换自动和轻量，确认配置重开后保存；执行一次搜索并取消，检查忙碌状态恢复。
3. 发消息：请使用璇玑提供的 Android 联网搜索工具查询 Android WebView 官方文档，
   保留我选择的搜索引擎与深度，给出来源链接与逐引擎状态。
   记录真实调用的工具名，不能用模型常识或其它工具冒充搜索。
4. 点击一个官方来源，确认在璇玑内打开。检查地址栏、加载、返回/前进、刷新、
   标签页切换与关闭；返回对话后聊天内容仍存在。
5. 发消息：请通过 Android 浏览器工具读取刚才页面，返回标题、URL、三个可操作
   元素的编号和快照编号。不要输入任何密码，不上传文件，不执行有副作用的点击。
   如需验证点击，只选站内文档导航，并在璇玑本地审批中由我确认一次；操作后重新读页。
6. 拒绝一次网页操作审批，确认页面没有被操作；切换目标标签页后检查旧目标操作被拒绝。
7. 新建对话询问：你运行在哪里？proot 的 uid=0 是否能证明 Android Root 已授权？
   需要联网与浏览器时应使用哪条实际工具链？不要调用工具。
   记录回答，不要求模型展示或复述系统提示全文。

请按项返回通过/失败/未测、实际工具调用与错误信息；不要把桌面测试当成手机验收。
```

## 自动验证命令

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --max-workers=2 --console=plain
node tools/runtime-prompt-check.mjs
# Playwright 需可用；BROWSER_CHANNEL=chrome 可复用本机 Chrome。
node tools/browser-dom-check.cjs
```

## 构建证据

- 全量测试：192 项，191 通过、0 失败、0 错误、1 项网络测试按默认配置跳过。
  显式开启网络测试后的独立执行已通过，逐源状态见上文。
- 正式 Node 加载钩子与原始 bundle 的请求测试通过；该脚本现在直接从 APK 的 rootfs 资产
  提取所需模块到独立 build 目录，不依赖之前的调研解包目录。
- Chromium DOM 交互验证通过。针对性 Lint：0 errors、1 条既有 InlinedApi 警告；不是全量 Lint。
- APK：`app/build/outputs/apk/debug/xuanji-0.2.3-debug.apk`，159,882,596 bytes。
- SHA256：`79064B589BD255BAFEBB215A1FB2CD3AB5A0396514C546977F2741214EA33E68`。
- aapt 确认 versionName 0.2.3、versionCode 32、minSdk 26、targetSdk 28、arm64-v8a。
- apksigner 验签通过，签名 SHA256 与 0.2.2 一致：
  `464ee1066a6421109c4f64b057c4e9e37b144508cb1ecbbd19eab971bda85b6d`。
- 本轮没有覆盖安装或操控手机，保留用户自行真机验收的安排。
