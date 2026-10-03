# Android 搜索、浏览器与运行环境融合计划

用户已授权睡眠期间自主设计和实施；沿用既有原生 Compose 界面和 Android MCP。
完整目标包括全部天枢命令、能力审查、环境系统提示、多引擎搜索和内置浏览器。
上轮命令修复是进展，不是完整目标验收完成。

## 当前实施顺序

- [x] 修复完整目录误禁用，并接入已有星域、模式、审查和禅阶段 HTTP 控制。
- [x] 搜索社区并阅读 modsearch、dsh-free-search、dsh-web-search-free、jupsearch-mcp 的实际实现。
- [x] 搜索 Android WebView 项目，阅读 dsh-browser 的 DOM 编号交互架构。
- [x] 搜索模型、八个引擎适配、并发汇总、URL 去重、逐引擎状态和深度预算。
- [x] 对话页多选/自动路由面板，凭据与实例地址保存，真实搜索探针。
- [x] Android MCP 搜索工具及接入测试。
- [x] 原生 WebView 浏览器，地址栏/导航/加载/错误状态/标签页及 DOM 操作工具（代码与 DOM 测试完成，原生生命周期待真机验收）。
- [x] 为主控和子代理注入稳定的 Android 环境系统上下文，验证确实进入模型请求（原始 bundle 的真实请求构建已验证，手机进程启动待验收）。
- [ ] 剩余天枢命令能力矩阵及真实桥接，包括胶囊、目标、计划审批和技能管理。
- [ ] 全量构建、集成测试、覆盖安装包与由用户执行的真机验收。

## 决策

采用原生 HTTP 引擎适配与系统 WebView。独立 Node MCP 服务可直接复用，
但会额外引入手机上子进程的生命周期、凭据同步和 Chromium 依赖；
原生适配复用现有前台 MCP 服务，并保留真实网页的登录态。

搜索多选时请求每个所选引擎，单个失败不影响其他结果，错误保留引擎名。
自动模式从启用池按深度选择引擎，失败继续尝试剩余候选。
低/中/高影响结果数量、摘录上限、自动模式成功源目标数及支持该参数的引擎深度。
不向模型公开密钥；不把空数据、验证码或业务错误码视为搜索成功。

搜索请求内容会发送给用户选择的外部引擎。配置页明确提示这一边界。
凭据用独立 Android Keystore 密钥加密；自建 SearXNG 的实例地址由用户配置。
网页正文与搜索摘要是外部数据，不具备更改系统规则的权限。

## 社区证据

- https://github.com/liustack/modsearch （MIT）：默认 Firecrawl、失败转移、结构化证据。
- https://github.com/DDDMUC/dsh-free-search （MIT）：DuckDuckGo/Bing/SearXNG、AnySearch、Keenable，
  lib/index.js 中已核对端点与请求字段。
- https://github.com/MochiNek0/dsh-web-search-free （MIT）：逐提供商源码，业务错误码验证、日期与截断标记。
- https://github.com/4cya/jupsearch-mcp （MIT）：多提供商汇总、认证、URL 去重。
- https://github.com/omdsh-dev/dsh-browser （MIT）：DOM 快照、编号点击、输入、导航与真实会话状态。
- https://github.com/KevinnZou/compose-webview （Apache-2.0）：Compose 中 WebView 生命周期和导航状态。
- https://github.com/anysearch-ai/anysearch-mcp-server （Apache-2.0）：官方 AnySearch MCP 项目。

这些项目是架构和接口参考，DSH 插件不能直接当成天枢插件安装。
复制代码时必须保留原许可；本轮优先使用官方 API 契约独立适配。

## 验证方式

使用现有本地 HTTP 测试服务器验证真实请求路径、认证头、载荷、响应解析、
并发调用、失败隔离和密钥脱敏。免费引擎另做真实网络探针，记录服务状态，
不把一次桌面网络成功外推成手机全功能验收。
浏览器验证以 Android 实际 WebView 的页面行为为准；纯 JVM 测试只证明 URL、
协议和快照契约。没有真机证据时不宣称完整目标已经完成。

## 2026-10-03 进展

- Exa 与 Keenable 新增社区已核验的匿名 MCP 入口，配置密钥后使用对应 REST API。
- 七个公开引擎通过实际 Kotlin 适配器探测：Tavily、Exa、AnySearch、Firecrawl、Keenable、Bing
  各返回 3 条；DuckDuckGo 连接超时。此前 PowerShell 探针中 DuckDuckGo 返回真实 HTML。
  SearXNG 是用户指定实例，未擅自选择公共实例代为验收。
- 浏览器采用独立原生 Activity 和应用进程内标签页，最多 8 页；WebView 不提供原生 JS bridge。
  已注册 tabs/open/snapshot/action 四个 MCP 工具，操作使用快照编号与本地审批。
  Chromium 页面验证了真实 DOM 点击、输入事件、遮蔽和失效条件；不替代 Android WebView 验收。
- Android 环境说明通过 Node 24 的 registerHooks 在加载时追加到真实静态系统提示入口，
  保留完整上游文件。入口版本和函数形状不匹配时明确失败，防止静默漏注入。
  原始 Tianshu 3.27.0 PromptEngine.buildOaiRequest 的主控/子代理 system 消息已通过测试。
- 新增社区浏览器调研：EinkBro 的工具栏与标签页模式只作参考，未复制其实现。
