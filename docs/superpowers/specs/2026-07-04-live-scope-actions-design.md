# LiveScope Actions Design

## Goal

Implement GitHub issue #4 in `scopex-core`: make the pure reducer handle the
`LiveScope` path for phone-side crosshair actions, edge scroll, recentering, and
two-step quit confirmation.

## Architecture

Keep the change inside the existing reducer seam in
`scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/InteractionReducer.kt`.
The reducer already owns canonical event handling, source arbitration, state
updates, and effect commands; this slice extends those public shapes instead of
adding a new module.

`ScopeXInteractionState.LiveScope` will keep the current crosshair content
point, source lock, optional active edge-scroll direction, last dominant
head-movement axis, and whether quit confirmation is active. System messages
remain overlay state on `LiveScope`, not a new top-level state.

## Public Shapes

Add canonical events for:

- `HoldCrosshair`
- `MoveHeldCrosshair`
- `ScrollAtCrosshair`
- `ZoomAtCrosshair`
- `RecenterScope`
- `Escape`

Add a result-style reducer input for crosshair movement that carries the new
crosshair content point plus the dominant movement axis. The reducer uses this
to start, change, or stop edge scroll.

Add timer event `QuitConfirmationTimeout`.

Add configuration event `SetEdgeZoneSize(size)` with a positive default edge
zone size. Keep the existing active-source timeout configuration unchanged.

Add effect commands for:

- `InjectLongPress`
- `InjectHeldMove`
- `InjectScroll`
- `InjectZoom`
- `StartEdgeScroll`
- `StopEdgeScroll`
- `RecenterScope`
- `ShowMessage`
- `StartQuitConfirmationTimer`
- `QuitScopeX`

## Data Flow

1. Platform adapters translate glasses controls into canonical events with
   source metadata.
2. `ScopeXReducer.reduce(state, event)` rejects events from competing active
   sources exactly as the current click path does.
3. Accepted touch-panel actions in `LiveScope` emit explicit phone-side effect
   commands at `LiveScope.crosshairContentPoint`.
4. Crosshair movement events update the `LiveScope` crosshair point, compare it
   with the configured edge zone, and emit edge-scroll start/stop effects only
   when the edge-scroll direction changes.
5. Corner edge zones choose horizontal or vertical edge scroll from the latest
   dominant movement axis.
6. `RecenterScope` returns the crosshair to the content center, clears edge
   scroll, and emits recenter plus stop-scroll effects when needed.
7. First `Escape` in `LiveScope` sets quit confirmation, shows
   `Double click again to quit ScopeX`, and emits
   `StartQuitConfirmationTimer(2000L)`. A second `Escape` while confirmation is
   active emits `QuitScopeX`.
8. `QuitConfirmationTimeout` clears quit confirmation and the prompt state.

## Error Handling And Safety

The reducer remains pure. It does not touch Android APIs, clocks, capture,
storage, permissions, audio, or network. Invalid configuration values fail at
event construction. Unhandled state/event pairs return the current state with no
effects.

## Testing

Use TDD in `ScopeXReducerTest` against the public reducer interface:

- normal crosshair actions emit the expected effects;
- active-source rejection still prevents effects;
- edge scroll starts when the crosshair enters an edge zone and stops when it
  leaves;
- corner edge scroll follows the last dominant movement axis;
- `RecenterScope` recenters and stops edge scroll;
- first `Escape` shows quit confirmation;
- second `Escape` emits `QuitScopeX`;
- `QuitConfirmationTimeout` clears confirmation.

Final verification:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```
