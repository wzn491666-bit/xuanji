"""Inventory shipped runtime/font artifacts and preserve their original notices.

No network access and no whole-rootfs extraction. The report is evidence, not
legal clearance. Re-run after changing bundled artifacts or Gradle resolution.
"""

import argparse
import datetime
import hashlib
import io
import json
import posixpath
import re
import tarfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path, PurePosixPath
from urllib.parse import quote


ROOT = Path(__file__).resolve().parents[1]
NOTICE_NAME = re.compile(r"^(?:licen[cs]e|copying|copyright|notice|authors|ofl)(?:[._-].*)?$", re.I)
PACKAGE_PATH = re.compile(r"(?:^|/)node_modules/(?:@[^/]+/)?[^/]+/package\.json$")


def digest_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def paragraphs(text):
    result = []
    for paragraph in re.split(r"\n\s*\n", text.replace("\r\n", "\n")):
        fields = {}
        previous = None
        for line in paragraph.splitlines():
            if line.startswith((" ", "\t")) and previous:
                fields[previous] += "\n" + line[1:]
            elif ":" in line:
                previous, value = line.split(":", 1)
                fields[previous] = value.strip()
        if fields:
            result.append(fields)
    return result


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def repository_url(value):
    if isinstance(value, dict):
        value = value.get("url", "")
    if not isinstance(value, str):
        return None
    value = value.removeprefix("git+")
    return value if value.startswith(("https://", "http://", "git://")) else None


def scan_runtime(rootfs, destination):
    packages, notices, native, status, lock = [], [], [], "", {}
    bundled_references, bundled_notices, doc_aliases = {}, [], {}
    with tarfile.open(rootfs, "r|gz") as archive:
        for member in archive:
            name = member.name.removeprefix("./")
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts:
                raise ValueError("Unsafe archive path")
            is_package = bool(PACKAGE_PATH.search(name))
            if member.issym() and name.startswith("usr/share/doc/") and len(path.parts) == 4:
                doc_aliases[name] = posixpath.normpath(posixpath.join(str(path.parent), member.linkname))
            is_lock = name == "opt/tianshu/package-lock.json"
            is_status = name == "var/lib/dpkg/status"
            is_notice = (
                (name.startswith("opt/") and "/node_modules/" in name and NOTICE_NAME.fullmatch(path.name))
                or (name.startswith("usr/share/doc/") and path.name == "copyright")
                or name.startswith("usr/share/common-licenses/")
                or name == "opt/node/LICENSE"
            )
            is_native = name.startswith("opt/") and path.suffix in (".node", ".wasm")
            is_bundle = name.startswith("opt/tianshu/node_modules/tianshu-harness/dist/") and path.suffix in (".js", ".cjs", ".mjs")
            if (is_notice or is_package) and member.issym():
                notices.append({"archivePath": name, "symlinkTarget": member.linkname, "kind": "symlink"})
            if not member.isfile() or not any((is_package, is_lock, is_status, is_notice, is_native, is_bundle)):
                continue
            source = archive.extractfile(member)
            if source is None:
                continue
            if is_native:
                digest = hashlib.sha256()
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    digest.update(chunk)
                native.append({"archivePath": name, "bytes": member.size, "sha256": digest.hexdigest()})
                continue
            if member.size > 32 * 1024 * 1024:
                raise ValueError("Unexpected metadata size")
            data = source.read()
            if is_bundle:
                text = data.decode("utf-8", "replace")
                for match in re.finditer(r"(?m)^// (?:.*?/)?node_modules/((?:@[^/\s]+/)?[^/\s]+)/", text):
                    bundled_references.setdefault(match.group(1), set()).add(name)
                blocks = re.findall(r"/\*!.*?\*/", text, re.S)
                if blocks:
                    target = destination / "license-texts/bundled-comments" / (path.name + ".txt")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_text("\n\n".join(blocks) + "\n", encoding="utf-8")
                    bundled_notices.append({"archivePath": name, "file": target.relative_to(destination).as_posix(),
                                            "blocks": len(blocks), "sha256": digest_file(target)})
            if is_package:
                try:
                    metadata = json.loads(data)
                except (UnicodeDecodeError, json.JSONDecodeError):
                    continue
                packages.append({
                    "name": metadata.get("name", "UNKNOWN"), "version": metadata.get("version", "UNKNOWN"),
                    "declaredLicense": metadata.get("license", metadata.get("licenses")),
                    "repository": repository_url(metadata.get("repository")),
                    "homepage": metadata.get("homepage"), "archivePath": name,
                    "metadataSha256": hashlib.sha256(data).hexdigest(),
                    "dependencies": metadata.get("dependencies", {}),
                    "optionalDependencies": metadata.get("optionalDependencies", {}),
                    "classification": "vendored-runtime" if "/dist/node_modules/" in name else "node-bundled-tool" if name.startswith("opt/node/") else "installed-npm",
                })
            elif is_lock:
                lock = json.loads(data)
            elif is_status:
                status = data.decode("utf-8", "replace")
            if is_notice:
                target = destination / "license-texts" / "runtime" / Path(*path.parts)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                notices.append({"archivePath": name, "file": target.relative_to(destination).as_posix(),
                                "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(), "kind": "text"})
    debs = []
    for fields in paragraphs(status):
        if fields.get("Status") != "install ok installed":
            continue
        package = fields["Package"]
        directory = "usr/share/doc/" + package
        seen = set()
        while directory in doc_aliases and directory not in seen:
            seen.add(directory)
            directory = doc_aliases[directory]
        prefix = directory + "/"
        debs.append({
            "name": package, "version": fields.get("Version"), "architecture": fields.get("Architecture"),
            "source": fields.get("Source", package), "homepage": fields.get("Homepage"),
            "description": fields.get("Description", "").split("\n")[0],
            "copyrightRecords": [record for record in notices if record["archivePath"] == prefix + "copyright"],
            "copyrightDirectoryAlias": directory if seen else None,
            "declaredLicense": None,
            "licenseEvidence": "copyright text, not an automatically inferred single SPDX expression",
        })
    installed = {package["archivePath"].removeprefix("opt/tianshu/").removesuffix("/package.json") for package in packages}
    omitted = [{"path": key, "name": info.get("name"), "version": info.get("version"),
                "declaredLicense": info.get("license"), "optional": info.get("optional", False),
                "reason": "lock entry exists but no shipped package.json at this path"}
               for key, info in lock.get("packages", {}).items() if key and key not in installed]
    return {"npmPackages": sorted(packages, key=lambda row: row["archivePath"]),
            "ubuntuPackages": sorted(debs, key=lambda row: row["name"]),
            "licenseRecords": sorted(notices, key=lambda row: row["archivePath"]),
            "nativePayloads": sorted(native, key=lambda row: row["archivePath"]),
            "copyrightDirectoryAliases": doc_aliases,
            "lockEntriesNotObservedAsShippedPackages": omitted,
            "embeddedBundleReferences": [{"name": name, "version": None, "declaredLicense": None,
                                           "bundles": sorted(files), "status": "bundle source marker; package/version/license requires upstream lockfile review"}
                                         for name, files in sorted(bundled_references.items())],
            "embeddedBundleNoticeRecords": bundled_notices}


def scan_gradle(destination, gradle_cache):
    input_path = destination / "inventory/gradle-components.json"
    if not input_path.exists():
        return [], "missing: run exportOpenSourceDependencies with the supplied init script"
    observed = json.loads(input_path.read_text(encoding="utf-8"))["components"]
    identities = {}
    for row in observed:
        key = tuple(row.get(field) for field in ("configuration", "group", "name", "version", "artifactFile", "sha256", "classifier", "type"))
        identities.setdefault(key, row)
    rows = list(identities.values())
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    for row in rows:
        directory = gradle_cache / row["group"] / row["name"] / row["version"]
        matches = sorted(directory.glob("*/*.pom")) if directory.exists() else []
        licenses, evidence, artifact_notices = [], [], []
        for pom in matches:
            document = ET.parse(pom).getroot()
            for node in document.findall("m:licenses/m:license", namespace):
                record = {key: node.findtext("m:" + key, default="", namespaces=namespace)
                          for key in ("name", "url", "distribution")}
                if record not in licenses:
                    licenses.append(record)
            target = destination / "metadata/gradle" / row["group"] / row["name"] / row["version"] / pom.name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(pom.read_bytes())
            evidence.append({"file": target.relative_to(destination).as_posix(), "sha256": digest_file(pom)})
        for artifact in sorted(directory.glob("*/" + row["artifactFile"])):
            if digest_file(artifact) != row["sha256"] or not zipfile.is_zipfile(artifact):
                continue
            def preserve_notices(bundle, prefix=""):
                for entry in bundle.infolist():
                    if entry.is_dir() or entry.file_size > 16 * 1024 * 1024:
                        continue
                    path = PurePosixPath(entry.filename)
                    if path.is_absolute() or ".." in path.parts:
                        raise ValueError("Unsafe artifact metadata path")
                    if not NOTICE_NAME.fullmatch(path.name):
                        continue
                    data = bundle.read(entry)
                    relative = prefix + path.as_posix()
                    target = destination / "license-texts/gradle" / row["group"] / row["name"] / row["version"] / row["artifactFile"] / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(data)
                    artifact_notices.append({"artifactEntry": relative, "file": target.relative_to(destination).as_posix(),
                                            "sha256": hashlib.sha256(data).hexdigest()})
            with zipfile.ZipFile(artifact) as bundle:
                preserve_notices(bundle)
                for entry in bundle.infolist():
                    if entry.filename == "classes.jar" and entry.file_size <= 64 * 1024 * 1024:
                        with zipfile.ZipFile(io.BytesIO(bundle.read(entry))) as nested:
                            preserve_notices(nested, "classes.jar/")
            break
        row["declaredLicenses"] = licenses
        row["pomEvidence"] = evidence
        row["artifactNoticeEvidence"] = artifact_notices
        row["licenseStatus"] = "declared in resolved POM; original notices still required" if licenses else "not found in cached POM; review parent/artifact/upstream"
    return rows, "resolved APK artifacts with locally available POM evidence"


def cyclonedx(report):
    components = []
    for package in report["runtime"]["npmPackages"]:
        value = {"type": "library", "name": package["name"], "version": package["version"],
                 "bom-ref": "runtime:" + package["archivePath"],
                 "properties": [{"name": "xuanji:archivePath", "value": package["archivePath"]}],
                 "hashes": [{"alg": "SHA-256", "content": package["metadataSha256"]}]}
        if isinstance(package["declaredLicense"], str):
            value["licenses"] = [{"license": {"name": package["declaredLicense"]}}]
        if package["repository"]:
            value["externalReferences"] = [{"type": "vcs", "url": package["repository"]}]
        components.append(value)
    for package in report["runtime"]["ubuntuPackages"]:
        components.append({"type": "library", "name": package["name"], "version": package["version"],
                           "bom-ref": "ubuntu:" + package["name"] + "@" + package["version"],
                           "purl": "pkg:deb/ubuntu/" + package["name"] + "@" + quote(package["version"], safe="") + "?arch=" + package["architecture"],
                           "properties": [{"name": "xuanji:licenseReview", "value": "Read package copyright records; no single license inferred"}]})
    for package in report["runtime"]["embeddedBundleReferences"]:
        components.append({"type": "library", "name": package["name"],
                           "bom-ref": "embedded:" + package["name"],
                           "properties": [{"name": "xuanji:licenseReview", "value": package["status"]}]})
    for artifact in report["gradle"]:
        value = {"type": "library", "group": artifact["group"], "name": artifact["name"], "version": artifact["version"],
                 "bom-ref": "gradle:" + artifact["configuration"] + ":" + artifact["group"] + ":" + artifact["name"] + ":" + artifact["version"] + ":" + artifact["artifactFile"],
                 "hashes": [{"alg": "SHA-256", "content": artifact["sha256"]}],
                 "properties": [{"name": "xuanji:configuration", "value": artifact["configuration"]}]}
        if artifact["declaredLicenses"]:
            value["licenses"] = [{"license": {"name": license["name"]}} for license in artifact["declaredLicenses"]]
        components.append(value)
    for artifact in report["standaloneRuntimeArtifacts"] + report["fonts"]:
        value = {"type": "file", "name": artifact["file"], "bom-ref": "asset:" + artifact["file"],
                 "hashes": [{"alg": "SHA-256", "content": artifact["sha256"]}]}
        if artifact.get("declaredLicense"):
            value["licenses"] = [{"license": {"name": artifact["declaredLicense"]}}]
        components.append(value)
    return {"bomFormat": "CycloneDX", "specVersion": "1.6", "version": 1,
            "metadata": {"timestamp": report["generatedAt"],
                         "tools": {"components": [{"type": "application", "name": "xuanji-open-source-inventory", "version": "1"}]},
                         "properties": [{"name": "xuanji:coverage", "value": "Observed package metadata and binaries; incomplete internal bundled code/native source provenance; not legal clearance"}]},
            "components": components}


def inventory_markdown(report, destination):
    def cell(value):
        if value is None or value == "":
            return "待核实"
        if isinstance(value, (dict, list)):
            value = json.dumps(value, ensure_ascii=False)
        return str(value).replace("|", "\\|").replace("\n", " ").replace("\r", "")

    lines = ["# 璇玑实际组件清单", "", "此表从本地打包物料自动生成。许可字段是原始声明 / 证据索引，不是授权结论；未知版本保留未知，不据包名推断。", "",
             "生成时间：`" + report["generatedAt"] + "`。精确数据与完整 notice 路径见 `inventory/components.json`。", "",
             "## 运行环境归档", "", "- 文件：`" + report["runtimeArchive"]["file"] + "`",
             "- SHA-256：`" + report["runtimeArchive"]["sha256"] + "`", "",
             "以下独立二进制与 version 文件来自实际 APK assets。对应源码 / 许可证据见 THIRD_PARTY_NOTICES 和发布清单。", "",
             "| 文件 | 字节 | SHA-256 |", "| --- | --- | --- |"]
    for row in report["standaloneRuntimeArtifacts"]:
        lines.append("| " + cell(row["file"]) + " | " + str(row["bytes"]) + " | " + row["sha256"] + " |")
    lines += ["", "## 实际 npm 包", "", "包含 Node 官方发行包自带 npm、Tianshu 安装依赖与它的 vendored runtime。这里只列归档中真实存在的 package.json，package-lock 中未装入的包不算已分发。", "",
              "| 名称 | 版本 | 原声明许可 | 分发类别 | 声明来源 |", "| --- | --- | --- | --- | --- |"]
    for row in report["runtime"]["npmPackages"]:
        source = row["repository"] or row["archivePath"]
        lines.append("| " + " | ".join(map(cell, [row["name"], row["version"], row["declaredLicense"], row["classification"], source])) + " |")
    lines += ["", "## 天枢 bundle 中观察到的嵌入包", "", "名称来自实际编译 source marker。精确版本 / 独立许可证仍需要原始构建 lockfile 与 notices核对；即使也在 npm 表出现，不可自动判定为同一个版本。", "",
              "| 名称 | 精确版本 | 许可证据 | 出现 bundle 数 |", "| --- | --- | --- | --- |"]
    for row in report["runtime"]["embeddedBundleReferences"]:
        lines.append("| " + cell(row["name"]) + " | 待核实 | 保留 compiled notice；逐包核实 | " + str(len(row["bundles"])) + " |")
    lines += ["", "## Ubuntu 已安装包", "", "许可列只收录版权原文中观察到的 License 字段（可能多许可 / 文件范围），不将它们折叠成单一 SPDX expression。版权目录符号链接按归档目标关联。", "",
              "| 二进制包 | 版本 | 源包 | 版权原文 License 字段 |", "| --- | --- | --- | --- |"]
    for row in report["runtime"]["ubuntuPackages"]:
        tokens = []
        for record in row["copyrightRecords"]:
            if record.get("file"):
                text = (destination / record["file"]).read_text(encoding="utf-8", errors="replace")
                for token in re.findall(r"(?m)^License:\s*([^\n]+)", text):
                    if token not in tokens:
                        tokens.append(token)
        lines.append("| " + " | ".join(map(cell, [row["name"], row["version"], row["source"], "; ".join(tokens) or "见完整copyright正文"])) + " |")
    lines += ["", "附加实际分发：ripgrep 14.1.1-1（由 dpkg-deb 直接解包，不在上述安装状态表）；原文许可 MIT / Unlicense 及特定文件 BSD-3-clause。", "",
              "## Android 实际 Gradle 依赖", "", "相同坐标合并显示 release / debug 覆盖情况。这里列 resolved artifacts，不把 BOM 约束、构建插件、test-only JUnit 当成 APK代码。POM未自带许可且artifact已带原文者明确指出，不写成全部未知。", "",
              "| Maven坐标 | 版本 | 原POM许可 / artifact证据 | APK配置 |", "| --- | --- | --- | --- |"]
    groups = {}
    for row in report["gradle"]:
        key = row["group"] + ":" + row["name"] + ":" + row["version"]
        entry = groups.setdefault(key, {"row": row, "configurations": set()})
        entry["configurations"].add(row["configuration"].removesuffix("RuntimeClasspath"))
    for entry in sorted(groups.values(), key=lambda item: item["row"]["group"] + ":" + item["row"]["name"]):
        row = entry["row"]
        declaration = "; ".join(license["name"] for license in row["declaredLicenses"])
        if not declaration:
            declaration = "POM未声明；" + ("artifact原LICENSE/NOTICE已保留" if row["artifactNoticeEvidence"] else "须核查继承或上游许可")
        lines.append("| " + " | ".join(map(cell, [row["group"] + ":" + row["name"], row["version"], declaration, ", ".join(sorted(entry["configurations"]))])) + " |")
    lines += ["", "## 8个OFL艺术字体", "", "原字体版权及输入散列见 THIRD_PARTY_NOTICES、stellar-welcome-font.md 与 tools/subset_welcome_font.py。此处散列是生产子集输出。", "",
              "| 资源 | 许可 | 输出SHA-256 |", "| --- | --- | --- |"]
    for row in report["fonts"]:
        lines.append("| " + cell(row["file"]) + " | OFL-1.1（改名子集） | " + row["sha256"] + " |")
    lines += ["", "## 原生 / WASM载荷", "", "此处记录真实字节，不把外层包许可自动外推为所有静态链接代码 / grammar许可。", "",
              "| 归档内路径 | 字节 | SHA-256 |", "| --- | --- | --- |"]
    for row in report["runtime"]["nativePayloads"]:
        lines.append("| " + cell(row["archivePath"]) + " | " + str(row["bytes"]) + " | " + row["sha256"] + " |")
    lines += ["", "## 完整版权文本", "", "目录 `license-texts/` 保留实际原文；每条记录的路径 / hash由components.json索引。POM metadata单独保存在metadata/gradle，不代替完整条文。", "",
              "外部参考项目 / 商业服务 / 视觉素材不作为已装库混入上述清单，其来源和未核实事项见THIRD_PARTY_NOTICES与SOURCES。", ""]
    (destination / "INVENTORY.md").write_text("\n".join(lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "docs/open-source")
    parser.add_argument("--gradle-cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    args = parser.parse_args()
    destination = args.output.resolve()
    if ROOT not in destination.parents:
        parser.error("Output must remain under the project directory")
    rootfs = ROOT / "app/src/main/assets/runtime/rootfs.tgz"
    runtime = scan_runtime(rootfs, destination)
    standalone = [{"file": path.relative_to(ROOT).as_posix(), "bytes": path.stat().st_size,
                   "sha256": digest_file(path), "sourceEvidence": "scripts/build-runtime.sh; exact source archives/patches not yet preserved"}
                  for path in sorted(rootfs.parent.iterdir()) if path.is_file() and path != rootfs]
    fonts = [{"file": path.relative_to(ROOT).as_posix(), "bytes": path.stat().st_size,
              "sha256": digest_file(path), "declaredLicense": "OFL-1.1",
              "sourceEvidence": "tools/subset_welcome_font.py; docs/stellar-welcome-font.md; app/src/main/assets/licenses"}
             for path in sorted((ROOT / "app/src/main/res/font").glob("*.ttf"))]
    font_notices = []
    for path in sorted((ROOT / "app/src/main/assets/licenses").glob("*.txt")):
        target = destination / "license-texts/fonts" / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
        font_notices.append({"sourceFile": path.relative_to(ROOT).as_posix(), "file": target.relative_to(destination).as_posix(),
                             "sha256": digest_file(path), "bytes": path.stat().st_size})
    gradle, gradle_status = scan_gradle(destination, args.gradle_cache)
    report = {"format": "xuanji-open-source-inventory-v1", "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
              "runtimeArchive": {"file": rootfs.relative_to(ROOT).as_posix(), "sha256": digest_file(rootfs), "bytes": rootfs.stat().st_size},
              "runtime": runtime, "standaloneRuntimeArtifacts": standalone, "fonts": fonts, "fontLicenseRecords": font_notices,
              "gradleStatus": gradle_status, "gradle": gradle,
              "limitations": [
                  "Package metadata license declarations are not a legal determination or a substitute for original notice texts.",
                  "Bundled/minified code without package.json, native static dependencies and WASM grammars may require additional upstream SBOM/source review.",
                  "DPKG status excludes ripgrep copied by dpkg-deb -x; its copyright record is retained in licenseRecords.",
                  "Native Termux binaries need exact corresponding sources, Termux patchsets, build instructions and notices before public binary redistribution.",
                  "Runtime license symlinks are recorded, not followed onto the host filesystem.",
                  "Android dependency POMs may omit inherited licenses; empty declarations require manual upstream review.",
                  "This inventory covers local artifacts at generation time; a public release must regenerate and audit its exact APK.",
              ]}
    save_json(destination / "inventory/components.json", report)
    save_json(destination / "inventory/bom.cdx.json", cyclonedx(report))
    inventory_markdown(report, destination)
    print(json.dumps({"npmPackageRecords": len(runtime["npmPackages"]), "ubuntuPackages": len(runtime["ubuntuPackages"]),
                      "licenseRecords": len(runtime["licenseRecords"]), "nativePayloads": len(runtime["nativePayloads"]),
                      "embeddedBundleReferences": len(runtime["embeddedBundleReferences"]),
                      "embeddedBundleNotices": len(runtime["embeddedBundleNoticeRecords"]),
                      "gradleArtifacts": len(gradle), "gradleStatus": gradle_status, "output": str(destination)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
