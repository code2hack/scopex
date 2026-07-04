# Active Source Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement issue #3: canonical ScopeX events acquire an active source lock, reject competing sources, release on explicit idle timer, and accept a configurable idle timeout.

**Architecture:** Keep source arbitration inside the pure `ScopeXReducer`. Canonical events carry product-level `ScopeXInputSource` metadata; reducer state carries `ScopeXSourceLock` with the active source, ownership flag, and idle timeout.

**Tech Stack:** Kotlin/JVM 2.2.20, Gradle, `kotlin.test`.

---

## File Structure

- Modify `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
  - Add public reducer tests for acquisition, competing-source rejection, timer release, timeout configuration, and reacquisition.
- Modify `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`
  - Add `ScopeXInputSource`, `ScopeXSourceLock`, source metadata on `ClickCrosshair`, timer/configuration events, and reducer arbitration.

### Task 1: Source Lock Tests

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`

- [ ] **Step 1: Write failing tests**

Add tests that call `ScopeXReducer.reduce` with canonical `ClickCrosshair(source)` events and assert:

```kotlin
assertEquals(
    ScopeXSourceLock(activeSource = ScopeXInputSource.Glasses, ownsActions = true),
    transition.state.sourceLock,
)
```

```kotlin
assertEquals(lockedState, rejectedTransition.state)
assertEquals(emptyList(), rejectedTransition.effects)
```

```kotlin
assertEquals(lockedState, owningSourceTransition.state)
assertEquals(listOf(ScopeXEffectCommand.InjectClick(crosshairContentPoint)), owningSourceTransition.effects)
```

```kotlin
assertEquals(ScopeXSourceLock(), releasedTransition.state.sourceLock)
```

```kotlin
assertEquals(750L, configuredTransition.state.sourceLock.activeSourceIdleTimeoutMillis)
```

```kotlin
assertFailsWith<IllegalArgumentException> {
    ScopeXEvent.Configuration.SetActiveSourceIdleTimeout(0L)
}
```

- [ ] **Step 2: Verify red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure because source-lock types and event payloads do not exist.

### Task 2: Minimal Reducer Support

**Files:**
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Implement the smallest reducer change**

Add:

```kotlin
enum class ScopeXInputSource {
    Glasses,
    Remote,
    Debug,
}

data class ScopeXSourceLock(
    val activeSource: ScopeXInputSource? = null,
    val ownsActions: Boolean = false,
    val activeSourceIdleTimeoutMillis: Long = DEFAULT_ACTIVE_SOURCE_IDLE_TIMEOUT_MILLIS,
)

const val DEFAULT_ACTIVE_SOURCE_IDLE_TIMEOUT_MILLIS: Long = 500L
```

Change `ClickCrosshair` to `data class ClickCrosshair(val source: ScopeXInputSource)`.

Add:

```kotlin
data object ActiveSourceIdleTimeout : Timer

data class SetActiveSourceIdleTimeout(
    val timeoutMillis: Long,
) : Configuration {
    init {
        require(timeoutMillis > 0) { "active source idle timeout must be positive" }
    }
}
```

Reducer behavior:

- configuration event copies the timeout into state source lock;
- timer event clears active source and ownership while preserving timeout;
- first accepted canonical event sets active source and ownership;
- same-source canonical event stays accepted;
- competing-source canonical event returns current state and no effects.

- [ ] **Step 2: Verify green**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: reducer tests pass.

### Task 3: Required Verification

**Files:**
- Verify all touched files.

- [ ] **Step 1: Run required command**

Run:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```

Expected: build succeeds.

## Self-Review

- Spec coverage: issue #3 acceptance criteria map to tests in Task 1 and reducer behavior in Task 2.
- Placeholder scan: no deferred implementation steps.
- Type consistency: `ScopeXInputSource`, `ScopeXSourceLock`, `ClickCrosshair(source)`, timer event, and configuration event names match across tasks.
