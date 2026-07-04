# Interaction Reducer Tracer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first pure ScopeX interaction reducer tracer for `LiveScope + ClickCrosshair -> InjectClick`.

**Architecture:** Put the reducer seam in one new `scopex-core` Kotlin file. The public interface is `ScopeXReducer.reduce(state, event): ScopeXTransition`; platform side effects stay as explicit `ScopeXEffectCommand` values returned from core.

**Tech Stack:** Kotlin/JVM 2.2.20, Gradle, `kotlin.test`.

---

## File Structure

- Create `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
  - Tests the public reducer interface only.
- Create `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`
  - Defines public interaction state, event, effect, transition, and reducer types.
- Do not modify `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/Geometry.kt`
  - Reuse existing `FloatPoint`.
- Do not modify `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/ScopeXMapper.kt`
  - Geometry mapping stays separate from interaction reduction.

### Task 1: Reducer Tracer

**Files:**
- Create: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Create: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write the failing reducer test**

Create `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`:

```kotlin
package com.code2hack.scopex.scopex

import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeXReducerTest {
    @Test
    fun liveScopeClickCrosshairEmitsInjectClickAtCrosshair() {
        val state = ScopeXInteractionState.LiveScope(
            crosshairContentPoint = FloatPoint(100f, 200f),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ClickCrosshair,
        )

        assertEquals(state, transition.state)
        assertEquals(
            listOf(ScopeXEffectCommand.InjectClick(FloatPoint(100f, 200f))),
            transition.effects,
        )
    }
}
```

- [ ] **Step 2: Run the reducer test to verify it fails**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: FAIL during test compilation because `ScopeXInteractionState`, `ScopeXReducer`, `ScopeXEvent`, and `ScopeXEffectCommand` do not exist.

- [ ] **Step 3: Add the minimal reducer implementation**

Create `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`:

```kotlin
package com.code2hack.scopex.scopex

sealed interface ScopeXInteractionState {
    data class LiveScope(
        val crosshairContentPoint: FloatPoint,
    ) : ScopeXInteractionState

    data object Recording : ScopeXInteractionState

    data object InputCachePanelOpen : ScopeXInteractionState
}

sealed interface ScopeXEvent {
    sealed interface Canonical : ScopeXEvent {
        data object ClickCrosshair : Canonical
    }

    sealed interface Result : ScopeXEvent

    sealed interface Timer : ScopeXEvent

    sealed interface Configuration : ScopeXEvent
}

sealed interface ScopeXEffectCommand {
    data class InjectClick(
        val crosshairContentPoint: FloatPoint,
    ) : ScopeXEffectCommand
}

data class ScopeXTransition(
    val state: ScopeXInteractionState,
    val effects: List<ScopeXEffectCommand> = emptyList(),
)

object ScopeXReducer {
    fun reduce(
        state: ScopeXInteractionState,
        event: ScopeXEvent,
    ): ScopeXTransition =
        when {
            state is ScopeXInteractionState.LiveScope &&
                event == ScopeXEvent.Canonical.ClickCrosshair ->
                ScopeXTransition(
                    state = state,
                    effects = listOf(
                        ScopeXEffectCommand.InjectClick(state.crosshairContentPoint),
                    ),
                )

            else -> ScopeXTransition(state)
        }
}
```

- [ ] **Step 4: Run the reducer test to verify it passes**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: PASS with `ScopeXReducerTest` green.

- [ ] **Step 5: Run the full required verification**

Run:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```

Expected: PASS. Existing `ScopeXMapperTest` still passes and `app:assembleDebug` succeeds.

- [ ] **Step 6: Commit the implementation**

Run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt
git commit -m "feat: add interaction reducer tracer"
```

Expected: one implementation commit containing only the reducer and reducer test.

## Self-Review

- Spec coverage: Task 1 covers the reducer interface, canonical `ClickCrosshair`, explicit result/timer/configuration event groups, public state, explicit `InjectClick`, public-interface test, geometry test preservation, and final Gradle verification.
- Red-flag scan: no banned marker text, deferred implementation language, or unspecified edge handling.
- Type consistency: test and implementation both use `ScopeXInteractionState`, `ScopeXEvent`, `ScopeXEffectCommand`, `ScopeXTransition`, `ScopeXReducer.reduce`, and `FloatPoint`.
