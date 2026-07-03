# scopex

ScopeX is a Kotlin Android + Rokid experiment that mirrors a user-approved Android app/display surface into a logical display. Rokid glasses eventually show a head-tracked physical scope over the padded logical display with a fixed center crosshair.

First milestones:

1. Kotlin Android project skeleton.
2. Pure ScopeX geometry module with tests.
3. Phone-side ScopeX simulator.
4. MediaProjection capture proof.
5. Captured frame connected to the ScopeX simulator.

Build and test:

```bash
./gradlew :scopex-core:test
./gradlew :app:assembleDebug
```

Initial scope is intentionally narrow: Android skeleton plus `scopex-core`. MediaProjection, Rokid SDK, ASR, Bluetooth ring input, AccessibilityService control, OCR, recording, cloud streaming, and Termux-specific logic are out of scope for this bootstrap.
