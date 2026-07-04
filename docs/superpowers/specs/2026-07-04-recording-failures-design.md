# Recording Failures Design

## Goal

Implement GitHub issue #7 in `scopex-core`: handle recording Escape rollback,
ASR failure, and microphone permission denial in the pure reducer.

## Architecture

Keep the behavior inside
`scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`.
The existing `Recording` state already stores the current buffer, saved session
line count, and pre-recording cache snapshot, so rollback can reuse those
fields instead of adding a second session model.

## Public Shapes

Add result events:

- `AsrFailure`
- `MicrophonePermissionDenied`

Add effect commands:

- `AbortAsr`
- `ShowPermissionRoute`

Reuse `HideMicIcon` and `ShowMessage` for user-visible failure and permission
messages.

## Data Flow

Recording Escape aborts ASR, hides the mic icon, discards the current buffer,
removes only lines committed during the active recording session, and restores
the pre-recording highlight. If the pre-recording cache was empty, the reducer
returns to `LiveScope`; otherwise it returns to `InputCachePanelOpen`.

ASR failure hides the mic icon and shows an error without adding persistence
state or effects. It keeps the panel open when cache entries remain and returns
to `LiveScope` when cache is empty.

Microphone permission denial leaves the current state unchanged, shows a
glasses-visible permission message, and emits a companion permission route
effect.

## Testing

Use TDD in `ScopeXReducerTest` for:

- Escape rollback with committed session lines and pre-existing cache;
- Escape rollback with an empty pre-cache;
- ASR failure with and without cache;
- microphone permission denial.

Final verification:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```
