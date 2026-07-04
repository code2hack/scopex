# Input Cache Foundation Design

## Goal

Implement GitHub issue #5 in `scopex-core`: add the memory-only input cache
foundation, panel open/close behavior, scope-freeze state transition, empty
cache prompt, cache limit timing, and clipboard import privacy state.

This slice does not implement cache insertion, deletion, panel navigation, ASR
recording, persistence, crash recovery, or Android clipboard access.

## Architecture

Keep one public module: `ScopeXReducer`. Extend the existing reducer seam in
`scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`
instead of adding a second cache state machine.

Add a small `ScopeXInputCache` value carried by all top-level interaction
states. That keeps queue contents, highlight, active limit, clipboard opt-in,
and mirroring/capture session activity inside core so platform adapters do not
own product semantics.

`InputCachePanelOpen` remains the top-level state for frozen-scope panel mode.
Opening the panel freezes the physical scope by transitioning out of
`LiveScope`; closing it returns to `LiveScope` when recording is inactive.

## Public Shapes

Add `ScopeXInputCache` with:

- `entries: List<String>`
- `activeLimit: Int = 50`
- `highlightedIndex: Int? = null`
- `clipboardImportOptedIn: Boolean = false`
- `sessionActive: Boolean = false`

Derived clipboard import activity is `clipboardImportOptedIn && sessionActive`.

Add canonical event:

- `ToggleInputCache`

Add result event:

- `AppendInputCacheEntry(text)`

Add configuration events:

- `SetInputCacheActiveLimit(limit)`
- `SetClipboardImportOptedIn(enabled)`
- `SetScopeXSessionActive(active)`

Add effect commands:

- `ShowEmptyInputCachePrompt`
- `ShowClipboardImportBadge`
- `HideClipboardImportBadge`

## Data Flow

1. Platform adapters translate the physical-button single-click into
   `ToggleInputCache`.
2. In `LiveScope`, `ToggleInputCache` opens `InputCachePanelOpen` only when the
   cache has entries. The highlighted index becomes the tail entry.
3. In `LiveScope`, `ToggleInputCache` with an empty cache leaves state in
   `LiveScope` and emits `ShowEmptyInputCachePrompt`.
4. In `InputCachePanelOpen`, `ToggleInputCache` closes the panel back to
   `LiveScope` because recording is not part of this slice.
5. `AppendInputCacheEntry(text)` appends text to the tail, preserves
   duplicates, and drops oldest head entries until size is at most
   `activeLimit`.
6. `SetInputCacheActiveLimit(limit)` applies immediately, rejects non-positive
   values, and evicts oldest entries if the new limit is smaller.
7. `SetClipboardImportOptedIn(enabled)` and `SetScopeXSessionActive(active)`
   update privacy state immediately.
8. The reducer emits clipboard badge show/hide effects only when derived active
   clipboard import changes.

## Error Handling And Safety

The reducer remains pure. It does not read Android clipboard contents, persist
cache entries, recover after crashes, inspect Android capture state, or touch
platform APIs.

Invalid cache limits fail at event construction. Unhandled state/event pairs
return the current state with no effects. Empty cache panel opening is explicit:
core stays in `LiveScope` and emits the empty cache prompt instead of creating
an empty panel.

Clipboard import stays opt-in and active only while mirroring or capturing is
reported active by configuration. Core exposes privacy state and badge effects;
platforms still perform the actual clipboard subscription.

## Testing

Use TDD in `ScopeXReducerTest` against the public reducer interface:

- ordered entries preserve duplicates;
- exceeding the default 50-entry active limit drops oldest head entries;
- reducing the active limit evicts oldest entries immediately;
- non-positive cache limits are rejected;
- `ToggleInputCache` opens the panel, freezes scope, and highlights the tail;
- toggling an empty cache leaves `LiveScope` and emits the empty prompt;
- closing the panel returns to `LiveScope`;
- clipboard import is active only when opted in and session active;
- clipboard badge effects emit only when derived active state changes.

Final verification:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```
