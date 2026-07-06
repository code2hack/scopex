## useful for MVP / not useful for MVP

Not useful for MVP pending real-device proof.

Official Android and Samsung docs do not establish a reliable, third-party path for ScopeX to capture Samsung DeX desktop, DeX external-display, or DeX app-window content on a Fold6. Android documents MediaProjection as a user-consented capture of a device display or app window, but the public `MediaProjectionConfig` API says display capture supports only `Display.DEFAULT_DISPLAY` when the user selects display capture. Samsung documents DeX as a desktop/external-display experience and an app-compatibility environment, not as a public frame-capture API for third-party apps.

## exact captured surface

The only officially established surfaces are:

- Default Fold6 display capture: the public Android MediaProjection display-capture path, constrained to `Display.DEFAULT_DISPLAY` by `MediaProjectionConfig.createConfigForUserChoice()`.
- User-selected app-window capture: Android 14 QPR2+ app screen sharing can capture a single app window and excludes status bar, navigation bar, notifications, and other system UI.

The following are not officially established as capturable by a normal third-party Fold6 app:

- Entire Samsung DeX desktop on an external monitor/TV.
- Samsung DeX external display as a non-default display target.
- A Samsung DeX app window running on the external DeX display.
- Samsung's internal DeX renderer or Miracast/HDMI output frames.

If real-device testing later proves DeX app-window capture works, the expected captured surface should be only the selected app's content, not the whole DeX desktop, DeX taskbar, other app windows, Samsung system UI, or protected `FLAG_SECURE` content.

## evidence from official docs and/or real-device test plan

Official evidence:

- Android's MediaProjection guide says MediaProjection captures "a device display or app window" into a virtual display rendered on an app-provided `Surface`, and Android 14 app screen sharing shares a single app window while excluding system UI ([Android Media projection](https://developer.android.com/media/grow/media-projection)).
- Android's app screen sharing doc says MediaProjection apps are automatically capable of app screen sharing, but only the selected app content is shared and apps still need testing to confirm intended behavior ([Android app screen sharing](https://developer.android.com/about/versions/14/features/app-screen-sharing)).
- `MediaProjectionConfig.createConfigForDefaultDisplay()` restricts capture to the default display. `createConfigForUserChoice()` lets the user choose a region, but if the user selects display capture, only `Display.DEFAULT_DISPLAY` is supported ([MediaProjectionConfig API](https://developer.android.com/reference/android/media/projection/MediaProjectionConfig)).
- Android connected-display docs say a phone connected to a display keeps the phone state unchanged while a blank desktop session starts on the connected display; the device and display act as individual systems with apps specific to each display ([Android support connected displays](https://developer.android.com/develop/adaptive-apps/guides/support-connected-displays)).
- Samsung says DeX is an extension of Android multi-window and that no proprietary Samsung APIs are needed to launch apps in DeX; this is app runtime compatibility guidance, not capture API documentation ([Samsung DeX how it works](https://developer.samsung.com/samsung-dex/how-it-works.html)).
- Samsung support documents DeX as connecting a phone/tablet to a monitor, TV, or PC for desktop mode, including switching a connected display from mirroring to DeX and using wireless DeX with Miracast-capable TVs/monitors ([Samsung DeX support](https://www.samsung.com/us/support/answer/ANS10001955/)).
- Samsung's DeX product page lists Galaxy Z Fold6 as DeX-compatible and describes external monitor/workspace behavior, but still does not document a third-party DeX frame-capture API ([Samsung DeX apps and services](https://www.samsung.com/ca/apps/samsung-dex/)).

Real-device test plan before reconsidering MVP:

1. On the target Fold6 firmware, run a minimal third-party capture app using `MediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())`.
2. Start wired DeX to an external monitor/TV. Repeat with wireless DeX if it is a supported target mode.
3. Test whole-display capture while DeX is active. Expected from docs: Fold6 default display only, not DeX external display.
4. Launch a known resizable test app on the DeX external display. Check whether Android's consent picker lists that DeX app window.
5. If the picker lists it, select it and verify captured frames show only that app window, with correct size, no system UI, and no other DeX windows.
6. Move the app between phone display and DeX display if Samsung allows it, then verify whether capture resizes, stops, or follows the window.
7. Test disconnect/reconnect, phone lock/screen off, orientation/fold-state changes, `FLAG_SECURE` content, and five-minute sustained capture.
8. Treat the path as MVP-eligible only if it works without ADB, hidden permissions, Samsung partner signing, internal packages, or undocumented broadcasts.

## risks

- The public Android API explicitly limits display capture to the default display, so full DeX desktop/external-display capture is not a supportable MVP assumption.
- Android app-window capture might not expose DeX external-display windows in the picker; official docs do not promise it.
- Samsung DeX behavior varies by device, One UI version, wired/wireless mode, and external-display path.
- Capture can stop or resize during lock, display disconnect, app close, app move, fold-state change, or consent/session changes.
- Protected content can be blanked or excluded by Android security rules.
- Even if a DeX app-window test succeeds on one Fold6 build, it may be too device-specific to make the primary ScopeX MVP architecture depend on it.

## recommended ScopeX decision

Do not use DeX capture for ScopeX MVP.

Keep the MVP capture source as the unlocked Fold6 main display via normal user-consented MediaProjection, then WebRTC video to Rokid. Track DeX capture as a separate hardware spike only. Reopen the MVP decision only if the exact target Fold6 proves, through the test plan above, that public MediaProjection can reliably capture the intended DeX app/window surface without ADB, privileged APIs, or undocumented Samsung behavior.
