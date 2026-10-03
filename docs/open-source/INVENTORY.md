# 璇玑实际组件清单

此表从本地打包物料自动生成。许可字段是原始声明 / 证据索引，不是授权结论；未知版本保留未知，不据包名推断。

生成时间：`2026-10-03T02:21:30.470104Z`。精确数据与完整 notice 路径见 `inventory/components.json`。

## 运行环境归档

- 文件：`app/src/main/assets/runtime/rootfs.tgz`
- SHA-256：`c4269295b5b3a609c383a53f5044c8bf66979d62a99c387da7dcead7683b8384`

以下独立二进制与 version 文件来自实际 APK assets。对应源码 / 许可证据见 THIRD_PARTY_NOTICES 和发布清单。

| 文件 | 字节 | SHA-256 |
| --- | --- | --- |
| app/src/main/assets/runtime/libandroid-shmem.so | 14432 | 84475798e07c8174dbbfaec70a827fdb02f19ffa69a589380c13e7507fd0e731 |
| app/src/main/assets/runtime/libtalloc.so.2 | 31440 | 3c9b207c0a6ea2896b7523e03f55d9ab0d9e88baa115d4c32b84058ff4246fbb |
| app/src/main/assets/runtime/loader | 18136 | cbdef0e652c2b78af25d867e1719fdebbb0915e25aae2dd35b3b5c1835f6b551 |
| app/src/main/assets/runtime/proot | 247408 | 98a5f86aad8d7f91e36ee039c64c8f5ac20738e545c883c559bf93e9b94e7bfa |
| app/src/main/assets/runtime/version | 53 | 3465ff00b39d4a8e27563f33e2e3a57a3b2917f09a3c18bef16a101609ab132a |

## 实际 npm 包

包含 Node 官方发行包自带 npm、Tianshu 安装依赖与它的 vendored runtime。这里只列归档中真实存在的 package.json，package-lock 中未装入的包不算已分发。

| 名称 | 版本 | 原声明许可 | 分发类别 | 声明来源 |
| --- | --- | --- | --- | --- |
| corepack | 0.36.0 | MIT | node-bundled-tool | https://github.com/nodejs/corepack.git |
| @gar/promise-retry | 1.0.3 | MIT | node-bundled-tool | git://github.com/wraithgar/node-promise-retry.git |
| @isaacs/fs-minipass | 4.0.1 | ISC | node-bundled-tool | https://github.com/npm/fs-minipass.git |
| @isaacs/string-locale-compare | 1.1.0 | ISC | node-bundled-tool | https://github.com/isaacs/string-locale-compare |
| @npmcli/agent | 4.0.2 | ISC | node-bundled-tool | https://github.com/npm/agent.git |
| @npmcli/arborist | 9.9.1 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| @npmcli/config | 10.12.0 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| @npmcli/fs | 5.0.0 | ISC | node-bundled-tool | https://github.com/npm/fs.git |
| @npmcli/git | 7.0.2 | ISC | node-bundled-tool | https://github.com/npm/git.git |
| @npmcli/installed-package-contents | 4.0.0 | ISC | node-bundled-tool | https://github.com/npm/installed-package-contents.git |
| @npmcli/map-workspaces | 5.0.3 | ISC | node-bundled-tool | https://github.com/npm/map-workspaces.git |
| @npmcli/metavuln-calculator | 9.0.3 | ISC | node-bundled-tool | https://github.com/npm/metavuln-calculator.git |
| @npmcli/name-from-folder | 4.0.0 | ISC | node-bundled-tool | https://github.com/npm/name-from-folder.git |
| @npmcli/node-gyp | 5.0.0 | ISC | node-bundled-tool | https://github.com/npm/node-gyp.git |
| @npmcli/package-json | 7.0.5 | ISC | node-bundled-tool | https://github.com/npm/package-json.git |
| @npmcli/promise-spawn | 9.0.1 | ISC | node-bundled-tool | https://github.com/npm/promise-spawn.git |
| @npmcli/query | 5.0.0 | ISC | node-bundled-tool | https://github.com/npm/query.git |
| @npmcli/redact | 4.0.0 | ISC | node-bundled-tool | https://github.com/npm/redact.git |
| @npmcli/run-script | 10.0.4 | ISC | node-bundled-tool | https://github.com/npm/run-script.git |
| @sigstore/bundle | 4.0.0 | Apache-2.0 | node-bundled-tool | https://github.com/sigstore/sigstore-js.git |
| @sigstore/core | 3.2.1 | Apache-2.0 | node-bundled-tool | https://github.com/sigstore/sigstore-js.git |
| @sigstore/protobuf-specs | 0.5.1 | Apache-2.0 | node-bundled-tool | https://github.com/sigstore/protobuf-specs.git |
| @sigstore/sign | 4.1.1 | Apache-2.0 | node-bundled-tool | https://github.com/sigstore/sigstore-js.git |
| @sigstore/tuf | 4.0.2 | Apache-2.0 | node-bundled-tool | https://github.com/sigstore/sigstore-js.git |
| @sigstore/verify | 3.1.1 | Apache-2.0 | node-bundled-tool | https://github.com/sigstore/sigstore-js.git |
| @tufjs/canonical-json | 2.0.0 | MIT | node-bundled-tool | https://github.com/theupdateframework/tuf-js.git |
| @tufjs/models | 4.1.0 | MIT | node-bundled-tool | https://github.com/theupdateframework/tuf-js.git |
| abbrev | 4.0.0 | ISC | node-bundled-tool | https://github.com/npm/abbrev-js.git |
| agent-base | 7.1.4 | MIT | node-bundled-tool | https://github.com/TooTallNate/proxy-agents.git |
| aproba | 2.1.0 | ISC | node-bundled-tool | https://github.com/iarna/aproba |
| archy | 1.0.0 | MIT | node-bundled-tool | http://github.com/substack/node-archy.git |
| balanced-match | 4.0.4 | MIT | node-bundled-tool | git://github.com/juliangruber/balanced-match.git |
| bin-links | 6.0.2 | ISC | node-bundled-tool | https://github.com/npm/bin-links.git |
| binary-extensions | 3.1.0 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/binary-extensions/package.json |
| brace-expansion | 5.0.7 | MIT | node-bundled-tool | https://github.com/juliangruber/brace-expansion.git |
| cacache | 20.0.4 | ISC | node-bundled-tool | https://github.com/npm/cacache.git |
| chalk | 5.6.2 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/chalk/package.json |
| chownr | 3.0.0 | BlueOak-1.0.0 | node-bundled-tool | git://github.com/isaacs/chownr.git |
| ci-info | 4.4.0 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/ci-info/package.json |
| cidr-regex | 5.0.5 | BSD-2-Clause | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/cidr-regex/package.json |
| cmd-shim | 8.0.0 | ISC | node-bundled-tool | https://github.com/npm/cmd-shim.git |
| common-ancestor-path | 2.0.0 | BlueOak-1.0.0 | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/common-ancestor-path/package.json |
| cssesc | 3.0.0 | MIT | node-bundled-tool | https://github.com/mathiasbynens/cssesc.git |
| debug | 4.4.3 | MIT | node-bundled-tool | git://github.com/debug-js/debug.git |
| diff | 8.0.4 | BSD-3-Clause | node-bundled-tool | https://github.com/kpdecker/jsdiff.git |
| env-paths | 2.2.1 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/env-paths/package.json |
| exponential-backoff | 3.1.3 | Apache-2.0 | node-bundled-tool | https://github.com/coveooss/exponential-backoff.git |
| fastest-levenshtein | 1.0.16 | MIT | node-bundled-tool | https://github.com/ka-weihe/fastest-levenshtein.git |
| fs-minipass | 3.0.3 | ISC | node-bundled-tool | https://github.com/npm/fs-minipass.git |
| glob | 13.0.6 | BlueOak-1.0.0 | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/glob/package.json |
| graceful-fs | 4.2.11 | ISC | node-bundled-tool | https://github.com/isaacs/node-graceful-fs |
| hosted-git-info | 9.0.3 | ISC | node-bundled-tool | https://github.com/npm/hosted-git-info.git |
| http-cache-semantics | 4.2.0 | BSD-2-Clause | node-bundled-tool | https://github.com/kornelski/http-cache-semantics.git |
| http-proxy-agent | 7.0.2 | MIT | node-bundled-tool | https://github.com/TooTallNate/proxy-agents.git |
| https-proxy-agent | 7.0.6 | MIT | node-bundled-tool | https://github.com/TooTallNate/proxy-agents.git |
| iconv-lite | 0.7.2 | MIT | node-bundled-tool | https://github.com/pillarjs/iconv-lite.git |
| ignore-walk | 8.0.0 | ISC | node-bundled-tool | https://github.com/npm/ignore-walk.git |
| ini | 6.0.0 | ISC | node-bundled-tool | https://github.com/npm/ini.git |
| init-package-json | 8.2.5 | ISC | node-bundled-tool | https://github.com/npm/init-package-json.git |
| ip-address | 10.2.0 | MIT | node-bundled-tool | git://github.com/beaugunderson/ip-address.git |
| is-cidr | 6.0.4 | BSD-2-Clause | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/is-cidr/package.json |
| isexe | 4.0.0 | BlueOak-1.0.0 | node-bundled-tool | https://github.com/isaacs/isexe |
| json-parse-even-better-errors | 5.0.0 | MIT | node-bundled-tool | https://github.com/npm/json-parse-even-better-errors.git |
| json-stringify-nice | 1.1.4 | ISC | node-bundled-tool | https://github.com/isaacs/json-stringify-nice |
| jsonparse | 1.3.1 | MIT | node-bundled-tool | http://github.com/creationix/jsonparse.git |
| just-diff-apply | 5.5.0 | MIT | node-bundled-tool | https://github.com/angus-c/just |
| just-diff | 6.0.2 | MIT | node-bundled-tool | https://github.com/angus-c/just |
| libnpmaccess | 10.0.3 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmdiff | 8.1.12 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmexec | 10.3.2 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmfund | 7.0.26 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmorg | 8.0.1 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmpack | 9.1.12 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmpublish | 11.2.0 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmsearch | 9.0.1 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmteam | 8.0.2 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| libnpmversion | 8.0.4 | ISC | node-bundled-tool | https://github.com/npm/cli.git |
| lru-cache | 11.5.1 | BlueOak-1.0.0 | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/lru-cache/package.json |
| make-fetch-happen | 15.0.6 | ISC | node-bundled-tool | https://github.com/npm/make-fetch-happen.git |
| minimatch | 10.2.5 | BlueOak-1.0.0 | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/minimatch/package.json |
| minipass-collect | 2.0.1 | ISC | node-bundled-tool | https://github.com/isaacs/minipass-collect |
| minipass-fetch | 5.0.2 | MIT | node-bundled-tool | https://github.com/npm/minipass-fetch.git |
| minipass-flush | 1.0.6 | BlueOak-1.0.0 | node-bundled-tool | https://github.com/isaacs/minipass-flush.git |
| minipass | 3.3.6 | ISC | node-bundled-tool | https://github.com/isaacs/minipass.git |
| yallist | 4.0.0 | ISC | node-bundled-tool | https://github.com/isaacs/yallist.git |
| minipass-pipeline | 1.2.4 | ISC | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/minipass-pipeline/package.json |
| minipass-sized | 2.0.0 | ISC | node-bundled-tool | https://github.com/isaacs/minipass-sized.git |
| minipass | 7.1.3 | BlueOak-1.0.0 | node-bundled-tool | https://github.com/isaacs/minipass |
| minizlib | 3.1.0 | MIT | node-bundled-tool | https://github.com/isaacs/minizlib.git |
| ms | 2.1.3 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/ms/package.json |
| mute-stream | 3.0.0 | ISC | node-bundled-tool | https://github.com/npm/mute-stream.git |
| negotiator | 1.0.0 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/negotiator/package.json |
| node-gyp | 12.4.0 | MIT | node-bundled-tool | git://github.com/nodejs/node-gyp.git |
| nopt | 9.0.0 | ISC | node-bundled-tool | https://github.com/npm/nopt.git |
| npm-audit-report | 7.0.0 | ISC | node-bundled-tool | https://github.com/npm/npm-audit-report.git |
| npm-bundled | 5.0.0 | ISC | node-bundled-tool | https://github.com/npm/npm-bundled.git |
| npm-install-checks | 8.0.0 | BSD-2-Clause | node-bundled-tool | https://github.com/npm/npm-install-checks.git |
| npm-normalize-package-bin | 5.0.0 | ISC | node-bundled-tool | https://github.com/npm/npm-normalize-package-bin.git |
| npm-package-arg | 13.0.2 | ISC | node-bundled-tool | https://github.com/npm/npm-package-arg.git |
| npm-packlist | 10.0.4 | ISC | node-bundled-tool | https://github.com/npm/npm-packlist.git |
| npm-pick-manifest | 11.0.3 | ISC | node-bundled-tool | https://github.com/npm/npm-pick-manifest.git |
| npm-profile | 12.0.2 | ISC | node-bundled-tool | https://github.com/npm/npm-profile.git |
| npm-registry-fetch | 19.1.1 | ISC | node-bundled-tool | https://github.com/npm/npm-registry-fetch.git |
| npm-user-validate | 4.0.0 | BSD-2-Clause | node-bundled-tool | https://github.com/npm/npm-user-validate.git |
| p-map | 7.0.4 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/p-map/package.json |
| pacote | 21.5.1 | ISC | node-bundled-tool | https://github.com/npm/pacote.git |
| parse-conflict-json | 5.0.1 | ISC | node-bundled-tool | https://github.com/npm/parse-conflict-json.git |
| path-scurry | 2.0.2 | BlueOak-1.0.0 | node-bundled-tool | https://github.com/isaacs/path-scurry |
| postcss-selector-parser | 7.1.4 | MIT | node-bundled-tool | https://github.com/postcss/postcss-selector-parser.git |
| proc-log | 6.1.0 | ISC | node-bundled-tool | https://github.com/npm/proc-log.git |
| proggy | 4.0.0 | ISC | node-bundled-tool | https://github.com/npm/proggy.git |
| promise-all-reject-late | 1.0.1 | ISC | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/promise-all-reject-late/package.json |
| promise-call-limit | 3.0.2 | ISC | node-bundled-tool | https://github.com/isaacs/promise-call-limit |
| promzard | 3.0.1 | ISC | node-bundled-tool | https://github.com/npm/promzard.git |
| qrcode-terminal | 0.12.0 | [{"type": "Apache 2.0"}] | node-bundled-tool | https://github.com/gtanner/qrcode-terminal |
| read-cmd-shim | 6.0.0 | ISC | node-bundled-tool | https://github.com/npm/read-cmd-shim.git |
| read | 5.0.1 | ISC | node-bundled-tool | https://github.com/npm/read.git |
| safer-buffer | 2.1.2 | MIT | node-bundled-tool | https://github.com/ChALkeR/safer-buffer.git |
| semver | 7.8.5 | ISC | node-bundled-tool | https://github.com/npm/node-semver.git |
| signal-exit | 4.1.0 | ISC | node-bundled-tool | https://github.com/tapjs/signal-exit.git |
| sigstore | 4.1.1 | Apache-2.0 | node-bundled-tool | https://github.com/sigstore/sigstore-js.git |
| smart-buffer | 4.2.0 | MIT | node-bundled-tool | https://github.com/JoshGlazebrook/smart-buffer.git |
| socks-proxy-agent | 8.0.5 | MIT | node-bundled-tool | https://github.com/TooTallNate/proxy-agents.git |
| socks | 2.8.9 | MIT | node-bundled-tool | https://github.com/JoshGlazebrook/socks.git |
| spdx-exceptions | 2.5.0 | CC-BY-3.0 | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/spdx-exceptions/package.json |
| spdx-expression-parse | 4.0.0 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/spdx-expression-parse/package.json |
| spdx-license-ids | 3.0.23 | CC0-1.0 | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/spdx-license-ids/package.json |
| ssri | 13.0.1 | ISC | node-bundled-tool | https://github.com/npm/ssri.git |
| supports-color | 10.2.2 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/supports-color/package.json |
| tar | 7.5.19 | BlueOak-1.0.0 | node-bundled-tool | https://github.com/isaacs/node-tar.git |
| text-table | 0.2.0 | MIT | node-bundled-tool | git://github.com/substack/text-table.git |
| tiny-relative-date | 2.0.2 | MIT | node-bundled-tool | https://github.com/wildlyinaccurate/relative-date.git |
| fdir | 6.5.0 | MIT | node-bundled-tool | https://github.com/thecodrr/fdir.git |
| picomatch | 4.0.4 | MIT | node-bundled-tool | opt/node/lib/node_modules/npm/node_modules/tinyglobby/node_modules/picomatch/package.json |
| tinyglobby | 0.2.17 | MIT | node-bundled-tool | https://github.com/SuperchupuDev/tinyglobby.git |
| treeverse | 3.0.0 | ISC | node-bundled-tool | https://github.com/npm/treeverse.git |
| tuf-js | 4.1.0 | MIT | node-bundled-tool | https://github.com/theupdateframework/tuf-js.git |
| undici | 6.27.0 | MIT | node-bundled-tool | https://github.com/nodejs/undici.git |
| util-deprecate | 1.0.2 | MIT | node-bundled-tool | git://github.com/TooTallNate/util-deprecate.git |
| validate-npm-package-name | 7.0.2 | ISC | node-bundled-tool | https://github.com/npm/validate-npm-package-name.git |
| walk-up-path | 4.0.0 | ISC | node-bundled-tool | https://github.com/isaacs/walk-up-path |
| which | 6.0.1 | ISC | node-bundled-tool | https://github.com/npm/node-which.git |
| write-file-atomic | 7.0.1 | ISC | node-bundled-tool | https://github.com/npm/write-file-atomic.git |
| yallist | 5.0.0 | BlueOak-1.0.0 | node-bundled-tool | https://github.com/isaacs/yallist.git |
| npm | 11.19.0 | Artistic-2.0 | node-bundled-tool | https://github.com/npm/cli.git |
| @ast-grep/lang-json | 0.0.7 | ISC | installed-npm | opt/tianshu/node_modules/@ast-grep/lang-json/package.json |
| @ast-grep/lang-python | 0.0.6 | ISC | installed-npm | opt/tianshu/node_modules/@ast-grep/lang-python/package.json |
| @ast-grep/napi-linux-arm64-gnu | 0.44.1 | MIT | installed-npm | https://github.com/ast-grep/ast-grep |
| @ast-grep/napi | 0.44.1 | MIT | installed-npm | https://github.com/ast-grep/ast-grep |
| @ast-grep/setup-lang | 0.0.6 | ISC | installed-npm | opt/tianshu/node_modules/@ast-grep/setup-lang/package.json |
| @esbuild/linux-arm64 | 0.28.2 | MIT | installed-npm | https://github.com/evanw/esbuild.git |
| @huiliyi37/node-addon-landlock-run-linux-arm64 | 0.4.0 | MIT | installed-npm | https://github.com/huiliyi37/oh-my-tianshu.git |
| @huiliyi37/node-addon-landlock-run | 0.4.0 | MIT | installed-npm | https://github.com/huiliyi37/oh-my-tianshu.git |
| @napi-rs/canvas-linux-arm64-gnu | 1.0.9 | MIT | installed-npm | https://github.com/Brooooooklyn/canvas.git |
| @napi-rs/canvas | 1.0.9 | MIT | installed-npm | https://github.com/Brooooooklyn/canvas.git |
| base64-js | 1.5.1 | MIT | installed-npm | git://github.com/beatgammit/base64-js.git |
| better-sqlite3 | 12.11.1 | MIT | installed-npm | git://github.com/WiseLibs/better-sqlite3.git |
| bindings | 1.5.0 | MIT | installed-npm | git://github.com/TooTallNate/node-bindings.git |
| bl | 4.1.0 | MIT | installed-npm | https://github.com/rvagg/bl.git |
| buffer | 5.7.1 | MIT | installed-npm | git://github.com/feross/buffer.git |
| chownr | 1.1.4 | ISC | installed-npm | git://github.com/isaacs/chownr.git |
| decompress-response | 6.0.0 | MIT | installed-npm | opt/tianshu/node_modules/decompress-response/package.json |
| deep-extend | 0.6.0 | MIT | installed-npm | git://github.com/unclechu/node-deep-extend.git |
| detect-libc | 2.1.2 | Apache-2.0 | installed-npm | git://github.com/lovell/detect-libc.git |
| end-of-stream | 1.4.5 | MIT | installed-npm | git://github.com/mafintosh/end-of-stream.git |
| esbuild | 0.28.2 | MIT | installed-npm | https://github.com/evanw/esbuild.git |
| expand-template | 2.0.3 | (MIT OR WTFPL) | installed-npm | https://github.com/ralphtheninja/expand-template.git |
| file-uri-to-path | 1.0.0 | MIT | installed-npm | git://github.com/TooTallNate/file-uri-to-path.git |
| fs-constants | 1.0.0 | MIT | installed-npm | https://github.com/mafintosh/fs-constants.git |
| github-from-package | 0.0.0 | MIT | installed-npm | git://github.com/substack/github-from-package.git |
| ieee754 | 1.2.1 | BSD-3-Clause | installed-npm | git://github.com/feross/ieee754.git |
| inherits | 2.0.4 | ISC | installed-npm | git://github.com/isaacs/inherits |
| ini | 1.3.8 | ISC | installed-npm | git://github.com/isaacs/ini.git |
| mimic-response | 3.1.0 | MIT | installed-npm | opt/tianshu/node_modules/mimic-response/package.json |
| minimist | 1.2.8 | MIT | installed-npm | git://github.com/minimistjs/minimist.git |
| mkdirp-classic | 0.5.3 | MIT | installed-npm | https://github.com/mafintosh/mkdirp-classic.git |
| napi-build-utils | 2.0.0 | MIT | installed-npm | https://github.com/inspiredware/napi-build-utils.git |
| node-abi | 3.96.0 | MIT | installed-npm | https://github.com/electron/node-abi.git |
| once | 1.4.0 | ISC | installed-npm | git://github.com/isaacs/once |
| pdfjs-dist | 6.3.289 | Apache-2.0 | installed-npm | https://github.com/mozilla/pdf.js.git |
| playwright-core | 1.62.0 | Apache-2.0 | installed-npm | https://github.com/microsoft/playwright.git |
| prebuild-install | 7.1.3 | MIT | installed-npm | https://github.com/prebuild/prebuild-install.git |
| pump | 3.0.4 | MIT | installed-npm | git://github.com/mafintosh/pump.git |
| rc | 1.2.8 | (BSD-2-Clause OR MIT OR Apache-2.0) | installed-npm | https://github.com/dominictarr/rc.git |
| readable-stream | 3.6.2 | MIT | installed-npm | git://github.com/nodejs/readable-stream |
| safe-buffer | 5.2.1 | MIT | installed-npm | git://github.com/feross/safe-buffer.git |
| semver | 7.8.5 | ISC | installed-npm | https://github.com/npm/node-semver.git |
| simple-concat | 1.0.1 | MIT | installed-npm | git://github.com/feross/simple-concat.git |
| simple-get | 4.0.1 | MIT | installed-npm | git://github.com/feross/simple-get.git |
| string_decoder | 1.3.0 | MIT | installed-npm | git://github.com/nodejs/string_decoder.git |
| strip-json-comments | 2.0.1 | MIT | installed-npm | opt/tianshu/node_modules/strip-json-comments/package.json |
| tar-fs | 2.1.5 | MIT | installed-npm | https://github.com/mafintosh/tar-fs.git |
| tar-stream | 2.2.0 | MIT | installed-npm | https://github.com/mafintosh/tar-stream.git |
| better-sqlite3 | 12.10.0 | MIT | vendored-runtime | git://github.com/WiseLibs/better-sqlite3.git |
| tianshu-harness | 3.27.0 | Apache-2.0 | installed-npm | https://github.com/huiliyi37/Tianshu-Tui.git |
| tree-sitter-wasms | 0.1.13 | Unlicense | installed-npm | https://github.com/Gregoor/tree-sitter-wasms |
| tunnel-agent | 0.6.0 | Apache-2.0 | installed-npm | https://github.com/mikeal/tunnel-agent |
| typescript | 5.9.3 | Apache-2.0 | installed-npm | https://github.com/microsoft/TypeScript.git |
| util-deprecate | 1.0.2 | MIT | installed-npm | git://github.com/TooTallNate/util-deprecate.git |
| web-tree-sitter | 0.24.7 | MIT | installed-npm | https://github.com/tree-sitter/tree-sitter.git |
| wrappy | 1.0.2 | ISC | installed-npm | https://github.com/npm/wrappy |
| zod-to-json-schema | 3.25.2 | ISC | installed-npm | https://github.com/StefanTerdell/zod-to-json-schema |
| zod | 4.6.5 | MIT | installed-npm | https://github.com/colinhacks/zod.git |

## 天枢 bundle 中观察到的嵌入包

名称来自实际编译 source marker。精确版本 / 独立许可证仍需要原始构建 lockfile 与 notices核对；即使也在 npm 表出现，不可自动判定为同一个版本。

| 名称 | 精确版本 | 许可证据 | 出现 bundle 数 |
| --- | --- | --- | --- |
| @mixmark-io/domino | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| @modelcontextprotocol/sdk | 待核实 | 保留 compiled notice；逐包核实 | 5 |
| ajv | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| ajv-formats | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| ansi-regex | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| chalk | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| cross-spawn | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| diff | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| eventsource | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| eventsource-parser | 待核实 | 保留 compiled notice；逐包核实 | 2 |
| fast-deep-equal | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| fast-uri | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| get-east-asian-width | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| isexe | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| json-schema-traverse | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| path-key | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| pixelmatch | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| pkce-challenge | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| pngjs | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| shebang-command | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| shebang-regex | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| string-width | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| strip-ansi | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| tsup | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| turndown | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| undici | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| which | 待核实 | 保留 compiled notice；逐包核实 | 1 |
| zod | 待核实 | 保留 compiled notice；逐包核实 | 3 |
| zod-to-json-schema | 待核实 | 保留 compiled notice；逐包核实 | 1 |

## Ubuntu 已安装包

许可列只收录版权原文中观察到的 License 字段（可能多许可 / 文件范围），不将它们折叠成单一 SPDX expression。版权目录符号链接按归档目标关联。

| 二进制包 | 版本 | 源包 | 版权原文 License 字段 |
| --- | --- | --- | --- |
| apt | 2.8.3 | apt | GPL-2+; GPL-2; BSD-3-clause; Expat |
| base-files | 13ubuntu10.5 | base-files | 见完整copyright正文 |
| base-passwd | 3.6.3build1 | base-passwd | GPL-2; public-domain |
| bash | 5.2.21-2ubuntu4 | bash | GPL-3+; GPL-3+ with Bison exception; GPL-2+; GFDL-NIV-1.3; Latex2e; BSD-4-clause-UC and MIT-like; BSD-4-clause-UC; MIT-like; permissive |
| bsdutils | 1:2.39.3-9ubuntu6.6 | util-linux (2.39.3-9ubuntu6.6) | GPL-2+; GPL-2; GPL-3+; public-domain; BSD-4-clause; MIT; BSD-3-clause; BSLA; LGPL-2+; LGPL-2.1+; LGPL; LGPL-3+ |
| coreutils | 9.4-3ubuntu6.3 | coreutils | GPL-3+; GPL-3+ and BSD-4-clause-UC; BSD-4-clause-UC; GPL-3+ and ISC; ISC; FSFULLR; GFDL-NIV-1.3 |
| dash | 0.5.12-6ubuntu5 | dash | BSD-3-Clause; public-domain; GPL-2+; BSD-3-clause |
| debconf | 1.5.86ubuntu1 | debconf | BSD-2-clause |
| debianutils | 5.17build1 | debianutils | GPL-2+; public-domain; SMAIL-GPL |
| diffutils | 1:3.10-1ubuntu0.1 | diffutils | GPL-3+; FSFULLR; GPL-3+ and FSFULLR; LGPL-2.1+; GPL-3+ with autoconf exception; GPL-3+ with texinfo exception; LGPL-2.0+; GPL-2+; X11; FSFAP; GFDL-NIV-1.3; LGPL-3.0+; LGPL-3.0+ or GPL-2+; public-domain |
| dpkg | 1.22.6ubuntu6.6 | dpkg | GPL-2+; public-domain-s-s-d |
| e2fsprogs | 1.47.0-2.4~exp1ubuntu4.1 | e2fsprogs | GPL-2; LGPL-2; BSD-3-Clause; Apache-2; ISC; GPL or MIT-US-export; Kazlib; Latex2e; GPL-2+ with Texinfo exception |
| findutils | 4.9.0-5build1 | findutils | GFDL-NIV-1.3+; GPL-3+; FSFAP; GPL-3+ with Autoconf-data exception; FSFULLR; GPL-2+ with Autoconf-data exception; GPL-2+; X11; public-domain; GPL with automake exception; LGPL-2.1+; LGPL-2+; LGPL-3+; BSD-3-clause and/or GPL-3+; GPL-3+ with Bison-2.2 exception; LGPL-3; ISC and/or LGPL-2.1+; BSD-3-clause; ISC |
| gcc-14-base | 14.2.0-4ubuntu2~24.04.1 | gcc-14 | 见完整copyright正文 |
| gpgv | 2.4.4-2ubuntu17.6 | gnupg2 | GPL-3+; permissive; LGPL-2.1+; LGPL-3+ or GPL-2+; Expat; GPL-3+ or BSD-3-clause; LGPL-3+; RFC-Reference; TinySCHEME; CC0-1.0; BSD-3-clause; GPL-2+ |
| grep | 3.11-4build1 | grep | GPL-3+ |
| gzip | 1.12-1ubuntu3.2 | gzip | GPL-3+; GFDL-1.3+-no-invariant; FSF-manpages |
| hostname | 3.23+nmu2ubuntu2 | hostname | GPL-2 |
| init-system-helpers | 1.66ubuntu1 | init-system-helpers | BSD-3-clause; GPL-2+ |
| libacl1 | 2.3.2-1build1.1 | acl | GPL-2+; LGPL-2+ |
| libapt-pkg6.0t64 | 2.8.3 | apt | GPL-2+; GPL-2; BSD-3-clause; Expat |
| libassuan0 | 2.5.6-1build1 | libassuan | LGPL-2.1+; GAP~FSF; LGPL-3+; GPL-2+ with libtool exception; GPL-3+; GAP; GPL-2+ |
| libattr1 | 1:2.5.2-1ubuntu0.1 | attr | GPL-2+; LGPL-2+ |
| libaudit-common | 1:3.1.2-2.1build1.1 | audit | GPL-2; LGPL-2.1 |
| libaudit1 | 1:3.1.2-2.1build1.1 | audit | GPL-2; LGPL-2.1 |
| libblkid1 | 2.39.3-9ubuntu6.6 | util-linux | GPL-2+; GPL-2; GPL-3+; public-domain; BSD-4-clause; MIT; BSD-3-clause; BSLA; LGPL-2+; LGPL-2.1+; LGPL; LGPL-3+ |
| libbz2-1.0 | 1.0.8-5.1ubuntu0.1 | bzip2 | BSD-variant; GPL-2 |
| libc-bin | 2.39-0ubuntu8.8 | glibc | 见完整copyright正文 |
| libc6 | 2.39-0ubuntu8.8 | glibc | 见完整copyright正文 |
| libcap-ng0 | 0.8.4-2build2 | libcap-ng | LGPL-2.1+; GPL-2+; GPL-3 |
| libcap2 | 1:2.66-5ubuntu2.4 | libcap2 | BSD-3-clause or GPL-2; BSD-3-clause or GPL-2+; BSD-3-clause; GPL-2; GPL-2+ |
| libcom-err2 | 1.47.0-2.4~exp1ubuntu4.1 | e2fsprogs | GPL-2; LGPL-2; BSD-3-Clause; Apache-2; ISC; GPL or MIT-US-export; Kazlib; Latex2e; GPL-2+ with Texinfo exception |
| libcrypt1 | 1:4.4.36-4build1 | libxcrypt | 见完整copyright正文 |
| libdb5.3t64 | 5.3.28+dfsg2-7 | db5.3 | Sleepycat and BSD-3-clause; Sleepycat; BSD-3-clause; Ms-PL; GPL or Artistic; X11; MIT-old; TCL-like; BSD-3-clause-fjord; GPL-3; zlib; Artistic or BSD-3-clause; GPL |
| libdebconfclient0 | 0.271ubuntu3 | cdebconf | BSD-2-Clause; GPL-2+; BSD-2-clause |
| libext2fs2t64 | 1.47.0-2.4~exp1ubuntu4.1 | e2fsprogs | GPL-2; LGPL-2; BSD-3-Clause; Apache-2; ISC; GPL or MIT-US-export; Kazlib; Latex2e; GPL-2+ with Texinfo exception |
| libffi8 | 3.4.6-1build1 | libffi | Expat; X11; GPL-2+; GPL-3+; MPL-1.1 or GPL-2+ or LGPL-2.1+; public-domain; GPL |
| libgcc-s1 | 14.2.0-4ubuntu2~24.04.1 | gcc-14 | 见完整copyright正文 |
| libgcrypt20 | 1.10.3-2ubuntu0.2 | libgcrypt20 | Most of the package is licensed under the GNU Lesser General Public |
| libgmp10 | 2:6.3.0+dfsg-2ubuntu6.1 | gmp | GPL-2+ or LGPL-3+; GPL-3+; GPL-3+ with Bison exception; GPL-2+; LGPL-3+ |
| libgnutls30t64 | 3.8.3-1.1ubuntu3.6 | gnutls28 | The main library is licensed under GNU Lesser; * Redistribution and use in source and binary forms, with or without; Public domain.; CC0 license; Expat; Apache-2.0; LGPLv3+_or_GPLv2+; LGPLv2.1+; based on Russian standard GOST 28147-89; GPLv3+; BSD-3-Clause |
| libgpg-error0 | 1.47-3build2.1 | libgpg-error | LGPL-2.1+; LGPL-2.1+ or BSD-3-clause; g10-permissive; GPL-3+; BSD-3-clause |
| libhogweed6t64 | 3.9.1-2.2build1.1 | nettle | LGPL-3+ or GPL-2+; LGPL-2+; Expat; GPL-2+; GPL-3+ with Autoconf exception; public-domain; GPL-2; GAP; LGPL-3+ |
| libidn2-0 | 2.3.7-2build1.1 | libidn2 | GPL-3+; LGPL-3+ or GPL-2+; Unicode; GPL-2+; LGPL-3+ |
| liblz4-1 | 1.9.4-1build1.1 | lz4 | BSD-2-clause; GPL-2+; GPL-2+ or BSD-2-clause |
| liblzma5 | 5.6.1+really5.4.5-1ubuntu0.3 | xz-utils | Different licenses apply to different files in this package. Here; PD; probably-PD; GPL-2+; LGPL-2.1+; permissive-fsf; Autoconf; permissive-nowarranty; GPL-2; none; config-h; noderivs; PD-debian |
| libmd0 | 1.1.0-2build1.1 | libmd | BSD-3-clause; BSD-3-clause-Aaron-D-Gifford; BSD-2-clause; BSD-2-clause-NetBSD; ISC; Beerware; public-domain-md4; public-domain-md5; public-domain-sha1 |
| libmount1 | 2.39.3-9ubuntu6.6 | util-linux | GPL-2+; GPL-2; GPL-3+; public-domain; BSD-4-clause; MIT; BSD-3-clause; BSLA; LGPL-2+; LGPL-2.1+; LGPL; LGPL-3+ |
| libncursesw6 | 6.4+20240113-1ubuntu2.2 | ncurses | MIT/X11; X11; BSD-3-clause |
| libnettle8t64 | 3.9.1-2.2build1.1 | nettle | LGPL-3+ or GPL-2+; LGPL-2+; Expat; GPL-2+; GPL-3+ with Autoconf exception; public-domain; GPL-2; GAP; LGPL-3+ |
| libnpth0t64 | 1.6-3.1build1 | npth | LGPL-2.1+ |
| libp11-kit0 | 0.25.3-4ubuntu2.2 | p11-kit | BSD-3-clause; FSFULLR; GPL-2+ with Autoconf-data exception; GPL-3+ with Autoconf-data exception; X11; ISC; customFSFULLRWD; Apache-2.0; LGPL-2.1+; customFSFUL; FSFAP |
| libpam-modules | 1.5.3-5ubuntu5.7 | pam | BSD-3-clause or GPL; GPL-2; GPL-2+; GPL-3; GPL-3+ with Bison exception; BSD-tcp_wrappers; LGPL-2+; public-domain; Beerware |
| libpam-modules-bin | 1.5.3-5ubuntu5.7 | pam | BSD-3-clause or GPL; GPL-2; GPL-2+; GPL-3; GPL-3+ with Bison exception; BSD-tcp_wrappers; LGPL-2+; public-domain; Beerware |
| libpam-runtime | 1.5.3-5ubuntu5.7 | pam | BSD-3-clause or GPL; GPL-2; GPL-2+; GPL-3; GPL-3+ with Bison exception; BSD-tcp_wrappers; LGPL-2+; public-domain; Beerware |
| libpam0g | 1.5.3-5ubuntu5.7 | pam | BSD-3-clause or GPL; GPL-2; GPL-2+; GPL-3; GPL-3+ with Bison exception; BSD-tcp_wrappers; LGPL-2+; public-domain; Beerware |
| libpcre2-8-0 | 10.42-4ubuntu2.1 | pcre2 | BSD-3-clause-Cambridge with BINARY LIBRARY-LIKE PACKAGES exception; BSD-3-clause; X11; BSD-2-clause; public-domain |
| libproc2-0 | 2:4.0.4-4ubuntu3.3 | procps | LGPL-2.1+; LGPL-2.0+; GPL-2.0+ |
| libseccomp2 | 2.5.5-1ubuntu3.1 | libseccomp | LGPL-2.1 |
| libselinux1 | 3.5-2ubuntu2.1 | libselinux | public-domain; GPL-2 |
| libsemanage-common | 3.5-1build5 | libsemanage | LGPL-2.1+; GPL-2 |
| libsemanage2 | 3.5-1build5 | libsemanage | LGPL-2.1+; GPL-2 |
| libsepol2 | 3.5-2build1 | libsepol | LGPL-2.1+; Zlib; GPL-2; GPL-2+ |
| libsmartcols1 | 2.39.3-9ubuntu6.6 | util-linux | GPL-2+; GPL-2; GPL-3+; public-domain; BSD-4-clause; MIT; BSD-3-clause; BSLA; LGPL-2+; LGPL-2.1+; LGPL; LGPL-3+ |
| libss2 | 1.47.0-2.4~exp1ubuntu4.1 | e2fsprogs | GPL-2; LGPL-2; BSD-3-Clause; Apache-2; ISC; GPL or MIT-US-export; Kazlib; Latex2e; GPL-2+ with Texinfo exception |
| libssl3t64 | 3.0.13-0ubuntu3.15 | openssl | Apache-2.0; Artistic or GPL-1+; Artistic; GPL-1+ |
| libstdc++6 | 14.2.0-4ubuntu2~24.04.1 | gcc-14 | 见完整copyright正文 |
| libsystemd0 | 255.4-1ubuntu8.17 | systemd | LGPL-2.1+; CC0-1.0; GPL-2 with Linux-syscall-note exception; Expat; public-domain; GPL-2+ |
| libtasn1-6 | 4.19.0-3ubuntu0.24.04.2 | libtasn1-6 | 见完整copyright正文 |
| libtinfo6 | 6.4+20240113-1ubuntu2.2 | ncurses | MIT/X11; X11; BSD-3-clause |
| libudev1 | 255.4-1ubuntu8.17 | systemd | LGPL-2.1+; CC0-1.0; GPL-2 with Linux-syscall-note exception; Expat; public-domain; GPL-2+ |
| libunistring5 | 1.1-2build1.1 | libunistring | LGPL-3+ or GPL-2+; FreeSoftware; GPL-3+ or GFDL-NIV-1.2+; GPL-3+; GPL-2+; GPL-2+ with distribution exception; GPL-2+ with distribution exception, Expat and GPL-2+; X11; LGPL-3+; GFDL-NIV-1.2+ |
| libuuid1 | 2.39.3-9ubuntu6.6 | util-linux | GPL-2+; GPL-2; GPL-3+; public-domain; BSD-4-clause; MIT; BSD-3-clause; BSLA; LGPL-2+; LGPL-2.1+; LGPL; LGPL-3+ |
| libxxhash0 | 0.8.2-2build1 | xxhash | BSD-2-clause; GPL-2+; BSD-2-clause or GPL-2+ |
| libzstd1 | 1.5.5+dfsg2-2build1.1 | libzstd | BSD-3-clause or GPL-2; zlib; Expat; GPL-2; BSD-3-clause |
| login | 1:4.13+dfsg1-4ubuntu3.2 | shadow | BSD-3-clause; GPL-1; GPL-2+; public-domain |
| logsave | 1.47.0-2.4~exp1ubuntu4.1 | e2fsprogs | GPL-2; LGPL-2; BSD-3-Clause; Apache-2; ISC; GPL or MIT-US-export; Kazlib; Latex2e; GPL-2+ with Texinfo exception |
| mawk | 1.3.4.20240123-1build1 | mawk | GPL-2.0-only; X11; CC-BY-3.0 |
| mount | 2.39.3-9ubuntu6.6 | util-linux | GPL-2+; GPL-2; GPL-3+; public-domain; BSD-4-clause; MIT; BSD-3-clause; BSLA; LGPL-2+; LGPL-2.1+; LGPL; LGPL-3+ |
| ncurses-base | 6.4+20240113-1ubuntu2.2 | ncurses | MIT/X11; X11; BSD-3-clause |
| ncurses-bin | 6.4+20240113-1ubuntu2.2 | ncurses | MIT/X11; X11; BSD-3-clause |
| passwd | 1:4.13+dfsg1-4ubuntu3.2 | shadow | BSD-3-clause; GPL-1; GPL-2+; public-domain |
| perl-base | 5.38.2-3.2ubuntu0.4 | perl | GPL-1+ or Artistic; Expat; REGCOMP, and GPL-1+ or Artistic; GPL-3+-WITH-BISON-EXCEPTION; Unicode; GPL-1+ or Artistic, and Unicode; BZIP; ZLIB; Artistic; GPL-2+ or Artistic; Expat or GPL-1+ or Artistic; FSFAP; BSD-3-clause-with-weird-numbering; CC0-1.0; TEXT-TABS; GPL-1+ or Artistic, and BSD-4-clause-POWERDOG; GPL-1+ or Artistic, and BSD-3-clause-GENERIC; BSD-3-clause; SDBM-PUBLIC-DOMAIN; DONT-CHANGE-THE-GPL; GPL-1+ or Artistic or Artistic-dist; Artistic-dist; Artistic or GPL-1+ or Artistic-dist; GPL-1+ or Artistic, and Expat; LGPL-2.1; GPL-1+; GPL-2+; Artistic-2; BSD-4-clause-POWERDOG; BSD-3-clause-GENERIC; REGCOMP |
| procps | 2:4.0.4-4ubuntu3.3 | procps | LGPL-2.1+; LGPL-2.0+; GPL-2.0+ |
| sed | 4.9-2ubuntu0.24.04.1 | sed | GPL-3+; X11; GFDL-NIV-1.3+; GPL-3+ and ISC; ISC; BSD-4-clause-UC; BSL-1; pcre |
| sensible-utils | 0.0.22 | sensible-utils | GPL-2+; All-permissive; configure; installsh; BSD-2-clause |
| sysvinit-utils | 3.08-6ubuntu3 | sysvinit | GPL-2.0+; LGPL-2.1+; GPL-3.0; GPL-2.0 |
| tar | 1.35+dfsg-3ubuntu0.4 | tar | GPL-3+; GPL-3+ with Bison exception; LGPL-2.1+; LGPL-3+; GPL-2+ |
| ubuntu-keyring | 2023.11.28.1 | ubuntu-keyring | 见完整copyright正文 |
| util-linux | 2.39.3-9ubuntu6.6 | util-linux | GPL-2+; GPL-2; GPL-3+; public-domain; BSD-4-clause; MIT; BSD-3-clause; BSLA; LGPL-2+; LGPL-2.1+; LGPL; LGPL-3+ |
| zlib1g | 1:1.3.dfsg-3.1ubuntu2.2 | zlib | Zlib |

附加实际分发：ripgrep 14.1.1-1（由 dpkg-deb 直接解包，不在上述安装状态表）；原文许可 MIT / Unlicense 及特定文件 BSD-3-clause。

## Android 实际 Gradle 依赖

相同坐标合并显示 release / debug 覆盖情况。这里列 resolved artifacts，不把 BOM 约束、构建插件、test-only JUnit 当成 APK代码。POM未自带许可且artifact已带原文者明确指出，不写成全部未知。

| Maven坐标 | 版本 | 原POM许可 / artifact证据 | APK配置 |
| --- | --- | --- | --- |
| androidx.activity:activity | 1.13.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.activity:activity-compose | 1.13.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.activity:activity-ktx | 1.13.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.annotation:annotation-experimental | 1.4.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.annotation:annotation-jvm | 1.10.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.arch.core:core-common | 2.2.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.arch.core:core-runtime | 2.2.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.autofill:autofill | 1.0.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.collection:collection-jvm | 1.5.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.collection:collection-ktx | 1.5.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.animation:animation-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.animation:animation-core-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.foundation:foundation-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.foundation:foundation-layout-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.material3:material3-android | 1.4.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.material:material-icons-core-android | 1.7.8 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.material:material-icons-extended-android | 1.7.8 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.material:material-ripple-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.runtime:runtime-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.runtime:runtime-annotation-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.runtime:runtime-retain-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.runtime:runtime-saveable-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.ui:ui-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.ui:ui-geometry-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.ui:ui-graphics-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.ui:ui-text-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.ui:ui-tooling-android | 1.12.1 | The Apache Software License, Version 2.0 | debug |
| androidx.compose.ui:ui-tooling-data-android | 1.12.1 | The Apache Software License, Version 2.0 | debug |
| androidx.compose.ui:ui-tooling-preview-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.ui:ui-unit-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.compose.ui:ui-util-android | 1.12.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.concurrent:concurrent-futures | 1.1.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.core:core | 1.19.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.core:core-ktx | 1.19.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.core:core-viewtree | 1.0.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.customview:customview-poolingcontainer | 1.0.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.emoji2:emoji2 | 1.4.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.graphics:graphics-path | 1.0.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.interpolator:interpolator | 1.0.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-common-java8 | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-common-jvm | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-livedata-core | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-process | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-runtime-android | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-runtime-compose-android | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-runtime-ktx-android | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-viewmodel-android | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-viewmodel-compose-android | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.lifecycle:lifecycle-viewmodel-savedstate-android | 2.11.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.navigation:navigation-common-android | 2.10.2 | The Apache Software License, Version 2.0 | debug, release |
| androidx.navigation:navigation-compose-android | 2.10.2 | The Apache Software License, Version 2.0 | debug, release |
| androidx.navigation:navigation-runtime-android | 2.10.2 | The Apache Software License, Version 2.0 | debug, release |
| androidx.navigationevent:navigationevent-android | 1.1.2 | The Apache Software License, Version 2.0 | debug, release |
| androidx.navigationevent:navigationevent-compose-android | 1.1.2 | The Apache Software License, Version 2.0 | debug, release |
| androidx.profileinstaller:profileinstaller | 1.4.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.savedstate:savedstate-android | 1.5.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.savedstate:savedstate-compose-android | 1.5.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.savedstate:savedstate-ktx | 1.5.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.startup:startup-runtime | 1.2.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.tracing:tracing | 1.2.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.versionedparcelable:versionedparcelable | 1.1.1 | The Apache Software License, Version 2.0 | debug, release |
| androidx.window:window | 1.5.0 | The Apache Software License, Version 2.0 | debug, release |
| androidx.window:window-core-android | 1.5.0 | The Apache Software License, Version 2.0 | debug, release |
| com.google.guava:listenablefuture | 1.0 | POM未声明；须核查继承或上游许可 | debug, release |
| com.squareup.okhttp3:okhttp-android | 5.5.0 | The Apache Software License, Version 2.0 | debug, release |
| com.squareup.okhttp3:okhttp-sse | 5.5.0 | The Apache Software License, Version 2.0 | debug, release |
| com.squareup.okio:okio-jvm | 3.18.1 | The Apache Software License, Version 2.0 | debug, release |
| commons-codec:commons-codec | 1.19.0 | POM未声明；artifact原LICENSE/NOTICE已保留 | debug, release |
| commons-io:commons-io | 2.20.0 | POM未声明；artifact原LICENSE/NOTICE已保留 | debug, release |
| dev.rikka.shizuku:aidl | 13.1.5 | MIT License | debug, release |
| dev.rikka.shizuku:api | 13.1.5 | MIT License | debug, release |
| dev.rikka.shizuku:provider | 13.1.5 | MIT License | debug, release |
| dev.rikka.shizuku:shared | 13.1.5 | MIT License | debug, release |
| org.apache.commons:commons-compress | 1.28.0 | POM未声明；artifact原LICENSE/NOTICE已保留 | debug, release |
| org.apache.commons:commons-lang3 | 3.18.0 | POM未声明；artifact原LICENSE/NOTICE已保留 | debug, release |
| org.commonmark:commonmark | 0.30.0 | BSD-2-Clause | debug, release |
| org.commonmark:commonmark-ext-gfm-strikethrough | 0.30.0 | POM未声明；artifact原LICENSE/NOTICE已保留 | debug, release |
| org.commonmark:commonmark-ext-gfm-tables | 0.30.0 | POM未声明；artifact原LICENSE/NOTICE已保留 | debug, release |
| org.jetbrains.kotlin:kotlin-stdlib | 2.4.20 | Apache-2.0 | debug, release |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.11.0 | Apache-2.0 | debug, release |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm | 1.11.0 | Apache-2.0 | debug, release |
| org.jetbrains.kotlinx:kotlinx-serialization-core-jvm | 1.11.0 | Apache-2.0 | debug, release |
| org.jetbrains.kotlinx:kotlinx-serialization-json-jvm | 1.11.0 | Apache-2.0 | debug, release |
| org.jetbrains:annotations | 23.0.0 | The Apache Software License, Version 2.0 | debug, release |
| org.jsoup:jsoup | 1.21.2 | The MIT License | debug, release |
| org.jspecify:jspecify | 1.0.0 | The Apache License, Version 2.0 | debug, release |

## 8个OFL艺术字体

原字体版权及输入散列见 THIRD_PARTY_NOTICES、stellar-welcome-font.md 与 tools/subset_welcome_font.py。此处散列是生产子集输出。

| 资源 | 许可 | 输出SHA-256 |
| --- | --- | --- |
| app/src/main/res/font/stellar_huangyou.ttf | OFL-1.1（改名子集） | 69de199b276e5d349dc7284f5b4b567f6329d8975e38f4685db5b589cc58783f |
| app/src/main/res/font/stellar_liujian.ttf | OFL-1.1（改名子集） | 6b99a3a5266e8805444587b43856c70321faf049bf292040a3d000c8f1adac2a |
| app/src/main/res/font/stellar_longcang.ttf | OFL-1.1（改名子集） | cb6595b003938fd09f6513e39b7668971c612fcbf4897fce5e13dd1cfb5a1ec2 |
| app/src/main/res/font/stellar_mashan.ttf | OFL-1.1（改名子集） | 7772cf793c0241e28291fc973e3078f0400d89225080c2139271626a273b14c6 |
| app/src/main/res/font/stellar_notoserif.ttf | OFL-1.1（改名子集） | 3df0fe61961998c1afc98259e398304c9954f6ff888d7d28f9caf359068d7d4f |
| app/src/main/res/font/stellar_wenkai.ttf | OFL-1.1（改名子集） | 742e277e88c7661d4da3b189236d473f1de426b6768f6045f6e1ab7578e2f768 |
| app/src/main/res/font/stellar_xiaowei.ttf | OFL-1.1（改名子集） | 1fa1ffdac49d0abc97fb06704d31036819e8e498971479481decb144baef1748 |
| app/src/main/res/font/stellar_zhimang.ttf | OFL-1.1（改名子集） | 8df8cff14403a0d9155956335e787078ff1ac095014de7a79d588a849210e001 |

## 原生 / WASM载荷

此处记录真实字节，不把外层包许可自动外推为所有静态链接代码 / grammar许可。

| 归档内路径 | 字节 | SHA-256 |
| --- | --- | --- |
| opt/tianshu/node_modules/@ast-grep/napi-linux-arm64-gnu/ast-grep-napi.linux-arm64-gnu.node | 7508080 | 86f2e6cf111c31f92bab1dd5ff0727b03f38aad6077abbac84b02a4d207743a2 |
| opt/tianshu/node_modules/@napi-rs/canvas-linux-arm64-gnu/skia.linux-arm64-gnu.node | 29423712 | b09456caeb8e1d04ea80824fd11402480ac53f912ea9c9b0786b8a0d404483a9 |
| opt/tianshu/node_modules/pdfjs-dist/wasm/jbig2.wasm | 104852 | e6bee67724a7b5436fe8162638e3708cfc8d52b6342db69a49715e30ff27cfdc |
| opt/tianshu/node_modules/pdfjs-dist/wasm/openjpeg.wasm | 252032 | 004a0e62db930ba9ff2a22212f4554d0bb57a0635a8287caf70f98117cee14ba |
| opt/tianshu/node_modules/pdfjs-dist/wasm/qcms_bg.wasm | 96589 | 663d86126d5f5fcb1c61490f94353e2a8375660b8c5498ab3ebab5a34b08800e |
| opt/tianshu/node_modules/pdfjs-dist/wasm/quickjs-eval.wasm | 469105 | 7bcacc9f22cacf7e9b23866d2a6d1639693d40c7f144e41b7c69ed37ba9cbe8f |
| opt/tianshu/node_modules/playwright-core/lib/webp_codec.wasm | 565369 | 164cd9583a3e4d081f767d3b41ff0a730443dc7535fcc5caff83561463384b79 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-bash.wasm | 1400214 | 807dcdb1380a59befb112ed8fbd3d3872c7fadaf5903a769282b50973b30696d |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-c.wasm | 792959 | 056b25072382f72deee2c64ec238ffc4bb8cf42844ef21502c0e70f03a8a0d66 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-c_sharp.wasm | 3978594 | 6266a7e32d68a3459104d994dc848df15d5672b0ea8e86d327274b694f8e6991 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-cpp.wasm | 4662978 | f6afdf53bfd6de76557bb7edb624a3a3869e14d9a83b78433f93617ecee42527 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-css.wasm | 98133 | 5fc615467b1b98420ed7517e5bf9e1f88468132dd903d842dfb13714f6a1cb0c |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-dart.wasm | 984666 | 7f5364e4256cf7e55efd01dd52421ef2663caa8061b82659b7e4bf61064545ec |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-elisp.wasm | 53740 | deedb03ccf150329ddfcc4ed92861c235bbae6f9692be6b93cac71617a4d42ab |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-elixir.wasm | 1617756 | 82e91b9759ddca30d8978ebbfa8e347b4451b64c931f9ae62112e6db9b8fac20 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-elm.wasm | 148886 | 962b8668a0e16a6fb1fe232ba3e07ba4537a6b72c47293fddea0f6ea6ff9912e |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-embedded_template.wasm | 5124 | 68584527f712dbf2cc39776c56980c08516991f184a4a17bb67c2f436f0fc373 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-go.wasm | 235957 | 9963ca89b616eaf04b08a43bc1fb0f07b85395bec313330851f1f1ead2f755b6 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-html.wasm | 18468 | 11b3405c1543fb012f5ed7f8ee73125076dce8b168301e1e787e4c717da6b456 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-java.wasm | 430239 | 637aac4415fb39a211a4f4292d63c66b5ce9c32fa2cd35464af4f681d91b9a1f |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-javascript.wasm | 647334 | 63812b9e275d26851264734868d27a1656bd44a2ef6eb3e85e6b03728c595ab5 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-json.wasm | 5961 | fdb5219abe058369e16897aaa11eecf47ef4f546752c3ddbac339cdd89e1e667 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-kotlin.wasm | 4052705 | b5cb00c8d06ed0f10f1dbe497205b437809d7e87db1f638721a8cfb30e044449 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-lua.wasm | 43539 | 75ef809136d610068c5b2135741d89f5df62690a3d55169203351cb7cc85727d |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-objc.wasm | 7708264 | 7c1b5bfdca7e64b6c63b6040bb7ba0afc347df116f9030ca32f8535d7377f6ff |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-ocaml.wasm | 5018068 | 60849b6320ee956233d77b017c65c45660e507d03ae70aa1bd5783458e2e9e18 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-php.wasm | 812594 | 55bb617b6f01e14bab997861f0b20a2420cf6ba3199ffeb295b9ec398966d8a3 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-python.wasm | 476105 | 9056d0fb0c337810d019fae350e8167786119da98f0f282aceae7ab89ee8253b |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-ql.wasm | 544940 | 836b2a51f6b2b4605ef7bfa908b978fed0fe838afb4eabaa9451552f12e953c1 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-rescript.wasm | 677948 | ae18d46336768b6c0eea07eb0b003408848766b3b67df1d807b40cbd93017bda |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-ruby.wasm | 2106447 | 93a5022855314cdb45458c7bb026a24a0ebc3a5ff6439e542e881f14dfa13a39 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-rust.wasm | 818756 | 4409921a70d0aa5bec7d1d7ce809a557a8ee1cf6ace901e3ac6a76e62cfea903 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-scala.wasm | 215264 | 160cfbb8ff7220886e99ed9699abceb6d837b4cd28993b9282c7f445a0554abd |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-solidity.wasm | 423940 | 160745e470f234cae903a9ba445d19e758d0b02e1197401fc765976c6254d2b6 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-swift.wasm | 3147876 | 41c4fdb2249a3aa6d87eed0d383081ff09725c2248b4977043a43825980ffcc7 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-systemrdl.wasm | 82495 | 09129542bbea6d19aa33b54f93bae2b41128144970be13ce09af6697146c4527 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-tlaplus.wasm | 4878724 | 72a07f94b0bc88b9123a6e41058e37ab9ca70d84a03b79511b25af7f435129b5 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-toml.wasm | 25436 | 7849ac8ce9d10a4684ca189ea8ad3654c20c38acb2d674a014a164398cbd37a2 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-tsx.wasm | 2411272 | 6aa3b2c70e76f5d48eafef1093e9c4de383e13f2fdde2f4e9b98a378f6a8f1b6 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-typescript.wasm | 2342690 | 8515404dceed38e1ed86aa34b09fcf3379fff1b4ff9dd3967bcd6d1eb5ac3d8f |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-vue.wasm | 23895 | 6244521bb3fb60f34ce5f677f2af81facb2c38691193985ca5fa85e1b6f29250 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-yaml.wasm | 182936 | 5dea7cfff83d41d8f87fb8e434e1a5b292c0d670bfcdc42cb2af420ef490dde5 |
| opt/tianshu/node_modules/tree-sitter-wasms/out/tree-sitter-zig.wasm | 690754 | 59cc4531aa661e2de4c5bc04e4045b6bdd5d2bfa75045cbda5f673102d140eef |
| opt/tianshu/node_modules/web-tree-sitter/tree-sitter.wasm | 190779 | 70aa2b222e10a91306a85f5b9c8e028e3dc09943854aa63640c643fc7e051c2f |

## 完整版权文本

目录 `license-texts/` 保留实际原文；每条记录的路径 / hash由components.json索引。POM metadata单独保存在metadata/gradle，不代替完整条文。

外部参考项目 / 商业服务 / 视觉素材不作为已装库混入上述清单，其来源和未核实事项见THIRD_PARTY_NOTICES与SOURCES。
