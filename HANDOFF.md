# ScopeX Handoff

Last updated: 2026-07-06

## Branch

- Repo: `/home/code2hack/Projects/glasses/scopex`
- Branch: `logical-display-capture-proof`
- PR: https://github.com/code2hack/scopex/pull/10

## Current State

- PR #10 implements the logical-display capture proof.
- Standard Android `MediaProjection` worked on Fold6 validation.
- Tested Rokid RG-glasses firmware did not expose a launchable standard Android MediaProjection consent activity, so ScopeX now fails visibly with `Screen capture consent is unavailable on this device`.
- User now expects PR #10 to be closed as the production direction because Rokid-side MediaProjection is blocked on the tested firmware.
- User tested connecting Rokid to Fold6 over USB-C and found the Fold6 can debug the Rokid device through ADB.
- A new experimental direction is under consideration: Fold6 captures the phone display with normal Fold6-side MediaProjection consent, then streams frames to ScopeX on Rokid for rendering into the padded logical display. ADB may be used as an experimental bootstrap/transport path.
- Important answer already resolved: this phone-origin path does not require Rokid-side MediaProjection consent unless the Rokid app tries to capture the Rokid display itself. The Fold6 needs MediaProjection consent; Rokid only receives and renders the frame stream.
- The Superpowers Codex plugin was removed from local Codex config/cache in favor of Matt Pocock skills plus Ponytail as the project harness stack.
- `AGENTS.md` now directs fresh sessions to use Matt skills for workflow routing, implementation, review, research, and handoff, with Ponytail governing implementation shape.

## Active Artifacts

- Capture proof design: `docs/superpowers/specs/2026-07-05-logical-display-capture-proof-design.md`
- Capture proof plan: `docs/superpowers/plans/2026-07-05-logical-display-capture-proof.md`
- Capture service ADR: `docs/adr/0005-activity-bound-capture-service.md`
- Rokid capture research note: `docs/research/2026-07-06-rokid-screen-capture.md`

The `docs/superpowers/` directory contains historical plan/spec artifacts. It is no longer a workflow authority.

## Recent Research Conclusion

The Rokid research note found no official public Rokid API that gives third-party apps raw local display frames when Android MediaProjection consent is unavailable. Official Rokid paths cover camera/media capture, phone-side preview, GB/RTSP enterprise video streaming, debug projection tools, and remote-collaboration screen sharing. Community reports of `com.rokid.os.master.screenstream` are reverse-engineered and system-privileged, so they are not production API evidence for ScopeX.

## Next Useful Actions

- Start a `/grill-with-docs` session for the experimental Fold6-to-Rokid ADB/streaming path.
- During grilling, decide the capture source, transport shape, frame codec/protocol, ScopeX renderer seam, and what "good enough experimental app" means.
- After grilling, create a new PRD for the phone-origin logical-display streaming path.
- Close PR #10 as the old Rokid-side MediaProjection proof direction, while preserving its reusable layout/view findings and research context.
- Keep the current visible Rokid-side MediaProjection failure behavior unless Rokid documents a supported API/contract.

## Suggested Skills

- `/research` for further vendor or Android source investigation.
- `/domain-modeling` if new Rokid-specific product terms become stable.
- `/grill-with-docs` next, focused on the experimental Fold6-to-Rokid ADB/streaming architecture.
- `/to-prd` and `/to-issues` after a supported implementation path is clear.
- `/implement`, `/tdd`, and `/code-review` for scoped production follow-up work.
