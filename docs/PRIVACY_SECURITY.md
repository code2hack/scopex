# Privacy And Security

scopex handles screen capture, so capture must remain explicit and user-controlled.

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

If protected content appears blank, treat that as an expected Android platform boundary.
