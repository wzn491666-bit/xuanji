# GitHub 开源与 APK 公开发布清单

复盘日期：2026-10-03。本清单将“有源码可上传”与“已经满足所有二进制分发条件”分开处理。未勾选项是真实尚未确认的事项。维护者已选择原创代码 Apache-2.0；当前未发布或推送，选择不能替代第三方分发义务。

## 已形成的证据

- [x] 技术架构文档与第三方归属文档已由当前源代码和实际 rootfs 复盘。
- [x] 提供 runtime / npm / Ubuntu / native / WASM / 字体的离线生成脚本。
- [x] 从实际 rootfs 保留可读 LICENSE / NOTICE / COPYRIGHT 和 Ubuntu common-licenses 原文。
- [x] 8 个字体的 OFL、原版权、子集 / 改名过程与输入散列有记录。
- [x] 参考项目公开许可文件已抓取并固定文本 SHA-256 / GitHub blob SHA。
- [x] 提供 Gradle release / debug runtime resolved artifacts 导出任务。
- [x] 提供只输出文件名 / 计数的公开候选文本凭据扫描。
- [x] 根 LICENSE 为 Apache-2.0 官方完整条文，NOTICE 保留天枢及 dsh-browser 实际改编归属；贡献指南保留完整 DCO 1.1。
- [x] 配置仓库忽略规则与源码发布扫描 / CI，避免默认纳入运行包、凭据、开发 output 和第三方设计参考截图。

以上勾选证明证据和工具存在，不证明全部法律义务已完成。

## A. 原创代码与仓库

- [ ] 核实原创贡献者实际权属与 Apache-2.0 提交授权；当前 NOTICE 使用 Xuanji contributors 集合署名，完整 DCO 已提供，仍不能替每个贡献者完成权利核查。
- [ ] 核对早期参考代码 / AI 协作生成内容与第三方实质片段归属，特别是 GPL Aether 代码；保留复制与改写的必要通知。
  - 进展（2026-10-03）：已用 `tools/provenance-similarity.py` 对 Aether（GPL-3.0，commit `7dac1ba9…`）与 dsh-browser（MIT，commit `23895d72…`）做逐行比对；≥8 行只命中 Android Studio 模板，≥5 行只增加通用 launcher 与 `findActivity()` 样板；PRoot 启动技术路线已在 `THIRD_PARTY_NOTICES.md` 登记。跨语言参考与深层改写仍待维护者确认，保持未勾选。
- [ ] 为每个版本固定上游 git commit、npm tarball SHA / integrity、Node / Ubuntu / Termux 原输入散列与构建参数。
- [x] 上传范围排除 `.gradle/`、`build/`、`.idea/`、本地 SDK 路径、签名密钥、用户配置、运行日志和不应公开的实验 output。
  - 证据（2026-10-03）：`git check-ignore` 验证 `.gradle/`、`build/`、`.idea/`、`local.properties`、`keystore.properties`、`*.jks`、`.env.*`、`*.log`、`output/`、`app/src/main/assets/runtime/` 与 `docs/design/references/` 全部命中忽略规则；字体资源保留发布。
- [x] 逐张核查或剔除 `docs/design/references/`、`output/imagegen/`、手机截图 / 视频；截图访问权限不等于再发布权。
  - 证据（2026-10-03）：发布范围的媒体扫描只在 `docs/design/references/` 找到 12 张参考 / 实测截图，该目录已被忽略；`output/` 已被忽略；源码内随包媒体为 8 个 OFL 字体，证据见 `docs/stellar-welcome-font.md` 与 `docs/open-source/license-texts/fonts/`。
- [ ] 检查 Git 全历史和 LFS / release 附件；只检查当前源树不能证明旧密钥已不存在。
- [x] 给公开资料写清独立项目身份，不使用不实官方合作、满血已验收或商标授权表述。
  - 证据：README 结尾声明不代表天枢、Google、OpenAI、Shizuku、Termux 或 Ubuntu 官方产品；`NOTICE` 写明不暗示任何背书或商标授权。

## B. 运行环境与 copyleft 二进制

- [ ] 确认当前 PRoot 与 loader 的精确 source commit、Termux patchset、GPL header、完整源码与编译脚本。
- [ ] 确认 libtalloc / libandroid-shmem 精确源码版本、许可和原版权，保留完整声明。
- [ ] 为实际 Ubuntu GPL / LGPL 包提供该版本对应源码获取 / source bundle，并根据具体许可版本选择符合条件的提供方式。
- [ ] 对 LGPL / GCC runtime exception / 其它静态或动态库条件逐项复核，提供必要的修改与重链接材料；不能凭“没有修改二进制”省略全部义务。
- [ ] 复核 Linux shell 独立程序与 Android 代码的组合 / 衍生关系，形成具体结论后再确认原创代码可选许可。
- [ ] 将源码、patches、NOTICE、SBOM 与对应 APK 一起固定；源码 URL 必须长期可用，不只指向移动的 main 分支。
  - 进展（2026-10-03）：已新增 `docs/open-source/CORRESPONDING_SOURCE.md` 与 `scripts/collect-corresponding-source.sh`，固定 PRoot `v5.1.107.95`、Termux 配方提交、libtalloc 2.4.3、libandroid-shmem v0.7、Node v24.21.0 与天枢 3.27.0 的入口；脚本已在 WSL 完整执行，9 个文件的 SHA256SUMS 通过校验，Ubuntu 源包名列表 67 个。Ubuntu `apt-get source` 产物与实际 `.deb` 逐补丁比对仍未完成，保持未勾选。
- [ ] 保留 Ubuntu source / binary package 元数据与实际 .deb；不能把 Ubuntu 整体统一写成 MIT 或 Apache。

## C. npm、bundle 与原生库

- [ ] 以实际包 / lockfile 确认天枢 3.27.0 npm 产物与源码对应；确认 Apache 原 notice / attribution 和修改说明完整。
- [ ] 补齐 bundle source marker 的所有包精确版本与许可证，保留 compiled `/*! ... */` 版权注释，验证是否有未带 marker 的静态依赖。
- [ ] 对 `.node` / `.wasm` 检查静态库、grammar 源码、native/sqlite 构建与许可，不能只对外层 npm 包作归属。
- [ ] 保留 Node 原始完整 LICENSE；npm 自带 `spdx-exceptions` 的 CC-BY-3.0 等条款不能被统一 MIT 覆盖。
- [ ] 按实际 tarball 复查所有 package.json、license symlinks、LICENSE / NOTICE / COPYING；metadata 不是法律批准。

## D. Android 依赖与字体

- [ ] 对 release / debug resolved artifact 的每个传递依赖核对 POM、继承许可、AAR / JAR META-INF 和原项目完整文本。
- [ ] Apache 组件保留适用 NOTICE、版权、完整条文；BSD / MIT 等保留原声明与免责声明。
- [x] 再生字体时核验官方输入 digest、必要字形、改名记录、版权和 OFL；字体文件继续以 OFL 分发。
  - 证据：`docs/stellar-welcome-font.md` 记录 8 款字体的来源与改名子集流程；`tools/subset_welcome_font.py` 固定官方输入 SHA-256 并校验必需字形；8 份 OFL 原文在 `docs/open-source/license-texts/fonts/`。
- [ ] 确认所有生产媒体确实在许可范围内；当前代码生成恒星与未生产使用的外部生图比较材料分开。

## E. 数据与对外服务

- [x] 按当前数据流提供隐私说明，明确模型 / vision、选中搜索引擎 / MCP、站点 cookie、手机工具和会话持久化。
  - 证据：新增 `docs/PRIVACY.md`（2026-10-03），覆盖本机存储、外发数据表、用户控制与第三方限制；已接入应用内离线文档。上游网络观测仍由下一项阻塞，文档已明确该限制。
- [ ] 真机观测外部网络连接，核实上游 / 依赖行为，不仅凭源文件未看到 telemetry 就宣称完全无采集。
- [ ] 清除公开资料中的真实 Key / token / 日志 / 用户路径，凭据扫描结果仅输出文件名计数；个人 Key 不能作为测试 fixture 分发。
- [ ] 核实搜索服务、公共 MCP、模型与生图中转服务的使用条款 / 配额 / 素材权利；开源客户端许可不替代服务授权。

## F. 交付验收

- [ ] 原生 unit tests、真实 Tianshu bundle 集成检查、构建与 APK 签名检查全部完成并归档证据。
- [ ] 原生首页 / 历史 / 发停 / 模型 / 思考 / 命令 / 权限 / 工作台 / 文档入口真机验收。
- [ ] 浏览器实际 WebView 的导航、标签页、快照、DOM 操作、动态页面、授权和失效目标验收，记录能力边界。
- [ ] Android SDK / target / OEM 生命周期与性能验收；不能将桌面脚本通过写成手机完全通过。
- [ ] 给公开 APK 递增 versionCode，记录 APK SHA-256、签名证书、版本、rootfs 与 SBOM 散列。
- [ ] 首次打开有简要来源摘要，设置有永久完整文档入口，全部许可离线可读，公开仓库 URL 指向真实项目。
- [ ] 清单全部核实后再发布；未完成项在 release notes 明确说明，不能用“全量合规”概括。

## 可复现证据生成

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat -I tools/open-source-dependencies.init.gradle exportOpenSourceDependencies --max-workers=2
python tools/open-source-inventory.py
python tools/fetch-license-evidence.py
python tools/publication-scan.py
python tools/verify-open-source-inventory.py
python tools/release-check.py --release
```

`publication-scan` 不读取私有 local.properties / 签名材料，不扫描图片 OCR、完整 Git 历史、APK 或所有可能的密钥格式。零命中只能证明这次有限文本扫描未发现相应模式。不得据此跳过人工素材与历史核查。
