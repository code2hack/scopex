# scopex Context

scopex is a wearable spatial mirroring project where glasses act as a physical scope over a larger padded logical display.

## Language

**scopex**:
The product and project: an Android-plus-glasses system for interacting with a larger captured Android surface through a physical glasses scope and center aiming mark.
_Avoid_: terminal project, generic mirroring app

**ScopeX**:
The core product metaphor. "Scope" is the physical scope in the glasses, and "X" is the crosshair in the middle of that scope for UI interaction.
_Avoid_: generic visible region

**physical scope**:
The glasses-visible area that frames part of the larger content surface.
_Avoid_: old visible-region names

**logical display**:
The mirrored Android app content coordinate space.
_Avoid_: legacy display-surface names

**padded logical display**:
The logical display plus display padding around it, providing the full coordinate space for physical scope movement and crosshair reachability.
_Avoid_: old whole-display names

**display padding**:
The required margin around the logical display that lets the crosshair reach content edges and corners while staying centered in the physical scope.
_Avoid_: legacy display padding names

**crosshair**:
The fixed center mark inside the physical scope used as the aim point for UI interaction.
_Avoid_: pointer-style names

**crosshair action**:
A user intent anchored to whatever Android content is under the crosshair, such as click, hold, scroll, or zoom.
_Avoid_: observe-only event

**canonical ScopeX action**:
A platform-neutral ScopeX interaction intent, independent of whether it came from glasses controls, an external remote, or another input source. Canonical actions are one-per-semantic-intent, such as click, hold, scroll, zoom, start recording, finish recording, toggle input cache, insert cache line, and delete cache line.
_Avoid_: hardware-specific action

**raw input event**:
A device-specific control event kept only in debug mode for hardware mapping troubleshooting before translation into a canonical ScopeX action.
_Avoid_: normal interaction state

**canonical ScopeX event**:
An intent-named event consumed by the ScopeX interaction state machine, such as `ClickCrosshair`, `HoldCrosshair`, `MoveHeldCrosshair`, `ScrollAtCrosshair`, `ZoomAtCrosshair`, `StartRecording`, `FinishRecording`, `ToggleInputCache`, `InsertHighlightedCacheLine`, `DeleteHighlightedCacheLine`, `MoveCacheHighlight`, `RecenterScope`, or `Escape`.
_Avoid_: physical input name

**Escape**:
A canonical ScopeX event mapped to glasses physical-button double-click in glasses-first controls. During recording, Escape aborts the whole recording session, discarding the current new-line buffer, removing ASR lines committed during that recording session, and restoring the pre-recording input cache panel and highlight state; if the cache was empty before recording, the panel closes. In `LiveScope`, including while edge scrolling, Escape shows "Double click again to quit ScopeX"; a second Escape within 2 seconds confirms and quits ScopeX.
_Avoid_: required remote button

**quit ScopeX**:
A glasses-side exit that hides the ScopeX overlay or leaves the glasses client. Capture teardown and broader session shutdown are decided by the companion app; MVP re-entry is manually opening the glasses app again.
_Avoid_: implicit capture teardown

**RecenterScope**:
A canonical ScopeX event that recenters the physical scope and is mapped to two-finger click on the glasses touch panel in glasses-first controls.
_Avoid_: physical-button recenter

**ScopeX interaction state**:
The pure state machine for recording mode, input cache panel visibility, active source lock, frozen scope, highlighted cache line, system messages, and canonical action transitions. Canonical top-level states are `LiveScope`, `Recording`, and `InputCachePanelOpen`; system messages are overlays on those states, not a top-level state.
_Avoid_: platform-owned interaction state

**ScopeX effect command**:
A platform-neutral instruction emitted by `scopex-core` alongside new interaction state, such as start ASR, inject tap, show prompt, or open a permission screen.
_Avoid_: inferred side effect

**ScopeX result event**:
A platform-neutral success or failure report sent back to `scopex-core` after a platform executes a ScopeX effect command.
_Avoid_: direct platform state mutation

**ASR transcript event**:
A ScopeX result event carrying confirmed and partial transcript updates into `scopex-core` for the new-line buffer.
_Avoid_: UI-owned transcript stream

**ScopeX timer event**:
An explicit time event sent into `scopex-core` for long-press thresholds, long silence timeout, active-source idle timeout, and cache-line scroll delay.
_Avoid_: core system-clock reads

**ScopeX configuration event**:
An event carrying companion setting changes into `scopex-core`; core owns safe defaults and platforms send overrides when settings change. Immediate safe configs include cache limit, active source timeout, edge zone size, and debug/privacy indicators; next-mode configs include long silence mode/timeout on the next recording and cache-line scroll delay on the next highlighted line; next-session configs include virtual display zoom or logical display scale on the next mirroring session.
_Avoid_: platform-only settings lookup

**phone-side action**:
A crosshair action delivered back to the mirrored Android app, such as click, hold, scroll, or phone zoom.
_Avoid_: glasses-side magnification

**glasses-first control**:
The baseline control path where built-in glasses controls prove ScopeX interaction before optional external remotes are added. The glasses touch panel acts as phone touch under the crosshair, while the glasses physical button controls speech recording and the input cache panel.
_Avoid_: remote-only control

**external remote control**:
A later optional input source that can connect through glasses or phone but emits the same canonical ScopeX actions through the same action protocol; only the input source and transport path change.
_Avoid_: remote-specific mode

**active source lock**:
The input arbitration rule where the first active input source owns ScopeX actions until a companion-configurable idle timeout, defaulting to 500 ms, preventing mixed gestures from glasses controls and external remotes.
_Avoid_: hardware priority

**baseline crosshair actions**:
The first complete action family ScopeX must prove through the glasses touch panel: click, hold, hold-plus-head-movement, swipe scroll, and phone zoom.
_Avoid_: observe-only mode

**glasses touch panel**:
The glasses input surface that acts like a finger touching the mirrored Android app under the crosshair.
_Avoid_: scope movement control

**glasses physical button**:
The glasses control used for ScopeX system actions, including speech recording and input cache panel toggling.
_Avoid_: phone touch proxy

**phone zoom**:
A phone-side action that applies Android app zoom under the crosshair. Glasses controls do not change physical-scope magnification.
_Avoid_: glasses-side zoom

**virtual display zoom**:
A companion-app configuration that determines the size or scale of the logical display. It is not controlled from glasses buttons, glasses touch, or Bluetooth remote actions.
_Avoid_: glasses zoom, crosshair zoom

**edge scroll**:
Phone-side fixed-speed scrolling triggered when head movement brings the crosshair into a configurable edge zone near the logical display boundary. In corner zones, the last dominant head-movement axis determines scroll direction. It stops when the crosshair leaves the edge zone or the user recenters.
_Avoid_: scope movement

**text selection hold**:
A hold interaction that enters Android-style long-press selection after the Android long-press threshold, then extends selection only after deliberate head movement exceeds a small threshold. If no selectable text is present, the action does nothing.
_Avoid_: drag-to-pan

**scope freeze**:
A state where the physical scope stops moving over the padded logical display and the crosshair location is fixed. Speech recording and any open input cache panel both freeze the scope until recording is finished and the panel is closed.
_Avoid_: pause capture

**speech recording mode**:
A physical-button mode where physical-button long-press starts ASR recording whether the input cache panel is closed or already open, opens or reuses the input cache panel, focuses a new-line buffer at the bottom of the panel, turns the crosshair into a mic icon, freezes the scope, and disables every interaction except physical-button single-click to finish recording and physical-button double-click to send Escape.
_Avoid_: always-listening ASR

**ASR failure**:
A speech recording failure that ends the recording session, shows an error, does not persist raw audio, and keeps the input cache panel open only when cache entries remain.
_Avoid_: silent retry, raw audio backlog

**ASR permission denial**:
A microphone permission failure shown in the glasses with a route to the companion phone permission screen.
_Avoid_: silent ASR disablement

**input cache**:
An ordered text queue that stores ASR-recognized text and, when enabled in companion settings, copied text from Android clipboard changes for later insertion into the mirrored Android app. It preserves duplicate entries exactly as they arrive, stays memory-only until the app exits or crashes, has no recovery buffer, and has a companion-configurable active limit that defaults to 50 entries; when the limit is exceeded, the oldest head entry is dropped.
_Avoid_: clipboard replacement

**clipboard import**:
An opt-in companion setting that appends Android clipboard text changes to the input cache only while ScopeX is actively mirroring or capturing.
_Avoid_: silent clipboard watching

**input cache privacy indicator**:
The glasses-visible privacy signal for input cache sources: a mic icon appears during ASR recording, and the input cache panel shows a badge when clipboard import is enabled.
_Avoid_: phone-only privacy signal

**recurrent cache navigation**:
Input cache highlight movement wraps through the queue: moving past the tail goes to the head, and after inserting or deleting an item the highlight moves to the next newer item, wrapping to the head when the removed item was the tail.
_Avoid_: one-way list navigation

**new-line buffer**:
The temporary bottom row in the input cache panel that displays current ASR text while recording is active. It is one visual line that shows stable confirmed text plus a visually distinct live partial suffix, shows the latest characters when text is wider than the physical scope, saves non-empty trimmed confirmed text on long silence or `FinishRecording`, and discards unconfirmed partial text and empty buffers.
_Avoid_: draft editor

**long silence**:
The ASR segmentation point that commits the current confirmed new-line buffer to the input cache tail. The companion can configure it as a numeric timeout defaulting to 2.0 seconds or defer it to ASR endpoint detection.
_Avoid_: hardcoded pause

**input cache panel**:
A lower-half physical-scope panel that lets the user choose text from the input cache while the scope and crosshair are frozen. After recording finishes, the new-line buffer disappears, the newly saved ASR tail line is highlighted; if no ASR text was saved, an empty cache closes the panel and otherwise the previous highlight is restored. Touch-panel swipe changes the highlighted item, touch-panel double-click inserts and removes the highlighted item only when the frozen crosshair location has Android editable focus, touch-panel long-press deletes the highlighted item, successful insert keeps the panel open while cache entries remain, an empty cache auto-closes the panel and unfreezes scope, and when recording is inactive physical-button single-click or double-click closes the panel and unfreezes scope.
_Avoid_: full-screen editor

**ScopeX overlay**:
Glasses-visible UI such as the crosshair, mic icon, input cache panel, prompts, and badges that is never part of captured app frames.
_Avoid_: mirrored app content

**debug capture mode**:
An explicit advanced setting, off by default and guarded by a visible warning, that may persist screenshots, overlay state, or input cache text for troubleshooting.
_Avoid_: automatic debug logging

**cache line display**:
The one-line visual representation of an input cache entry. Long highlighted lines start horizontal recurrent scrolling after a short pause inside their single line instead of wrapping; non-highlighted long lines remain truncated with the tail visible.
_Avoid_: multiline cache item

**empty cache prompt**:
The message shown when physical-button single-click tries to open the input cache panel while the input cache has no entries.
_Avoid_: empty panel

**input cache head gesture shortcuts**:
Optional companion-configured raw input mappings where nodding becomes `InsertHighlightedCacheLine` and shaking becomes `DeleteHighlightedCacheLine`.
_Avoid_: baseline cache control
