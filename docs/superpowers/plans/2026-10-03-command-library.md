# Command Library Implementation Plan

**Goal:** Make Android commands easy to find and connect supported Tianshu 3.27.0 session operations to real server routes.

**Architecture:** Keep the existing native Compose UI, slash resolver, session API and runtime overlay. Add a presentation model for category/search/history, replace the command sheet with an inset-aware full-screen library, and extend verified session routes. Expanded workflow prompts stay server-side; chat shows short commands.

**Stack:** Kotlin, Jetpack Compose Material 3, SharedPreferences, kotlinx.serialization, Node runtime overlay, JUnit.

## Constraints

- Preserve user files and app data. Do not install or operate the phone.
- Use the bundled Tianshu 3.27.0 implementation as the capability boundary.
- Keep parameterized actions reviewable in the composer before sending.
- Show terminal-only capabilities with their actual limitations.
- No new UI or search dependencies; reuse the existing theme and icons.

## Tasks

- [ ] Command discovery: add Chinese titles, purpose categories, alias deduplication, normalized multi-token relevance search, enabled and disabled skill discovery. Test `/skill qa`, Chinese terms, aliases, custom precedence and ranking in `CommandPaletteModelTest`.
- [ ] Command library: replace `CommandPaletteSheet` with a full-screen dialog; fixed search/header, category rail, compact rows, favorites and recent selections, argument detail, disabled reasons and skill enable controls. Persist only command identifiers in SharedPreferences. Keep keyboard/navigation inset ownership in one container.
- [ ] Real session interfaces: extend `NativeSlashCommand`, `TianshuApi`, `TianshuSessionCommands`, `XuanjiViewModel`, `SessionCommandSheet`; connect compact, fork, handoff, team-resume, queue, steer and skill enable/disable. Reject invalid arguments, preserve session generation, distinguish accepted from completed.
- [ ] Runtime safeguards: protect team-resume before storing plans, preserve short handoff/team-resume prompt text and verify against the actual bundled server. Test queue/steer readiness and stop semantics with `tools/*runtime-check.mjs`.
- [ ] Project commands: bounded readable Markdown discovery, official descriptions and path safety; tests in `ProjectCommandDiscoveryTest`.
- [ ] Independent review: inspect all changes and correct concrete findings. Run JVM tests, runtime checks, `assembleDebug` and `lintDebug` using Android Studio JBR.
- [ ] Delivery: update canonical architecture and delivery notes, increment to 0.2.6 / 35, rebuild after doc updates, verify APK version/signature/assets and provide phone test steps.

## Ownership

- Root: presentation model, palette UI, ChatScreen wiring, version, final verification.
- Integration reviewer: Kotlin resolver/API/ViewModel/result sheet and tests.
- Capability reviewer: runtime overlay and executable runtime checks.
- Documentation reviewer: project command discovery/tests, independent UI review and canonical documentation.

## Verification Commands

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --max-workers=2
node tools/command-runtime-check.mjs
node tools/runtime-prompt-check.mjs
node tools/todo-runtime-check.mjs
```

Expected: no failed JVM tests, runtime checks exit 0, build/lint exit 0; remaining existing warnings reported explicitly. Phone layout/performance remains subject to the user's real-device test.
