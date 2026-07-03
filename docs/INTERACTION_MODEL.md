# ScopeX Interaction Model

This document is the current interaction model for ScopeX. `CONTEXT.md` owns the glossary; ADRs own architectural decisions; this document owns workflows, state transitions, events, effects, settings, and failure behavior.

## User Workflows

### Touch Panel Phone Control

In `LiveScope`, the glasses touch panel acts like a finger touching the mirrored Android app under the crosshair.

- Single click sends a phone click at the crosshair.
- Long press sends an Android-style long press at the crosshair.
- Hold plus deliberate head movement extends text selection when selectable text is present.
- Swipe forward/back sends phone-side scroll under the crosshair.
- Phone zoom is a phone-side action under the crosshair, not glasses-side magnification.
- Two-finger click sends `RecenterScope`.

The touch panel does not move the physical scope. The physical scope moves through head/glasses movement over the logical display.

### Edge Scroll

Edge scroll is behavior inside `LiveScope`.

- It starts when head movement brings the crosshair into a configurable edge zone near the logical display content's boundary.
- It sends fixed-speed phone-side scroll.
- In corner zones, the last dominant head-movement axis determines scroll direction.
- It stops when the crosshair leaves the edge zone or `RecenterScope` happens.
- `Escape` does not stop edge scroll; in `LiveScope`, `Escape` starts the quit confirmation flow.

### ASR Recording

Physical-button long-press sends `StartRecording` whether the input cache panel is closed or already open.

Starting recording:

- opens or reuses the input cache panel;
- freezes the physical scope and crosshair;
- changes the crosshair icon to a mic icon;
- focuses a new-line buffer at the bottom of the panel;
- disables every interaction except physical-button single-click and double-click.

During recording:

- physical-button single-click sends `FinishRecording`;
- physical-button double-click sends `Escape`;
- ASR transcript events update the new-line buffer with confirmed text plus a visually distinct live partial suffix;
- long silence commits non-empty trimmed confirmed text to the input cache tail and starts a fresh new-line buffer;
- unconfirmed partial text is never committed.

Finishing recording:

- commits non-empty trimmed confirmed text;
- discards empty buffers and unconfirmed partial text;
- removes the new-line buffer;
- keeps the input cache panel open if cache entries remain;
- highlights the newest ASR tail line if any ASR text was saved in the recording session;
- if no ASR text was saved, closes the panel when cache is empty, otherwise restores the pre-recording highlight.

Escaping recording:

- discards the current new-line buffer;
- removes ASR lines committed during that recording session;
- restores the pre-recording input cache panel and highlight state;
- closes the panel if cache was empty before recording.

### Input Cache

The input cache is an ordered, memory-only text queue. It stores ASR text and, when clipboard import is enabled, Android clipboard text changes while ScopeX is actively mirroring or capturing.

Rules:

- duplicate entries are preserved;
- default active limit is 50 entries;
- when over limit, the oldest head entry is dropped;
- cache is lost when the companion app exits or crashes;
- no recovery buffer exists.

When the input cache panel is open:

- the physical scope and crosshair are frozen;
- the panel occupies the lower half of the physical scope;
- cache entries render as one visual line;
- non-highlighted long lines show tail-visible truncation;
- highlighted long lines auto-scroll horizontally after a short pause;
- touch-panel swipe moves highlight through recurrent cache navigation;
- touch-panel double-click inserts the highlighted line only when the frozen crosshair location has Android editable focus;
- successful insert removes the line from the active cache;
- touch-panel long press deletes the highlighted line;
- insert/delete moves highlight to the next newer item, wrapping to head if needed;
- successful insert keeps the panel open while entries remain;
- empty cache auto-closes the panel and unfreezes scope;
- physical-button single-click or double-click closes the panel and unfreezes scope when not recording.

If physical-button single-click tries to open the panel while cache is empty, ScopeX shows an empty cache prompt instead of opening an empty panel.

Optional companion settings may map nodding to `InsertHighlightedCacheLine` and shaking to `DeleteHighlightedCacheLine`. These are raw input mappings to existing canonical events, not new canonical events.

### Clipboard Import

Clipboard import is opt-in. When enabled, Android clipboard text changes append to input cache only while ScopeX is actively mirroring or capturing. The input cache panel shows a badge while clipboard import is enabled.

### External Remote

External remote control is a later optional input source. A remote may connect through glasses or phone, but it emits the same canonical ScopeX actions through the same action protocol. The active source lock prevents mixed gestures by letting the first active input source own ScopeX actions until the idle timeout.

### Debug Capture

Debug capture mode is an advanced setting, off by default, guarded by a visible warning. It may persist screenshots, overlay state, or input cache text for troubleshooting. ScopeX overlays are never part of captured app frames unless debug capture mode explicitly captures them.

## State Machine

Top-level states:

- `LiveScope`
- `Recording`
- `InputCachePanelOpen`

System messages are overlays on those states, not their own state.

### LiveScope

| Event | Guard | Next state | Effects |
| --- | --- | --- | --- |
| `ClickCrosshair` | active source owns input | `LiveScope` | inject phone click at crosshair |
| `HoldCrosshair` | active source owns input | `LiveScope` | inject Android long press at crosshair |
| `MoveHeldCrosshair` | hold active and movement threshold passed | `LiveScope` | extend phone-side text selection |
| `ScrollAtCrosshair` | active source owns input | `LiveScope` | inject phone scroll at crosshair |
| `ZoomAtCrosshair` | active source owns input | `LiveScope` | inject phone zoom at crosshair |
| crosshair enters edge zone | not already edge scrolling | `LiveScope` | start fixed-speed edge scroll |
| crosshair leaves edge zone | edge scrolling | `LiveScope` | stop edge scroll |
| `RecenterScope` | any | `LiveScope` | recenter physical scope; stop edge scroll |
| `StartRecording` | microphone permission available | `Recording` | open or reuse input cache panel; freeze scope; show mic icon; start ASR |
| `StartRecording` | microphone permission denied | `LiveScope` | show glasses permission message; route to companion permission screen |
| `ToggleInputCache` | input cache has entries | `InputCachePanelOpen` | open panel; freeze scope; highlight tail |
| `ToggleInputCache` | input cache empty | `LiveScope` | show empty cache prompt |
| first `Escape` | no quit confirmation active | `LiveScope` | show "Double click again to quit ScopeX"; start 2-second confirmation timer |
| second `Escape` | quit confirmation active | `LiveScope` | quit ScopeX on glasses side |
| quit confirmation timer expires | quit confirmation active | `LiveScope` | clear quit confirmation message |

### Recording

| Event | Guard | Next state | Effects |
| --- | --- | --- | --- |
| `ASRTranscript` | confirmed or partial text received | `Recording` | update new-line buffer |
| long silence timer or ASR endpoint | confirmed text is non-empty after trim | `Recording` | append confirmed text to input cache tail; start fresh new-line buffer |
| long silence timer or ASR endpoint | buffer empty after trim | `Recording` | discard empty buffer; keep recording |
| `FinishRecording` | recording session saved at least one line | `InputCachePanelOpen` | finish ASR; hide mic icon; remove new-line buffer; highlight newest ASR tail line |
| `FinishRecording` | no line saved and cache has entries | `InputCachePanelOpen` | finish ASR; hide mic icon; remove new-line buffer; restore pre-recording highlight |
| `FinishRecording` | no line saved and cache empty | `LiveScope` | finish ASR; hide mic icon; close panel; unfreeze scope |
| `Escape` | any | pre-recording state | abort ASR; remove lines committed during recording session; discard current buffer; restore pre-recording panel/highlight or close if cache was empty |
| ASR failure | cache has entries | `InputCachePanelOpen` | end ASR; hide mic icon; show error; remove new-line buffer |
| ASR failure | cache empty | `LiveScope` | end ASR; hide mic icon; close panel; unfreeze scope; show error |

### InputCachePanelOpen

| Event | Guard | Next state | Effects |
| --- | --- | --- | --- |
| `MoveCacheHighlight` | cache has entries | `InputCachePanelOpen` | move highlight through recurrent cache navigation |
| `InsertHighlightedCacheLine` | frozen crosshair target has Android editable focus and cache has more than one entry | `InputCachePanelOpen` | insert highlighted text; remove inserted line; highlight next newer item |
| `InsertHighlightedCacheLine` | frozen crosshair target has Android editable focus and cache has one entry | `LiveScope` | insert highlighted text; remove inserted line; close panel; unfreeze scope |
| `InsertHighlightedCacheLine` | frozen crosshair target lacks Android editable focus | `InputCachePanelOpen` | show "move crosshair to input area to input" |
| `DeleteHighlightedCacheLine` | cache has more than one entry | `InputCachePanelOpen` | remove highlighted line; highlight next newer item |
| `DeleteHighlightedCacheLine` | cache has one entry | `LiveScope` | remove highlighted line; close panel; unfreeze scope |
| `ToggleInputCache` | recording inactive | `LiveScope` | close panel; unfreeze scope |
| `Escape` | recording inactive | `LiveScope` | close panel; unfreeze scope |
| `StartRecording` | microphone permission available | `Recording` | reuse panel; save pre-recording highlight; focus new-line buffer; show mic icon; start ASR |
| `StartRecording` | microphone permission denied | `InputCachePanelOpen` | show glasses permission message; route to companion permission screen |

## Canonical Events

Canonical ScopeX events are intent-named and consumed by `scopex-core`:

- `ClickCrosshair`
- `HoldCrosshair`
- `MoveHeldCrosshair`
- `ScrollAtCrosshair`
- `ZoomAtCrosshair`
- `StartRecording`
- `FinishRecording`
- `ToggleInputCache`
- `InsertHighlightedCacheLine`
- `DeleteHighlightedCacheLine`
- `MoveCacheHighlight`
- `RecenterScope`
- `Escape`

Raw device events use physical names and are kept only in debug mode for hardware mapping.

## Effect Commands

`scopex-core` returns effect commands alongside new state. Platform modules execute them and report result events back.

- `InjectClick`
- `InjectLongPress`
- `InjectHeldMove`
- `InjectScroll`
- `InjectZoom`
- `InsertText`
- `StartAsr`
- `FinishAsr`
- `AbortAsr`
- `ShowMessage`
- `ShowPermissionRoute`
- `ShowMicIcon`
- `HideMicIcon`
- `QuitScopeX`

## Result, Timer, And Configuration Events

Result events:

- ASR transcript update with confirmed text and optional live partial suffix.
- ASR failure.
- ASR permission denial.
- Text insertion success or failure.
- Platform effect success or failure.

Timer events:

- Android long-press threshold.
- Long silence timeout.
- Active source idle timeout.
- Cache-line scroll delay.
- Quit confirmation timeout.

Configuration events:

- immediate: cache limit, active source timeout, edge zone size, debug/privacy indicators;
- next-mode: long silence mode/timeout on next recording, cache-line scroll delay on next highlighted line;
- next-session: virtual display zoom or logical display content scale.

## Settings

| Setting | Default | Timing |
| --- | --- | --- |
| input cache active limit | 50 entries | immediate |
| active source idle timeout | 500 ms | immediate |
| long silence timeout | 2.0 s or ASR endpoint mode | next recording |
| clipboard import | off | immediate |
| debug capture mode | off | immediate |
| quit confirmation timeout | 2 s | immediate |
| virtual display zoom / logical display content scale | companion-defined | next mirroring session |

## Privacy And Failure Rules

- No raw audio persistence.
- No input cache crash recovery.
- Input cache survives mirroring stop/start but is cleared on companion app exit or crash.
- Clipboard import is opt-in and active only while ScopeX is actively mirroring or capturing.
- ASR recording shows a mic icon in glasses.
- Clipboard import shows a badge in the input cache panel.
- ASR permission denial is shown in glasses and routes the user to companion phone permissions.
- ASR failure ends recording, shows an error, and keeps the panel open only if cache entries remain.
- ScopeX overlays are never part of captured app frames.
- Debug capture mode is explicit, advanced, off by default, and visibly warned.

## Open Questions For Next Grill

- What physical touch gesture emits `ZoomAtCrosshair`?
- What is the default edge zone size?
- What is the default cache-line scroll delay?
- Does `RecenterScope` do anything while `InputCachePanelOpen` or `Recording` is active, or is it ignored by source mapping?
