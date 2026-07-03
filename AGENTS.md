Skill priority for this project:

1. User instructions, repository conventions, security, validation, accessibility, data safety, and explicit requirements win.
2. Superpowers owns production delivery:
   - brainstorming/spec approval
   - implementation planning
   - worktree setup
   - TDD
   - execution
   - code review
   - verification
   - branch finishing
3. Matt Pocock skills own preparation and workflow routing:
   - /setup-matt-pocock-skills for repo setup
   - /ask-matt when unsure which Matt flow fits
   - /grill-with-docs for requirement/domain clarification
   - /domain-modeling for glossary and ADR updates
   - /codebase-design for module/interface/seam decisions
   - /improve-codebase-architecture for codebase-health scans
   - /to-prd and /to-issues for backlog creation
   - /triage for incoming external issues/PRs
   - /handoff for fresh-session transfer
   - /prototype for throwaway design exploration
4. Ponytail governs implementation shape:
   - reuse existing code before writing new code
   - stdlib/native features before dependencies
   - shortest correct diff
   - no speculative abstractions
   - no scaffolding “for later”
5. Ponytail must not be used to skip Superpowers gates, tests, root-cause investigation, security, validation, accessibility, or required documentation.
6. Matt’s preparation artifacts feed Superpowers; they do not replace Superpowers execution for production code.

## Agent skills

### Issue tracker

Issues and PRDs are tracked in GitHub Issues; external PRs are not a triage request surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default Matt Pocock skill label vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repo with root `CONTEXT.md` and `docs/adr/`. See `docs/agents/domain.md`.
