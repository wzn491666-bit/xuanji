# Quality Audit: 2026-10-02

## Scope

Review and repair the existing Android application for faithful Tianshu
integration and a readable native interface. Feature expansion is paused.
The repository has no commit baseline; findings were checked against source,
focused tests, official contracts and the actual packaged Tianshu 3.27.0.

## Repairs

| Area | Repair |
| --- | --- |
| Startup | Recover a permanently invalidated Keystore alias once; show save errors in onboarding and reject startup after persistence failure. |
| Settings | Commit encrypted credential, IV and profile metadata together. Remove the misleading claim that upstream credentials are only held in memory. |
| HTTP | Send an empty body for bodyless POST; cancel requests with their coroutine; bound ordinary calls to 90 seconds. |
| SSE | Enable sending only after connection opens; retry with 2/4/8/16/30-second backoff; expose failures and manual retry; close on queue overflow and resume from the last consumed sequence. |
| Session | Restore official running state and sequence; ignore historical lifecycle events for that state; retain intermediate turns until task done. |
| Approvals | Queue concurrent requests; retain failed responses for retry; show full arguments and write contents; avoid splitting Unicode pairs in long previews. |
| Tool output | Append partial results and keep the tool running until its final result. |
| Cockpit | Clear old verification on new runs and authoritative empty snapshots; reject stale snapshot responses; distinguish failed delivery operations from their reported gate. |
| Metrics | Label cumulative usage correctly; show missing reasoning usage as unknown; identify context percentage as an estimate. |
| Runtime | Serialize installation, check cancellation during extraction/copy, preserve the existing root home, and own spawned processes through shutdown. |
| Android MCP | Bound active clients and workers, reject excess sockets, enforce request deadlines, close active sockets on stop. |
| Accessibility | Bound traversal, retain clickable/scrollable ancestor semantics, validate the intended package/window again after approval. |
| Shizuku | Set the required process suffix; log execution errors; unbind on cancellation and wait for the owned blocking call to settle before releasing the mutex. |
| Markdown | CommonMark with GFM tables and strikethrough; nested inline formatting, links, escaped pipes, tilde fences, preserved list numbering and equal-height table rows. Raw HTML remains inert. |
| Chat UI | Neutral light/dark tokens, compact runtime title, full-width composer, icon actions with tooltips, unframed assistant output, gray inline code and user bubbles. |
| Thinking | Only the heading toggles expansion; text remains selectable; measured-tail scrolling yields to user touch. |
| Commands | Separate executable workflows, loaded skills and the official directory. Choose a command to fill the composer; unsupported entries are disabled. |
| Workbench | Unframed sections, restrained item surfaces, clearer metric labels and explicit delivery failure. |

## Verification

Final gate completed successfully: 139 JVM tests, zero failures/errors;
Android lint: zero errors, 33 warnings; debug APK assembly succeeded.
The remaining lint groups are AGP version, ARM-only ABI support, redundant API
checks/super call, a guarded API constant, intentional sdcard paths and KTX
style suggestions. Warnings were not suppressed to obtain a clean report.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=2 --console=plain
```

Device: Xiaomi 23116PN5BC, ADB serial 16bf8479. Replacement installs preserve
application data. No paid model requests were issued by the audit automation.
User workspace files were not modified.

Final artifact: versionName 0.1.18, versionCode 19.
SHA-256: `8B418DF072089AFF887F304FCC1E3CAF480A5060DF2D82335AE89C8D5EE126CC`.

Device evidence obtained on the intermediate 0.1.17/code18 build:

- Replacement APK install succeeded; package version was checked independently.
- User re-entered credentials in the app and completed startup. Actual chat
  showed the runtime running, restored tool history and a ready new session.
- Ciphertext and IV entry presence was checked without printing their values.
- Actual keyboard/composer and offline Markdown sample screenshots were read.
  Gray inline code, nested lists and list numbering were visibly correct.
- The initial three-column table was too wide; final code now uses available
  width with a 104dp minimum per column instead of a fixed 164dp column.
- Further touch checks were paused when the user resumed operating the phone.
  No scroll-success claim is based on a screenshot of the wrong activity.

Final-build installation, uninterrupted light/dark/scroll checks and a process
restart remain pending the device being available for the short touch test.

The debug-only `UiAuditActivity` renders fixed offline content using the real
message, Markdown, composer and command-sheet components. It is excluded from
release builds and does not create sessions or write conversation history.
Its sample runtime label is fixed, and cannot establish real service health.
The actual MainActivity is checked separately for startup and restoration.

## Remaining Gaps

- Full terminal parity is not achieved. Several official slash commands have
  no matching HTTP route. Catalog visibility does not make them executable.
- The packaged runtime lacks Git and Python. Python workflows and native Git
  operations require those dependencies; a UI cannot substitute for them.
- No verified convergence-score or agent-wave event exists in the packaged
  protocol. Those panels remain hidden instead of manufacturing telemetry.
- The Android service and UI share one application process. Foreground service
  status does not guarantee survival under every OEM power restriction.
- Shell cancellation destroys the owned process; descendant processes started
  by commands may survive. Full process-group ownership still needs work.
- Markdown images currently render their alt/link content, without fetching
  bitmap images. Code blocks have no syntax highlighting or mathematical layout.
- Android Keystore encrypts Android settings. Official provider configuration
  persists separately inside the app-private runtime, according to upstream
  behavior; this is not an ephemeral-credential mode.
- Real streaming scroll, lock-screen survival and Shizuku Binder cancellation
  need dedicated device scenarios beyond the offline visual sample.
- The Windows Computer Use screenshot channel still reports FrameArrived or
  window-capture timeout after recovery attempts. ADB screenshot acquisition
  works; no claim is made that the desktop channel is repaired.
- A historical model description of a `whoami;` failure is not reproducible
  evidence. The packaged tool uses `sh -c`, and bash/whoami/dash are present.
  Exact tool arguments and stderr are required before diagnosing that report.

## References

- Material 3: https://m3.material.io/components
- Conversation hierarchy: https://www.assistant-ui.com/examples/chatgpt
- Tianshu: https://github.com/huiliyi37/Tianshu-harness

## Manual Acceptance Prompt

Use a fresh test conversation. Ask for Markdown containing a three-column
table, gray inline code, a fenced code block, nested lists and a long response.
Expand completed thinking, scroll to the bottom and back, and confirm no forced
return. Open the command panel, search and select a loaded skill; confirm only
the compact slash command fills the composer. Do not run destructive commands,
modify user files or test automatic deletion. Record the installed version,
screen behavior, runtime state and any complete error messages.
