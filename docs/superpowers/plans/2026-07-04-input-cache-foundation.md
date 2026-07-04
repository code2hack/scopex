# Input Cache Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement issue #5 by adding memory-only input cache queue rules, panel toggle state, and clipboard privacy state to the pure `scopex-core` reducer.

**Architecture:** Keep one public module, `ScopeXReducer`, and extend its existing state/event/effect shapes. Store input cache and clipboard privacy state in `ScopeXInteractionState` so platform adapters translate events and execute effects without owning product rules.

**Tech Stack:** Kotlin/JVM 2.2.20, Gradle, `kotlin.test`.

---

## File Structure

- Modify `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`
  - Add `ScopeXInputCache`, cache result/configuration events, `ToggleInputCache`, and three effect commands.
  - Keep all behavior behind `ScopeXReducer.reduce(state, event)`.
- Modify `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
  - Add public reducer tests for queue ordering, limits, panel toggle, and clipboard privacy.
  - Update local test helpers to pass input cache state.

### Task 1: Input Cache Queue And Default Limit

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing queue tests**

Add these tests before the existing `quitConfirmationTimeoutClearsConfirmationState` test:

```kotlin
@Test
fun inputCacheAppendsOrderedEntriesAndPreservesDuplicates() {
    val state = liveScope()

    val first = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Result.AppendInputCacheEntry("same"),
    ).state
    val second = ScopeXReducer.reduce(
        state = first,
        event = ScopeXEvent.Result.AppendInputCacheEntry("same"),
    ).state

    assertEquals(
        ScopeXInputCache(entries = listOf("same", "same")),
        second.inputCache,
    )
}

@Test
fun inputCacheDefaultLimitDropsOldestHeadEntries() {
    val finalState = (1..51).fold(liveScope() as ScopeXInteractionState) { state, index ->
        ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.AppendInputCacheEntry("line-$index"),
        ).state
    }

    assertEquals(50, finalState.inputCache.entries.size)
    assertEquals("line-2", finalState.inputCache.entries.first())
    assertEquals("line-51", finalState.inputCache.entries.last())
    assertEquals(DEFAULT_INPUT_CACHE_ACTIVE_LIMIT, finalState.inputCache.activeLimit)
}
```

- [ ] **Step 2: Run the reducer test to verify red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing `ScopeXInputCache`, `inputCache`, `AppendInputCacheEntry`, and `DEFAULT_INPUT_CACHE_ACTIVE_LIMIT`.

- [ ] **Step 3: Add the minimal cache state and append behavior**

In `InteractionReducer.kt`, add this constant after `DEFAULT_EDGE_ZONE_SIZE`:

```kotlin
const val DEFAULT_INPUT_CACHE_ACTIVE_LIMIT: Int = 50
```

Add this data class after `ScopeXSourceLock`:

```kotlin
data class ScopeXInputCache(
    val entries: List<String> = emptyList(),
    val activeLimit: Int = DEFAULT_INPUT_CACHE_ACTIVE_LIMIT,
    val highlightedIndex: Int? = null,
    val clipboardImportOptedIn: Boolean = false,
    val sessionActive: Boolean = false,
) {
    init {
        require(activeLimit > 0) { "input cache active limit must be positive" }
        require(highlightedIndex == null || highlightedIndex in entries.indices) {
            "highlighted input cache index must reference an entry"
        }
    }

    val clipboardImportActive: Boolean
        get() = clipboardImportOptedIn && sessionActive
}
```

Update the `ScopeXInteractionState` interface and state classes:

```kotlin
sealed interface ScopeXInteractionState {
    val sourceLock: ScopeXSourceLock
    val inputCache: ScopeXInputCache

    data class LiveScope(
        val crosshairContentPoint: FloatPoint,
        val logicalDisplaySize: IntSize,
        val edgeScrollDirection: ScopeXEdgeScrollDirection? = null,
        val lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
        val edgeZoneSize: Float = DEFAULT_EDGE_ZONE_SIZE,
        val quitConfirmationActive: Boolean = false,
        val systemMessage: String? = null,
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
        override val inputCache: ScopeXInputCache = ScopeXInputCache(),
    ) : ScopeXInteractionState

    data class Recording(
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
        override val inputCache: ScopeXInputCache = ScopeXInputCache(),
    ) : ScopeXInteractionState

    data class InputCachePanelOpen(
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
        override val inputCache: ScopeXInputCache = ScopeXInputCache(),
    ) : ScopeXInteractionState
}
```

Add this result event next to `CrosshairMoved`:

```kotlin
data class AppendInputCacheEntry(
    val text: String,
) : Result
```

Add this branch in `ScopeXReducer.reduce` before `event is ScopeXEvent.Result.CrosshairMoved`:

```kotlin
event is ScopeXEvent.Result.AppendInputCacheEntry ->
    ScopeXTransition(state.withInputCache(state.inputCache.appendEntry(event.text)))
```

Update `withSourceLock` and add cache helpers near it:

```kotlin
private fun ScopeXInteractionState.withSourceLock(
    sourceLock: ScopeXSourceLock,
): ScopeXInteractionState =
    when (this) {
        is ScopeXInteractionState.LiveScope -> copy(sourceLock = sourceLock)
        is ScopeXInteractionState.Recording -> copy(sourceLock = sourceLock)
        is ScopeXInteractionState.InputCachePanelOpen -> copy(sourceLock = sourceLock)
    }

private fun ScopeXInteractionState.withInputCache(
    inputCache: ScopeXInputCache,
): ScopeXInteractionState =
    when (this) {
        is ScopeXInteractionState.LiveScope -> copy(inputCache = inputCache)
        is ScopeXInteractionState.Recording -> copy(inputCache = inputCache)
        is ScopeXInteractionState.InputCachePanelOpen -> copy(inputCache = inputCache)
    }

private fun ScopeXInputCache.appendEntry(text: String): ScopeXInputCache {
    val nextEntries = entries + text
    val droppedCount = (nextEntries.size - activeLimit).coerceAtLeast(0)
    val boundedEntries = nextEntries.drop(droppedCount)
    val nextHighlightedIndex = highlightedIndex
        ?.minus(droppedCount)
        ?.takeIf { it in boundedEntries.indices }

    return copy(entries = boundedEntries, highlightedIndex = nextHighlightedIndex)
}
```

- [ ] **Step 4: Update the test helper to expose input cache**

Replace the `liveScope` helper with:

```kotlin
private fun liveScope(
    sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    inputCache: ScopeXInputCache = ScopeXInputCache(),
    crosshairContentPoint: FloatPoint = this.crosshairContentPoint,
    edgeScrollDirection: ScopeXEdgeScrollDirection? = null,
    lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
    edgeZoneSize: Float = 100f,
    quitConfirmationActive: Boolean = false,
    systemMessage: String? = null,
) = ScopeXInteractionState.LiveScope(
    crosshairContentPoint = crosshairContentPoint,
    logicalDisplaySize = logicalDisplaySize,
    sourceLock = sourceLock,
    inputCache = inputCache,
    edgeScrollDirection = edgeScrollDirection,
    lastDominantMovementAxis = lastDominantMovementAxis,
    edgeZoneSize = edgeZoneSize,
    quitConfirmationActive = quitConfirmationActive,
    systemMessage = systemMessage,
)
```

- [ ] **Step 5: Run the reducer tests to verify green**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: reducer tests pass.

- [ ] **Step 6: Commit**

Run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add input cache queue"
```

### Task 2: Input Cache Panel Toggle

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing panel tests**

Add these tests after the queue tests from Task 1:

```kotlin
@Test
fun toggleInputCacheOpensPanelFreezesScopeAndHighlightsTail() {
    val inputCache = ScopeXInputCache(entries = listOf("first", "second"))
    val state = liveScope(inputCache = inputCache)

    val transition = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.ToggleInputCache(ScopeXInputSource.Glasses),
    )

    assertEquals(
        inputCachePanelOpen(
            inputCache = inputCache.copy(highlightedIndex = 1),
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        transition.state,
    )
    assertEquals(emptyList(), transition.effects)
}

@Test
fun toggleInputCacheOnEmptyCacheKeepsLiveScopeAndShowsPrompt() {
    val state = liveScope()

    val transition = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.ToggleInputCache(ScopeXInputSource.Glasses),
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
    assertEquals(listOf(ScopeXEffectCommand.ShowEmptyInputCachePrompt), transition.effects)
}

@Test
fun toggleInputCacheClosesPanelAndUnfreezesScope() {
    val inputCache = ScopeXInputCache(
        entries = listOf("first", "second"),
        highlightedIndex = 1,
    )
    val state = inputCachePanelOpen(inputCache = inputCache)

    val transition = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.ToggleInputCache(ScopeXInputSource.Glasses),
    )

    assertEquals(
        liveScope(
            inputCache = inputCache.copy(highlightedIndex = null),
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        transition.state,
    )
    assertEquals(emptyList(), transition.effects)
}
```

Add this helper below `liveScope`:

```kotlin
private fun inputCachePanelOpen(
    sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    inputCache: ScopeXInputCache = ScopeXInputCache(),
    crosshairContentPoint: FloatPoint = this.crosshairContentPoint,
    lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
    edgeZoneSize: Float = 100f,
) = ScopeXInteractionState.InputCachePanelOpen(
    crosshairContentPoint = crosshairContentPoint,
    logicalDisplaySize = logicalDisplaySize,
    lastDominantMovementAxis = lastDominantMovementAxis,
    edgeZoneSize = edgeZoneSize,
    sourceLock = sourceLock,
    inputCache = inputCache,
)
```

Update `canonicalTouchActionInNonLiveScopeStateIsNoOp` so the panel state uses the helper:

```kotlin
val states = listOf(
    ScopeXInteractionState.Recording(),
    inputCachePanelOpen(),
)
```

- [ ] **Step 2: Run the reducer test to verify red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing `ToggleInputCache`, `ShowEmptyInputCachePrompt`, and `InputCachePanelOpen` fields.

- [ ] **Step 3: Add the panel state shape**

Replace `InputCachePanelOpen` in `InteractionReducer.kt` with:

```kotlin
data class InputCachePanelOpen(
    val crosshairContentPoint: FloatPoint,
    val logicalDisplaySize: IntSize,
    val lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
    val edgeZoneSize: Float = DEFAULT_EDGE_ZONE_SIZE,
    override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    override val inputCache: ScopeXInputCache = ScopeXInputCache(),
) : ScopeXInteractionState
```

- [ ] **Step 4: Add the event, effect, and toggle reducer**

Add this canonical event after `ZoomAtCrosshair`:

```kotlin
data class ToggleInputCache(
    override val source: ScopeXInputSource,
) : Canonical
```

Add this effect command after `ShowMessage`:

```kotlin
data object ShowEmptyInputCachePrompt : ScopeXEffectCommand
```

In `reduceLiveScopeCanonical`, after `val nextState = state.withSourceLock(...)`, add:

```kotlin
if (nextState is ScopeXInteractionState.InputCachePanelOpen &&
    event is ScopeXEvent.Canonical.ToggleInputCache
) {
    return ScopeXTransition(nextState.toLiveScope())
}
```

Add this branch inside the canonical `when` before `RecenterScope`:

```kotlin
is ScopeXEvent.Canonical.ToggleInputCache -> {
    if (nextState.inputCache.entries.isEmpty()) {
        return ScopeXTransition(
            nextState,
            listOf(ScopeXEffectCommand.ShowEmptyInputCachePrompt),
        )
    }

    return ScopeXTransition(nextState.toInputCachePanelOpen())
}
```

Add these helpers near `centerCrosshairContentPoint`:

```kotlin
private fun ScopeXInteractionState.LiveScope.toInputCachePanelOpen(): ScopeXInteractionState.InputCachePanelOpen =
    ScopeXInteractionState.InputCachePanelOpen(
        crosshairContentPoint = crosshairContentPoint,
        logicalDisplaySize = logicalDisplaySize,
        lastDominantMovementAxis = lastDominantMovementAxis,
        edgeZoneSize = edgeZoneSize,
        sourceLock = sourceLock,
        inputCache = inputCache.highlightTail(),
    )

private fun ScopeXInteractionState.InputCachePanelOpen.toLiveScope(): ScopeXInteractionState.LiveScope =
    ScopeXInteractionState.LiveScope(
        crosshairContentPoint = crosshairContentPoint,
        logicalDisplaySize = logicalDisplaySize,
        lastDominantMovementAxis = lastDominantMovementAxis,
        edgeZoneSize = edgeZoneSize,
        sourceLock = sourceLock,
        inputCache = inputCache.copy(highlightedIndex = null),
    )

private fun ScopeXInputCache.highlightTail(): ScopeXInputCache =
    copy(highlightedIndex = entries.lastIndex)
```

- [ ] **Step 5: Run the reducer tests to verify green**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: reducer tests pass.

- [ ] **Step 6: Commit**

Run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add input cache panel toggle"
```

### Task 3: Cache Limit Configuration And Clipboard Privacy

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing configuration tests**

Add these tests after the panel tests:

```kotlin
@Test
fun configurationEventOverridesInputCacheActiveLimitAndEvictsHead() {
    val state = liveScope(
        inputCache = ScopeXInputCache(
            entries = listOf("first", "second", "third"),
            activeLimit = 3,
            highlightedIndex = 2,
        ),
    )

    val transition = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Configuration.SetInputCacheActiveLimit(2),
    )

    assertEquals(
        state.copy(
            inputCache = ScopeXInputCache(
                entries = listOf("second", "third"),
                activeLimit = 2,
                highlightedIndex = 1,
            ),
        ),
        transition.state,
    )
    assertEquals(emptyList(), transition.effects)
}

@Test
fun configurationEventRejectsNonPositiveInputCacheActiveLimit() {
    assertFailsWith<IllegalArgumentException> {
        ScopeXEvent.Configuration.SetInputCacheActiveLimit(0)
    }
}

@Test
fun clipboardImportIsActiveOnlyWhenOptedInAndSessionActive() {
    val optedIn = ScopeXReducer.reduce(
        state = liveScope(),
        event = ScopeXEvent.Configuration.SetClipboardImportOptedIn(true),
    )

    assertEquals(false, optedIn.state.inputCache.clipboardImportActive)
    assertEquals(emptyList(), optedIn.effects)

    val active = ScopeXReducer.reduce(
        state = optedIn.state,
        event = ScopeXEvent.Configuration.SetScopeXSessionActive(true),
    )

    assertEquals(true, active.state.inputCache.clipboardImportActive)
    assertEquals(listOf(ScopeXEffectCommand.ShowClipboardImportBadge), active.effects)

    val inactive = ScopeXReducer.reduce(
        state = active.state,
        event = ScopeXEvent.Configuration.SetScopeXSessionActive(false),
    )

    assertEquals(false, inactive.state.inputCache.clipboardImportActive)
    assertEquals(listOf(ScopeXEffectCommand.HideClipboardImportBadge), inactive.effects)
}

@Test
fun clipboardImportOptOutHidesBadgeOnlyWhenItWasActive() {
    val inactiveOptOut = ScopeXReducer.reduce(
        state = liveScope(),
        event = ScopeXEvent.Configuration.SetClipboardImportOptedIn(false),
    )

    assertEquals(false, inactiveOptOut.state.inputCache.clipboardImportActive)
    assertEquals(emptyList(), inactiveOptOut.effects)

    val activeState = liveScope(
        inputCache = ScopeXInputCache(
            clipboardImportOptedIn = true,
            sessionActive = true,
        ),
    )
    val activeOptOut = ScopeXReducer.reduce(
        state = activeState,
        event = ScopeXEvent.Configuration.SetClipboardImportOptedIn(false),
    )

    assertEquals(false, activeOptOut.state.inputCache.clipboardImportActive)
    assertEquals(listOf(ScopeXEffectCommand.HideClipboardImportBadge), activeOptOut.effects)
}
```

- [ ] **Step 2: Run the reducer test to verify red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing configuration events and clipboard badge effects.

- [ ] **Step 3: Add configuration events and effects**

Add these configuration events after `SetEdgeZoneSize`:

```kotlin
data class SetInputCacheActiveLimit(
    val limit: Int,
) : Configuration {
    init {
        require(limit > 0) { "input cache active limit must be positive" }
    }
}

data class SetClipboardImportOptedIn(
    val enabled: Boolean,
) : Configuration

data class SetScopeXSessionActive(
    val active: Boolean,
) : Configuration
```

Add these effect commands after `ShowEmptyInputCachePrompt`:

```kotlin
data object ShowClipboardImportBadge : ScopeXEffectCommand

data object HideClipboardImportBadge : ScopeXEffectCommand
```

- [ ] **Step 4: Add the reducer branches**

In `ScopeXReducer.reduce`, add these branches before `SetActiveSourceIdleTimeout`:

```kotlin
event is ScopeXEvent.Configuration.SetInputCacheActiveLimit ->
    ScopeXTransition(state.withInputCache(state.inputCache.withActiveLimit(event.limit)))

event is ScopeXEvent.Configuration.SetClipboardImportOptedIn ->
    reduceClipboardImportConfiguration(state) {
        it.copy(clipboardImportOptedIn = event.enabled)
    }

event is ScopeXEvent.Configuration.SetScopeXSessionActive ->
    reduceClipboardImportConfiguration(state) {
        it.copy(sessionActive = event.active)
    }
```

Add these helpers below `appendEntry`:

```kotlin
private fun ScopeXInputCache.withActiveLimit(activeLimit: Int): ScopeXInputCache {
    val droppedCount = (entries.size - activeLimit).coerceAtLeast(0)
    val boundedEntries = entries.drop(droppedCount)
    val nextHighlightedIndex = highlightedIndex
        ?.minus(droppedCount)
        ?.takeIf { it in boundedEntries.indices }

    return copy(
        entries = boundedEntries,
        activeLimit = activeLimit,
        highlightedIndex = nextHighlightedIndex,
    )
}

private fun reduceClipboardImportConfiguration(
    state: ScopeXInteractionState,
    configure: (ScopeXInputCache) -> ScopeXInputCache,
): ScopeXTransition {
    val wasActive = state.inputCache.clipboardImportActive
    val nextState = state.withInputCache(configure(state.inputCache))
    val isActive = nextState.inputCache.clipboardImportActive
    val effects = when {
        !wasActive && isActive -> listOf(ScopeXEffectCommand.ShowClipboardImportBadge)
        wasActive && !isActive -> listOf(ScopeXEffectCommand.HideClipboardImportBadge)
        else -> emptyList()
    }

    return ScopeXTransition(nextState, effects)
}
```

- [ ] **Step 5: Run the reducer tests to verify green**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: reducer tests pass.

- [ ] **Step 6: Commit**

Run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add input cache privacy config"
```

### Task 4: Final Verification

**Files:**
- Verify: repository root

- [ ] **Step 1: Run full verification**

Run:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```

Expected: all `scopex-core` tests pass and `:app:assembleDebug` succeeds.

- [ ] **Step 2: Inspect the final diff**

Run:

```bash
git status --short
git log --oneline -5
```

Expected: clean worktree after the three feature commits, with recent commits:

```text
feat: add input cache privacy config
feat: add input cache panel toggle
feat: add input cache queue
```
