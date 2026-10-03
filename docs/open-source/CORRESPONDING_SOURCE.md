# 对应源码与构建证据

核对日期：2026-10-03。本文记录随 APK 分发的第三方二进制如何对应到可获得的源码、版本与补丁入口。它不作出法律结论；每个组件在正式发布前仍需由维护者按实际下载内容复核许可证与补丁。

源码仓库（GitHub）与可运行 APK 是两种不同分发：公开源码不包含 runtime 二进制；公开 APK 必须同时提供本文所列的对应源码、许可与修正材料。当前状态见 [发布清单](RELEASE_CHECKLIST.md)。

## 直接二进制

| 组件 | 随包文件与 SHA-256 | 版本 | 上游源码入口 | 许可族（待按源码文本复核） |
| --- | --- | --- | --- | --- |
| PRoot | `app/src/main/assets/runtime/proot`<br>`98a5f86aad8d7f91e36ee039c64c8f5ac20738e545c883c559bf93e9b94e7bfa` | Termux `proot_5.1.107.95_aarch64.deb` | `https://github.com/termux/proot` 标签 `v5.1.107.95`（commit `d4d2a19081c3c07f75250e4ce2980b9fa2f5720f`）；构建配方与补丁在 `termux/termux-packages` 的 `packages/proot` | GPL（以源码内 COPYING 为准） |
| PRoot loader | `app/src/main/assets/runtime/loader`<br>`cbdef0e652c2b78af25d867e1719fdebbb0915e25aae2dd35b3b5c1835f6b551` | 同上 | 同上（loader 由 proot 包提供） | 同上 |
| libtalloc | `app/src/main/assets/runtime/libtalloc.so.2`<br>`3c9b207c0a6ea2896b7523e03f55d9ab0d9e88baa115d4c32b84058ff4246fbb` | Termux `libtalloc_2.4.3_aarch64.deb` | `https://download.samba.org/pub/talloc/talloc-2.4.3.tar.gz`；上游仓库 `https://git.samba.org/?p=talloc.git` | LGPL（以源码内 COPYING / LICENSE 为准） |
| libandroid-shmem | `app/src/main/assets/runtime/libandroid-shmem.so`<br>`84475798e07c8174dbbfaec70a827fdb02f19ffa69a589380c13e7507fd0e731` | Termux `libandroid-shmem_0.7_aarch64.deb` | `https://github.com/termux/libandroid-shmem` 标签 `v0.7` | 宽松许可（以仓库 LICENSE 为准） |
| Ubuntu 24.04.5 base arm64 | `app/src/main/assets/runtime/rootfs.tgz`<br>`c4269295b5b3a609c383a53f5044c8bf66979d62a99c387da7dcead7683b8384` | Ubuntu Base 24.04.5，含 91 个记录在案的二进制包 | `https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz`；各包源码通过 Ubuntu 24.04 的 `deb-src` 获取 | 多许可集合，不能写成单一许可证 |
| ripgrep | rootfs 内 `/usr/bin/rg`（打包在 `rootfs.tgz`） | Ubuntu `ripgrep_14.1.1-1_arm64.deb` | `http://ports.ubuntu.com/pool/universe/r/rust-ripgrep/` 对应版本源码包 | MIT / Unlicense 等，按实际 `copyright` 复核 |
| Node.js | rootfs 内 `/opt/node`（打包在 `rootfs.tgz`） | v24.21.0 linux-arm64 | 发行包 `node-v24.21.0-linux-arm64.tar.xz` SHA-256 `6ad1325edbdb5649c379b75a237147a666c95d4f9ae8d340fef2d1575d289ad2`；源码 `node-v24.21.0.tar.xz` SHA-256 `a6f54defb6fd7c84f41dba13d61e78e9b4e0961712cf61f29715c05f5ced94fc`（`https://nodejs.org/dist/v24.21.0/`） | MIT 与发行包内多组件通知；源码不是 MIT 的强制条件，但发行包必须保留 LICENSE |
| Tianshu-harness | rootfs 内 `/opt/tianshu`（打包在 `rootfs.tgz`） | npm `tianshu-harness@3.27.0` | npm 元数据 `gitHead` `7347ba4703d5d30375fa3cb6cdfa1897a3024deb`；npm tarball `https://registry.npmjs.org/tianshu-harness/-/tianshu-harness-3.27.0.tgz`，shasum `a573b1d19d38e51166154cff3fece4ca0e0297f3`，integrity `sha512-sRTJRYLWzfWFfstBjj9TrqCzXbGODS9P1Ym4MqnYwCrJzcpdc/eAB/4OhsW0IHfYj5OX/xskaSf8JZcRFaX/Jg==` | Apache-2.0（上游仓库 `huiliyi37/Tianshu-harness`） |

## Termux 构建配方固定点

Termux 的 `.deb` 由 `termux/termux-packages` 中的配方、补丁与版本文件构建。2026-10-03 核对的路径级最新提交：

| 包 | 配方路径 | 固定提交 |
| --- | --- | --- |
| proot | `packages/proot` | `de39661946f7e8175b5dd0755121fa28cb0aebd1` |
| libtalloc | `packages/libtalloc` | `bddd9721910e56bc36412a764b4b4f7bbdbb1eb1` |
| libandroid-shmem | `packages/libandroid-shmem` | `b25e257208da6d2e8b558b8a2b51762158a2e806` |

这些提交是核对时刻的路径固定点，不保证与实际 `.deb` 的构建流水线完全一致。发布前应下载对应源码与补丁、逐项比对 `.deb` 内版本文件，并把确认后的提交写入本文件的发布副本。

## Ubuntu 源码

`rootfs.tgz` 包含 91 个已记录的 Ubuntu 二进制包，名称、版本、主页与 `copyright` 证据在 `docs/open-source/inventory/components.json`。Ubuntu 是多个不同许可证包的集合；GPL / LGPL / 其它 copyleft 包的对应源码应按“实际安装版本”分别获取，不能用一个 Ubuntu 镜像或一个宽松许可证概括。

获取方式（在 Ubuntu 24.04 环境执行）：

1. 使用 Ubuntu 官方 `noble` / `noble-updates` / `noble-security` 的 `deb-src` 源；ARM64 主机使用 `ports.ubuntu.com`。
2. 读取本仓库生成的 `ubuntu-packages.txt`（`scripts/collect-corresponding-source.sh` 会从 `components.json` 生成 `name=version` 列表）。
3. 对需要提供对应源码的包执行 `apt-get source <name>=<version>`，保存 `.dsc`、原始压缩包与 `debian/patches`。
4. 将下载物、SHA-256 清单和实际 rootfs 的 `dpkg` 状态一起挂到 GitHub Release。源码 URL 不能只指向移动的 `main` 分支。

## 发布资产建议

每个公开 APK 至少同时提供：

- APK 本体与 SHA-256 / 签名证书指纹 / versionCode。
- `corresponding-source/`：PRoot 源码与 Termux 配方、libtalloc 源码、libandroid-shmem 源码、Ubuntu copyleft 源码包、Node / 天枢发行与许可证、`SHA256SUMS`、构建/重链接说明。
- `bom.cdx.json`、`components.json`、`INVENTORY.md`、`THIRD_PARTY_NOTICES.md`、`NOTICE`、`LICENSE`、离线授权原文。
- 真机测试证据与仍存在的已知限制。

`scripts/collect-corresponding-source.sh` 会下载可直接获取的源码并生成 Ubuntu 包列表；它不会自动完成全部许可证判断或 Ubuntu 源码抓取。维护者必须在发布前运行、复核并记录结果。

## 2026-10-03 本地收集结果

脚本已在 WSL Ubuntu 中完整执行，输出到被忽略的 `output/corresponding-source/`；`sha256sum -c SHA256SUMS` 通过。已核对压缩包内确实包含源码与许可文本：

| 文件 | SHA-256 | 已核对内容 |
| --- | --- | --- |
| `proot/proot-v5.1.107.95.tar.gz` | `f76716a9531c25be6f0bf7eb21003f0ebec3f832085a7fc233c3f475e75e4bf5` | 含 `COPYING` 与完整源码树 |
| `termux-packages/termux-packages-de39661946f7e8175b5dd0755121fa28cb0aebd1.tar.gz` | `8a8a9b6c197f8f9b286f677b701ab88a33a93eb0a97e42c752dcf50fe8e19952` | 含 `packages/proot/build.sh` 等配方 |
| `talloc/talloc-2.4.3.tar.gz` | `dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd` | 含 `LICENSE` 与完整源码 |
| `libandroid-shmem/libandroid-shmem-v0.7.tar.gz` | `1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867` | 含 `LICENSE`、`Makefile` |
| `tianshu/tianshu-harness-3.27.0.tgz` | `75125b4c92dfc39f287a53fbe3bbe86d4705247cefff5f1ac52ad198fde9c6b0` | npm 发行包 |
| `node/SHASUMS256.txt` | `f410428039e2c922a14058df067a4482691c9304a5c01a75847f9f3f2d3307f6` | Node 官方散列清单 |
| `node/LICENSE` | `5888dbb9a1d2b18f2c3e6c5f6af1b39de658372b402a0577b002777f14c62ace` | Node 发行许可文本 |
| `ubuntu/ubuntu-packages.txt` | `9354f71d693b64b64c201db49881cfdb7171253e2db279a186a529bf11359a7a` | 91 个二进制包（含表头行） |
| `ubuntu/ubuntu-source-packages.txt` | `facb0039d6a9d89c406dd6a722616c44227b1f09c96d5f8b36040706e175affa` | 67 个唯一 source 包名 |

该结果只证明下载与散列自洽，不证明实际 `.deb` 与配方 commit 的逐补丁一致，也不包含 Ubuntu `apt-get source` 产物。公开发布仍需完成上一节与发布清单中的剩余事项。

## 尚未完成

- 实际 `.deb` 的精确 Termux 构建流水线 commit 与逐补丁比对。
- Ubuntu copyleft 源的完整下载、版本核对与长期托管。
- 静态库 / WASM / 原生依赖和运行时链接条件的逐项复核。
- 每个 Maven / npm 传递依赖的完整许可文本与 NOTICE 核对。

这些事项仍由 [发布清单](RELEASE_CHECKLIST.md) 阻塞公开 APK 分发；公开源码仓库不受运行时二进制义务影响，但仍需完成清单中的源码与素材核查。
