# Xuanji Quality Audit And Remediation

Goal: improve the existing Android application against two requirements: faithful
Tianshu integration and a polished, usable native interface. Roadmap expansion is
paused. Review the complete source baseline because the Git repository has no
commits yet.

## Constraints

- Preserve user files and application data. Use replacement APK installs only.
- Keep upstream Tianshu behavior and protocol semantics authoritative.
- Never advertise simulated telemetry or unsupported commands as operational.
- Never describe provider credentials as ephemeral when upstream persists them.
- Keep native Compose, existing Android icons, and explicit light/dark tokens.
- Validate security and protocol changes with focused regression tests.
- Verify actual screens on the connected Android device without sending paid
  model requests or modifying workspace files.

## Tasks

- [x] Inspect upstream API, event protocol, runtime packaging and Android bridge.
- [x] Correct bodyless POST requests, intermediate turn completion, event loss,
  HTTP cancellation and stale session refreshes. Files: TianshuClient.kt,
  XuanjiViewModel.kt and focused protocol tests.
- [x] Correct secure-save failure feedback and invalidated Keystore recovery.
  Files: SecureSettings.kt, onboarding/OnboardingScreen.kt, ui/XuanjiApp.kt,
  XuanjiViewModel.kt. Reject persistence failure before provider synchronization.
- [x] Serialize runtime installation and honor cancellation. Bound Android MCP
  connections and validate accessibility targets after approval. Files:
  Runtime.kt, TianshuService.kt, AndroidMcpServer.kt and approval/accessibility
  support. Focused tests cover rejection, cancellation and target mismatch.
- [x] Replace incomplete Markdown parsing with CommonMark plus GFM tables.
  Preserve existing block contracts where practical; add links, escaped pipes,
  fenced-code variants, correct numbering and nested inline formatting.
  Keep raw HTML inert. Files: MarkdownModel.kt, MarkdownView.kt, Gradle catalog
  and Markdown tests.
- [x] Complete native design tokens; refine chat composer, message hierarchy,
  command panel and workbench containment. Remove inherited purple surfaces,
  nested section cards and unsafe whole-card touch handlers.
- [ ] Record reviewed capabilities, remaining integration gaps and tests in
  docs/quality-audit.md. Run full JVM tests, lint and assemble. Install the
  resulting build and verify light/dark screens and startup persistence.

Documentation and final JVM/lint/assemble gate are complete (139 tests,
0 lint errors, 33 warnings). Device startup and credential-entry checks succeeded
on 0.1.17. Final 0.1.18 install and uninterrupted visual/restart acceptance are
pending device availability. See docs/quality-audit.md for exact evidence.

## Design References

- https://m3.material.io/components : native controls, sheets and accessible
  touch targets.
- https://www.assistant-ui.com/examples/chatgpt : readable unframed assistant
  output, compact composer controls and explicit primary-action states.

## Validation

JAVA_HOME: C:/Program Files/Android/Android Studio/jbr

Run `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
On device, verify onboarding feedback, encrypted configuration recovery,
restart, command search, chat scrolling and workbench navigation. Record lint
warnings separately from errors and do not claim full Tianshu parity where the
official server does not expose a terminal-only capability.
