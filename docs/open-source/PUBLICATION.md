# 开源发布手册

核对日期：2026-10-03。本手册把“公开源码仓库”和“公开可运行 APK”分开处理。前者是 Apache-2.0 源码分发；后者还要满足 runtime 二进制的 GPL / LGPL 对应源码、Ubuntu 包、素材与真机验收义务。两条轨道不能互相替代。

## 一、源码仓库发布

公开源码仓库只包含本仓库的原创代码、第三方归属文档、许可原文与可复现证据；不包含 `app/src/main/assets/runtime/`、APK、签名材料、构建缓存或用户数据。

发布前在仓库根目录执行：

```powershell
python tools/release-check.py --self-test
python tools/release-check.py
python tools/release-check.py --tracked
python tools/verify-open-source-inventory.py
python tools/check-dco.py --self-test
```

期望结果：

- `--self-test` 输出 `selfTest: passed`。
- 默认模式在 8 MiB 以下的文本候选上没有凭据命中；输出中的 `manualReview` 需要逐项人工确认。
- `--tracked` 只能在仓库根目录是项目自身时使用；它会把误提交的私密文件和被排除目录直接列为 finding。
- inventory 校验输出 `legalClearance: false`，表示证据散列通过，不代表法律批准。

GitHub 仓库设置建议：

1. 默认分支启用保护，要求 PR、DCO 检查与 source checks 通过。
2. 启用 **Private vulnerability reporting**，与 [SECURITY.md](../../SECURITY.md) 的说明一致。
3. 启用 Dependabot 或等价依赖更新提醒；CI 不构造可运行 APK，也不上传调试包。
4. 确认 `.gitignore` 仍覆盖 `app/src/main/assets/runtime/`、`docs/design/references/`、`output/`、`local.properties`、签名材料与日志。

首次提交示例（由维护者手动执行；不要代替用户执行）：

```bash
git init
git add -A
git status --short            # 只应看到预期文件；runtime / 截图 / 构建输出不得出现
git commit -s -m "chore: initial public source snapshot"
```

首次提交后必须补做历史检查：

```bash
python tools/release-check.py --tracked --history
```

Git 历史、LFS 对象、Release 附件、媒体 OCR 与 EXIF 不在当前文本扫描范围内，需按 [发布清单](RELEASE_CHECKLIST.md) 单独核查。

## 二、APK 发布

公开 APK 前必须完成 `RELEASE_CHECKLIST.md` 的全部项目。至少包括：

1. 递增 `versionCode` 与 `versionName`，记录构建 commit。
2. 按 [对应源码与构建证据](CORRESPONDING_SOURCE.md) 生成并复核 `corresponding-source/`。
3. 运行 JVM 测试、runtime / bundle 检查、`assembleDebug` 或正式签名构建与 lint，归档命令、输出、失败项。
4. 记录 APK SHA-256、签名证书指纹、rootfs SHA-256、`bom.cdx.json` 与 `components.json`。
5. 完成真机验收：首页、历史、发送 / 停止、模型、思考、命令、权限、工作台、离线文档、浏览器与 OEM 生命周期。
6. 在 Release notes 中写明未完成项与限制，不把“文本扫描通过”写成“全量合规”。

Release 资产建议布局：

```text
xuanji-<version>-release.apk
xuanji-<version>-release.apk.sha256
SHA256SUMS
bom.cdx.json
components.json
INVENTORY.md
THIRD_PARTY_NOTICES.md / NOTICE / LICENSE
corresponding-source/
  SHA256SUMS
  proot-v5.1.107.95.tar.gz
  termux-packages-<commit>.tar.gz
  talloc-2.4.3.tar.gz
  libandroid-shmem-v0.7.tar.gz
  tianshu-harness-3.27.0.tgz
  ubuntu-packages.txt
  node-v24.21.0-<dist-or-source>
  README.txt
```

## 三、对应源码流程

```bash
bash scripts/collect-corresponding-source.sh
```

脚本默认写入 `output/corresponding-source/`（已被 `.gitignore` 排除），下载 PRoot、Termux 配方、libtalloc、libandroid-shmem、天枢发行包，生成 Ubuntu 包列表与 `SHA256SUMS`。脚本不会自动完成 Ubuntu `apt-get source`、许可证判断或补丁一致性复核；这些必须由维护者执行并在 Release 说明中记录。

长期义务：

- 源码 URL 必须长期可用，指向固定标签 / commit，而不是移动的 `main`。
- 不能把 Ubuntu 整体写成单一许可证，也不能把 PRoot / LGPL 组件改写成 Apache-2.0。
- 若使用 GPL 的书面 offer 方式，必须保留有效期与获取说明；更简单可靠的方式是把对应源码直接挂在同一 Release。
- 新的依赖、媒体或代码来源变更时，先更新 `THIRD_PARTY_NOTICES.md` 与 inventory，再重新检查清单。

## 四、禁止公开的内容

- API Key、Bearer token、cookie、签名密钥、`.env` / `local.properties` / keystore。
- 用户手机截图、聊天记录、日志、设备序列号与真实用户路径。
- 第三方 App 截图、视频、Mobbin / Refero 素材与任何没有明确再发布权的媒体。
- `app/src/main/assets/runtime/` 中的二进制（它们通过 Release 资产与对应源码分发，不进入 Git 历史）。
- 与公开事实不符的“官方合作”“满血已验收”“全量合规”表述。

## 五、当前状态

- 源码文本候选扫描：0 命中，详见 `tools/release-check.py` 输出。
- 证据散列校验：通过，`legalClearance: false`。
- `--release` 仍会因发布清单的未完成项失败；这是预期行为，不应当绕过。
- 待维护者确认：贡献者权利、AI 协作内容的第三方片段核查、媒体权利、真实设备网络观测、Ubuntu 与 Termux 对应源码。

2026-10-03 本地验证快照（同类验证在发布前必须重跑）：

- `release-check --self-test` 与 `check-dco --self-test` 通过。
- 文本候选扫描：422 个文件，0 个 unread，0 命中；媒体 / 历史 / APK 仍不在此范围。
- JVM 测试：53 个测试套件，251 个测试，0 失败 / 0 错误 / 3 跳过（联网探测条件跳过）。
- `lintDebug`：0 error，52 个既有 warning。
- inventory 校验：518 个证据文件、508 个 CycloneDX 组件、91 个 Ubuntu 版权关联、172 个 Gradle artifact；`legalClearance: false`。
- 对应源码收集：9 个文件写入 `output/corresponding-source/`，`sha256sum -c SHA256SUMS` 通过；Ubuntu 源包名列表 67 个，`apt-get source` 产物未包含。
- `--release` 阻塞项从 34 项降到 29 项；剩余项不能由本快照自动关闭。

维护者首次公开前的个人确认项见 [MAINTAINER_ATTESTATION.md](MAINTAINER_ATTESTATION.md)。
