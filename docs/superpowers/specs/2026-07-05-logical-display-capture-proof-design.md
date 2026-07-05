# Logical-Display Capture Proof Design

## Goal

Build the first real capture milestone: a whole-display Android capture rendered
inside ScopeX's padded logical display, with display padding, physical scope,
and crosshair visible for inspection.

This proof is not a production glasses renderer, interaction-injection layer,
ASR feature, or app-window picker. It exists to prove Android capture consent,
latest-frame delivery, ScopeX geometry mapping, and visible privacy controls in
the current Android app.

## Scope

Implement this as two issues:

1. Add a pure capture-proof layout model in `scopex-core` with JVM tests.
2. Add the Android MediaProjection proof in `app` using the core layout model.

The first issue should not touch Android APIs. The second issue should not
expand into control injection, background capture, frame recording, app-window
selection, or calibration UI.

## Product Behavior

The proof starts from the existing debug home. The user explicitly starts
capture and grants Android's screen-capture consent. The app captures the whole
display and renders the latest captured frame into the logical display inside a
single custom View.

Self-capture is acceptable for this milestone. If the user stays in ScopeX, the
rendered frame may show the proof screen recursively. The success condition is
live MediaProjection frame flow plus ScopeX geometry overlay, not target-app
fidelity.

The proof renders:

- the captured frame as the logical display;
- display padding around that logical display;
- the physical scope rectangle;
- a fixed crosshair.

The captured frame size is the logical display size. The physical scope size is
derived from the proof View using a fixed fraction of the shorter View dimension
and a 4:3 aspect ratio. Synthetic movement controls move the crosshair to the
center and corners. There is no drag gesture or calibration UI in this slice.

## Architecture

Android capture stays in `app`. ScopeX layout math lives in `scopex-core` and
reuses `ScopeXMapper` so the proof does not grow a second geometry model.

`scopex-core` should expose a small pure layout model that accepts:

- captured frame size;
- proof View size;
- selected crosshair position, such as center or corner;
- the fixed physical-scope sizing rule.

It returns enough public geometry for Android drawing:

- logical display draw bounds;
- padded logical display draw bounds;
- physical scope draw bounds;
- crosshair draw point.

The Android side should keep one latest frame only. New frames replace the old
frame. There is no frame queue, replay buffer, or captured-frame persistence.

## Android Capture Requirements

The app targets SDK 36, so this design follows the current Android
MediaProjection and foreground-service rules:

- ask for user consent for each capture session;
- use a foreground service declared with the `mediaProjection` foreground
  service type;
- declare the required foreground-service permissions;
- create the projection from the consent result;
- clean up when projection stops.

The foreground service is intentionally Activity-bound. It exists to satisfy
Android's MediaProjection requirements, not to introduce background capture.
Capture stops when the user taps Stop, taps the notification Stop action, the
Activity stops or is destroyed, or Android stops the projection.

References:

- Android MediaProjection guide:
  https://developer.android.com/media/grow/media-projection
- Android foreground service types:
  https://developer.android.com/develop/background-work/services/fgs/service-types
- MediaProjectionManager API flow:
  https://developer.android.com/reference/kotlin/android/media/projection/MediaProjectionManager

## UI

Use one Activity. Keep capture rendering in a focused custom View rather than
mixing drawing logic into `MainActivity`.

The proof UI needs:

- Start Capture;
- Stop Capture;
- a visible active-capture indicator while capture is running;
- status text for idle, requesting permission, active, denied, stopped, and
  error states;
- synthetic crosshair movement buttons for center and corners.

The foreground-service notification should say `ScopeX screen capture active`
and include a Stop action.

## Privacy And Safety

No captured frame is written to disk. No debug snapshot button is included.
No automatic frame persistence exists. No background capture is introduced.

Permission denial returns the proof to idle and shows a status message.
Projection stop returns the proof to idle and shows a status message. Stale last
frames should not remain presented as active capture after stop.

ScopeX overlays remain app UI for this proof; they are not part of captured
content unless self-capture naturally captures the proof screen.

## Testing

Issue 1 tests should be pure JVM tests in `scopex-core`:

- actual captured frame size becomes the logical display size;
- padded logical display includes half the physical scope on every side;
- center and corner crosshair selections map through `ScopeXMapper`;
- physical scope draw bounds are stable for representative View sizes;
- invalid frame/View sizes fail clearly.

Issue 2 tests should avoid real MediaProjection consent:

- fake frame injection replaces the latest frame instead of queueing;
- renderer state clears or returns to idle on stop;
- proof status changes on denied/stopped paths;
- custom View can draw from a fake frame/layout result without needing Android
  capture.

Manual smoke test for issue 2:

```bash
./gradlew :scopex-core:test :app:assembleDebug --no-problems-report
```

Install the debug APK, start capture, grant Android screen-capture consent,
observe the live whole-display capture inside the padded logical display, move
the crosshair with center/corner controls, and stop capture from both the app
and notification.
