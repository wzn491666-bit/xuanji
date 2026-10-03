# Product UI Implementation Plan

**Goal:** Replace the rejected default-looking chat surface with a coherent
native chat and workspace design grounded in inspected product screens.

**Architecture:** Reuse existing Compose components and ViewModel actions.
Share the real header and composer with the debug visual activity. No backend
protocol changes and no new UI framework.

**Stack:** Kotlin, Compose Material 3, existing Material icons and CommonMark.

## Tasks

- [x] Inspect current UI and save/inspect actual Claude references.
- [ ] Implement compact shared chat header, monochrome identity, refined empty
  view, wrapping user bubble, BasicTextField composer and real model entry.
  Files: ChatComponents.kt, ChatScreen.kt, Theme.kt, ic_xuanji_mark.xml.
- [ ] Unify command search/navigation and workspace navigation/metrics/settings.
  Files: CommandPaletteSheet.kt, WorkspaceScreen.kt.
- [ ] Update offline UiAuditActivity to use production components; bump APK
  version to 0.1.19/code 20.
- [ ] Run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` using JBR
  and `--max-workers=2`; inspect all failures before delivery.
- [ ] Inspect actual device screenshots when ADB is connected. Record any
  unavailable device checks honestly in the delivery report.

## Acceptance

No credential loss, no unsupported command treated as executable, no fake
runtime health inferred from offline samples. Message selection, Markdown,
thinking collapse, approval queues and manual scroll control must remain.
Model toolbar opens configured provider controls. Long labels ellipsize and
navigation remains operable at narrow width and increased font scale.
