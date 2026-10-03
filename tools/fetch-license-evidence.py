"""Fetch public upstream license evidence; never infer exact binary provenance."""

import hashlib
import json
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ("termux/proot", "COPYING", "native binary source family; exact Termux build correspondence pending"),
    ("termux/libandroid-shmem", "LICENSE", "native binary source family; exact Termux build correspondence pending"),
    ("Zhou-Shilin/Aether", "LICENSE", "architecture reference, not declared as vendored code"),
    ("woaiys3/deepseek-harness-android-app", "LICENSE", "architecture reference, not declared as vendored code"),
    ("liustack/modsearch", "LICENSE", "search API and architecture reference"),
    ("DDDMUC/dsh-free-search", "LICENSE", "search API and architecture reference"),
    ("MochiNek0/dsh-web-search-free", "LICENSE", "search API and architecture reference"),
    ("4cya/jupsearch-mcp", "LICENSE", "search API and architecture reference"),
    ("omdsh-dev/dsh-browser", "LICENSE", "DOM browser reference; rich-text editing algorithm locally adapted in assets/browser/action.js", "23895d7292495f665e5676d2f174d022bb3c3605"),
    ("KevinnZou/compose-webview", "LICENSE", "WebView lifecycle reference"),
    ("anysearch-ai/anysearch-mcp-server", "LICENSE", "provider API reference"),
    ("vercel-labs/agent-browser", "LICENSE", "browser capability audit reference"),
    ("browser-use/browser-use", "LICENSE", "browser capability audit reference"),
    ("RikkaApps/Shizuku-API", "LICENSE", "actual APK Gradle runtime dependency; resolved 13.1.5 POM declares MIT"),
]


def main():
    destination = ROOT / "docs/open-source"
    manifest = destination / "inventory/research-license-evidence.json"
    previous = {record["repository"]: record for record in json.loads(manifest.read_text(encoding="utf-8"))} if manifest.exists() else {}
    records = []
    for source in SOURCES:
        repository, filename, relationship, *revisions = source
        revision = revisions[0] if revisions else None
        try:
            api = "https://api.github.com/repos/" + repository + "/contents/" + filename
            if revision:
                api += "?ref=" + revision
            request = urllib.request.Request(api, headers={"User-Agent": "Xuanji-public-license-audit", "Accept": "application/vnd.github+json"})
            with urllib.request.urlopen(request, timeout=20) as response:
                metadata = json.load(response)
            import base64
            data = base64.b64decode(metadata["content"])
            target = destination / "license-texts/research" / repository.replace("/", "--") / filename
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            records.append({"repository": repository, "relationship": relationship,
                            "url": metadata["html_url"], "githubBlobSha": metadata["sha"],
                            "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data),
                            "file": target.relative_to(destination).as_posix(),
                            "sourceRevision": revision,
                            "limitation": "Exact inspected source revision; local adaptation attribution still needs review" if revision else "License at inspected default branch; not proof of exact binary source version or original code-copy history"})
            print(repository + ": license evidence saved")
        except Exception as error:
            prior = previous.get(repository, {})
            target = destination / "license-texts/research" / repository.replace("/", "--") / filename
            if prior.get("file"):
                records.append({**prior, "refreshError": type(error).__name__})
                print(repository + ": earlier evidence preserved (refresh " + type(error).__name__ + ")")
            elif target.exists():
                data = target.read_bytes()
                blob_sha = hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()
                records.append({"repository": repository, "relationship": relationship,
                                "url": "https://api.github.com/repos/" + repository + "/git/blobs/" + blob_sha,
                                "githubBlobSha": blob_sha, "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data),
                                "file": target.relative_to(destination).as_posix(), "sourceRevision": revision,
                                "refreshError": type(error).__name__,
                                "limitation": "Previously fetched license bytes preserved. Blob ID recalculated locally; no fresh network result or exact binary provenance claimed."})
                print(repository + ": earlier downloaded bytes preserved (refresh " + type(error).__name__ + ")")
            else:
                records.append({"repository": repository, "relationship": relationship, "error": type(error).__name__})
                print(repository + ": evidence unavailable (" + type(error).__name__ + ")")
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
