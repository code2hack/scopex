# LiveScope Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement issue #4: `LiveScope` crosshair actions, edge scroll, recenter, and quit confirmation through the pure reducer.

**Architecture:** Extend the existing `InteractionReducer.kt` reducer seam. Keep source arbitration in the reducer, keep geometry mapping outside it, and store only the `LiveScope` state needed to decide edge scroll and quit confirmation.

**Tech Stack:** Kotlin/JVM 2.2.20, Gradle, `kotlin.test`.

---

## File Structure

- Modify `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
  - Add public reducer tests for new `LiveScope` actions, edge scroll, recenter, and quit confirmation.
- Modify `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`
  - Add canonical events, movement/result input, timer/configuration input, `LiveScope` fields, edge-scroll direction types, and effect commands.

### Task 1: Touch-Panel Action Effects

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing tests**

Update the `liveScope` test helper to include a logical display size:

```kotlin
private val logicalDisplaySize = IntSize(1000, 800)

private fun liveScope(
    sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
) = ScopeXInteractionState.LiveScope(
    crosshairContentPoint = crosshairContentPoint,
    logicalDisplaySize = logicalDisplaySize,
    sourceLock = sourceLock,
)
```

Add this test:

```kotlin
@Test
fun liveScopeTouchActionsEmitPhoneSideEffectsAtCrosshair() {
    val state = liveScope()
    val scrollDelta = FloatPoint(0f, -120f)
    val actions = listOf(
        ScopeXEvent.Canonical.HoldCrosshair(ScopeXInputSource.Glasses) to
            ScopeXEffectCommand.InjectLongPress(crosshairContentPoint),
        ScopeXEvent.Canonical.MoveHeldCrosshair(ScopeXInputSource.Glasses) to
            ScopeXEffectCommand.InjectHeldMove(crosshairContentPoint),
        ScopeXEvent.Canonical.ScrollAtCrosshair(ScopeXInputSource.Glasses, scrollDelta) to
            ScopeXEffectCommand.InjectScroll(crosshairContentPoint, scrollDelta),
        ScopeXEvent.Canonical.ZoomAtCrosshair(ScopeXInputSource.Glasses, 1.25f) to
            ScopeXEffectCommand.InjectZoom(crosshairContentPoint, 1.25f),
    )

    for ((event, effect) in actions) {
        val transition = ScopeXReducer.reduce(state, event)

        assertEquals(
            state.copy(
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(listOf(effect), transition.effects)
    }
}
```

- [ ] **Step 2: Run the reducer test to verify red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing `logicalDisplaySize`, canonical event classes, and effect command classes.

- [ ] **Step 3: Add the minimal action implementation**

In `InteractionReducer.kt`, add `logicalDisplaySize` to `LiveScope`:

```kotlin
data class LiveScope(
    val crosshairContentPoint: FloatPoint,
    val logicalDisplaySize: IntSize,
    override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
) : ScopeXInteractionState
```

Replace the single click-only canonical event set with these events:

```kotlin
data class ClickCrosshair(
    override val source: ScopeXInputSource,
) : Canonical

data class HoldCrosshair(
    override val source: ScopeXInputSource,
) : Canonical

data class MoveHeldCrosshair(
    override val source: ScopeXInputSource,
) : Canonical

data class ScrollAtCrosshair(
    override val source: ScopeXInputSource,
    val delta: FloatPoint,
) : Canonical

data class ZoomAtCrosshair(
    override val source: ScopeXInputSource,
    val scaleFactor: Float,
) : Canonical
```

Add these effect commands:

```kotlin
data class InjectLongPress(
    val crosshairContentPoint: FloatPoint,
) : ScopeXEffectCommand

data class InjectHeldMove(
    val crosshairContentPoint: FloatPoint,
) : ScopeXEffectCommand

data class InjectScroll(
    val crosshairContentPoint: FloatPoint,
    val delta: FloatPoint,
) : ScopeXEffectCommand

data class InjectZoom(
    val crosshairContentPoint: FloatPoint,
    val scaleFactor: Float,
) : ScopeXEffectCommand
```

Route all canonical events through source arbitration:

```kotlin
event is ScopeXEvent.Canonical -> reduceLiveScopeCanonical(state, event)
```

Use this reducer body:

```kotlin
private fun reduceLiveScopeCanonical(
    state: ScopeXInteractionState,
    event: ScopeXEvent.Canonical,
): ScopeXTransition {
    if (!state.sourceLock.accepts(event.source)) {
        return ScopeXTransition(state)
    }

    val nextState = state.withSourceLock(state.sourceLock.acquire(event.source))
    if (nextState !is ScopeXInteractionState.LiveScope) {
        return ScopeXTransition(state)
    }

    val effect = when (event) {
        is ScopeXEvent.Canonical.ClickCrosshair ->
            ScopeXEffectCommand.InjectClick(nextState.crosshairContentPoint)
        is ScopeXEvent.Canonical.HoldCrosshair ->
            ScopeXEffectCommand.InjectLongPress(nextState.crosshairContentPoint)
        is ScopeXEvent.Canonical.MoveHeldCrosshair ->
            ScopeXEffectCommand.InjectHeldMove(nextState.crosshairContentPoint)
        is ScopeXEvent.Canonical.ScrollAtCrosshair ->
            ScopeXEffectCommand.InjectScroll(nextState.crosshairContentPoint, event.delta)
        is ScopeXEvent.Canonical.ZoomAtCrosshair ->
            ScopeXEffectCommand.InjectZoom(nextState.crosshairContentPoint, event.scaleFactor)
    }

    return ScopeXTransition(nextState, listOf(effect))
}
```

- [ ] **Step 4: Verify green**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: reducer tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add live scope touch actions"
```

### Task 2: Edge Scroll

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing tests**

Extend the helper so tests can set edge-scroll fields:

```kotlin
private fun liveScope(
    sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    crosshairContentPoint: FloatPoint = this.crosshairContentPoint,
    edgeScrollDirection: ScopeXEdgeScrollDirection? = null,
    lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
    edgeZoneSize: Float = 100f,
) = ScopeXInteractionState.LiveScope(
    crosshairContentPoint = crosshairContentPoint,
    logicalDisplaySize = logicalDisplaySize,
    sourceLock = sourceLock,
    edgeScrollDirection = edgeScrollDirection,
    lastDominantMovementAxis = lastDominantMovementAxis,
    edgeZoneSize = edgeZoneSize,
)
```

Add these tests:

```kotlin
@Test
fun edgeScrollStartsWhenCrosshairEntersEdgeZoneAndStopsWhenItLeaves() {
    val state = liveScope()

    val entered = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Result.CrosshairMoved(
            crosshairContentPoint = FloatPoint(50f, 400f),
            dominantMovementAxis = ScopeXMovementAxis.Horizontal,
        ),
    )

    assertEquals(
        liveScope(
            crosshairContentPoint = FloatPoint(50f, 400f),
            edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
        ),
        entered.state,
    )
    assertEquals(
        listOf(ScopeXEffectCommand.StartEdgeScroll(ScopeXEdgeScrollDirection.Left)),
        entered.effects,
    )

    val left = ScopeXReducer.reduce(
        state = entered.state,
        event = ScopeXEvent.Result.CrosshairMoved(
            crosshairContentPoint = FloatPoint(500f, 400f),
            dominantMovementAxis = ScopeXMovementAxis.Horizontal,
        ),
    )

    assertEquals(liveScope(crosshairContentPoint = FloatPoint(500f, 400f)), left.state)
    assertEquals(listOf(ScopeXEffectCommand.StopEdgeScroll), left.effects)
}
```

```kotlin
@Test
fun cornerEdgeScrollUsesLastDominantMovementAxis() {
    val state = liveScope()

    val horizontal = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Result.CrosshairMoved(
            crosshairContentPoint = FloatPoint(50f, 50f),
            dominantMovementAxis = ScopeXMovementAxis.Horizontal,
        ),
    )

    assertEquals(
        liveScope(
            crosshairContentPoint = FloatPoint(50f, 50f),
            edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
        ),
        horizontal.state,
    )
    assertEquals(
        listOf(ScopeXEffectCommand.StartEdgeScroll(ScopeXEdgeScrollDirection.Left)),
        horizontal.effects,
    )

    val vertical = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Result.CrosshairMoved(
            crosshairContentPoint = FloatPoint(50f, 50f),
            dominantMovementAxis = ScopeXMovementAxis.Vertical,
        ),
    )

    assertEquals(
        liveScope(
            crosshairContentPoint = FloatPoint(50f, 50f),
            edgeScrollDirection = ScopeXEdgeScrollDirection.Up,
            lastDominantMovementAxis = ScopeXMovementAxis.Vertical,
        ),
        vertical.state,
    )
    assertEquals(
        listOf(ScopeXEffectCommand.StartEdgeScroll(ScopeXEdgeScrollDirection.Up)),
        vertical.effects,
    )
}
```

```kotlin
@Test
fun configurationEventRejectsNonPositiveEdgeZoneSize() {
    assertFailsWith<IllegalArgumentException> {
        ScopeXEvent.Configuration.SetEdgeZoneSize(0f)
    }
}
```

- [ ] **Step 2: Run the reducer test to verify red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing edge-scroll state fields, types, result event, config event, and effect commands.

- [ ] **Step 3: Add the minimal edge-scroll implementation**

Add constants and types:

```kotlin
const val DEFAULT_EDGE_ZONE_SIZE: Float = 64f

enum class ScopeXMovementAxis {
    Horizontal,
    Vertical,
}

enum class ScopeXEdgeScrollDirection {
    Left,
    Right,
    Up,
    Down,
}
```

Extend `LiveScope`:

```kotlin
data class LiveScope(
    val crosshairContentPoint: FloatPoint,
    val logicalDisplaySize: IntSize,
    val edgeScrollDirection: ScopeXEdgeScrollDirection? = null,
    val lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
    val edgeZoneSize: Float = DEFAULT_EDGE_ZONE_SIZE,
    override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
) : ScopeXInteractionState
```

Add result and configuration inputs:

```kotlin
data class CrosshairMoved(
    val crosshairContentPoint: FloatPoint,
    val dominantMovementAxis: ScopeXMovementAxis,
) : Result
```

```kotlin
data class SetEdgeZoneSize(
    val size: Float,
) : Configuration {
    init {
        require(size > 0f) { "edge zone size must be positive" }
    }
}
```

Add effect commands:

```kotlin
data class StartEdgeScroll(
    val direction: ScopeXEdgeScrollDirection,
) : ScopeXEffectCommand

data object StopEdgeScroll : ScopeXEffectCommand
```

Handle events:

```kotlin
event is ScopeXEvent.Result.CrosshairMoved ->
    reduceCrosshairMoved(state, event)

event is ScopeXEvent.Configuration.SetEdgeZoneSize ->
    when (state) {
        is ScopeXInteractionState.LiveScope ->
            ScopeXTransition(state.copy(edgeZoneSize = event.size))
        else -> ScopeXTransition(state)
    }
```

Add the edge reducer:

```kotlin
private fun reduceCrosshairMoved(
    state: ScopeXInteractionState,
    event: ScopeXEvent.Result.CrosshairMoved,
): ScopeXTransition {
    if (state !is ScopeXInteractionState.LiveScope) {
        return ScopeXTransition(state)
    }

    val direction = edgeScrollDirection(
        point = event.crosshairContentPoint,
        logicalDisplaySize = state.logicalDisplaySize,
        edgeZoneSize = state.edgeZoneSize,
        dominantMovementAxis = event.dominantMovementAxis,
    )
    val nextState = state.copy(
        crosshairContentPoint = event.crosshairContentPoint,
        edgeScrollDirection = direction,
        lastDominantMovementAxis = event.dominantMovementAxis,
    )
    val effects = when {
        state.edgeScrollDirection == direction -> emptyList()
        direction == null -> listOf(ScopeXEffectCommand.StopEdgeScroll)
        else -> listOf(ScopeXEffectCommand.StartEdgeScroll(direction))
    }

    return ScopeXTransition(nextState, effects)
}
```

Add the direction helper:

```kotlin
private fun edgeScrollDirection(
    point: FloatPoint,
    logicalDisplaySize: IntSize,
    edgeZoneSize: Float,
    dominantMovementAxis: ScopeXMovementAxis,
): ScopeXEdgeScrollDirection? {
    val horizontal = when {
        point.x <= edgeZoneSize -> ScopeXEdgeScrollDirection.Left
        point.x >= logicalDisplaySize.width - edgeZoneSize -> ScopeXEdgeScrollDirection.Right
        else -> null
    }
    val vertical = when {
        point.y <= edgeZoneSize -> ScopeXEdgeScrollDirection.Up
        point.y >= logicalDisplaySize.height - edgeZoneSize -> ScopeXEdgeScrollDirection.Down
        else -> null
    }

    return when {
        horizontal != null && vertical != null ->
            if (dominantMovementAxis == ScopeXMovementAxis.Horizontal) horizontal else vertical
        horizontal != null -> horizontal
        else -> vertical
    }
}
```

- [ ] **Step 4: Verify green**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: reducer tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add live scope edge scroll"
```

### Task 3: Recenter And Quit Confirmation

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing tests**

Extend the helper again:

```kotlin
private fun liveScope(
    sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
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
    edgeScrollDirection = edgeScrollDirection,
    lastDominantMovementAxis = lastDominantMovementAxis,
    edgeZoneSize = edgeZoneSize,
    quitConfirmationActive = quitConfirmationActive,
    systemMessage = systemMessage,
)
```

Add these tests:

```kotlin
@Test
fun recenterScopeRecentersCrosshairAndStopsEdgeScroll() {
    val state = liveScope(
        crosshairContentPoint = FloatPoint(25f, 400f),
        edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
    )
    val center = FloatPoint(500f, 400f)

    val transition = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.RecenterScope(ScopeXInputSource.Glasses),
    )

    assertEquals(
        liveScope(
            crosshairContentPoint = center,
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        ),
        transition.state,
    )
    assertEquals(
        listOf(
            ScopeXEffectCommand.RecenterScope(center),
            ScopeXEffectCommand.StopEdgeScroll,
        ),
        transition.effects,
    )
}
```

```kotlin
@Test
fun escapeShowsQuitConfirmationThenSecondEscapeQuits() {
    val state = liveScope()

    val first = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Canonical.Escape(ScopeXInputSource.Glasses),
    )

    val confirmingState = liveScope(
        sourceLock = ScopeXSourceLock(
            activeSource = ScopeXInputSource.Glasses,
            ownsActions = true,
        ),
        quitConfirmationActive = true,
        systemMessage = QUIT_CONFIRMATION_MESSAGE,
    )
    assertEquals(confirmingState, first.state)
    assertEquals(
        listOf(
            ScopeXEffectCommand.ShowMessage(QUIT_CONFIRMATION_MESSAGE),
            ScopeXEffectCommand.StartQuitConfirmationTimer(DEFAULT_QUIT_CONFIRMATION_TIMEOUT_MILLIS),
        ),
        first.effects,
    )

    val second = ScopeXReducer.reduce(
        state = first.state,
        event = ScopeXEvent.Canonical.Escape(ScopeXInputSource.Glasses),
    )

    assertEquals(confirmingState, second.state)
    assertEquals(listOf(ScopeXEffectCommand.QuitScopeX), second.effects)
}
```

```kotlin
@Test
fun quitConfirmationTimeoutClearsConfirmationState() {
    val state = liveScope(
        quitConfirmationActive = true,
        systemMessage = QUIT_CONFIRMATION_MESSAGE,
    )

    val transition = ScopeXReducer.reduce(
        state = state,
        event = ScopeXEvent.Timer.QuitConfirmationTimeout,
    )

    assertEquals(liveScope(), transition.state)
    assertEquals(emptyList(), transition.effects)
}
```

- [ ] **Step 2: Run the reducer test to verify red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing recenter/escape events, quit timer, quit state fields, constants, and effect commands.

- [ ] **Step 3: Add the minimal recenter and quit implementation**

Add constants:

```kotlin
const val DEFAULT_QUIT_CONFIRMATION_TIMEOUT_MILLIS: Long = 2_000L
const val QUIT_CONFIRMATION_MESSAGE: String = "Double click again to quit ScopeX"
```

Extend `LiveScope`:

```kotlin
val quitConfirmationActive: Boolean = false,
val systemMessage: String? = null,
```

Add canonical and timer events:

```kotlin
data class RecenterScope(
    override val source: ScopeXInputSource,
) : Canonical

data class Escape(
    override val source: ScopeXInputSource,
) : Canonical
```

```kotlin
data object QuitConfirmationTimeout : Timer
```

Add effect commands:

```kotlin
data class RecenterScope(
    val crosshairContentPoint: FloatPoint,
) : ScopeXEffectCommand

data class ShowMessage(
    val message: String,
) : ScopeXEffectCommand

data class StartQuitConfirmationTimer(
    val timeoutMillis: Long,
) : ScopeXEffectCommand

data object QuitScopeX : ScopeXEffectCommand
```

Handle the timer before canonical events:

```kotlin
event == ScopeXEvent.Timer.QuitConfirmationTimeout ->
    when (state) {
        is ScopeXInteractionState.LiveScope ->
            ScopeXTransition(
                state.copy(
                    quitConfirmationActive = false,
                    systemMessage = null,
                ),
            )
        else -> ScopeXTransition(state)
    }
```

Add these canonical branches:

```kotlin
is ScopeXEvent.Canonical.RecenterScope -> {
    val center = nextState.centerCrosshairContentPoint()
    val effects = buildList {
        add(ScopeXEffectCommand.RecenterScope(center))
        if (nextState.edgeScrollDirection != null) {
            add(ScopeXEffectCommand.StopEdgeScroll)
        }
    }
    return ScopeXTransition(
        nextState.copy(
            crosshairContentPoint = center,
            edgeScrollDirection = null,
        ),
        effects,
    )
}

is ScopeXEvent.Canonical.Escape -> {
    if (nextState.quitConfirmationActive) {
        return ScopeXTransition(nextState, listOf(ScopeXEffectCommand.QuitScopeX))
    }

    return ScopeXTransition(
        nextState.copy(
            quitConfirmationActive = true,
            systemMessage = QUIT_CONFIRMATION_MESSAGE,
        ),
        listOf(
            ScopeXEffectCommand.ShowMessage(QUIT_CONFIRMATION_MESSAGE),
            ScopeXEffectCommand.StartQuitConfirmationTimer(DEFAULT_QUIT_CONFIRMATION_TIMEOUT_MILLIS),
        ),
    )
}
```

Add the center helper:

```kotlin
private fun ScopeXInteractionState.LiveScope.centerCrosshairContentPoint(): FloatPoint =
    FloatPoint(
        x = logicalDisplaySize.width / 2f,
        y = logicalDisplaySize.height / 2f,
    )
```

- [ ] **Step 4: Verify green**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: reducer tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add live scope recenter and quit"
```

### Task 4: Required Verification

**Files:**
- Verify all touched files.

- [ ] **Step 1: Run required verification**

Run:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```

Expected: build succeeds.

- [ ] **Step 2: Commit verification cleanup if needed**

If formatting or a small compile fix was needed during verification, run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "fix: polish live scope reducer"
```

If no files changed, skip this step.

## Self-Review

- Spec coverage: Task 1 covers normal touch actions and source arbitration reuse. Task 2 covers edge scroll start, stop, corner-axis selection, and edge-zone configuration validation. Task 3 covers recenter, first Escape, second Escape, and quit-confirmation timeout. Task 4 covers the required Gradle command.
- Placeholder scan: no open-ended implementation steps.
- Type consistency: test snippets and implementation snippets use the same event, state, constant, and effect names.
