# Input Cache Panel Design

## Goal

Implement GitHub issue #8 in `scopex-core`: cache-panel highlight navigation,
insert/delete behavior, platform-neutral text insertion effects/results, and
one-line cache display state.

This slice does not implement Android text injection, raw nod/shake detection,
new persistence, or a separate UI renderer.

## Architecture

Keep the behavior in
`scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`.
The existing `InputCachePanelOpen` state already freezes the scope and carries
the active cache, so this slice extends that state instead of adding another
panel model.

Core owns only product semantics. Platform code reports whether the frozen
crosshair target has editable focus, executes `InsertText`, and reports success
or failure. Core removes an inserted line only after success.

## Public Shapes

Add canonical events:

- `MoveCacheHighlight(offset: Int)`
- `InsertHighlightedCacheLine`
- `DeleteHighlightedCacheLine`

Add result events:

- `FrozenCrosshairTargetEditableFocusChanged(hasEditableFocus: Boolean)`
- `TextInsertionSucceeded`
- `TextInsertionFailed`

Add timer event:

- `CacheLineScrollDelay`

Add effect command:

- `InsertText(text: String)`

Add constants:

- `MOVE_CROSSHAIR_TO_INPUT_AREA_MESSAGE = "move crosshair to input area to input"`
- `DEFAULT_CACHE_LINE_VISIBLE_CHARACTERS`

Extend `InputCachePanelOpen` with:

- `frozenCrosshairTargetHasEditableFocus: Boolean = false`
- `pendingInsertedCacheIndex: Int? = null`
- `highlightedLineScrollOffset: Int = 0`

Add a small value for display:

- `ScopeXCacheLineDisplay(text: String, scrollOffset: Int = 0)`

## Data Flow

`MoveCacheHighlight(offset)` only acts in `InputCachePanelOpen`. Positive and
negative offsets move through the cache recurrently. Offset `0`, empty cache,
and non-panel states are no-ops. Any highlight change resets highlighted-line
scroll offset to `0`.

`FrozenCrosshairTargetEditableFocusChanged` updates panel focus state only while
the cache panel is open. The default is false, so insertion is rejected until the
platform reports editable focus.

`InsertHighlightedCacheLine` in a panel with editable focus emits
`InsertText(highlightedText)` and records `pendingInsertedCacheIndex`. It does
not remove cache entries until `TextInsertionSucceeded`.

`InsertHighlightedCacheLine` without editable focus keeps the panel open and
emits `ShowMessage(MOVE_CROSSHAIR_TO_INPUT_AREA_MESSAGE)`.

`TextInsertionSucceeded` removes the pending line. If entries remain, the panel
stays open and highlights the next newer item, wrapping to the head when the
removed item was the tail. If the cache becomes empty, core returns to
`LiveScope` and clears the highlight.

`TextInsertionFailed` clears the pending insert and keeps the panel open without
removing cache entries.

`DeleteHighlightedCacheLine` removes the highlighted line immediately. It uses
the same next-newer highlight rule as insert success and returns to `LiveScope`
when deleting the only line.

`CacheLineScrollDelay` increments highlighted-line scroll offset recurrently
only when the highlighted line is longer than the display width. The pure
display helper returns one-line text: non-highlighted long lines show the tail,
and highlighted long lines show the current scroll window.

Optional nod/shake raw mappings can point at `InsertHighlightedCacheLine` and
`DeleteHighlightedCacheLine` later without adding new canonical events.

## Error Handling And Safety

The reducer remains pure. It does not inspect Android view trees, perform text
injection, read clipboard contents, persist cache entries, or own timer
scheduling.

Invalid highlighted indexes remain rejected by `ScopeXInputCache`. Missing
highlight, empty cache, non-panel insert/delete/move events, and insertion
results without a pending insert are no-ops.

## Testing

Use TDD in `ScopeXReducerTest` against the public reducer interface:

- recurrent highlight movement, including tail-to-head wrapping;
- insert emits `InsertText` only when editable focus is true;
- insert without editable focus emits the input-area prompt;
- insertion success removes the inserted line and highlights the next newer item;
- inserting the only remaining line closes the panel and unfreezes scope;
- deletion removes the highlighted line, wraps highlight correctly, and closes
  on the last line;
- display helper returns tail-visible truncation for non-highlighted long lines;
- highlighted long-line scroll advances after `CacheLineScrollDelay`;
- insertion failure keeps cache entries.

Final verification:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```
