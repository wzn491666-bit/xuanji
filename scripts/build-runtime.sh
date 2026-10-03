#!/usr/bin/env bash
# 构建璇玑的手机运行环境：proot + Ubuntu 24.04 arm64 + Node.js 24 + 天枢
# 在 WSL Ubuntu 里运行：  bash scripts/build-runtime.sh
# 产物写到 app/src/main/assets/runtime/
#
# 不需要 root、不需要 arm64 模拟器：所有东西都是下载现成的 arm64 包解压组装，
# 天枢用 npm 的 --cpu=arm64 --os=linux 安装 arm64 版本的原生依赖。
set -euo pipefail

UBUNTU=24.04.5
NODE=v24.21.0
TIANSHU=3.27.0
PROXY=${PROXY:-}                      # 例如 PROXY=http://127.0.0.1:7897
TERMUX=https://packages-cf.termux.dev/apt/termux-main

HERE=$(cd "$(dirname "$0")/.." && pwd)
OUT=$HERE/app/src/main/assets/runtime
WORK=${WORK:-$HOME/xuanji-runtime-build}
curl() { command curl ${PROXY:+-x "$PROXY"} -fSL --retry 3 "$@"; }

rm -rf "$WORK" && mkdir -p "$WORK/rootfs" "$WORK/deb" "$OUT"
cd "$WORK"

# 记录本次构建实际下载的输入散列，随 runtime 一起保留，便于对应源码与复现核对。
command -v sha256sum >/dev/null || { echo "缺少 sha256sum，无法记录 runtime 输入散列" >&2; exit 2; }
INPUT_MANIFEST=$OUT/runtime-inputs.sha256
: > "$INPUT_MANIFEST"
record_input() {
  printf '%s  %s\n' "$(sha256sum "$2" | awk '{print $1}')" "$1" >> "$INPUT_MANIFEST"
}

echo "== 1/5 proot（Termux 官方 aarch64 包）"
for pkg in p/proot/proot_5.1.107.95 libt/libtalloc/libtalloc_2.4.3 liba/libandroid-shmem/libandroid-shmem_0.7; do
  curl -so pkg.deb "$TERMUX/pool/main/${pkg}_aarch64.deb"
  record_input "$TERMUX/pool/main/${pkg}_aarch64.deb" pkg.deb
  ar p pkg.deb data.tar.xz | tar -xJ -C deb
done
T=deb/data/data/com.termux/files/usr
cp "$T/bin/proot" "$OUT/proot"
cp "$T/libexec/proot/loader" "$OUT/loader"
cp -L "$T/lib/libtalloc.so.2" "$OUT/libtalloc.so.2"
cp -L "$T/lib/libandroid-shmem.so" "$OUT/libandroid-shmem.so"

echo "== 2/5 Ubuntu $UBUNTU base arm64"
curl -so ubuntu.tar.gz "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-$UBUNTU-base-arm64.tar.gz"
record_input "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-$UBUNTU-base-arm64.tar.gz" ubuntu.tar.gz
tar -xzf ubuntu.tar.gz -C rootfs --exclude='dev/*'

echo "== 3/5 Node.js $NODE linux-arm64 → /opt/node"
curl -so node.tar.xz "https://nodejs.org/dist/$NODE/node-$NODE-linux-arm64.tar.xz"
record_input "https://nodejs.org/dist/$NODE/node-$NODE-linux-arm64.tar.xz" node.tar.xz
mkdir -p rootfs/opt/node && tar -xJf node.tar.xz -C rootfs/opt/node --strip-components=1

echo "== 4/5 天枢 $TIANSHU（arm64 原生依赖）→ /opt/tianshu"
# 用一份 x64 的 Node 24 来执行 npm（系统自带的 npm 可能太旧，不支持 --cpu/--os）
curl -so hostnode.tar.xz "https://nodejs.org/dist/$NODE/node-$NODE-linux-x64.tar.xz"
record_input "https://nodejs.org/dist/$NODE/node-$NODE-linux-x64.tar.xz" hostnode.tar.xz
mkdir -p hostnode && tar -xJf hostnode.tar.xz -C hostnode --strip-components=1
export PATH=$WORK/hostnode/bin:$PATH
mkdir -p rootfs/opt/tianshu && cd rootfs/opt/tianshu
npm init -y >/dev/null
# --ignore-scripts：安装脚本是 x86 上跑的，arm64 的预编译包由 optionalDependencies 按 --cpu 选好
npm install --omit=dev --cpu=arm64 --os=linux --libc=glibc --ignore-scripts \
  ${PROXY:+--proxy "$PROXY" --https-proxy "$PROXY"} "tianshu-harness@$TIANSHU"
cd "$WORK"
record_input "npm-lock:tianshu-harness@$TIANSHU" "$WORK/rootfs/opt/tianshu/package-lock.json"
mkdir -p rootfs/usr/local/bin
ln -sf /opt/tianshu/node_modules/tianshu-harness/dist/cli/entry.js rootfs/usr/local/bin/tianshu
ln -sf /opt/node/bin/node rootfs/usr/local/bin/node
# 天枢搜索文件用 ripgrep，没有它会退化成慢速搜索——直接用 Ubuntu 的 arm64 包
curl -so rg.deb "http://ports.ubuntu.com/pool/universe/r/rust-ripgrep/ripgrep_14.1.1-1_arm64.deb"
record_input "http://ports.ubuntu.com/pool/universe/r/rust-ripgrep/ripgrep_14.1.1-1_arm64.deb" rg.deb
dpkg-deb -x rg.deb rootfs

echo "== 5/5 打包"
# Ubuntu 镜像自带的 apt 源默认指向 ports.ubuntu.com，换成国内镜像方便用户之后 apt install
sed -i 's#http://ports.ubuntu.com/ubuntu-ports#https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports#' \
  rootfs/etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || true
tar -czf "$OUT/rootfs.tgz" -C rootfs .
echo "ubuntu-$UBUNTU-node-$NODE-tianshu-$TIANSHU-runtime2" > "$OUT/version"
echo "输入散列清单：$INPUT_MANIFEST"
ls -la "$OUT"
