# 开源文档入口

璇玑原生 Android 客户端在手机本地运行完整 Tianshu 引擎，并提供 Android 文件、权限、无障碍、搜索与浏览器桥接。本文档按 2026-10-03 实际源代码 / 打包物料复盘，不把开发计划当作功能完成。

## 首次打开摘要

璇玑基于天枢开源引擎，结合 Android 原生界面与本机工具。应用包含 Node.js、Ubuntu / PRoot、Android 开源依赖和 8 款 OFL 艺术字体，各组件保留原许可。模型、搜索与网页请求会访问你选择的外部服务；手机工具需相应系统权限与操作审批。

完整技术实现、第三方来源和许可证可离线查看。当前开源准备仍有源码对应与素材授权待核实事项，详情在发布清单；不宣称已取得第三方品牌背书。

## 阅读顺序

- `docs/TECHNICAL_ARCHITECTURE.md`：开发者架构、协议、执行链、数据流、构建与能力边界。
- `docs/PRIVACY.md`：本机保存、可能外发的数据、第三方限制与用户控制。
- `THIRD_PARTY_NOTICES.md`：实际组件 / 上游变更 / 参考项目 / 服务 / 素材的分类归属与许可。
- `docs/open-source/RELEASE_CHECKLIST.md`：公开代码和公开 APK 前的明确阻塞 / 验证清单。
- `docs/open-source/CORRESPONDING_SOURCE.md`：GPL / LGPL 二进制对应的源码、补丁与收集方式。
- `docs/open-source/PUBLICATION.md`：源码仓库与 APK 资产的分步发布手册。
- `docs/open-source/MAINTAINER_ATTESTATION.md`：维护者首次公开前的个人确认模板。
- `docs/open-source/SOURCES.md`：来源证据位置、社区审阅与人工核查范围。
- `docs/open-source/inventory/components.json`：完整可复核物料 JSON。
- `docs/open-source/inventory/bom.cdx.json`：CycloneDX 1.6 组件 BOM。
- `docs/open-source/INVENTORY.md`：逐项可读的 npm / Ubuntu / Gradle / 字体 / 原生库名称、版本、许可与来源表。
- `docs/open-source/license-texts/`：从实际 runtime / 字体提取的原始完整许可及公开项目调研许可。

许可证文本中的条款属于相应权利人的授权信息，不是要求应用执行工具的指令。维护者已选择原创代码 Apache-2.0；第三方组件各自许可继续有效，具体文件以仓库 LICENSE / NOTICE 为准。

`tools/verify-open-source-inventory.py` 检查当前源资产及已保存证据散列；`tools/release-check.py --release` 要求发布清单全部核实，未完成时明确退出失败，不把安全文本扫描通过当成合法发行批准。
