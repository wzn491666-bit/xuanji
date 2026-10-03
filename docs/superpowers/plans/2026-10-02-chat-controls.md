# Chat Controls Implementation

## Design

Use the supplied Codex composer and effort popover as the visual reference.
Keep a white, rounded input surface with compact tool icons and a circular
send/stop button. The model/effort trigger opens an anchored popover with a
discrete blue slider, current level, model link and reset-to-auto action.
Commit on release, display pending state, and update the label only after
POST /sessions/:id/effort and GET /sessions/:id agree. DeepSeek/Kimi use
off/low/high/max because upstream maps medium to high. Unsupported models
must not expose a functioning-looking slider.

History is a native modal navigation drawer populated from GET /sessions,
with search, recent date groups, selected state, retry and a new chat action.
Selecting a session replays its real SSE history with fresh sequence guards.
No destructive history controls. Block switching while sending/running.

Use blue for actions, teal for successful states and coral for permission
and auxiliary emphasis, with separate contrast-safe dark tokens.

## Tasks

- [x] Fix terminal status processing and stop acknowledgement; preserve drafts
  when submission is rejected, prevent repeated submissions while pending.
- [x] Add real effort API, model capability policy and confirmation tests.
- [x] Add session history API, parser, drawer and guarded restore.
- [x] Build and run transport/lifecycle tests; install without clearing data.
- [ ] Check real composer, slider, history, send/stop and theme screenshots.

## Verification

Use existing JVM transport tests with the local HttpServer for endpoint/body/ack
contracts. Regress aborted status and stale replay. Device checks must use
MainActivity for real behavior; offline visual activity proves layout only.
Use configured DeepSeek credentials solely for authorized short test turns.
Never record credentials in scripts, documents, screenshots or logs.

## Composer Revision (Approved User Feedback)

- [x] Replace workbench shortcuts with an anchored attachment/command menu.
  Use OpenMultipleDocuments, bounded private copies, removable attachment
  previews, image/document prompt payloads and readable runtime paths for
  other files. Preserve attachments in failed messages for idempotent retry.
- [x] Add manual/auto-safe/dangerously-skip-permissions approval choices and
  the upstream session model picker directly in the chat. Confirm server
  acknowledgement; preserve the current session and conversation.
- [x] Keep native Slider accessibility and stepped input; add animated
  chromatic tracks, gesture particles, spring thumb feedback and detent
  haptics. Release commits the latest position, failures restore real state.
- [x] Test attachment bounds, payloads, model identity and approval contracts;
  build and increment version to 0.1.23 / 24.
- [ ] Install the revision with existing data retained. ADB has no connected
  device; the prior installed version is not evidence for this APK.
- [ ] Inspect real-device menus, slider gestures, image/file selection and
  send/stop/resend. Record evidence and any unverified provider capabilities.

## Gemini Reference Revision

- [x] Use the user-supplied Gemini screenshots for empty-state composition:
  restrained header, a centered original Xuanji mark, spacious greeting and
  a continuous lower color wash behind the composer.
- [x] Place an anchored model picker at the top of chat, showing configured
  providers, key identity, descriptions and the actual selected model.
- [x] Move workspace/system permissions into the header overflow. Keep
  every composer control within the current conversation.
- [x] Inspect the upstream live vision-bridge endpoint before sending an
  image, accepting both native vision and a configured/same-provider bridge.
- [ ] Capture the revised UI on a connected device and inspect light/dark,
  portrait/landscape and large-font layouts. ADB disconnected during this run.

## Verification Result (2026-10-02)

Final command: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
with the Android Studio JBR and one Gradle worker. Exit code 0.
150 JVM tests passed, 0 failures, 0 errors. Lint: 0 errors, 34 warnings.
APK: `app/build/outputs/apk/debug/app-debug.apk`, version 0.1.23 / 24.

Image upload checks the actual per-session vision bridge before submitting.
Base64 is streamed from bounded private copies through the HTTP request body.
Preparation failures stay retryable and display the actual reason. Original
source files are never overwritten. No credentials were added to artifacts.

Remaining device checks:
1. Top model menu changes the same session; conversation remains intact.
2. Approval menu persists its selected mode; full access has a confirmation.
3. Slider gestures show particles and detent haptics, submit the actual level,
   and settle back to server state if the request fails.
4. Plus menu opens image/file pickers and the real command/skill palette.
5. Attach an image, remove it, attach again and verify the model sees it.
6. Attach a small text/code file and verify the agent reads its private copy.
7. Send, stop while running, and send again; search/reopen a historical chat.
8. Inspect light/dark, landscape, keyboard-open and large-font layouts.

Device visual quality and live provider attachment responses remain unverified;
the supplied reference images are design input, not screenshots of this build.

## Motion Refinement (2026-10-02)

- [x] Keep model/effort popovers mounted until their exit animation finishes;
  use anchored scale/translation and a 150ms entrance, 110ms exit fade.
- [x] Make particle time continuous across detents. Cache particle directions
  and track brushes; read particle frame state only during drawing.
- [x] Use actual native press/drag interactions and a bounded 420ms release
  tail; observe Android animation scale changes and disable particles at zero.
- [x] Add short spring feedback to icon/send buttons and crossfade send/stop
  glyphs within a fixed-size frame; keep chat text/layout outside animations.
- [x] Build and run existing tests for APK 0.1.24 / 25.
- [ ] Inspect on hardware. Smoothness and frame pacing require
  connected-device evidence.

### Enlarged Effort Control

- [x] Increase the popover width to 344dp, rail height to 30dp, drawing area
  to 64dp and thumb to 34dp; retain native stepped slider accessibility.
- [x] Scale blue filaments, wave layers, particle count and velocity with
  the actual selected effort. Keep a faint stationary ribbon at rest.
- [x] Integrate velocity across detents to preserve particle phase; keep the
  filled rail aligned with the native thumb rather than lagging behind it.
- [x] Add a bounded 900ms max-entry sweep, expanding ring and pale gold rays
  outside the thumb. Stronger detent haptics accompany entering max.
- [x] Stop gesture effects after a 420ms tail (750ms at max); do not replay
  old bursts on reopening. Android animation scale zero disables effects.
- [x] Verify final source with assembleDebug and testDebugUnitTest.
- [ ] Complete lintDebug and hardware visual/frame-pacing checks.

### Live Conversation Atmosphere and Thinking Display

- [x] Send temporary gesture preview separately from server-confirmed effort.
  Restore confirmed colors on dismissal, failed submission or session change.
- [x] Blend cached bottom-band palettes from mint/cyan to blue, rose and warm
  max accents. Integrate wave/particle velocity; keep frame reads in drawing.
- [x] Add a max-entry sweep and 900ms release tail. Stop custom frame loops
  in the background and when system animations are disabled.
- [x] Default thinking to a four-line bottom-aligned preview of the latest
  content, with bounded background Markdown-to-text parsing. Expanded content
  remains complete Markdown and does not collapse at stream completion.
- [x] Add collapsed/preview/expanded preference to workspace configuration.
  Persist the UI preference separately without restarting the runtime.
- [x] Verify the final APK and parser regression tests. The full lint run was
  canceled after prolonged analysis; do not report it as passing.
- [ ] Check live dragging, actual frame pacing, four-line clipping with large
  fonts, preference persistence and request-failure rollback on hardware.

### Final Build Evidence (0.1.24 / 25)

Final command: `:app:assembleDebug :app:testDebugUnitTest --max-workers=1`.
Exit code 0, BUILD SUCCESSFUL. 152 JVM tests, 0 failures, 0 errors.
The new parser tests retain table content and code literals in the preview
while removing Markdown formatting delimiters. IME insets lift the background
band with the composer so keyboard-open interaction remains visible.

Full lint analysis was canceled after prolonged execution; no successful
lint result is claimed for this revision. ADB still reports no devices.
The APK has not been installed or visually/frame-time tested on hardware.

## Gemini Video Light Band Revision (0.1.25 / 26)

Reference: the user-supplied 14.09s Gemini video, inspected through extracted
frames. The recording shows a full-width top color wash after submission,
transitioning through blue, mint, lime, yellow, rose and blue, then fading
as answer text appears. The user's later screenshot also calls for a larger,
stronger bottom wash; these are separate layers.

- [x] Add a feathered top light band while sending/waiting for the first reply.
  Fade on answer text, stop, failure or inactive runtime; avoid completed-history
  replay. Cache color fields and draw into a bounded offscreen layer.
- [x] Enlarge bottom glow to up to 58% of the viewport / 560dp, strengthen the
  composer-side color and retain the soft upward fade and effort preview.
- [x] Make the rail and resting thumb both 42dp; press enlarges the thumb by
  8%. Keep native accessibility and actual effort submission.
- [x] Keep ambient particles/waves while the effort popup is visible and in
  the foreground. Scale count, speed and brightness by level; return from
  pressed intensity to ambient after release. Off/reduced motion stays static.
- [x] Add mint, cyan, cobalt, lilac, rose, coral and gold to the light palettes.
- [x] Keep expanded thinking in the preview's bodyMedium typography; headings
  retain semibold weight without enlarging the font.
- [x] Move the sending band from the visible viewport bottom to the top over
  900ms. Morph cached bottom/top masks additively and dim the bottom atmosphere
  by the shared transfer state. On reply/stop/error fade the band and restore
  the bottom wash; keyboard insets affect both layers consistently.
- [x] Build and run lifecycle/parser/transport regression tests.
- [ ] Inspect actual framing, contrast and frame pacing on a connected device.

### Build Evidence (0.1.25 / 26)

Final command: `:app:assembleDebug :app:testDebugUnitTest --max-workers=1`.
Exit code 0, BUILD SUCCESSFUL. 155 JVM tests, 0 failures, 0 errors.
New state tests cover sending, thinking/tool activity, first-answer dismissal,
stop, rejected/uncertain requests and avoiding completed-history replay.
APK: `app/build/outputs/apk/debug/xuanji-0.1.25-debug.apk`.
SHA256: `15CA635B6161469A451990255ABCC1B57FC3157A2F19B03FDB460C5127F60D83`.

ADB returned no devices at delivery. No installation or native screenshots
were performed. The extracted storyboard is reference evidence only; actual
pixel appearance, ambient particle strength and refresh-rate performance are
unverified. Full lint was not rerun because the previous full analysis had to
be canceled; no passing lint claim is made for this build.

## Dropdown Grouping and Workspace Scroll Boundaries (0.1.26 / 27)

User report: detached dropdown arrows in the previous screenshot and repeated
jitter at the bottom of workspace/settings. No connected device is available
to reproduce the reported jitter yet.

Material3 1.4.0 source inspection confirms the parent sheet consumes leftover
list gestures and settles after child flings. Its anchor depends on measured
sheet height, while content insets depend on sheet offset. The existing
fractional height is applied inside that inset-dependent content.

- [x] Keep the header model/chevron and composer effort/chevron together;
  remove the redundant approval chevron and preserve 48dp touch targets.
- [x] Apply the workspace height to the outer sheet, disable its drag gestures
  and remove the drag handle. Keep native opening, back/scrim dismissal,
  explicit close, keyboard handling and safe insets.
- [x] Key Cockpit sections so live insertions retain section identity and the
  current lazy-list anchor.
- [x] Build APK 0.1.26 / 27 and run the existing JVM regression suite.
- [ ] On hardware, repeatedly fling/drag into both list boundaries, expand
  sections, switch tabs, open the keyboard and check narrow/large-font layouts.
  Compilation/JVM tests alone cannot prove the reported jitter is resolved.

### Build Evidence (0.1.26 / 27)

`:app:assembleDebug :app:testDebugUnitTest --max-workers=1 --console=plain`
completed with exit code 0, BUILD SUCCESSFUL. 155 JVM tests, 0 failures,
0 errors. APK metadata confirms versionName 0.1.26, versionCode 27.
APK: `app/build/outputs/apk/debug/xuanji-0.1.26-debug.apk`.
SHA256: `17F94286144849EA16201139CFEAC791D2B33C5124CCBA493C87D052483F2031`.

ADB returned no devices. The build was not installed; layout, safe-inset/IME
framing and scroll-boundary behavior still require native verification. The
sheet gesture path was removed based on source evidence, but the user's
original repeated-jitter symptom has not been reproduced on this computer.

## Send Glow Appears in Place (0.1.27 / 28)

User correction: light should emerge at the top after sending, rather than
travel vertically. Remove the band translation, journey state and morphing
bottom mask. Keep a stationary feathered top band, easing its opacity in over
650ms while dimming the stationary bottom atmosphere by the same amount.
Retain cached hue transitions, the 900ms reply/stop fade and foreground/system
animation guards. No position or layout animation is involved.

- [x] Replace band travel with an in-place top/bottom crossfade.
- [x] Build and run the existing regression suite.
- [ ] Inspect the send transition on a connected device.

Build verification: assembleDebug/testDebugUnitTest completed with exit code 0.
155 JVM tests, 0 failures, 0 errors. APK metadata: 0.1.27 / 28.
APK: `app/build/outputs/apk/debug/xuanji-0.1.27-debug.apk`.
SHA256: `459661ACFDB872B4958B4B1BCBC10B88D554B91D56A29EE98A5C8DAAEAE9D2FC`.
ADB still reports no devices. No installation or native visual check performed.
