# Recording Failures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement issue #7 by adding reducer support for recording Escape rollback, ASR failure, and microphone permission denial.

**Architecture:** Extend the existing pure `ScopeXReducer` seam. Reuse `Recording.savedLineCount` and `Recording.preRecordingInputCache` to roll back session-only ASR lines without adding another session abstraction.

**Tech Stack:** Kotlin/JVM 2.2.20, Gradle, `kotlin.test`.

---

## File Structure

- Modify `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
  - Add reducer tests for Escape rollback, ASR failure, and permission denial.
- Modify `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`
  - Add two result events, two effect commands, and minimal recording failure reducers.

### Task 1: Recording Escape Rollback

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write failing Escape tests**

Add tests that assert:

```kotlin
ScopeXEvent.Canonical.Escape(ScopeXInputSource.Glasses)
```

from `Recording` removes `savedLineCount` tail entries, clears the buffer,
restores `preRecordingInputCache.highlightedIndex`, returns to
`InputCachePanelOpen` when pre-recording entries exist, returns to `LiveScope`
when the pre-cache was empty, and emits:

```kotlin
listOf(ScopeXEffectCommand.AbortAsr, ScopeXEffectCommand.HideMicIcon)
```

- [ ] **Step 2: Run red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing `AbortAsr`, or assertion failure because `Escape` is ignored during recording.

- [ ] **Step 3: Implement minimal Escape rollback**

Add `AbortAsr`, route recording `Escape` inside `reduceRecordingCanonical`, drop `savedLineCount` tail entries from `inputCache.entries`, restore highlight from `preRecordingInputCache`, and return panel or live state from existing conversion helpers.

- [ ] **Step 4: Run green**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: reducer tests pass.

### Task 2: ASR Failure And Permission Denial

**Files:**
- Modify: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/ScopeXReducerTest.kt`
- Modify: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`

- [ ] **Step 1: Write failing failure tests**

Add tests for:

```kotlin
ScopeXEvent.Result.AsrFailure
ScopeXEvent.Result.MicrophonePermissionDenied
```

ASR failure returns to `InputCachePanelOpen` with cache entries, returns to
`LiveScope` when cache is empty, emits `HideMicIcon` and `ShowMessage`, and
does not emit persistence-related effects. Permission denial emits
`ShowMessage` and `ShowPermissionRoute`.

- [ ] **Step 2: Run red**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
```

Expected: compile failure for missing result events and `ShowPermissionRoute`.

- [ ] **Step 3: Implement minimal failure reducers**

Add the result events and `ShowPermissionRoute`. Handle ASR failure only from
`Recording`; handle permission denial by returning the current state with the
two visible permission effects.

- [ ] **Step 4: Run green and final verification**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.ScopeXReducerTest --no-problems-report
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```

Expected: both commands succeed.

## Self-Review

- Spec coverage: the tasks cover every issue #7 acceptance criterion.
- Placeholder scan: no deferred implementation steps.
- Type consistency: event and effect names match between tests and implementation plan.
