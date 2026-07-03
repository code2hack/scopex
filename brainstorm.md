# scopex — Handoff Specification

Status: **initial product/design handoff**  
Project name: **scopex**  
Design origin: **Rokid Spatious ScopeX**  
Primary target companion device: **Android phone / Fold 6 class device**  
Primary glasses target: **Rokid display-capable glasses**  
Preferred implementation language: **Kotlin**  
First engineering goal: **phone-side Android skeleton + pure ScopeX geometry + MediaProjection proof**

---

## 0. One-line thesis

**ScopeX turns Rokid glasses into a head-tracked physical scope into a larger Android app/display surface.**

An Android companion app captures a user-approved app window or full display, places that captured surface into a logical display, and eventually streams tiled overscan regions to a Rokid-side renderer. The Rokid app uses head pose to crop the physical scope locally from cached content and shows a fixed center crosshair.

---

## 1. Why this project exists

Normal mirroring into glasses has a fundamental problem:

```text
Android app/display surface: large
Rokid visible display:       small
```

If the entire Android screen is scaled down into the glasses display, text and UI become hard to read. If the view is zoomed in, the user needs constant manual panning.

ScopeX uses the user's head as the panning mechanism:

```text
Android app/display surface
→ large padded logical display
→ small Rokid physical scope
→ head movement pans the physical scope
```

The user should feel like a large Android app surface is floating in front of them, while the physical glasses display is only a movable window into that surface.

---

## 2. Product identity

### 2.1 What scopex is

ScopeX is a **spatial ScopeX system** for Android app/display mirroring.

Core product primitive:

```text
MediaProjection capture
+ logical display
+ fixed center crosshair
+ head-tracked physical scope
+ tiled overscan cache
```

### 2.2 What scopex is not

ScopeX is **not** primarily:

- a Termux project;
- a terminal emulator;
- a generic agent runtime;
- a Rokid SDK-first experiment;
- an ASR/voice-first product;
- an Android app-control automation tool;
- a screen recording product.

Termux is only a future/flagship mirrored-app use case. It should not define the core architecture.

### 2.3 Product name guidance

Use `scopex` for the new repository/project. Optional display styling can be `ScopeX`, but code/package names should remain stable and lowercase where appropriate.

Suggested Android package:

```text
com.code2hack.scopex
```

Alternative if keeping Rokid identity in package:

```text
com.code2hack.rokid.scopex
```

Recommendation: use `com.code2hack.scopex` so the project can evolve beyond a single hardware vendor while still targeting Rokid first.

---

## 3. Target user and use cases

### 3.1 Primary user

The initial user is the maintainer/developer who wants to experiment with Rokid glasses and Android app mirroring using a clean, agent-readable codebase.

### 3.2 First practical use cases

The first useful mirrored apps are text-heavy or productivity-oriented apps:

- Termux, as a future flagship use case;
- browser pages;
- PDFs/documents;
- notes;
- chat apps;
- dashboards;
- settings/developer tools;
- simple app UIs for testing capture and panning.

### 3.3 Later use cases

After ScopeX is stable:

- use Termux through a spatial physical scope;
- use a Bluetooth ring for click/Escape/scroll/recenter;
- use ASR for semantic commands;
- add crosshair-based click/scroll through an explicit input bridge;
- add Rokid-native renderer and real head pose;
- add OCR/semantic assistance around the crosshair.

---

## 4. System overview

The system has two sides.

```text
Android companion app
├── starts user-approved MediaProjection capture
├── receives app/display frames through a capture Surface
├── maps captured frames into a logical display
├── computes physical scope, display padding, overscan, and tiles
├── sends tile/overscan data to the client renderer later
└── receives optional input events later

Rokid-side app / client
├── receives tiled overscan/cached content
├── reads head pose or fake pose
├── crops physical scope locally
├── renders fixed center crosshair
├── exposes recenter/freeze/debug controls
└── sends optional input events later
```

First implementation does **not** require Rokid integration. The phone-side simulator should prove the spatial ScopeX behavior first.

---

## 5. Core visual model

### 5.1 Coordinate systems

ScopeX uses several coordinate systems:

```text
1. Captured frame coordinates
   Pixel coordinates from MediaProjection output.

2. Logical display coordinates
   The captured frame placed at `(0, 0)`.

3. Padded logical display coordinates
   Logical display plus half-physical-scope padding on all sides.

4. ScopeX coordinates
   The physical visible Rokid region expressed in padded logical display coordinates.

5. Crosshair coordinates
   The fixed center point of the physical scope, mapped back to logical display coordinates.
```

### 5.2 Crosshair concept

The crosshair is a fixed center pointer in the glasses display:

```text
Rokid physical scope
┌────────────────────────┐
│                        │
│           +            │  ← fixed center crosshair
│                        │
└────────────────────────┘
```

Head movement changes what content is underneath the crosshair. The crosshair itself stays visually fixed.

The crosshair is not:

- the Android cursor;
- the terminal cursor;
- a mouse pointer in the first MVP.

It is initially a spatial aiming point and debug coordinate.

Later, the crosshair can drive actions:

```text
ring tap       → click/tap at crosshair coordinate
voice command  → act on content near crosshair
phone touch    → precision fallback
```

### 5.3 Padded logical display requirement

The fixed center crosshair must be able to point at the true corners of the captured app surface.

If the content is `contentWidth × contentHeight` and the physical scope is `physicalScopeWidth × physicalScopeHeight`, then the padded logical display should be:

```text
paddedLeft   = -physicalScopeWidth  / 2
paddedTop    = -physicalScopeHeight / 2
paddedRight  = contentWidth  + physicalScopeWidth  / 2
paddedBottom = contentHeight + physicalScopeHeight / 2
```

Example:

```text
content: 1920 × 1080
physical scope: 640 × 480

padded logical display:
left   = -320
top    = -240
right  = 2240
bottom = 1320
```

When the crosshair points at the top-left content corner:

```text
crosshair = 0,0
physical scope = -320,-240 to 320,240
```

When the crosshair points at the bottom-right content corner:

```text
crosshair = 1920,1080
physical scope = 1600,840 to 2240,1320
```

This is a hard product requirement. Do not clamp the physical scope so tightly that the crosshair cannot point at the true content corners.

---

## 6. Head-pose mapping

### 6.1 Conceptual mapping

Head pose controls which content coordinate sits under the fixed center crosshair.

```text
head yaw   → horizontal movement across the logical display
head pitch → vertical movement across the logical display
```

The app should use a fused/stabilized orientation signal where available, not raw gyroscope integration if a better orientation signal exists.

### 6.2 Recenter model

The user needs a neutral pose:

```text
recenterPose = current head pose when user presses/says recenter
currentPose  = current head pose
poseDelta    = currentPose - recenterPose
```

### 6.3 Mapping formula

Initial mapping:

```text
normalizedX = clamp(yawDelta   / maxYawDegrees,   -1, 1)
normalizedY = clamp(pitchDelta / maxPitchDegrees, -1, 1)

crosshairX = map(normalizedX, -1..1, 0..contentWidth)
crosshairY = map(normalizedY, -1..1, 0..contentHeight)

physicalScopeLeft = crosshairX - physicalScopeWidth  / 2
physicalScopeTop  = crosshairY - physicalScopeHeight / 2
```

The physical scope may extend into padding.

### 6.4 Required controls

Even in simulator form, the UX should include or reserve space for:

- recenter;
- freeze/unfreeze physical scope;
- sensitivity adjustment;
- dead-zone adjustment;
- smoothing adjustment;
- show/hide debug overlay.

### 6.5 Stabilization requirements

Reading text through a head-tracked physical scope will be sensitive to jitter. Build stabilization hooks early.

Required or planned behaviors:

- small dead zone around neutral pose;
- low-pass smoothing;
- optional velocity-aware damping;
- fast recenter action;
- freeze/lock mode for reading.

Do not overbuild these in the first commit, but design the geometry/input API so they can be added without rewriting the mapping.

---

## 7. MediaProjection capture model

### 7.1 Purpose

MediaProjection is the Android capture foundation. It provides a stream of pixels from a user-approved display or app window into an app-provided Surface.

### 7.2 Supported capture modes

The project should support these modes over time:

```text
Mode A: full-display capture
- User shares the default/full device display.
- Works as a broad fallback.

Mode B: single-app capture
- On Android 14/API 34+, users can share a single app window.
- This is preferred where available.
```

### 7.3 MediaProjection boundaries

MediaProjection is display/capture only.

It does **not**:

- control the captured app;
- provide semantic UI structure;
- provide off-screen app contents;
- bypass secure/protected content;
- remove the need for user consent.

### 7.4 User consent and session lifecycle

The app must request Android system consent before capture.

Treat a capture session as session-scoped and disposable:

- user starts capture;
- system consent UI appears;
- user chooses display/app where supported;
- app creates virtual display once for that session;
- app registers stop callbacks;
- app releases virtual display and surface on stop.

### 7.5 Capture surface options

Initial options:

```text
ImageReader
- easiest to debug;
- CPU-visible frames;
- likely higher copy cost.

SurfaceTexture / OpenGL texture
- better long-term for GPU crop/composite;
- more complex.

Encoder surface
- useful for video stream;
- less flexible for tiled spatial cache.
```

Recommendation:

```text
Phase 1 capture proof: ImageReader or simplest viable preview path.
Later performance pass: SurfaceTexture / GPU path.
```

### 7.6 Protected content

If an app or region uses secure-window protection, it may appear blank/black/hidden in capture. This is expected.

Hard rule:

```text
Do not bypass protected content.
```

---

## 8. Strategy C: tiled padded logical display with overscan margin

Strategy C is selected as the long-term streaming model.

### 8.1 Why Strategy C exists

Sending only the visible crop means every head movement requires a round trip:

```text
Rokid pose update
→ Android crop/render/encode
→ send to Rokid
→ display
```

That is likely too laggy.

Sending the full padded logical display every frame can be wasteful or infeasible.

Strategy C sends more than the physical scope but less than the full padded logical display.

### 8.2 Model

```text
Full padded logical display
┌──────────────────────────────────────────────┐
│                                              │
│       cached overscan region                 │
│       ┌──────────────────────────────┐       │
│       │                              │       │
│       │      physical scope        │       │
│       │      ┌──────────────┐        │       │
│       │      │      +       │        │       │
│       │      └──────────────┘        │       │
│       │                              │       │
│       └──────────────────────────────┘       │
│                                              │
└──────────────────────────────────────────────┘
```

Android sends/caches:

- physical scope region;
- overscan margin around it;
- nearby tiles;
- later, directionally predicted tiles.

Rokid locally crops the physical scope from the cached region as the user's head moves.

### 8.3 Initial defaults

Suggested initial constants:

```text
tile size:         512 × 512
overscan margin X: physicalScopeWidth  × 0.75
overscan margin Y: physicalScopeHeight × 0.75
```

These are starting values only and should be tuned through device tests.

### 8.4 Implementation evolution

Do not start with full tiling complexity.

Recommended progression:

```text
1. Full-frame local crop in phone simulator.
2. Full overscan-region update in local simulator.
3. Tile grid abstraction.
4. Dirty tile update prototype.
5. Directional prefetch based on head velocity.
6. Multi-resolution tile pyramid.
7. Transport-specific optimization.
```

### 8.5 Tile cache concepts

Future tile cache key:

```text
sessionId
contentEpoch or frameId
tileLevel
tileX
tileY
```

Rokid renderer behavior on missing tile:

1. Use last-known tile if recent enough.
2. Use low-resolution fallback if available.
3. Show subtle loading/edge indicator if needed.
4. Never crash or block head movement because of a cache miss.

---

## 9. Display modes

### 9.1 Phone-side simulator mode

This mode is mandatory and should come before Rokid integration.

Input source:

- synthetic image/grid first;
- later MediaProjection frame.

Pose source:

- touch drag first;
- optional phone sensors;
- fake pose provider.

Purpose:

- test logical display math;
- test crosshair behavior;
- test readability;
- test recenter/freeze;
- debug coordinates and overscan.

### 9.2 Full-display ScopeX mode

The Android companion captures the full display and maps it into the logical display.

This is the fallback mode.

### 9.3 Single-app ScopeX mode

Where supported, the user selects one app/window through Android's capture UI.

This should become the preferred mode because it excludes unrelated system UI and narrows the shared surface.

### 9.4 Rokid live mode

The Android companion sends cached content to the Rokid-side app. Rokid uses real head pose to crop locally.

This should begin only after the phone-side simulator and capture proof are stable.

---

## 10. Interaction model

### 10.1 MVP interaction

MVP interaction should be minimal:

- start capture;
- stop capture;
- pan physical scope in simulator;
- recenter;
- freeze/unfreeze;
- show debug overlay.

No arbitrary app control in MVP.

### 10.2 Future crosshair-based control

Later, the system can map crosshair coordinates back to Android display coordinates:

```text
crosshair in Rokid physical scope
→ logical display coordinate
→ captured frame coordinate
→ display coordinate
→ input bridge
```

Potential action:

```text
ring tap → Android tap gesture at mapped coordinate
```

### 10.3 AccessibilityService boundary

App control should be an optional, explicit future layer. Android AccessibilityService gesture dispatch may be the likely mechanism, but it must be separately gated and user-approved.

Hard rule:

```text
Do not merge display/capture with app-control automation in the first implementation.
```

### 10.4 Future input providers

Potential providers:

- Bluetooth ring;
- ASR / speech commands;
- phone touchpad;
- hardware keyboard;
- Rokid touch/head gestures;
- AccessibilityService bridge.

Best later interaction model:

```text
head movement = aim / physical scope movement
ring          = click / Escape / scroll / recenter
ASR           = semantic command
phone         = fallback precision input
```

---

## 11. Privacy and security requirements

ScopeX deals with screen capture. Its privacy/security posture must be clear from day one.

Hard requirements:

- no silent capture;
- no capture without Android system consent;
- no bypassing `FLAG_SECURE` or protected content;
- no frame persistence by default;
- no automatic recording by default;
- no cloud streaming by default;
- no AccessibilityService app-control without explicit user approval;
- obvious active-capture UI;
- quick stop-capture action.

If protected content appears blank, document it as an expected platform boundary.

---

## 12. Recommended clean repository structure

Because this is a new project, avoid carrying old `rokid-termux` scaffolding.

Recommended initial layout:

```text
scopex/
  README.md
  AGENTS.md
  spec.md
  settings.gradle.kts
  build.gradle.kts
  gradle.properties

  docs/
    ARCHITECTURE.md
    ROADMAP.md
    PRIVACY_SECURITY.md
    adr/
      0001-product-scope.md
      0002-scopex-geometry-padding.md
      0003-mediaprojection-capture.md
      0004-tiled-overscan-strategy.md
    codex/
      START_PROMPT.md
      PM_ORCHESTRATION.md
      workers/
        ANDROID_SKELETON.md
        SPATIAL_VIEWPORT_CORE.md
        MEDIAPROJECTION_CAPTURE.md

  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/code2hack/scopex/
        MainActivity.kt

  scopex-core/
    build.gradle.kts
    src/main/kotlin/com/code2hack/scopex/scopex/
      Geometry.kt
      ScopeXMapper.kt
    src/test/kotlin/com/code2hack/scopex/scopex/
      ScopeXMapperTest.kt
```

Later modules/directories:

```text
capture/
  MediaProjectionController.kt
  MediaProjectionService.kt
  CaptureFrameSource.kt

simulator/
  ScopeXSimulatorScreen.kt
  DebugOverlay.kt

tiling/
  TileGrid.kt
  OverscanRegion.kt
  TileCache.kt

transport/
  ScopeXProtocol.kt
  LocalTransport.kt
  WebSocketTransport.kt

rokid-client/
  added later after SDK/head-pose path is verified
```

Do not add all later modules at once. Start with:

```text
app/
scopex-core/
```

---

## 13. Kotlin module design

### 13.1 scopex-core module

`scopex-core` must be pure Kotlin/JVM-testable.

It should not depend on Android UI classes, MediaProjection, Compose, or Rokid SDK.

Suggested types:

```kotlin
data class IntSize(val width: Int, val height: Int)

data class FloatPoint(val x: Float, val y: Float)

data class FloatRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class PoseDelta(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float = 0f,
)

data class ScopeXConfig(
    val contentSize: IntSize,
    val physicalScopeSize: IntSize,
    val maxYawDegrees: Float,
    val maxPitchDegrees: Float,
)

data class ScopeXState(
    val crosshairContentPoint: FloatPoint,
    val physicalScopeRect: FloatRect,
    val logicalDisplayRect: FloatRect,
    val paddedLogicalDisplayRect: FloatRect,
)
```

Suggested mapper API:

```kotlin
class ScopeXMapper(
    private val config: ScopeXConfig,
) {
    fun paddedLogicalDisplayRect(): FloatRect
    fun stateForNormalizedPose(x: Float, y: Float): ScopeXState
    fun stateForPoseDelta(delta: PoseDelta): ScopeXState
    fun stateForCrosshairContentPoint(point: FloatPoint): ScopeXState
}
```

### 13.2 Geometry tests

Required tests:

- padded logical display calculation;
- center pose maps to content center;
- top-left corner;
- top-right corner;
- bottom-left corner;
- bottom-right corner;
- physical scope extends into padding by half physical scope size;
- normalized values clamp correctly;
- invalid sizes fail clearly or are rejected.

Use exact example values:

```text
content = 1920 × 1080
physical scope = 640 × 480
```

Expected padded logical display:

```text
left   = -320
top    = -240
right  = 2240
bottom = 1320
```

---

## 14. Android app skeleton guidance

### 14.1 Kotlin-first

Use Kotlin, not Java.

### 14.2 Compose vs Views

Either Jetpack Compose or classic Android Views is acceptable. For a new project, Compose is reasonable if it does not slow setup.

Keep the first UI simple.

### 14.3 Initial screens

Initial app can have one debug screen:

```text
ScopeX debug home
├── ScopeX simulator placeholder
├── MediaProjection capture placeholder
├── Diagnostics placeholder
└── Build/version info
```

Do not implement MediaProjection in the skeleton commit.

### 14.4 Build command

Document the exact build/test commands in README, for example:

```bash
./gradlew :app:assembleDebug
./gradlew :scopex-core:test
```

---

## 15. MediaProjection capture proof design

Start this only after the Android skeleton and `scopex-core` exist.

### 15.1 Components

Possible classes:

```text
MediaProjectionController
MediaProjectionService
CaptureFrameSource
CapturePreviewView / CapturePreviewScreen
ProjectionState
```

### 15.2 First proof flow

```text
User taps Start Capture
→ Android consent UI opens
→ user selects app/display
→ app receives MediaProjection token
→ app starts foreground service if required
→ app creates virtual display
→ captured frames go to ImageReader/SurfaceTexture
→ debug UI displays frames
→ user taps Stop Capture
→ app releases virtual display, surface, projection
```

### 15.3 Acceptance criteria

- User can start capture through Android system consent.
- Captured content appears in debug view.
- User can stop capture.
- Projection stop callback releases resources.
- Resize/rotation does not crash, or limitation is documented.
- No frame persistence occurs by default.
- Protected/blank content is treated as expected platform behavior.

---

## 16. Phone-side simulator design

The simulator is central to reducing Rokid risk.

### 16.1 First simulator source

Start with a synthetic coordinate grid, not live capture.

The grid should make it obvious where the physical scope is:

- large coordinate labels;
- colored or patterned regions if desired;
- corner labels;
- center label;
- high-contrast text.

### 16.2 Simulator inputs

Initial:

- touch drag for fake head pose;
- buttons for recenter/freeze;
- sliders for sensitivity/dead zone if easy.

Later:

- phone sensors;
- fake head-pose provider;
- actual Rokid pose.

### 16.3 Debug overlay

Show:

- content size;
- physical scope size;
- crosshair coordinate;
- physical scope rect;
- padded logical display rect;
- current normalized pose;
- FPS if available.

---

## 17. Transport design, later

Transport is not needed for the first phone-side simulator.

Later transport should be abstracted:

```kotlin
interface ScopeXTransport {
    fun sendFrameMetadata(metadata: FrameMetadata)
    fun sendTile(tile: EncodedTile)
    fun receivePoseUpdates(): Flow<PoseUpdate>
    fun receiveInputEvents(): Flow<InputEvent>
}
```

Potential transports:

- local in-process simulator;
- WebSocket over Wi-Fi for development;
- Bluetooth/Wi-Fi Direct if practical;
- Rokid SDK/vendor channel if available.

Do not lock core architecture to one transport too early.

---

## 18. Rokid-side renderer, later

Start Rokid integration only after:

- Android skeleton builds;
- scopex-core tests pass;
- phone-side simulator works;
- MediaProjection capture proof works;
- overscan cache prototype exists or is planned.

Rokid-side components:

```text
ScopeXRenderer
TileCache
CrosshairOverlay
HeadPoseProvider
FakeHeadPoseProvider
TransportClient
DiagnosticsOverlay
```

Head-pose provider must hide SDK-specific details behind an interface.

---

## 19. Agent workflow for new project

### 19.1 Recommended first PM instruction

The PM should read `AGENTS.md` and this `spec.md`, then proceed in this order:

```text
1. Create clean Kotlin Android skeleton.
2. Add pure scopex-core module/tests.
3. Add phone-side synthetic ScopeX simulator.
4. Add MediaProjection capture proof.
5. Connect captured frame to the ScopeX simulator.
```

### 19.2 Recommended worker split

Current phase should use at most two workers plus PM.

#### Worker 1 — Android skeleton

Branch:

```text
chore/android-kotlin-skeleton
```

Scope:

- Gradle setup;
- app module;
- MainActivity;
- simple debug UI;
- README build commands.

Do not implement MediaProjection.

#### Worker 2 — scopex-core

Branch:

```text
feat/scopex-core
```

Scope:

- pure Kotlin geometry module;
- padded logical display math;
- pose-to-crosshair mapping;
- tests.

Do not implement MediaProjection.

#### Worker 3 — MediaProjection capture

Start later.

Branch:

```text
feat/mediaprojection-capture
```

Scope:

- consent flow;
- virtual display;
- capture surface;
- debug preview;
- stop/release handling.

### 19.3 Stop conditions for agents

Agents should stop and ask before:

- adding Rokid SDK assumptions;
- adding ASR;
- adding Bluetooth ring;
- adding AccessibilityService;
- adding cloud streaming;
- adding recording/frame persistence;
- changing project/package naming;
- turning the project back into a Termux-first design.

---

## 20. Suggested initial files for the clean repo

### 20.1 README.md summary

```markdown
# scopex

ScopeX is a Kotlin Android + Rokid experiment that mirrors a user-approved Android app/display surface into a logical display. Rokid glasses show a head-tracked physical scope over the padded logical display with a fixed center crosshair.

First milestones:

1. Kotlin Android project skeleton.
2. Pure ScopeX geometry module with tests.
3. Phone-side ScopeX simulator.
4. MediaProjection capture proof.
5. Captured frame connected to the ScopeX simulator.
```

### 20.2 AGENTS.md summary

```markdown
# AGENTS.md

Read `spec.md` before coding.

Implementation order:

1. Android Kotlin skeleton.
2. Pure scopex-core module/tests.
3. Phone-side simulator.
4. MediaProjection capture proof.
5. Capture-to-ScopeX integration.

Do not start with Rokid SDK, ASR, Bluetooth ring, AccessibilityService, OCR, Termux-specific logic, or cloud streaming.
```

---

## 21. Roadmap

### Phase 0 — Clean project bootstrap

Deliverables:

- clean repo;
- README;
- AGENTS.md;
- spec.md;
- Kotlin Gradle project skeleton;
- app module placeholder;
- scopex-core module placeholder if convenient.

Acceptance:

- repo opens cleanly;
- build command is documented;
- no stale `rokid-termux` framing.

### Phase 1 — scopex-core

Deliverables:

- pure geometry code;
- unit tests;
- padded logical display behavior;
- crosshair/physical scope mapping.

Acceptance:

- tests pass;
- all four content corners are reachable by the fixed center crosshair.

### Phase 2 — phone-side simulator

Deliverables:

- synthetic logical display;
- physical scope crop;
- fixed crosshair;
- touch drag/fake pose;
- debug overlay.

Acceptance:

- user can pan around synthetic content;
- crosshair coordinates are correct;
- freeze/recenter works or is stubbed.

### Phase 3 — MediaProjection proof

Deliverables:

- consent flow;
- foreground service if needed;
- virtual display;
- capture surface;
- debug preview;
- stop/release handling.

Acceptance:

- user can capture app/display and see frames in debug UI.

### Phase 4 — capture-to-ScopeX integration

Deliverables:

- captured frame becomes logical display;
- ScopeX simulator crops captured frame;
- debug overlay shows capture + physical scope coordinates.

Acceptance:

- user can pan around a captured app/display frame in the phone simulator.

### Phase 5 — overscan cache prototype

Deliverables:

- tile grid;
- overscan region;
- cache hit/miss diagnostics;
- visible crop from cached region.

Acceptance:

- local prototype demonstrates Strategy C.

### Phase 6 — Rokid live renderer

Deliverables:

- Rokid renderer skeleton;
- head-pose provider;
- fake pose fallback;
- crosshair overlay;
- tile cache renderer.

Acceptance:

- ScopeX appears on Rokid;
- head pose moves local crop.

### Phase 7 — input providers

Deliverables:

- Bluetooth ring provider;
- ASR provider;
- optional phone touchpad mode;
- optional accessibility-gated input bridge.

Acceptance:

- input remains explicit, safe, and optional.

---

## 22. Open questions

Track these in issues or docs:

1. Which exact Rokid model and SDK path will be used?
2. What is the actual Rokid display resolution and refresh rate?
3. What head-pose API is available on the Rokid side?
4. What transport path between Fold 6 and Rokid is available and lowest latency?
5. Which Android version is the Fold 6 running?
6. Does Samsung's MediaProjection implementation behave differently in single-app sharing?
7. Should the app use Compose or Views for early debug UI?
8. Should first capture proof use ImageReader or SurfaceTexture?
9. What is the first demo app: browser, Termux, PDF, settings, or a synthetic test app?
10. How much physical scope jitter is acceptable for reading text?

---

## 23. External platform references to verify during implementation

These references are included to help implementation agents verify Android platform behavior against official documentation.

- Android MediaProjection guide: `https://developer.android.com/media/grow/media-projection`
- Android `MediaProjectionConfig` API: `https://developer.android.com/reference/android/media/projection/MediaProjectionConfig`
- Android `WindowManager.LayoutParams.FLAG_SECURE`: `https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE`

Important facts to verify against those docs during implementation:

- Android 14/API 34 supports app screen sharing so a user can share a single app window instead of the entire display.
- MediaProjection captures a display or app window into a virtual display rendered onto an app-provided `Surface`.
- Common Surface consumers include `ImageReader` and `SurfaceTexture`.
- Android 14+ requires careful session/token handling; do not reuse a token/session for multiple `createVirtualDisplay()` calls.
- Capture size can change on rotation or selected-app window changes; handle resize callbacks.
- `FLAG_SECURE` prevents secure content from appearing in screenshots or non-secure displays/screen sharing.

---

## 24. Final handoff statement

ScopeX should start as a clean Kotlin Android project focused on one breakthrough:

```text
A user-approved Android app/display surface can be viewed through a padded logical display using a fixed center crosshair and a movable physical scope.
```

Do not start with Rokid SDK, Termux, ASR, Bluetooth ring, OCR, or AccessibilityService. Those are extensions.

The correct first path is:

```text
clean Android skeleton
→ pure ScopeX geometry tests
→ phone-side simulator
→ MediaProjection proof
→ capture-to-ScopeX integration
→ overscan cache
→ Rokid renderer
→ input providers
```

If this foundation feels stable and readable, the product can expand naturally into a true wearable spatial mirror for Android apps.
