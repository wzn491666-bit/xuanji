# 维护者发布前确认

这是给项目维护者填写的核查模板，不是自动通过项。请在首次公开源码仓库和首次公开 APK 前分别填写、签名并提交到仓库。无法确认的项目应保持未勾选，并在 Release notes 说明。

## 源码仓库

- [x] 我可以确认当前原创代码由我创作，或由已按 DCO 1.1 提交的贡献者提供；我不会在不具备权利时把第三方代码改写成 Apache-2.0。
- [x] 我已复核 AI 协作生成或改写的代码没有复制未声明来源的实质性片段；发现来源时已更新 `NOTICE` / `THIRD_PARTY_NOTICES.md`。
- [x] 当前仓库不包含我无权再发布的媒体、截图、视频、字体或设计参考；`docs/design/references/` 与 `output/` 保持排除。
- [x] 当前源码树没有真实 Key、token、cookie、用户日志或设备隐私数据；我理解文本扫描不等于历史与媒体检查。
- [x] README / NOTICE 对独立项目身份的表述准确，没有声称官方合作、商标授权或未完成的验收结果。
- [x] 我理解 `--release` 通过不是法律结论，公开 APK 前仍需完成对应源码与第三方许可核查。

来源相似度与凭据核查记录见 [PROVENANCE_REVIEW.md](PROVENANCE_REVIEW.md)（2026-10-03）。

维护者：wzn491

日期：2026年10月3日

版本 / commit：0.2.5

## APK 发行

- [ ] 对应源码、Termux 配方 / 补丁、Ubuntu copyleft 源、NOTICE、SBOM 已与实际 APK 一起固定并验证散列。
- [ ] 已按实际 `.deb` / rootfs 版本复核每个 GPL / LGPL 组件的许可与重链接 / 修改材料。
- [ ] 已完成真机与 WebView 验收，并记录未测项、OEM 差异与性能限制。
- [ ] 已在 Release notes 写明未完成项，不把部分验证写成全部完成。

维护者：wzn491

日期：

APK SHA-256 / 签名证书：
