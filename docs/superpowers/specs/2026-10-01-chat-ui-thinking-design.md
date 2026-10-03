# Chat UI and Thinking Stream Design

## Goal

Improve the native Android chat screen and show model-provided thinking content that is already emitted by Tianshu. Keep the implementation in the Xuanji Android app; do not change Tianshu or its HTTP/SSE contract.

## Evidence and root cause

Tianshu declares `thinking_delta` in `src/server/protocol.ts`. The session manager forwards provider `onThinkingDelta` callbacks as `thinking_delta` events with `{ text }` data, and the session stream persists and replays them. Xuanji currently folds only `text_delta` into `ChatItem.Assistant`, so thinking events are ignored in `XuanjiViewModel.onEvent`.

## Data model and event flow

- Add a dedicated thinking item to the Android chat model; keep thinking separate from assistant answer text.
- Fold `thinking_delta` events using `data.text`, appending to the active thinking item while preserving event order relative to text and tool events.
- A new user event starts a new turn and must not append to thinking content from a previous turn.
- `done` ends the active thinking state. If a response contains no thinking events, render no thinking section.
- Session reset clears thinking items with the rest of the conversation.
- The app displays only content explicitly returned by the configured provider through Tianshu. It does not infer or generate thinking text.

## Chat presentation

- Show thinking inline at its actual position in the message stream, associated with the assistant turn.
- While streaming, display a compact `正在思考` header and update the text as deltas arrive.
- After completion, collapse the thinking body by default; tapping the header toggles the full text.
- Keep answer text and tool cards in their original order. Thinking must never be merged into the final answer bubble.
- For providers that do not return thinking deltas, show no empty or synthetic thinking block.

## UI refinement scope

Refine the existing single-screen Compose chat without introducing navigation or a new feature area:

- Strengthen the top bar hierarchy between product name, service state, permissions, and new session actions.
- Reduce excess padding and visual weight in the cockpit while keeping it collapsible and scannable.
- Give user messages, assistant prose, thinking sections, and tool execution/result cards distinct but consistent surfaces.
- Improve composer alignment, multiline behavior, and send/stop affordance at small phone widths and with the IME open.
- Replace the blank new-conversation area with a concise first-message prompt.
- Use restrained neutral surfaces with distinct action and status colors; preserve both system light and dark themes.
- Keep controls accessible with descriptive content labels and minimum touch targets.

## Error and lifecycle behavior

- Empty thinking deltas are ignored.
- Streaming reconnects continue to rely on the existing sequence cursor; replayed deltas must not be appended twice.
- Phase, todo, and other progress metadata do not split an active thinking segment; user, answer, tool, error, and turn-ending events do.
- If the stream ends or `done` arrives, the current thinking item remains readable and collapsed by default.
- A new session clears conversation-local expansion state and content.

## Verification

- Add focused unit coverage for `thinking_delta` folding, separation from assistant text, ordering, and turn/session boundaries where the existing test setup permits.
- Run Android unit tests and a debug build.
- Provide a manual phone test prompt that causes a nontrivial DeepSeek response, then verify live thinking updates, collapse/expand, answer separation, and normal behavior when no thinking is returned.
- Phone and local DeepSeek harness testing remains user-run; a successful build is not a claim of device verification.

## Out of scope

- Changes to Tianshu source or API behavior.
- A new model settings screen or changes to provider thinking configuration.
- File preview, terminal, MCP, or device-control features from the broader project checklist.
- Presenting an app-generated explanation as if it were provider-returned thinking.
