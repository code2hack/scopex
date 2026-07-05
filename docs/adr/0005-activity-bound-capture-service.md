# Use an Activity-bound foreground service for capture proof

The logical-display capture proof uses a minimal `mediaProjection` foreground service even though capture is visible-Activity scoped, because target SDK 36 MediaProjection requires foreground-service handling for real screen capture. The service lifetime stays bound to the proof screen and its Stop surfaces, so this satisfies Android capture requirements without introducing background capture behavior.
