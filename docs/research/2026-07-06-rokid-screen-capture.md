# Rokid Screen Capture Research

Date: 2026-07-06

## Summary

Android's normal, supportable baseline for ScopeX remains `MediaProjection`: a user-consented session launched through `MediaProjectionManager.createScreenCaptureIntent()`, feeding a `Surface` via `createVirtualDisplay()` for real-time use, recording, or casting ([Android Media projection](https://developer.android.com/media/grow/media-projection), [MediaProjection API](https://developer.android.com/reference/android/media/projection/MediaProjection)). Android requires user consent for each projection session, and target Android 14+ apps need the `mediaProjection` foreground-service type ([Android Media projection](https://developer.android.com/media/grow/media-projection)).

I did not find an official Rokid public SDK that gives third-party apps raw, general-purpose screen frames from Rokid RG-glasses when the standard Android MediaProjection consent activity is unavailable. Official Rokid docs cover first-person camera/media capture, phone-side live preview of glasses camera/audio, ADB/scrcpy-style debug projection, enterprise GB/RTSP first-person video streaming, and an enterprise remote-collaboration SDK with in-meeting screen sharing, but those are not documented as a general local screen-capture API for ScopeX ([Glass SDK capture / recording / AI](https://x-docs.rokid.com/docs/en/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/30-media/01-%E7%9C%BC%E9%95%9C%E7%AB%AF-SDK-%E6%8B%8D%E7%85%A7%E5%BD%95%E5%83%8F%E5%BD%95%E9%9F%B3%E4%B8%8E-AI.html), [Live video preview](https://x-docs.rokid.com/docs/en/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/20-message-transfer/06-%E5%AE%9E%E6%97%B6%E8%A7%86%E9%A2%91%E9%A2%84%E8%A7%88.html), [GB/RTSP Streaming Integration](https://x-docs.rokid.com/docs/en/scenario-guides/%E5%9B%BD%E6%A0%87RTSP%E6%8E%A8%E6%B5%81%E6%8E%A5%E5%85%A5.html), [Wired/Wireless Projection Tools](https://x-docs.rokid.com/docs/en/terminal-sdk/resources/%E6%9C%89%E7%BA%BF%E6%97%A0%E7%BA%BF%E6%8A%95%E5%B1%8F.html), [Remote Collaboration SDK for Android](https://x-docs.rokid.com/docs/en/remote-collaboration-sdk/android.html)).

Community reverse-engineering reports a system package named `com.rokid.os.master.screenstream` that can record or stream the glasses screen, matching one locally observed package clue. That evidence is explicitly community-maintained and not endorsed by Rokid, and it describes system-only privileges such as `android.uid.system` and `MANAGE_MEDIA_PROJECTION`, so it should not be treated as a usable third-party ScopeX API ([community evidence: rokid-docs README](https://github.com/buildwithfenna/rokid-docs), [community evidence: RokidScreenRecord](https://github.com/buildwithfenna/rokid-docs/blob/main/yodaos/docs/apps/screen-record.md)). Given ScopeX's explicit-consent/privacy requirements, the current visible failure state, `Screen capture consent is unavailable on this device`, is the right product behavior until Rokid documents a consentful API or provides a supported integration path.

## Official Findings

### Android MediaProjection baseline

`MediaProjection` is designed to capture a device display or app window into an app-provided `Surface`, which can be consumed by `MediaRecorder`, `SurfaceTexture`, or `ImageReader` for recording, casting, or real-time image handling ([Android Media projection](https://developer.android.com/media/grow/media-projection)). A session starts by launching the intent returned by `createScreenCaptureIntent()` and then calling `getMediaProjection()` from the activity result ([Android Media projection](https://developer.android.com/media/grow/media-projection)).

Android's privacy model is session-based: apps must request user consent before each media-projection session, and Android 14+ enforces one-time token and one-`createVirtualDisplay()` semantics for each consent result ([Android Media projection](https://developer.android.com/media/grow/media-projection)). Android also exposes user/system stop paths for projection sessions, so ScopeX should keep releasing capture resources when `MediaProjection.Callback.onStop()` fires ([Android Media projection](https://developer.android.com/media/grow/media-projection), [MediaProjection API](https://developer.android.com/reference/android/media/projection/MediaProjection)).

For target Android 14+, Android requires the `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission and a service declaring `android:foregroundServiceType="mediaProjection"` ([Android Media projection](https://developer.android.com/media/grow/media-projection)). That aligns with ScopeX ADR 0005's Activity-bound foreground service for the logical-display capture proof.

The system permission `android.permission.MANAGE_MEDIA_PROJECTION` is a signature permission intended for system apps, not normal third-party apps ([AOSP AndroidManifest](https://android.googlesource.com/platform/frameworks/base/+/android12-release/core/res/AndroidManifest.xml)). This matters because Rokid's apparent internal recorder can plausibly work in ways ScopeX cannot, unless Rokid signs/whitelists ScopeX or exposes a supported API.

Android's secure-window model is also relevant: `FLAG_SECURE` prevents screenshots and showing a protected window on non-secure displays such as casting targets ([Secure sensitive activities](https://developer.android.com/security/fraud-prevention/activities)). ScopeX should continue to respect platform capture exclusions and should not attempt to bypass secure content.

### Rokid official documentation

Rokid's public product page describes Rokid Glasses as display-equipped AI glasses with a Micro-LED display, a first-person 12MP camera, voice/touch/button controls, and video recording battery life around 45 minutes ([Rokid Academy](https://global.rokid.com/pages/academy)). This is evidence for first-person camera capture and onboard user features, not for third-party screen capture of arbitrary app display frames.

Rokid Sprite Enterprise docs appear to distinguish enterprise/work systems from consumer systems and say consumer and enterprise stacks use different apps/workflows ([Glass3 SDK FAQ](https://x-docs.rokid.com/docs/en/faq/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98.html)). Treat these docs as high-trust for the documented Glass3/Sprite Enterprise stack, but do not assume every RG-glasses consumer firmware exposes the same SDK behavior.

Rokid's "Glass SDK capture / recording / AI" sample documents still capture, MP4 pipelines, H264 feeder/teardown, optical zoom intents, microphone AAC capture, and AI-chat wiring, with representative calls such as `takePhoto(...)`, `startRecord(...)`, `stopRecord()`, and `sendVideoStreamDataV2(...)` ([Glass SDK capture / recording / AI](https://x-docs.rokid.com/docs/en/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/30-media/01-%E7%9C%BC%E9%95%9C%E7%AB%AF-SDK-%E6%8B%8D%E7%85%A7%E5%BD%95%E5%83%8F%E5%BD%95%E9%9F%B3%E4%B8%8E-AI.html)). The caveat section says recordings persist under shared gallery paths, which conflicts with ScopeX's default "no frame persistence" posture unless gated behind explicit debug/user workflows ([Glass SDK capture / recording / AI](https://x-docs.rokid.com/docs/en/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/30-media/01-%E7%9C%BC%E9%95%9C%E7%AB%AF-SDK-%E6%8B%8D%E7%85%A7%E5%BD%95%E5%83%8F%E5%BD%95%E9%9F%B3%E4%B8%8E-AI.html)).

Rokid's "Live video preview" sample is phone-side and describes the phone requesting synchronized video and audio streams from the glasses, receiving RAW NV21 planes, and rendering them locally ([Live video preview](https://x-docs.rokid.com/docs/en/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/20-message-transfer/06-%E5%AE%9E%E6%97%B6%E8%A7%86%E9%A2%91%E9%A2%84%E8%A7%88.html)). This is useful evidence for camera/audio streaming, not capture of the glasses display or a logical display.

Rokid's GB/RTSP streaming guide describes associating a Glass3 device with a Lingmou/GB platform, starting on-demand streaming, and consuming an RTSP URL such as `rtsp://<RTSP_HOST>:<RTSP_PORT>/rtp/<GB_ID>_<GB_ID>` after platform playback is active ([GB/RTSP Streaming Integration](https://x-docs.rokid.com/docs/en/scenario-guides/%E5%9B%BD%E6%A0%87RTSP%E6%8E%A8%E6%B5%81%E6%8E%A5%E5%85%A5.html)). The stated scenarios are first-person monitoring, dispatch, emergency response, and downstream video-service integration; this is a platform-mediated video-monitoring path, not documented local display capture for a glasses-side third-party app ([GB/RTSP Streaming Integration](https://x-docs.rokid.com/docs/en/scenario-guides/%E5%9B%BD%E6%A0%87RTSP%E6%8E%A8%E6%B5%81%E6%8E%A5%E5%85%A5.html)).

Rokid's official projection-tools page recommends RokidMirror and `scrcpy` to project the glasses screen to a computer for debugging, demos, troubleshooting pages, permission dialogs, and runtime state ([Wired/Wireless Projection Tools](https://x-docs.rokid.com/docs/en/terminal-sdk/resources/%E6%9C%89%E7%BA%BF%E6%97%A0%E7%BA%BF%E6%8A%95%E5%B1%8F.html)). This is a developer/debug path requiring ADB, cable or network reachability, and external tooling. It is not a third-party runtime capture API for ScopeX.

Rokid's Remote Collaboration SDK for Android documents in-meeting local device camera/audio controls, `configScreenShareProperty(...)`, `share.startScreenShare(screenShareParam)`, `share.stopScreenShare()`, and server-side meeting recording APIs ([Remote Collaboration SDK for Android](https://x-docs.rokid.com/docs/en/remote-collaboration-sdk/android.html)). The Web SDK similarly documents browser screen sharing and explicitly depends on browser media permissions and secure contexts ([Remote Collaboration SDK for Web](https://x-docs.rokid.com/docs/en/remote-collaboration-sdk/web.html)). These docs prove Rokid has a first-party collaboration product surface with screen sharing, but they do not document raw local frame access, standalone local capture, or a ScopeX-compatible consent flow.

I did not find official Rokid documentation for a public third-party API corresponding to the local package clues `com.rokid.os.master.screenstream`, `com.rokid.os.sprite.record`, `com.rokid.os.sprite.live`, `com.rokid.sysconfig`, or `com.rokid.os.sprite.launcher`. Those package names remain unverified implementation clues, not API evidence.

## Community Findings

Community evidence: `buildwithfenna/rokid-docs` states it is community-maintained, reverse-engineered, and not affiliated with or endorsed by Rokid ([community evidence: rokid-docs README](https://github.com/buildwithfenna/rokid-docs)). Its `RokidScreenRecord` page describes a system app at `com.rokid.os.master.screenstream` for screen recording and live FLV streaming, with shared UID `android.uid.system`, `MANAGE_MEDIA_PROJECTION`, `SYSTEM_ALERT_WINDOW`, microphone capture, HTTP serving on port 8080, broadcast actions for `SCREENRECORD_ON` and `SCREENRECORD_OFF`, and MP4/FLV output paths ([community evidence: RokidScreenRecord](https://github.com/buildwithfenna/rokid-docs/blob/main/yodaos/docs/apps/screen-record.md)).

That reverse-engineered package is important because it matches the locally observed `com.rokid.os.master.screenstream` clue. It is also high-risk for ScopeX as a dependency: it appears to rely on system-only privileges, can persist recordings, includes microphone capture, and exposes streaming/recording mechanics not backed by a public Rokid API or documented consent contract ([community evidence: RokidScreenRecord](https://github.com/buildwithfenna/rokid-docs/blob/main/yodaos/docs/apps/screen-record.md), [AOSP AndroidManifest](https://android.googlesource.com/platform/frameworks/base/+/android12-release/core/res/AndroidManifest.xml)).

Community evidence: Rokid subreddit users describe built-in "AR Recording" and "AR Capture" user flows that mix first-person video/photos with the HUD, and mention physical button recording shortcuts ([community evidence: Reddit - How do you film what you see?](https://www.reddit.com/r/rokid_official/comments/1pxu5sk/how_do_you_film_what_you_see/)). This suggests a user-facing capture feature exists on some Rokid glasses firmware, but it does not establish a third-party app API or a ScopeX-safe consent model.

Community evidence: a CXR-M developer reports that the raw camera stream does not include the overlay, while a glasses screen recording only captures the WebView content without the camera passthrough ([community evidence: Reddit - CXR-M HUD-overlaid POV capture](https://www.reddit.com/r/rokid_official/comments/1tz5kdt/rokid_cxrm_development_capturing_hudoverlaid_pov/)). This is not official, but it reinforces that "camera feed", "HUD overlay", and "screen recording" are different capture planes on Rokid devices.

Community evidence: another Rokid-focused project, Rokid Live Studio, streams the glasses camera and microphone through a glasses helper plus phone app to YouTube/Twitch ([community evidence: Rokid Live Studio](https://github.com/Anezium/Rokid-Live-Studio)). This is useful for understanding ecosystem experiments, but it is camera/mic streaming, not logical-display capture.

Access limitation: the Rokid Developer Forum page surfaced in search for a camera-feed capture question, but direct access in this environment returned only the JavaScript app shell and no readable post body. No claims from that forum page are used here ([Rokid Developer Forum URL attempted](https://developer-forum.rokid.com/post/detail/2063)).

## Implications For ScopeX

ScopeX should keep the current behavior on tested Rokid RG-glasses: if `MediaProjectionManager.createScreenCaptureIntent()` cannot be launched or resolved, fail visibly with `Screen capture consent is unavailable on this device`. That preserves the Android consent contract and avoids normalizing hidden/system capture.

Do not build production ScopeX capture on undocumented Rokid broadcasts, system properties, port-8080 FLV streams, `MANAGE_MEDIA_PROJECTION`, or internal packages unless Rokid provides a public API/contract and a user-visible consent path. The community-described `screenstream` path looks like a system recorder, not a third-party SDK. It can persist frames and capture audio, which directly conflicts with ScopeX's default privacy posture.

The official Rokid surfaces worth follow-up are narrow:

- Ask Rokid whether RG-glasses firmware intentionally removes or hides Android's standard MediaProjection consent UI for third-party apps.
- Ask whether the Remote Collaboration Android SDK's `startScreenShare(...)` can run on the relevant glasses model without cloud recording, expose a local frame `Surface` or stream, and present explicit user consent suitable for ScopeX.
- Ask whether GB/RTSP streaming can be enabled outside the Lingmou enterprise monitoring workflow, whether it captures camera view or display/HUD content, and whether it can satisfy ScopeX's no-persistence, user-consent, and local-frame requirements.
- Ask whether there is an official enterprise entitlement, signed-partner API, or allowlist that enables consentful screen capture without relying on internal package broadcasts.
- Use RokidMirror/scrcpy only for development verification and demos, not product capture.

Phone-side capture remains the portable path for normal Android devices, as PR #10 already showed on Fold6. Rokid glasses may need either a vendor-supported capture adapter or a product limitation note that logical-display capture is unavailable on this hardware/firmware.

## Open Questions

- Which exact Rokid model, firmware build, and enterprise/consumer stack was tested? The official Sprite Enterprise docs may not apply to every RG-glasses consumer build.
- Is the Android MediaProjection consent activity absent, non-exported, disabled by policy, or blocked by launcher/windowing constraints on the tested Rokid build?
- Does Rokid have an official, documented API for `com.rokid.os.sprite.record` or `com.rokid.os.sprite.live`, or are they user-facing/private apps only?
- Can Remote Collaboration screen sharing be used without uploading media to Rokid services, without server recording, and without retaining frames?
- Does any Rokid-supported path capture only app/display content while excluding ScopeX overlay, microphone audio, camera passthrough, and persisted files?
- What user-visible privacy indicators does Rokid require for screen sharing or screen recording on glasses?

## Sources

Official/high-trust sources:

- [Android Media projection guide](https://developer.android.com/media/grow/media-projection)
- [Android MediaProjection API reference](https://developer.android.com/reference/android/media/projection/MediaProjection)
- [AOSP AndroidManifest permission definitions](https://android.googlesource.com/platform/frameworks/base/+/android12-release/core/res/AndroidManifest.xml)
- [Android Secure sensitive activities / FLAG_SECURE](https://developer.android.com/security/fraud-prevention/activities)
- [Rokid Academy product page](https://global.rokid.com/pages/academy)
- [Rokid Sprite Enterprise - Glass3 SDK FAQ](https://x-docs.rokid.com/docs/en/faq/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98.html)
- [Rokid Sprite Enterprise - Glass SDK capture / recording / AI](https://x-docs.rokid.com/docs/en/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/30-media/01-%E7%9C%BC%E9%95%9C%E7%AB%AF-SDK-%E6%8B%8D%E7%85%A7%E5%BD%95%E5%83%8F%E5%BD%95%E9%9F%B3%E4%B8%8E-AI.html)
- [Rokid Sprite Enterprise - Live video preview](https://x-docs.rokid.com/docs/en/%E4%BB%A3%E7%A0%81%E7%A4%BA%E4%BE%8B/20-message-transfer/06-%E5%AE%9E%E6%97%B6%E8%A7%86%E9%A2%91%E9%A2%84%E8%A7%88.html)
- [Rokid Sprite Enterprise - GB/RTSP Streaming Integration](https://x-docs.rokid.com/docs/en/scenario-guides/%E5%9B%BD%E6%A0%87RTSP%E6%8E%A8%E6%B5%81%E6%8E%A5%E5%85%A5.html)
- [Rokid Sprite Enterprise - Wired/Wireless Projection Tools](https://x-docs.rokid.com/docs/en/terminal-sdk/resources/%E6%9C%89%E7%BA%BF%E6%97%A0%E7%BA%BF%E6%8A%95%E5%B1%8F.html)
- [Rokid Sprite Enterprise - Remote Collaboration SDK for Android](https://x-docs.rokid.com/docs/en/remote-collaboration-sdk/android.html)
- [Rokid Sprite Enterprise - Remote Collaboration SDK for Web](https://x-docs.rokid.com/docs/en/remote-collaboration-sdk/web.html)

Community evidence:

- [buildwithfenna/rokid-docs README](https://github.com/buildwithfenna/rokid-docs)
- [buildwithfenna/rokid-docs - RokidScreenRecord](https://github.com/buildwithfenna/rokid-docs/blob/main/yodaos/docs/apps/screen-record.md)
- [Reddit - How do you film what you see?](https://www.reddit.com/r/rokid_official/comments/1pxu5sk/how_do_you_film_what_you_see/)
- [Reddit - Rokid CXR-M Development: Capturing HUD-overlaid POV video](https://www.reddit.com/r/rokid_official/comments/1tz5kdt/rokid_cxrm_development_capturing_hudoverlaid_pov/)
- [Anezium/Rokid-Live-Studio](https://github.com/Anezium/Rokid-Live-Studio)
- [Rokid Developer Forum URL attempted but not readable in this environment](https://developer-forum.rokid.com/post/detail/2063)
