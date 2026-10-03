# 贡献指南

欢迎提交可复现问题、实现和技术文档。请先阅读 [README](README.md)、[技术架构](docs/TECHNICAL_ARCHITECTURE.md) 与 [第三方来源说明](THIRD_PARTY_NOTICES.md)。公开仓库中的内容会长期保存，提交前应移除个人凭据、用户数据和无权再发布的素材。

## 开发与验证

使用仓库 Gradle Wrapper；依赖版本集中在 `gradle/libs.versions.toml`。不要提交本机 SDK 路径、构建输出、签名材料、真实 API Key 或 runtime 二进制。

```bash
python tools/release-check.py --self-test
python tools/release-check.py
bash gradlew :app:testDebugUnitTest --max-workers=2
```

涉及天枢适配时，先核对相应上游版本与社区问题，再运行真实 runtime bundle 检查。涉及浏览器操作时，分别验证 DOM 脚本与实际 Android WebView；桌面浏览器检查不能替代手机验收。涉及权限、执行审批或持久化时，应覆盖拒绝、取消、并发和恢复情形。

PR 应说明问题、实现、可复现验证、手机测试范围和已知限制。改动保持聚焦；不要同时重写无关模块。不要仅凭 UI 有按钮就声明执行能力已经接通。

## 来源与许可

原创贡献按根 `LICENSE` 中的 Apache-2.0 提交，除非相关文件已有其它明确许可。复制、改写、移植、嵌入第三方内容时，必须提供原始仓库 / 文件、版本或 commit、适用许可证、版权、修改说明与需要保留的 NOTICE，并更新第三方文档及物料证据。

引用项目供调研和实际复制代码是两件事。公开可见不等于获准复制；不得把 GPL、LGPL、OFL 或其它授权内容静默改为 Apache。许可不明、缺少可对应源码或没有明确再发布权的代码 / 媒体应先暂停纳入发行版。AI 协作生成内容同样需要来源和权利核查。

## Developer Certificate of Origin

每个贡献提交应以 `git commit -s` 签署 [DCO 1.1](https://developercertificate.org/)，表示你有权提交该贡献，且接受 DCO 对公开记录的说明。签署不转移版权，也不能替代第三方许可审查。请使用可归属你的身份和邮箱；GitHub noreply 邮箱可用于隐私保护。

CI 在 PR 上用 `tools/check-dco.py` 检查每个非合并提交的 sign-off 邮箱是否与提交作者一致；本地可先运行 `python tools/check-dco.py --self-test`。

```text
Signed-off-by: Your Name <your-address@example.com>
```

完整 DCO 1.1 原文如下，保持原文不变：

```text
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.


Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```
