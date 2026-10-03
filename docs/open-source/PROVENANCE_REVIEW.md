# 来源相似度与凭据核查记录

核查日期：2026-10-03。本文为 [MAINTAINER_ATTESTATION.md](MAINTAINER_ATTESTATION.md) 的源码项提供可复核证据，不是法律结论，也不替维护者完成个人陈述。

## 方法

- 工具：[tools/provenance-similarity.py](../../tools/provenance-similarity.py)（本仓库新增，仅用标准库）。
- 方法：对目标与参考树的文本行做归一化（去空行、行首注释、单独括号，压缩空白）后滑动窗口比对；≥8 行命中列为严格命中，≥5 行列人工复核。
- 排除：`output/`、`docs/open-source/license-texts/`、`docs/design/references/`、`app/src/main/assets/runtime/`、构建与 IDE 缓存目录。
- 参考仓库与固定点（只读克隆于 `output/provenance/`）：
  - `Zhou-Shilin/Aether`，commit `7dac1ba9b69f96d373e7b8391d2ace16f3d195ed`，GPL-3.0。
  - `omdsh-dev/dsh-browser`，commit `23895d7292495f665e5676d2f174d022bb3c3605`（与 NOTICE 引用一致），MIT。
- 局限：逐行匹配发现不了跨语言重写、重命名后的复制、深层逻辑搬运或概念复用；零命中只代表该启发式未命中。

## Aether 比对结果

- ≥8 行严格命中：只有 `settings.gradle.kts` 的 10 行 Android Studio 标准模板（`pluginManagement` / `dependencyResolutionManagement` 仓库块）。属生成模板，不是业务代码。
- 降到 ≥5 行后新增两处：Android 启动 Activity 的标准 `<intent-filter>` 五元组；`tailrec fun Context.findActivity()` 这一广泛使用的 Context 解包辅助函数。均为通用 Android 样板，未发现应用逻辑片段。
- PRoot 启动人工对照：璇玑 `Runtime.kt` 与 Aether `AlpineRuntime.kt` 都使用 `/system/bin/linker64` 启动 proot；`Runtime.kt` 第 151 行注释已明确写明“与 Aether 相同的做法”。两边的参数构造、绑定目录、shell 与 env 处理不同（璇玑使用 `--kill-on-exit`、`/sdcard` 绑定与 `/bin/bash`）。属已在 `THIRD_PARTY_NOTICES.md` 登记的启动技术参考，不是逐行复制。

## dsh-browser 比对结果

- ≥8 行严格命中：0。
- `app/src/main/assets/browser/action.js` 的富文本管线是同一浏览器编辑 API 的紧凑重写（`closest('[contenteditable]')`、`range.selectNodeContents`、`execCommand insertText / delete`）；`action.js` 顶部版权注释、根 `NOTICE` 与 MIT 原文齐全，归属声明准确。

## 凭据与个人信息

- `tools/publication-scan.py`（本次修复了 `rglob` 追入瞬时构建目录导致崩溃的问题）：793 个文本文件、0 个凭据形状命中；1 个私密候选（`local.properties`，已被忽略规则排除）。
- 临时深度扫描（`output/provenance/deep_scan.py`，不随仓库提交）：243 个文本文件。剩余命中逐条确认为合成值：`example.com` 邮箱、测试 / DNS IP、`test-token` / `production-smoke-token` 形式的 bearer fixture、文档与清单中的 SHA-256 / UUID、Kotlin 测试数据；未发现真实密钥。
- 清理了 3 处本机绝对路径：`docs/2026-10-03-native-design-review.md`（构建产物截图链接）、`docs/superpowers/plans/2026-10-01-chat-markdown-command-palette.md`（清单路径）、`tools/capture_native_audit.ps1`（硬编码 adb 路径）。复扫后 `personal-absolute-path` 为 0。

## 复现命令

```powershell
python tools/provenance-similarity.py --self-test
python tools/provenance-similarity.py --reference output/provenance/aether --target . --min-lines 8 --json output/provenance/aether-similarity.json
python tools/provenance-similarity.py --reference output/provenance/dsh-browser --target . --min-lines 8 --json output/provenance/dsh-browser-similarity.json
python tools/publication-scan.py
```

## 结论与未决

- 对 Aether 与 dsh-browser 这两个具体同语言风险，未发现未声明的实质性逐行复制；已登记的改编与参考归属完整。
- `MAINTAINER_ATTESTATION.md` 第 2 条可依据本文复核后由维护者确认；第 1 条仍须维护者本人确认创作与提交权利。
- 尚未覆盖：首次提交后的 Git 历史、媒体 EXIF / OCR、其它仅作跨语言参考的项目、无法由行匹配发现的深层改写。公开前仍按 [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) 执行 `--tracked --history` 与真机 / 网络观测。
