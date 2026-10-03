# Native Product UI Redesign

## Scope

Redesign the existing Compose chat, command palette and workspace navigation.
Keep Tianshu state, permissions, approvals, model configuration and command
execution connected to their current production sources.

## References Viewed

- ScreensDesign Claude screen 15 (01:35): compact navigation and bottom composer.
- ScreensDesign Claude screen 18 (01:51): right-aligned user bubble, unframed
  assistant reply, separate thinking status. `claude-conversation.png`.
- ScreensDesign Claude screen 82 (10:02): grouped settings, restrained row
  hierarchy. `claude-settings.png` was opened and visually inspected.
- Refero Claude Home Page:
  https://refero.design/screens/cadc2a85-bda6-42ac-b726-0678fcf58d48
  The visible screenshot has a compact brand header and anchored composer.
  The upgrade illustration and promotional content are excluded.

Screenshots are in `docs/design/references`. Refero sign-in works, but many
detail pages require Pro. The Linear search returned documentation pages,
not a task dashboard; those pages are not dashboard design evidence.
The shadcn dashboard tab resolved to installation documentation and is not
claimed as a viewed dashboard. No reference is fabricated.

## Visual System

- Neutral white/charcoal surfaces, dark primary commands, emerald live status,
  blue links and tool details, amber warnings. No dominant tinted background.
- 4/8/12/16/24/32 dp spacing, 20 dp conversation margins, 16 dp composer margins.
- System CJK font for reliable Android coverage, zero letter spacing; reply
  body 16/26 sp, section title 16/24, navigation title 17/24, label 12/18.
- Framed tools and message bubbles use at most 8 dp corners. Modal sheets keep
  the established platform shape. No cards inside cards or decorative orbs.
- Brand mark is the existing vector identity simplified for monochrome use.

## Layout And Behavior

Chat uses a shared compact header with real runtime state. The empty view has
a centered brand mark, a short heading and three compact action shortcuts.
Replies remain full-width readable Markdown. User bubbles wrap content rather
than stretching short prompts across the screen. Thinking and tool disclosures
share icon, label, state and chevron alignment, with only the header clickable.

Composer follows the user's supplied Codex screenshot: a white raised input
surface, 20 dp corners (explicit user reference), full-width text above a single
tool row. File (+), real system permissions and slash tools sit on the left;
model, actual reasoning effort and circular send/stop sit on the right. The
permissions label does not imply unconditional full access. Model configuration
and file browsing open their production workspace tabs. The field grows to six
lines and follows keyboard insets. All tool
targets are at least 48 dp and have accessibility descriptions/tooltips.

Command palette has a compact header, tonal search and segmented categories.
Selection still fills the short native command into the composer. Unsupported
official commands remain disabled. Search and real loaded skills are retained.

Workspace uses a near-full-height surface with icon-and-label navigation and
understated separators. Overview metrics use a single divided strip. Settings
retains actual provider state, credential masking and explicit save/test actions.
Model entry from the composer opens the configuration tab directly.

## Verification

Build the debug APK, run the existing unit suite and lint. Inspect production
components using the offline debug activity, clearly distinguished from real
runtime tests. Verify light/dark, narrow width, keyboard, long thinking, tables,
commands and model navigation on ADB when the device is connected. Do not send
paid model prompts or clear app data. Bump versionCode for traceable delivery.
