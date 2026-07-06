Skill priority for this project:

1. User instructions, repository conventions, security, validation, accessibility, data safety, and explicit requirements win.
2. Matt Pocock skills are the primary workflow harness:
   - /setup-matt-pocock-skills for repo setup
   - /ask-matt when unsure which Matt flow fits
   - /grill-with-docs for requirement/domain clarification
   - /domain-modeling for glossary and ADR updates
   - /codebase-design for module/interface/seam decisions
   - /improve-codebase-architecture for codebase-health scans
   - /to-prd and /to-issues for backlog creation
   - /triage for incoming external issues/PRs
   - /research for cited source investigation
   - /diagnosing-bugs for root-cause investigation
   - /tdd for test-first implementation at agreed seams
   - /implement for production execution from issues/PRDs
   - /code-review for standards/spec review
   - /handoff for fresh-session transfer
   - /prototype for throwaway design exploration
3. Ponytail governs implementation shape:
   - reuse existing code before writing new code
   - stdlib/native features before dependencies
   - shortest correct diff
   - no speculative abstractions
   - no scaffolding “for later”
4. Ponytail must not be used to skip Matt workflow gates, tests, root-cause investigation, security, validation, accessibility, or required documentation.
5. Matt preparation artifacts must feed implementation, verification, review, and handoff; they do not replace production execution for production code.

## Sub-agent authorization

You may spawn sub-agents implicitly whenever a selected skill, workflow, or engineering judgment calls for parallel review, exploration, verification, or implementation. Do not ask me each time.

## Agent skills

### Issue tracker

Issues and PRDs are tracked in GitHub Issues; external PRs are not a triage request surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default Matt Pocock skill label vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repo with root `CONTEXT.md` and `docs/adr/`. See `docs/agents/domain.md`.

### Handoff file

Use root `HANDOFF.md` as the single handoff file for this worktree/branch. Update or replace that file for fresh-session transfer; do not create handoff files under `/tmp` or other locations unless explicitly requested.
