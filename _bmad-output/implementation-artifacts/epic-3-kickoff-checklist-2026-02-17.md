# Epic 3 Kickoff Checklist

Date: 2026-02-17
Epic: 3
Source Context: `epic-2-retro-2026-02-17.md`

## Status Legend
- `pending`: Not started
- `in-progress`: Actively being worked
- `done`: Complete and evidence captured

## Kickoff Gates (Critical Path)

| ID | Story Key | Gate | Owner | Assigned Date | Status | Evidence / Notes |
|---|---|---|---|---|---|---|
| E3-G1 | `3-01-enforce-single-status-authority-change-log-discipline` | Single status authority + change-log discipline enforced in active story artifacts | Bob (SM), Alice (PO), Dana (QA) | 2026-02-17 | pending | Enforce top-level `Status:` as only status source. Change Log records events only (no status-state encoding). Evidence: checklist updates + example corrected story artifacts. |
| E3-G2 | `3-02-scoped-refactor-for-context-bloat-reduction-in-high-churn-files` | Scoped refactor completed for high-churn large files (context-bloat reduction) | Charlie (Dev) | 2026-02-17 | pending | Deliver bounded refactor plan and implementation with clearer module/file boundaries and reduced review surface. Evidence: refactor scope artifact + code/test diffs + review notes. |
| E3-G3 | `3-03-fix-grouped-track-selectorless-get-track-details-mapping-regression-coverage` | `get_track_details` grouped-track selectorless mapping bug fixed + regression coverage | Charlie (Dev), Dana (QA) | 2026-02-17 | pending | Fix selectorless grouped-track mapping issue; add targeted regression tests for grouped parent and grouped child selection cases. Evidence: passing targeted tests + regression logs + story references. |
| E3-G4 | `3-04-epic-2-runtime-ac-functional-runsheet-evidence-execution` | Epic 2 runtime-AC functional runsheet created and executed with evidence | Dana (QA), Bob (SM) | 2026-02-17 | pending | Build step-by-step runsheet with prompts for each runtime-related AC across Epic 2 stories, execute it, and capture pass/fail evidence package. |
| E3-G5 | `3-05-record-formal-stakeholder-acceptance-for-epic-2` | Formal stakeholder acceptance for Epic 2 recorded | Alice (PO), Josh (Project Lead) | 2026-02-17 | pending | Document stakeholder decision and acceptance notes in implementation artifacts; link evidence and any accepted caveats. |

## Planning Items (Per Gate)

### E3-G1 Planning Items
1. Publish artifact policy: top-level `Status:` is authoritative.
2. Update review checklist to include `single-status-authority`.
3. Audit active Epic 2/3 stories for status-state duplication in logs/sections.

### E3-G2 Planning Items
1. Identify high-churn oversized files and refactor boundaries.
2. Split by responsibility with no behavior drift.
3. Validate with targeted and full regression checks before closeout.

### E3-G3 Planning Items
1. Reproduce grouped-track selectorless mismatch in a controlled test case.
2. Implement resolver/index mapping correction.
3. Add regression tests for grouped parent/child selected-track paths.

### E3-G4 Planning Items
1. Create runtime-AC matrix for Epic 2 stories.
2. Build execution runsheet with explicit prompts and expected outcomes.
3. Execute end-to-end and store evidence logs/artifacts.

### E3-G5 Planning Items
1. Schedule stakeholder acceptance review.
2. Present evidence package (runtime AC verification + defect status).
3. Record final acceptance decision and constraints.

## Exit Criteria for Epic 3 Kickoff

Epic 3 can move from `backlog` to `in-progress` only when:
1. All gates `E3-G1..E3-G5` are marked `done`
2. Evidence is documented for each gate in this file
3. Any defects discovered during runsheet execution are either resolved or explicitly accepted
4. Stakeholder acceptance for Epic 2 is explicitly recorded

## Update Protocol

When progress changes:
1. Update `Status` (`pending` -> `in-progress` -> `done`)
2. Add date-stamped evidence in `Evidence / Notes`
3. Keep this file synchronized with `_bmad-output/implementation-artifacts/sprint-status.yaml`
