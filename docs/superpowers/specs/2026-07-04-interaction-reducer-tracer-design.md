# ScopeX Interaction Reducer Tracer Design

## Goal

Add the first platform-neutral ScopeX interaction reducer in `scopex-core`.

This slice proves the core seam by handling one canonical `LiveScope` event:
`ClickCrosshair` keeps the public interaction state in `LiveScope` and emits an
explicit `InjectClick` effect command at the current crosshair content point.

## Architecture

The reducer is a small, pure core module. Callers pass the current public
`ScopeXInteractionState` and one `ScopeXEvent`; the reducer returns a
`ScopeXTransition` containing the next state and effect commands. Platform
modules execute those commands and may later report result events back through
the same event surface.

The external seam is the reducer call. Tests and platform modules should not
reach into helper functions or infer side effects from state.

## Public Shapes

`scopex-core` will expose explicit platform-neutral shapes for:

- `ScopeXInteractionState`: top-level states `LiveScope`, `Recording`, and
  `InputCachePanelOpen`. This slice only gives `LiveScope` behavior.
- `ScopeXEvent`: canonical events, result events, timer events, and
  configuration events. This slice implements `Canonical.ClickCrosshair`;
  result, timer, and configuration event groups are explicit sealed interfaces
  without behavior until later slices need concrete events.
- `ScopeXEffectCommand`: platform commands. This slice implements
  `InjectClick(crosshairContentPoint)`.
- `ScopeXTransition`: reducer output with `state` and `effects`.

## Data Flow

1. A platform adapter converts a device input into `ScopeXEvent.Canonical.ClickCrosshair`.
2. The platform calls `ScopeXReducer.reduce(currentState, event)`.
3. If the current state is `LiveScope`, core returns the same state plus
   `InjectClick` at `LiveScope.crosshairContentPoint`.
4. The platform injects the phone-side click and keeps owning platform-specific
   side effects outside `scopex-core`.

Other state/event pairs return the current state with no effects in this first
slice.

## Error Handling And Safety

The reducer is pure and does not touch Android APIs, clocks, ASR, capture,
storage, permissions, or network. It does not start capture, persist frames,
persist audio, or bypass protected content. Invalid geometry remains guarded by
the existing geometry value types.

## Testing

Add a reducer test that exercises only the public reducer interface:

- Given `LiveScope(crosshairContentPoint = FloatPoint(100f, 200f))`
- When reducing `ScopeXEvent.Canonical.ClickCrosshair`
- Then the state remains `LiveScope` with the same crosshair point
- And the only effect is `ScopeXEffectCommand.InjectClick(FloatPoint(100f, 200f))`

Existing geometry tests must keep passing. Final verification command:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```
