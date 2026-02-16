# Epic 2 Kickoff Checklist

Date: 2026-02-14  
Epic: 2  
Source Context: `epic-1-retro-2026-02-13.md`

## Status Legend
- `pending`: Not started
- `in-progress`: Actively being worked
- `done`: Complete and evidence captured

## Kickoff Gates

| ID | Gate | Owner | Assigned Date | Status | Evidence / Notes |
|---|---|---|---|---|---|
| G1 | Contract Semantics DoR and Contract Lockstep gate active | Bob (SM), Dana (QA), Charlie (Dev) | 2026-02-14 | pending | Add DoR contract section to Epic 2 stories and enforce runtime/test/docs lockstep before review |
| G2 | Duplicate-track-name ambiguity behavior defined and tested | Charlie (Dev), Dana (QA) | 2026-02-14 | pending | Must guarantee ambiguous names never trigger implicit mutating actions |
| G3 | Epic 1 smoke test executed with evidence | Dana (QA) | 2026-02-14 | done | Completed 2026-02-14 with strengthened validation after transport-stop concern. Harness/run sheet now require state verification (`status.transport.playing` true after start, false after stop). Evidence: Step 1 pass (`tests=175, failures=0, errors=0, skipped=0`) `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/01-epic1-targeted-tests.log`; Step 2 safe smoke PASS `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/02-safe-smoke.log`; Step 3 mutation smoke PASS with state checks (`start` attempt 1 true, `stop` attempt 2 false) `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/03-mutation-smoke.log`; Step 4 optional host timing stress PASS via `./gradlew mcpTimingStressTest` (`Passed: 15 | Failed: 0`) `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/04-timing-stress.log`. |
| G4 | Epic 1 deployment completed and post-deploy smoke verified | Charlie (Dev), Dana (QA) | 2026-02-14 | done | Completed 2026-02-14. Per project lead confirmation, deployment + post-deploy verification were executed in the same execution window captured under G3 evidence. Reused evidence set: `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/01-epic1-targeted-tests.log`, `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/02-safe-smoke.log`, `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/03-mutation-smoke.log`, `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/04-timing-stress.log`. |
| G5 | Stakeholder acceptance for Epic 1 recorded | Alice (PO), Josh (Project Lead) | 2026-02-14 | done | Accepted on 2026-02-16 by stakeholder direction from Josh (Project Lead). Scope accepted: Epic 1 (Stories 1.1-1.7), based on retrospective completion and G3/G4 evidence set. |
| G6 | Dependency/version refresh checkpoint from Story 1.6 and 1.7 completed | Charlie (Dev) | 2026-02-14 | pending | Confirm dependency updates and versions per Story 1.6/1.7 notes |

## Additional Planning Item (From Retro Discussion)

| ID | Item | Owner | Assigned Date | Priority | Status | Notes |
|---|---|---|---|---|---|---|
| P1 | Epic 1 full functional run sheet and smoke test procedure | Bob (SM), Dana (QA) | 2026-02-14 | High | done | Run sheet created: `_bmad-output/implementation-artifacts/epic-1-functional-smoke-runsheet-2026-02-14.md` |

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
