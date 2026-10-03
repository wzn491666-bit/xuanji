# 来源复盘证据索引

检查日期：2026-10-03。这里记录“从哪里知道”，不为未知来源填猜测。

| 范围 | 本地权威证据 | 外部来源 / 核查 |
| --- | --- | --- |
| 实际随 APK runtime | `app/src/main/assets/runtime/` 的字节与 version | `scripts/build-runtime.sh` 记录官方下载地址、包版本与组装方式 |
| 天枢本体 | 实际 rootfs 中 LICENSE / package.json / dist | `huiliyi37/Tianshu-harness` 本地 git commit；npm 发布对应待核实 |
| 上游被改写入口 | `assets/android-runtime/register.mjs` / transforms | 原始 3.27.0 bundle，精确锚点与实际运行测试 |
| Node / npm | 实际 Node LICENSE 与 bundled npm package metadata | Node 官方发行包，Node LICENSE 内多组件通知 |
| Ubuntu | 实际 dpkg status / copyright / common-licenses | Canonical Ubuntu Base，精确源包补充待完成 |
| Termux native | APK 中 proot / loader / talloc / shmem 散列 | Termux包地址，公开 PRoot / shmem LICENSE；2026-10-03 已固定 proot `v5.1.107.95` (d4d2a190…)、talloc 2.4.3、shmem `v0.7` 与 Termux 配方提交，入口见 `CORRESPONDING_SOURCE.md`；精确补丁比对待完成 |
| Gradle依赖 | Gradle resolved artifacts / SHA / 缓存 POM | Maven原始POM及每个上游项目条文，继承和 artifact notice仍需核查 |
| 字体 | 8 个 OFL、子集脚本固定输入 digest、输出字节 | LXGW 官方 release 与 google/fonts ofl 目录 |
| 社区参考 | 既有开发计划 + 本轮真实源码 / license读取 | `research-license-evidence.json` 固定内容散列，不把default branch当成特定二进制版本 |
| 产品视觉 | 原生 `StellarBrand.kt`、vector launcher、欢迎场景 | 用户参考与设计文档；截图 / 生成素材出版权未自动取得 |

## 社区审阅的用途

搜索参考：modsearch、dsh-free-search、dsh-web-search-free、jupsearch-mcp、AnySearch 官方 MCP。浏览器参考：dsh-browser、compose-webview、agent-browser、browser-use，以及 Android WebView 官方 API。

已经确认 `assets/browser/action.js` 富文本编辑管线局部改编 dsh-browser/actions.ts 第305–362行，版权2026 Yuxiang Lin、MIT；许可固定到实际审阅commit23895d7292495f665e5676d2f174d022bb3c3605。该项区别于只有思想/接口参考的agent-browser与browser-use，并列入THIRD_PARTY_NOTICES / 根NOTICE及源码归属。

原始许可证据抓取自公开参考项目及 Shizuku API，通过 GitHub contents API 获取实际文件，保留原始字节、GitHub blob SHA 与 SHA-256，存于 `license-texts/research/`。PRoot 的 GitHub license 自动分类为 NOASSERTION，但实际 COPYING 包含 GPL v2 条文及原作者署名，故不采用自动分类代替阅读。Shizuku API 的实际 13.1.5 resolved POM 和当前 LICENSE 均为 MIT，不能与其它 Android Apache 组件混写。

历史 UI 审阅来源包括 Refero、Mobbin、assistant-ui 与用户提供的 Codex / Gemini / Claude 对比；文档记录参考不能证明这些产品屏幕可再发布。`docs/design/references/` 当前有第三方屏幕和本项目实测屏幕，需逐项隐私 / 权利清理后再公开。

## 当前证据覆盖与缺口

`components.json` 是按实际归档建立的清单，不是从 README 枚举的愿望列表：npm 实际 package metadata、Node自带npm、Ubuntu已装包、额外 ripgrep版权、嵌入bundle包名标记、native/WASM字节、8字体、release/debug Gradle artifacts。每个文本记录和实物都可用散列对照。

包名 / license字段仅是声明，Ubuntu有一个 copyright 文本也不能说明它是单一 SPDX 许可；Node / Tianshu 静态依赖和 WASM grammar 可能缺单独 package.json，清单明确保留未知版本 / 待核实。

未检验 Git 全历史或媒体 OCR，未访问个人 Key / local.properties / 签名仓库，未执行安装 / 删除手机文件。公开 GitHub 仓库目前尚未建立最终发布身份，文档不虚构仓库 URL / 版权主体。原创代码 Apache-2.0 是维护者明确选择，实际版权归属和第三方材料仍分别审查。

2026-10-03 新增 `CORRESPONDING_SOURCE.md`、`PUBLICATION.md`、`MAINTAINER_ATTESTATION.md` 与 `scripts/collect-corresponding-source.sh`，并把 `docs/PRIVACY.md` 接入应用内离线文档。对应源码脚本已在 WSL 中执行验证；Ubuntu 源码、实际 `.deb` 补丁一致性和真机网络观测仍在发布清单中阻塞。

同日完成第一轮来源相似度与凭据核查：`tools/provenance-similarity.py` 对 Aether（GPL-3.0，commit `7dac1ba9…`）与 dsh-browser（MIT，commit `23895d72…`）做 ≥8 / ≥5 行比对，未发现未声明的实质性逐行复制；清理了 3 处本机绝对路径；`publication-scan.py` 修复构建目录剪枝缺陷后 793 个文本文件 0 凭据命中。完整方法与未决项见 `PROVENANCE_REVIEW.md`，该核查不替代维护者本人确认与 Git 历史检查。
