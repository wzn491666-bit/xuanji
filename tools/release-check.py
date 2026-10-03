"""Check publication inputs without printing credential values or changing files.

Default mode checks source candidates. --tracked and --history require this
project to be its own repository. --release also enforces the reviewed checklist;
a clean text scan alone never establishes binary or media distribution rights.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAX_BYTES = 8 * 1024 * 1024
APACHE_SHA256 = "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"
REQUIRED = (
    "LICENSE", "NOTICE", "README.md", "CONTRIBUTING.md", "SECURITY.md",
    "THIRD_PARTY_NOTICES.md", "docs/TECHNICAL_ARCHITECTURE.md",
    "docs/PRIVACY.md", "docs/open-source/CORRESPONDING_SOURCE.md", "docs/open-source/PUBLICATION.md",
    "docs/open-source/RELEASE_CHECKLIST.md", "docs/open-source/SOURCES.md",
    "docs/open-source/inventory/components.json", "docs/open-source/inventory/bom.cdx.json",
)
SKIP_DIRS = {".git", ".gradle", ".idea", ".kotlin", ".vscode", ".npm", "build", "node_modules", "__pycache__"}
EXCLUDED_PREFIXES = ("output/", "docs/design/references/", "app/src/main/assets/runtime/")
EVIDENCE_PREFIX = "docs/open-source/license-texts/"
PRIVATE_NAMES = {"local.properties", "keystore.properties", "signing.properties", "google-services.json", ".netrc", ".npmrc", ".pypirc"}
PRIVATE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key"}
ARCHIVE_SUFFIXES = {".apk", ".aab", ".apks", ".tgz", ".gz", ".zip", ".7z", ".deb", ".jar", ".aar"}
MEDIA_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".mov", ".webm"}
TEXT_SUFFIXES = {".kt", ".kts", ".gradle", ".md", ".txt", ".json", ".toml", ".xml", ".pom", ".html", ".css", ".csv", ".lock", ".ini", ".cfg", ".conf", ".js", ".mjs", ".cjs", ".py", ".sh", ".properties", ".yaml", ".yml", ".ps1", ".bat"}
TEXT_NAMES = {"LICENSE", "NOTICE", "COPYING", "COPYRIGHT", "OFL", "gradlew", ".gitignore", ".gitattributes"}
PATTERNS = {
    "provider-key-shaped": re.compile(rb"\bsk-[A-Za-z0-9_-]{20,}\b"),
    "github-token-shaped": re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b"),
    "private-key-header": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----"),
    "aws-key-shaped": re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "credential-url-shaped": re.compile(rb"\b(?:https?|postgres(?:ql)?|mysql|mongodb(?:\+srv)?)://[^\s/@:]+:[^\s/@]+@[^\s\x22\x27\x60<>\\]+"),
}
SYNTHETIC_URLS = {
    "app/src/test/java/com/xuanji/android/BrowserContractTest.kt": {b"https://" + b"user:pass@example.com/"},
    "app/src/test/java/com/xuanji/android/WebSearchClientTest.kt": {b"https://" + b"user:pass@example.com/"},
    "tools/browser-dom-check.cjs": {b"https://" + b"user:pass@example.com"},
}


def categories(data, relative=""):
    return [category for category, pattern in PATTERNS.items() if any(
        category != "credential-url-shaped" or match.group() not in SYNTHETIC_URLS.get(relative, set())
        for match in pattern.finditer(data))]


def private_path(relative):
    path = Path(relative)
    return (path.name in PRIVATE_NAMES or path.suffix.lower() in PRIVATE_SUFFIXES or
            path.name == ".env" or path.name.startswith(".env.") and path.name != ".env.example")


def excluded_path(relative):
    # The preserved license evidence tree intentionally mirrors runtime paths,
    # including node_modules directories that contain only license/notice text.
    # Real dependency trees outside the evidence directory remain excluded.
    evidence = relative.startswith(EVIDENCE_PREFIX)
    skip_dirs = SKIP_DIRS - {"node_modules"} if evidence else SKIP_DIRS
    return (relative.startswith(EXCLUDED_PREFIXES) or any(part in skip_dirs for part in Path(relative).parts) or
            (not evidence and any("npm" in part.lower() and "cache" in part.lower() for part in Path(relative).parts)))


def source_paths():
    for directory, names, files in os.walk(ROOT, followlinks=False):
        parent = Path(directory).relative_to(ROOT)
        names[:] = [name for name in names if not excluded_path((parent / name).as_posix() + "/")]
        for name in files:
            relative = (parent / name).as_posix()
            if not excluded_path(relative) and not private_path(relative) and Path(relative).suffix.lower() not in {".log", ".hprof"}:
                yield relative


def git(*args):
    result = subprocess.run(["git", "-C", str(ROOT), *args], capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError("git-operation-failed")
    return result.stdout


def require_project_repository():
    root = Path(os.fsdecode(git("rev-parse", "--show-toplevel")).strip()).resolve()
    if root != ROOT:
        raise RuntimeError("project-is-not-repository-root")


def tracked_paths():
    require_project_repository()
    return [os.fsdecode(path) for path in git("ls-files", "-z").split(b"\0") if path]


def path_kind(relative):
    path = Path(relative)
    if private_path(relative):
        return "private-material"
    if excluded_path(relative) or path.suffix.lower() in {".log", ".hprof"}:
        return "excluded-publication-material"
    if path.suffix.lower() in MEDIA_SUFFIXES:
        return "media-manual-review"
    if path.suffix.lower() in ARCHIVE_SUFFIXES and relative != "gradle/wrapper/gradle-wrapper.jar":
        return "binary-manual-review"
    if path.name in TEXT_NAMES or path.suffix.lower() in TEXT_SUFFIXES:
        return "text"
    return "other"


def check_paths(paths, tracked):
    findings, review, scanned, unread = [], [], 0, 0
    for relative in paths:
        kind = path_kind(relative)
        if kind in {"private-material", "excluded-publication-material"}:
            if tracked:
                findings.append({"path": relative, "category": kind})
            continue
        if kind in {"media-manual-review", "binary-manual-review"}:
            review.append({"path": relative, "category": kind})
            continue
        if kind != "text":
            continue
        path = ROOT / relative
        if not path.is_file() or path.is_symlink() or path.stat().st_size > MAX_BYTES:
            findings.append({"path": relative, "category": "text-not-checked"})
            unread += 1
            continue
        try:
            data = path.read_bytes()
        except OSError:
            findings.append({"path": relative, "category": "text-not-readable"})
            unread += 1
            continue
        scanned += 1
        findings.extend({"path": relative, "category": category} for category in categories(data, relative))
    return findings, review, scanned, unread


def check_history():
    require_project_repository()
    findings, review = [], []
    entries = set()
    # Tree listings are NUL-delimited: filenames with spaces or newlines cannot
    # bypass path checks. A blob may have different paths in different commits.
    for tree in set(git("log", "--all", "--format=%T").splitlines()):
        for entry in git("ls-tree", "-r", "-z", os.fsdecode(tree)).split(b"\0"):
            if not entry:
                continue
            metadata, name = entry.split(b"\t", 1)
            _, object_type, object_id = metadata.split()
            if object_type == b"blob":
                entries.add((object_id, name))
    last_object, last_data = None, None
    scanned_objects = set()
    for object_id, name in sorted(entries):
        relative = os.fsdecode(name)
        kind = path_kind(relative)
        if kind in {"private-material", "excluded-publication-material"}:
            findings.append({"path": relative, "category": "history-" + kind})
            continue
        if kind in {"media-manual-review", "binary-manual-review"}:
            review.append({"path": relative, "category": "history-" + kind})
            continue
        if kind != "text":
            continue
        if object_id != last_object:
            last_object = object_id
            if int(git("cat-file", "-s", os.fsdecode(object_id))) > MAX_BYTES:
                last_data = None
            else:
                last_data = git("cat-file", "blob", os.fsdecode(object_id))
        data = last_data
        if data is None:
            findings.append({"path": relative, "category": "history-text-not-checked"})
            continue
        scanned_objects.add(object_id)
        findings.extend({"path": relative, "category": "history-" + category} for category in categories(data, relative))
    return findings, review, len(scanned_objects)


def document_checks(release):
    findings = [{"path": path, "category": "required-document-missing"} for path in REQUIRED if not (ROOT / path).is_file()]
    license_file = ROOT / "LICENSE"
    if license_file.is_file():
        canonical = license_file.read_bytes().replace(b"\r\n", b"\n")
        if hashlib.sha256(canonical).hexdigest() != APACHE_SHA256:
            findings.append({"path": "LICENSE", "category": "apache-official-text-mismatch"})
    for path in ("docs/open-source/inventory/components.json", "docs/open-source/inventory/bom.cdx.json"):
        if (ROOT / path).is_file():
            try:
                json.loads((ROOT / path).read_text(encoding="utf-8"))
            except (ValueError, OSError):
                findings.append({"path": path, "category": "inventory-json-invalid"})
    checklist = ROOT / "docs/open-source/RELEASE_CHECKLIST.md"
    if release and checklist.is_file():
        outstanding = len(re.findall(r"^\s*- \[ \]", checklist.read_text(encoding="utf-8"), re.MULTILINE))
        if outstanding:
            findings.append({"path": checklist.relative_to(ROOT).as_posix(), "category": "release-checklist-incomplete", "count": outstanding})
    return findings


def self_test():
    assert categories(b"sk-" + b"a" * 32) == ["provider-key-shaped"]
    assert categories(b"ghp_" + b"a" * 36) == ["github-token-shaped"]
    assert categories(b"-----BEGIN " + b"PRIVATE KEY-----") == ["private-key-header"]
    assert categories(b"AKIA" + b"A" * 16) == ["aws-key-shaped"]
    assert categories(b"https://" + b"user:password@example.com") == ["credential-url-shaped"]
    fixture = "tools/browser-dom-check.cjs"
    dummy = b"https://" + b"user:pass@example.com"
    assert categories(dummy, fixture) == []
    assert categories(dummy, "app/src/main/java/example.kt") == ["credential-url-shaped"]
    assert categories(dummy + b"?token=not-a-fixture", fixture) == ["credential-url-shaped"]
    assert categories(b"https://" + b"user:unreviewed-password@example.com", fixture) == ["credential-url-shaped"]
    assert categories(dummy + b" " + b"sk-" + b"a" * 32, fixture) == ["provider-key-shaped"]
    assert categories(b"https://example.com; sk-placeholder; test-token") == []
    assert private_path(".env.production") and not private_path(".env.example")
    assert excluded_path("app/src/main/assets/runtime/rootfs.tgz")
    assert excluded_path("docs/design/references/example.png")
    assert excluded_path("node_modules/pkg/index.js")
    assert not excluded_path("docs/open-source/license-texts/runtime/opt/node/lib/node_modules/npm/LICENSE")
    assert path_kind("gradle/wrapper/gradle-wrapper.jar") == "other"
    assert path_kind("app/src/main/res/drawable/icon.png") == "media-manual-review"
    assert path_kind("NOTICE") == "text"
    from unittest.mock import patch
    blob_a, blob_b, blob_c = b"a" * 40, b"b" * 40, b"c" * 40
    blobs = {blob_a: dummy, blob_b: b"sk-" + b"a" * 32}
    tree = (b"100644 blob " + blob_a + b"\ttools/browser-dom-check.cjs\0" +
            b"100644 blob " + blob_a + b"\tapp/src/main/copy.txt\0" +
            b"100644 blob " + blob_b + b"\tnotes with space.txt\0" +
            b"100644 blob " + blob_b + b"\tnotes\nsecond-line.txt\0" +
            b"100644 blob " + blob_c + b"\tprivate.pem\0")
    def fake_git(*args):
        if args[:1] == ("log",):
            return b"test-tree\n"
        if args[:1] == ("ls-tree",):
            return tree
        object_id = args[-1].encode()
        assert object_id != blob_c, "Private material must be flagged without reading it"
        return str(len(blobs[object_id])).encode() if args[1] == "-s" else blobs[object_id]
    with patch.dict(globals(), {"git": fake_git, "require_project_repository": lambda: None}):
        findings, review, scanned = check_history()
    assert scanned == 2 and review == []
    assert len(findings) == 4
    assert {finding["path"] for finding in findings} == {"app/src/main/copy.txt", "notes with space.txt", "notes\nsecond-line.txt", "private.pem"}
    assert b"a" * 32 not in json.dumps(findings).encode()
    print(json.dumps({"selfTest": "passed", "credentialValuesPrinted": False}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--tracked", action="store_true", help="Check exactly git-tracked paths, including accidentally committed private files")
    parser.add_argument("--history", action="store_true", help="Also inspect all reachable git blobs; values are never printed")
    parser.add_argument("--release", action="store_true", help="Require the reviewed release checklist to have no unfinished items")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    try:
        paths = tracked_paths() if args.tracked else source_paths()
        findings, review, scanned, unread = check_paths(paths, args.tracked)
        findings.extend(document_checks(args.release))
        history_scanned = 0
        if args.history:
            history_findings, history_review, history_scanned = check_history()
            findings.extend(history_findings)
            review.extend(history_review)
    except (OSError, RuntimeError, ValueError) as error:
        # Do not print subprocess stderr, file contents or credential-bearing values.
        print(json.dumps({"status": "check-failed", "category": str(error) if isinstance(error, RuntimeError) else "input-check-failed"}))
        return 2
    print(json.dumps({
        "status": "findings" if findings else "text-and-document-checks-passed",
        "mode": "tracked" if args.tracked else "source-candidates",
        "scannedTextFiles": scanned, "unreadTextFiles": unread,
        "historyScannedTextBlobs": history_scanned,
        "findings": findings, "manualReview": review,
        "limitations": [
            "Only selected credential patterns and files up to 8 MiB are checked; values are never printed.",
            "Git history was checked." if args.history else "Git history has not been checked; use --history in the project repository.",
            "History checking covers commit trees reachable from local refs; remote-only refs, submodule histories, LFS objects and dangling objects require separate review.",
            "Media OCR/EXIF, archive contents, final APK, service authorization and all third-party legal obligations are not verified.",
            "A clean result is not publication approval. Use the reviewed release checklist and inspect manual-review inputs.",
        ],
    }, ensure_ascii=True))
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
