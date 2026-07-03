# scopex Spec

Read `brainstorm.md` for the full handoff. This file is the working bootstrap spec.

## Product

ScopeX turns a user-approved Android app/display surface into content on a larger logical display. A visible device, such as Rokid glasses later, shows a movable physical scope into that logical display while the crosshair stays fixed at the center.

## Package

Use `com.code2hack.scopex`.

## Bootstrap Scope

Build only:

- Kotlin Android project skeleton;
- `app` module with a simple debug home placeholder;
- pure Kotlin/JVM `scopex-core` module;
- ScopeX geometry tests.

Do not add MediaProjection, Rokid SDK, ASR, Bluetooth ring input, AccessibilityService control, OCR, recording, cloud streaming, or Termux-specific logic in this phase.

## ScopeX Core

`scopex-core` must not depend on Android UI classes, MediaProjection, Compose, or the Rokid SDK.

Coordinate model:

- logical display content: captured content bounds, starting at `(0, 0)`;
- logical display: content plus half the physical scope size on all sides;
- physical scope rect: physical scope in logical display coordinates;
- crosshair content point: fixed center of the physical scope mapped into content coordinates.

Required example:

- content: `1920 x 1080`;
- physical scope: `640 x 480`;
- logical display rect: `(-320, -240, 2240, 1320)`.

## Privacy And Security

- no silent capture;
- no capture without Android system consent;
- no bypassing protected content or `FLAG_SECURE`;
- no frame persistence by default;
- no automatic recording by default;
- no cloud streaming by default;
- no AccessibilityService app-control without explicit user approval;
- active capture UI and a quick stop-capture action are required before capture work starts.
