# Input Cache Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement GitHub issue #8: cache-panel recurrent navigation, insert/delete behavior, insertion results, and one-line cache display state in `scopex-core`.

**Architecture:** Extend the existing pure `ScopeXReducer` seam in `InteractionReducer.kt`. Keep Android focus and text injection platform-neutral through result events and effect commands; remove cache entries only after successful insertion.

**Tech Stack:** Kotlin/JVM, Gradle, `kotlin.test`.

---

## File Structure

- Modify `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
  - Add reducer tests for navigation, insertion, deletion, and display scroll.
  - Extend the local `inputCachePanelOpen()` helper with new panel fields.
- Modify `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`
  - Add new public events/effects/state fields.
  - Add panel reducer helper functions.
  - Add one display helper for cache lines.

No new module, dependency, Android class, renderer, persistence layer, or raw gesture event.

---

### Task 1: Cache Highlight Navigation

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing navigation test**

Add this test near the existing input-cache tests:

```kotlin
@Test
fun moveCacheHighlightWrapsThroughEntriesAndResetsScrollOffset() {
    val state = inputCachePanelOpen(
        inputCache = ScopeXInputCache(
            entries = listOf("old", "middle", "new"),
            highlightedIndex = 2,
        ),
        highlightedLineScrollOffset = 4,
    )

    val wrappedToHead = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.MoveCacheHighlight(
            source = ScopeXInputSource.Glasses,
            offset = 1,
        ),
    )

    assertEquals(
        inputCachePanelOpen(
            inputCache = state.inputCache.copy(highlightedIndex = 0),
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        wrappedToHead.state,
    )
    assertEquals(emptyList(), wrappedToHead.effects)

    val wrappedToTail = ScopeXReducer.reduce(
        state = wrappedToHead.state,
        event = ScopeXEvent.Canonical.MoveCacheHighlight(
            source = ScopeXInputSource.Glasses,
            offset = -1,
        ),
    )

    assertEquals(
        inputCachePanelOpen(
            inputCache = state.inputCache.copy(highlightedIndex = 2),
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        wrappedToTail.state,
    )
    assertEquals(emptyList(), wrappedToTail.effects)
}
```

Update the test helper signature:

```kotlin
private fun inputCachePanelOpen(
    sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    inputCache: ScopeXInputCache = ScopeXInputCache(),
    crosshairContentPoint: FloatPoint = this.crosshairContentPoint,
    lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
    edgeZoneSize: Float = 100f,
    frozenCrosshairTargetHasEditableFocus: Boolean = false,
    pendingInsertedCacheIndex: Int? = null,
    highlightedLineScrollOffset: Int = 0,
) = ScopeXInteractionState.InputCachePanelOpen(
    crosshairContentPoint = crosshairContentPoint,
    logicalDisplaySize = logicalDisplaySize,
    lastDominantMovementAxis = lastDominantMovementAxis,
    edgeZoneSize = edgeZoneSize,
    sourceLock = sourceLock,
    inputCache = inputCache,
    frozenCrosshairTargetHasEditableFocus = frozenCrosshairTargetHasEditableFocus,
    pendingInsertedCacheIndex = pendingInsertedCacheIndex,
    highlightedLineScrollOffset = highlightedLineScrollOffset,
)
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest.moveCacheHighlightWrapsThroughEntriesAndResetsScrollOffset --no-problems-report
```

Expected: compile failure for missing `MoveCacheHighlight` and new `InputCachePanelOpen` fields.

- [ ] **Step 3: Add minimal navigation implementation**

In `InteractionReducer.kt`, extend `InputCachePanelOpen`:

```kotlin
data class InputCachePanelOpen(
    val crosshairContentPoint: FloatPoint,
    val logicalDisplaySize: IntSize,
    val lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
    val edgeZoneSize: Float = DEFAULT_EDGE_ZONE_SIZE,
    override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    override val inputCache: ScopeXInputCache = ScopeXInputCache(),
    val frozenCrosshairTargetHasEditableFocus: Boolean = false,
    val pendingInsertedCacheIndex: Int? = null,
    val highlightedLineScrollOffset: Int = 0,
) : ScopeXInteractionState {
    init {
        require(highlightedLineScrollOffset >= 0) {
            "highlighted cache line scroll offset must be non-negative"
        }
    }
}
```

Add the canonical event:

```kotlin
data class MoveCacheHighlight(
    override val source: ScopeXInputSource,
    val offset: Int,
) : Canonical
```

Route panel events before the LiveScope-only branch in `reduceLiveScopeCanonical`:

```kotlin
if (state is ScopeXInteractionState.InputCachePanelOpen) {
    return reduceInputCachePanelCanonical(state, event)
}
```

Add the reducer helper:

```kotlin
private fun reduceInputCachePanelCanonical(
    state: ScopeXInteractionState.InputCachePanelOpen,
    event: ScopeXEvent.Canonical,
): ScopeXTransition =
    when (event) {
        is ScopeXEvent.Canonical.ToggleInputCache,
        is ScopeXEvent.Canonical.Escape -> ScopeXTransition(
            state.copy(sourceLock = state.sourceLock.acquire(event.source)).toLiveScope(),
        )
        is ScopeXEvent.Canonical.MoveCacheHighlight -> ScopeXTransition(
            state.copy(
                sourceLock = state.sourceLock.acquire(event.source),
                inputCache = state.inputCache.moveHighlight(event.offset),
                highlightedLineScrollOffset = 0,
            ),
        )
        else -> ScopeXTransition(state)
    }
```

Add the cache helper:

```kotlin
private fun ScopeXInputCache.moveHighlight(offset: Int): ScopeXInputCache {
    if (entries.isEmpty() || offset == 0) {
        return this
    }

    val current = highlightedIndex ?: entries.lastIndex
    return copy(highlightedIndex = Math.floorMod(current + offset, entries.size))
}
```

Remove the older special-case panel toggle block from `reduceLiveScopeCanonical`:

```kotlin
if (nextState is ScopeXInteractionState.InputCachePanelOpen &&
    event is ScopeXEvent.Canonical.ToggleInputCache
) {
    return ScopeXTransition(nextState.toLiveScope())
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest.moveCacheHighlightWrapsThroughEntriesAndResetsScrollOffset --no-problems-report
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add cache panel highlight navigation"
```

---

### Task 2: Cache Insert And Delete

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing insertion and deletion tests**

Add these tests near the navigation test:

```kotlin
@Test
fun insertHighlightedCacheLineRequiresEditableFocus() {
    val state = inputCachePanelOpen(
        inputCache = ScopeXInputCache(entries = listOf("old", "new"), highlightedIndex = 1),
    )

    val transition = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.InsertHighlightedCacheLine(ScopeXInputSource.Glasses),
    )

    assertEquals(
        state.copy(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        transition.state,
    )
    assertEquals(
        listOf(ScopeXEffectCommand.ShowMessage(MOVE_CROSSHAIR_TO_INPUT_AREA_MESSAGE)),
        transition.effects,
    )
}

@Test
fun insertHighlightedCacheLineEmitsInsertTextAndSuccessRemovesLine() {
    val state = inputCachePanelOpen(
        inputCache = ScopeXInputCache(
            entries = listOf("old", "selected", "newer"),
            highlightedIndex = 1,
        ),
        frozenCrosshairTargetHasEditableFocus = true,
    )

    val insertion = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.InsertHighlightedCacheLine(ScopeXInputSource.Glasses),
    )

    val pendingState = state.copy(
        sourceLock = ScopeXSourceLock(
            activeSource = ScopeXInputSource.Glasses,
            ownsActions = true,
        ),
        pendingInsertedCacheIndex = 1,
    )
    assertEquals(pendingState, insertion.state)
    assertEquals(listOf(ScopeXEffectCommand.InsertText("selected")), insertion.effects)

    val success = ScopeXReducer.reduce(
        state = insertion.state,
        event = ScopeXEvent.Result.TextInsertionSucceeded,
    )

    assertEquals(
        pendingState.copy(
            inputCache = ScopeXInputCache(
                entries = listOf("old", "newer"),
                highlightedIndex = 1,
            ),
            pendingInsertedCacheIndex = null,
        ),
        success.state,
    )
    assertEquals(emptyList(), success.effects)
}

@Test
fun insertingOnlyRemainingCacheLineClosesPanelAndUnfreezesScope() {
    val state = inputCachePanelOpen(
        inputCache = ScopeXInputCache(entries = listOf("only"), highlightedIndex = 0),
        frozenCrosshairTargetHasEditableFocus = true,
    )

    val insertion = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.InsertHighlightedCacheLine(ScopeXInputSource.Glasses),
    )
    val success = ScopeXReducer.reduce(
        state = insertion.state,
        event = ScopeXEvent.Result.TextInsertionSucceeded,
    )

    assertEquals(
        liveScope(
            inputCache = ScopeXInputCache(),
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        success.state,
    )
    assertEquals(emptyList(), success.effects)
}

@Test
fun deleteHighlightedCacheLineRemovesLineAndWrapsOrClosesPanel() {
    val state = inputCachePanelOpen(
        inputCache = ScopeXInputCache(
            entries = listOf("old", "middle", "new"),
            highlightedIndex = 2,
        ),
        highlightedLineScrollOffset = 3,
    )

    val deletedTail = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.DeleteHighlightedCacheLine(ScopeXInputSource.Glasses),
    )

    assertEquals(
        inputCachePanelOpen(
            inputCache = ScopeXInputCache(entries = listOf("old", "middle"), highlightedIndex = 0),
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        deletedTail.state,
    )
    assertEquals(emptyList(), deletedTail.effects)

    val oneLineState = inputCachePanelOpen(
        sourceLock = ScopeXSourceLock(
            activeSource = ScopeXInputSource.Glasses,
            ownsActions = true,
        ),
        inputCache = ScopeXInputCache(entries = listOf("only"), highlightedIndex = 0),
    )
    val deletedOnly = ScopeXReducer.reduce(
        state = oneLineState,
        event = ScopeXEvent.Canonical.DeleteHighlightedCacheLine(ScopeXInputSource.Glasses),
    )

    assertEquals(
        liveScope(
            inputCache = ScopeXInputCache(),
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        deletedOnly.state,
    )
    assertEquals(emptyList(), deletedOnly.effects)
}

@Test
fun textInsertionFailureKeepsCacheEntriesAndClearsPendingInsert() {
    val state = inputCachePanelOpen(
        inputCache = ScopeXInputCache(entries = listOf("old", "selected"), highlightedIndex = 1),
        pendingInsertedCacheIndex = 1,
    )

    val transition = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Result.TextInsertionFailed,
    )

    assertEquals(state.copy(pendingInsertedCacheIndex = null), transition.state)
    assertEquals(emptyList(), transition.effects)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing insert/delete events, result events, effect command, and prompt constant.

- [ ] **Step 3: Add minimal insert/delete implementation**

Add constants:

```kotlin
const val MOVE_CROSSHAIR_TO_INPUT_AREA_MESSAGE: String =
    "move crosshair to input area to input"
```

Add canonical events:

```kotlin
data class InsertHighlightedCacheLine(
    override val source: ScopeXInputSource,
) : Canonical

data class DeleteHighlightedCacheLine(
    override val source: ScopeXInputSource,
) : Canonical
```

Add result events:

```kotlin
data class FrozenCrosshairTargetEditableFocusChanged(
    val hasEditableFocus: Boolean,
) : Result

data object TextInsertionSucceeded : Result

data object TextInsertionFailed : Result
```

Add effect command:

```kotlin
data class InsertText(
    val text: String,
) : ScopeXEffectCommand
```

Add result branches in `ScopeXReducer.reduce` before `AppendInputCacheEntry`:

```kotlin
event is ScopeXEvent.Result.FrozenCrosshairTargetEditableFocusChanged ->
    when (state) {
        is ScopeXInteractionState.InputCachePanelOpen -> ScopeXTransition(
            state.copy(frozenCrosshairTargetHasEditableFocus = event.hasEditableFocus),
        )
        else -> ScopeXTransition(state)
    }

event == ScopeXEvent.Result.TextInsertionSucceeded ->
    reduceTextInsertionSucceeded(state)

event == ScopeXEvent.Result.TextInsertionFailed ->
    when (state) {
        is ScopeXInteractionState.InputCachePanelOpen ->
            ScopeXTransition(state.copy(pendingInsertedCacheIndex = null))
        else -> ScopeXTransition(state)
    }
```

Extend `reduceInputCachePanelCanonical`:

```kotlin
is ScopeXEvent.Canonical.InsertHighlightedCacheLine ->
    reduceInsertHighlightedCacheLine(state, event.source)
is ScopeXEvent.Canonical.DeleteHighlightedCacheLine ->
    reduceDeleteHighlightedCacheLine(state, event.source)
```

Add helpers:

```kotlin
private fun reduceInsertHighlightedCacheLine(
    state: ScopeXInteractionState.InputCachePanelOpen,
    source: ScopeXInputSource,
): ScopeXTransition {
    val nextState = state.copy(sourceLock = state.sourceLock.acquire(source))
    if (!nextState.frozenCrosshairTargetHasEditableFocus) {
        return ScopeXTransition(
            nextState,
            listOf(ScopeXEffectCommand.ShowMessage(MOVE_CROSSHAIR_TO_INPUT_AREA_MESSAGE)),
        )
    }
    if (nextState.pendingInsertedCacheIndex != null) {
        return ScopeXTransition(nextState)
    }

    val highlightedIndex = nextState.inputCache.highlightedIndex ?: return ScopeXTransition(nextState)
    val highlightedText = nextState.inputCache.entries.getOrNull(highlightedIndex)
        ?: return ScopeXTransition(nextState)

    return ScopeXTransition(
        nextState.copy(pendingInsertedCacheIndex = highlightedIndex),
        listOf(ScopeXEffectCommand.InsertText(highlightedText)),
    )
}

private fun reduceDeleteHighlightedCacheLine(
    state: ScopeXInteractionState.InputCachePanelOpen,
    source: ScopeXInputSource,
): ScopeXTransition {
    val nextState = state.copy(sourceLock = state.sourceLock.acquire(source))
    val highlightedIndex = nextState.inputCache.highlightedIndex ?: return ScopeXTransition(nextState)
    val inputCache = nextState.inputCache.removeEntryAt(highlightedIndex)

    if (inputCache.entries.isEmpty()) {
        return ScopeXTransition(nextState.copy(inputCache = inputCache).toLiveScope())
    }

    return ScopeXTransition(
        nextState.copy(
            inputCache = inputCache,
            pendingInsertedCacheIndex = null,
            highlightedLineScrollOffset = 0,
        ),
    )
}

private fun reduceTextInsertionSucceeded(state: ScopeXInteractionState): ScopeXTransition {
    if (state !is ScopeXInteractionState.InputCachePanelOpen) {
        return ScopeXTransition(state)
    }

    val pendingIndex = state.pendingInsertedCacheIndex ?: return ScopeXTransition(state)
    val inputCache = state.inputCache.removeEntryAt(pendingIndex)
    val nextState = state.copy(
        inputCache = inputCache,
        pendingInsertedCacheIndex = null,
        highlightedLineScrollOffset = 0,
    )

    if (inputCache.entries.isEmpty()) {
        return ScopeXTransition(nextState.toLiveScope())
    }

    return ScopeXTransition(nextState)
}

private fun ScopeXInputCache.removeEntryAt(index: Int): ScopeXInputCache {
    if (index !in entries.indices) {
        return this
    }

    val nextEntries = entries.filterIndexed { entryIndex, _ -> entryIndex != index }
    val nextHighlightedIndex = when {
        nextEntries.isEmpty() -> null
        index in nextEntries.indices -> index
        else -> 0
    }

    return copy(entries = nextEntries, highlightedIndex = nextHighlightedIndex)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add cache panel insertion and deletion"
```

---

### Task 3: Cache Line Display And Scroll Timer

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write failing display and timer tests**

Add these tests:

```kotlin
@Test
fun cacheLineDisplaysTruncateNonHighlightedLongLinesToTail() {
    val state = inputCachePanelOpen(
        inputCache = ScopeXInputCache(
            entries = listOf("0123456789abcdefghijklmnopqrstuvwxyz", "highlighted"),
            highlightedIndex = 1,
        ),
    )

    assertEquals(
        listOf(
            ScopeXCacheLineDisplay(text = "uvwxyz"),
            ScopeXCacheLineDisplay(text = "highlighted"),
        ),
        state.cacheLineDisplays(visibleCharacters = 6),
    )
}

@Test
fun cacheLineScrollTimerAdvancesHighlightedLongLineRecurrently() {
    val longLine = "0123456789abcdefghijklmnopqrstuvwxyz"
    val state = inputCachePanelOpen(
        inputCache = ScopeXInputCache(entries = listOf(longLine), highlightedIndex = 0),
    )

    assertEquals(
        listOf(ScopeXCacheLineDisplay(text = "012345", scrollOffset = 0)),
        state.cacheLineDisplays(visibleCharacters = 6),
    )

    val scrolled = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Timer.CacheLineScrollDelay,
    )

    assertEquals(
        state.copy(highlightedLineScrollOffset = 1),
        scrolled.state,
    )
    assertEquals(
        listOf(ScopeXCacheLineDisplay(text = "123456", scrollOffset = 1)),
        (scrolled.state as ScopeXInteractionState.InputCachePanelOpen)
            .cacheLineDisplays(visibleCharacters = 6),
    )
    assertEquals(emptyList(), scrolled.effects)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing `ScopeXCacheLineDisplay`, `cacheLineDisplays`, and `CacheLineScrollDelay`.

- [ ] **Step 3: Add minimal display and scroll implementation**

Add constant and display value:

```kotlin
const val DEFAULT_CACHE_LINE_VISIBLE_CHARACTERS: Int = 24

data class ScopeXCacheLineDisplay(
    val text: String,
    val scrollOffset: Int = 0,
)
```

Add timer event:

```kotlin
data object CacheLineScrollDelay : Timer
```

Add reducer branch before `ActiveSourceIdleTimeout`:

```kotlin
event == ScopeXEvent.Timer.CacheLineScrollDelay ->
    reduceCacheLineScrollDelay(state)
```

Add helpers:

```kotlin
fun ScopeXInteractionState.InputCachePanelOpen.cacheLineDisplays(
    visibleCharacters: Int = DEFAULT_CACHE_LINE_VISIBLE_CHARACTERS,
): List<ScopeXCacheLineDisplay> {
    require(visibleCharacters > 0) { "visible cache line characters must be positive" }

    return inputCache.entries.mapIndexed { index, entry ->
        val highlighted = index == inputCache.highlightedIndex
        when {
            entry.length <= visibleCharacters -> ScopeXCacheLineDisplay(entry)
            highlighted -> {
                val offset = Math.floorMod(highlightedLineScrollOffset, entry.length)
                ScopeXCacheLineDisplay(
                    text = (entry + entry).substring(offset, offset + visibleCharacters),
                    scrollOffset = offset,
                )
            }
            else -> ScopeXCacheLineDisplay(entry.takeLast(visibleCharacters))
        }
    }
}

private fun reduceCacheLineScrollDelay(state: ScopeXInteractionState): ScopeXTransition {
    if (state !is ScopeXInteractionState.InputCachePanelOpen) {
        return ScopeXTransition(state)
    }

    val highlightedIndex = state.inputCache.highlightedIndex ?: return ScopeXTransition(state)
    val highlightedText = state.inputCache.entries.getOrNull(highlightedIndex)
        ?: return ScopeXTransition(state)
    if (highlightedText.length <= DEFAULT_CACHE_LINE_VISIBLE_CHARACTERS) {
        return ScopeXTransition(state.copy(highlightedLineScrollOffset = 0))
    }

    return ScopeXTransition(
        state.copy(
            highlightedLineScrollOffset = Math.floorMod(
                state.highlightedLineScrollOffset + 1,
                highlightedText.length,
            ),
        ),
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add cache line display scrolling"
```

---

### Task 4: Final Verification

**Files:**
- Verify: full repo build/test command.

- [ ] **Step 1: Run final verification**

Run:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```

Expected: all `scopex-core` tests pass and `:app:assembleDebug` succeeds.

- [ ] **Step 2: Review issue #8 acceptance criteria**

Confirm:

- `MoveCacheHighlight` recurrent navigation works.
- `InsertHighlightedCacheLine` emits `InsertText` only with editable focus.
- Missing editable focus emits `move crosshair to input area to input`.
- Insert success removes the inserted line and keeps/closes the panel correctly.
- Delete removes the highlighted line and keeps/closes the panel correctly.
- Display helper supports one-line tail truncation and highlighted recurrent scroll.
- Optional nod/shake mappings require no new canonical events.
- Final Gradle command passes.

- [ ] **Step 3: Commit verification-only doc state if needed**

If only the plan file is uncommitted at this point:

```bash
git add docs/superpowers/plans/2026-07-04-input-cache-panel.md
git commit -m "docs: add input cache panel plan"
```

If code commits already included the plan file, skip this step.

---

## Self-Review

- Spec coverage: Task 1 covers recurrent navigation. Task 2 covers editable
  focus, insert effects/results, insert success/failure, delete, panel close,
  and optional raw mapping by reusing canonical events. Task 3 covers one-line
  display, tail truncation, and highlighted scroll timer. Task 4 covers final
  verification and acceptance review.
- Placeholder scan: no deferred implementation markers.
- Type consistency: event, effect, state-field, helper, and constant names match
  across tasks.
