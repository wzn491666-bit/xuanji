#!/usr/bin/env bash
# 收集璇玑 APK 的第三方对应源码（PRoot / Termux 配方 / libtalloc / libandroid-shmem /
# 天枢发行包 / Node 许可与散列 / Ubuntu 包列表）。
#
# 在 WSL 或 Linux 运行：
#   bash scripts/collect-corresponding-source.sh
#
# 默认输出到 output/corresponding-source/（.gitignore 已排除）。脚本只下载公开源码并生成
# SHA256SUMS 与说明；它不完成 Ubuntu apt-get source，也不给出许可结论。发布前必须人工复核。
set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  sed -n '2,15p' "$0"
  exit 0
fi

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
OUT_DIR=${OUT_DIR:-"$HERE/output/corresponding-source"}

PROOT_TAG=${PROOT_TAG:-v5.1.107.95}
PROOT_COMMIT=${PROOT_COMMIT:-d4d2a19081c3c07f75250e4ce2980b9fa2f5720f}
TALLOC_VERSION=${TALLOC_VERSION:-2.4.3}
SHMEM_TAG=${SHMEM_TAG:-v0.7}
NODE_VERSION=${NODE_VERSION:-v24.21.0}
TIANSHU_VERSION=${TIANSHU_VERSION:-3.27.0}
TERMUX_COMMIT=${TERMUX_COMMIT:-de39661946f7e8175b5dd0755121fa28cb0aebd1}
INCLUDE_NODE_SOURCE=${INCLUDE_NODE_SOURCE:-0}

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少命令：$1" >&2; exit 2; }
}
need curl
need sha256sum
need python3
need sort
need xargs

mkdir -p "$OUT_DIR"/{proot,termux-packages,talloc,libandroid-shmem,tianshu,node,ubuntu}

curl_opts=(-fSL --retry 3 --connect-timeout 20)
[[ -n "${PROXY:-}" ]] && curl_opts+=(-x "$PROXY")

fetch() {
  local url=$1 target=$2
  if [[ -s "$target" ]]; then
    echo "已存在：$target"
    return 0
  fi
  echo "下载：$url"
  curl "${curl_opts[@]}" -o "$target.part" "$url"
  mv "$target.part" "$target"
}

echo "== PRoot 源码（Termux 分支 $PROOT_TAG, commit $PROOT_COMMIT）"
fetch "https://github.com/termux/proot/archive/refs/tags/$PROOT_TAG.tar.gz" \
  "$OUT_DIR/proot/proot-$PROOT_TAG.tar.gz"

echo "== Termux 构建配方（commit $TERMUX_COMMIT）"
fetch "https://github.com/termux/termux-packages/archive/$TERMUX_COMMIT.tar.gz" \
  "$OUT_DIR/termux-packages/termux-packages-$TERMUX_COMMIT.tar.gz"

echo "== libtalloc $TALLOC_VERSION 源码"
fetch "https://download.samba.org/pub/talloc/talloc-$TALLOC_VERSION.tar.gz" \
  "$OUT_DIR/talloc/talloc-$TALLOC_VERSION.tar.gz"

echo "== libandroid-shmem $SHMEM_TAG 源码"
fetch "https://github.com/termux/libandroid-shmem/archive/refs/tags/$SHMEM_TAG.tar.gz" \
  "$OUT_DIR/libandroid-shmem/libandroid-shmem-$SHMEM_TAG.tar.gz"

echo "== 天枢 npm 发行包 $TIANSHU_VERSION"
fetch "https://registry.npmjs.org/tianshu-harness/-/tianshu-harness-$TIANSHU_VERSION.tgz" \
  "$OUT_DIR/tianshu/tianshu-harness-$TIANSHU_VERSION.tgz"

echo "== Node $NODE_VERSION 许可与官方散列"
fetch "https://nodejs.org/dist/$NODE_VERSION/SHASUMS256.txt" "$OUT_DIR/node/SHASUMS256.txt"
fetch "https://raw.githubusercontent.com/nodejs/node/$NODE_VERSION/LICENSE" "$OUT_DIR/node/LICENSE"
if [[ "$INCLUDE_NODE_SOURCE" == "1" ]]; then
  fetch "https://nodejs.org/dist/$NODE_VERSION/node-$NODE_VERSION.tar.xz" \
    "$OUT_DIR/node/node-$NODE_VERSION.tar.xz"
fi

echo "== Ubuntu 包列表（从实际 rootfs 清单生成）"
python3 - "$HERE/docs/open-source/inventory/components.json" \
  "$OUT_DIR/ubuntu/ubuntu-packages.txt" "$OUT_DIR/ubuntu/ubuntu-source-packages.txt" <<'PY'
import json
import sys

report_path, binary_path, source_path = sys.argv[1:4]
report = json.load(open(report_path, encoding="utf-8"))
packages = report["runtime"]["ubuntuPackages"]
with open(binary_path, "w", encoding="utf-8") as out:
    out.write("# 实际 rootfs 中记录的 Ubuntu 二进制包（name=version）\n")
    for row in sorted(packages, key=lambda item: item["name"]):
        out.write(f"{row['name']}={row['version']}\n")
with open(source_path, "w", encoding="utf-8") as out:
    out.write("# 从二进制包声明的 source 字段提取；不代表版本已精确对应\n")
    for name in sorted({(row.get("source") or row["name"]) for row in packages}):
        out.write(name + "\n")
print(f"Ubuntu 二进制包：{len(packages)}")
PY

cat > "$OUT_DIR/README.txt" <<EOF
璇玑对应源码收集结果
====================

核对日期：$(date -u +%Y-%m-%dT%H:%M:%SZ)

PRoot 标签：$PROOT_TAG（commit $PROOT_COMMIT）
Termux 配方：$TERMUX_COMMIT
libtalloc：$TALLOC_VERSION
libandroid-shmem：$SHMEM_TAG
Node：$NODE_VERSION
天枢 npm：$TIANSHU_VERSION

本目录内容：
- proot/、termux-packages/、talloc/、libandroid-shmem/：直接对应源码与构建配方。
- tianshu/：npm 发行包，不代表其全部静态依赖；依赖缺口见 components.json。
- node/：官方 SHASUMS256.txt 与 LICENSE；设置 INCLUDE_NODE_SOURCE=1 可追加完整源码。
- ubuntu/：实际 rootfs 记录的二进制包列表与 source 包名列表。

Ubuntu 对应源码尚未下载。应在 Ubuntu 24.04 主机启用 noble / noble-updates /
noble-security 的 deb-src（arm64 使用 ports.ubuntu.com），按 components.json 中的
实际版本执行 apt-get source，并把 .dsc、原始压缩包与 debian/patches 一并保存。

本脚本不完成许可证判断、补丁一致性比对、Ubuntu 源版本固定或 GPL / LGPL
重链接材料整理。发布 APK 前请按 docs/open-source/CORRESPONDING_SOURCE.md 与
RELEASE_CHECKLIST.md 人工复核。
EOF

echo "== 生成 SHA256SUMS"
(
  cd "$OUT_DIR"
  find . -type f ! -name SHA256SUMS ! -name README.txt -print0 \
    | sort -z \
    | xargs -0 sha256sum \
    | sed 's#  \./#  #' > SHA256SUMS
)

echo "完成：$OUT_DIR"
echo "清单：$OUT_DIR/SHA256SUMS"
