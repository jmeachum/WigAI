# Epic 2 Kickoff Checklist

Date: 2026-02-14  
Epic: 2  
Source Context: `epic-1-retro-2026-02-13.md`

## Status Legend
- `pending`: Not started
- `in-progress`: Actively being worked
- `done`: Complete and evidence captured

## Kickoff Gates

| ID | Gate | Owner | Status | Evidence / Notes |
|---|---|---|---|---|
| G1 | Contract Semantics DoR and Contract Lockstep gate active | Bob (SM), Dana (QA), Charlie (Dev) | pending | Add DoR contract section to Epic 2 stories and enforce runtime/test/docs lockstep before review |
| G2 | Duplicate-track-name ambiguity behavior defined and tested | Charlie (Dev), Dana (QA) | pending | Must guarantee ambiguous names never trigger implicit mutating actions |
| G3 | Epic 1 smoke test executed with evidence | Dana (QA) | pending | Include safe + mutating flow evidence |
| G4 | Epic 1 deployment completed and post-deploy smoke verified | Charlie (Dev), Dana (QA) | pending | Record deployment confirmation and verification results |
| G5 | Stakeholder acceptance for Epic 1 recorded | Alice (PO), Josh (Project Lead) | pending | Capture acceptance decision and date |
| G6 | Dependency/version refresh checkpoint from Story 1.6 and 1.7 completed | Charlie (Dev) | pending | Confirm dependency updates and versions per Story 1.6/1.7 notes |

## Additional Planning Item (From Retro Discussion)

| ID | Item | Owner | Priority | Status | Notes |
|---|---|---|---|---|---|
| P1 | Epic 1 full functional run sheet and smoke test procedure | Bob (SM), Dana (QA) | High | pending | Produce step-by-step instructions for all Epic 1 functionality |

## Exit Criteria for Epic 2 Kickoff

Epic 2 can move from `backlog` to `in-progress` only when:
1. All gates `G1..G6` are marked `done`
2. Evidence is documented for each gate in this file
3. Stakeholder acceptance for Epic 1 is explicitly recorded

## Update Protocol

When progress changes:
1. Update `Status` (`pending` -> `in-progress` -> `done`)
2. Add date-stamped evidence in `Evidence / Notes`
3. Keep this file synchronized with `_bmad-output/implementation-artifacts/sprint-status.yaml`