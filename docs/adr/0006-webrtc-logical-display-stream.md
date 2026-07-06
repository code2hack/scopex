# Use WebRTC for the logical-display stream

The production-shaped MVP sends the logical-display stream from the Fold6 companion app to the Rokid ScopeX renderer over WebRTC, while Bluetooth is used only for pairing/bootstrap over an existing OS-level device bond that ScopeX does not own or close. This rejects a simpler TCP/TLS video socket because ScopeX needs the lowest practical latency path for head-motion inspection and future crosshair interaction, and WebRTC provides real-time video transport, congestion behavior, jitter handling, and data channels without making ScopeX own a custom UDP/RTP stack.

The MVP keeps WebRTC local-only: Bluetooth carries the offer/answer bootstrap, no internet signaling server or STUN/TURN service is required, and failed local connectivity is a visible retryable error. ScopeX must not silently fall back to ADB, cloud relay, TCP video, or undocumented Rokid capture paths.
