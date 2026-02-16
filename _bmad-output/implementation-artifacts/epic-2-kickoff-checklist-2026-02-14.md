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
| G1 | Contract Semantics DoR and Contract Lockstep gate active | Bob (SM), Dana (QA), Charlie (Dev) | 2026-02-14 | pending | Completion contract: Story `2-1-contract-semantics-dor-and-runtime-test-docs-lockstep-gate-activation` is `done` and Epic 2 story templates/context show Contract Semantics DoR + Runtime/Test/Docs lockstep sections with explicit pass criteria references. |
| G2 | Duplicate-track-name ambiguity behavior defined and tested | Charlie (Dev), Dana (QA) | 2026-02-14 | pending | Completion contract: Story `2-2-duplicate-track-name-ambiguity-behavior-defined-and-tested` is `done` with evidence links proving ambiguous track names never trigger implicit mutating actions without explicit `track_index` confirmation. |
| G3 | Epic 1 smoke test executed with evidence | Dana (QA) | 2026-02-14 | done | Completed 2026-02-14 with strengthened validation after transport-stop concern. Harness/run sheet now require state verification (`status.transport.playing` true after start, false after stop). Evidence: Step 1 pass (`tests=175, failures=0, errors=0, skipped=0`) `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/01-epic1-targeted-tests.log`; Step 2 safe smoke PASS `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/02-safe-smoke.log`; Step 3 mutation smoke PASS with state checks (`start` attempt 1 true, `stop` attempt 2 false) `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/03-mutation-smoke.log`; Step 4 optional host timing stress PASS via `./gradlew mcpTimingStressTest` (`Passed: 15 | Failed: 0`) `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/04-timing-stress.log`. |
| G4 | Epic 1 deployment completed and post-deploy smoke verified | Charlie (Dev), Dana (QA) | 2026-02-14 | done | Completed 2026-02-14. Per project lead confirmation, deployment + post-deploy verification were executed in the same execution window captured under G3 evidence. Reused evidence set: `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/01-epic1-targeted-tests.log`, `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/02-safe-smoke.log`, `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/03-mutation-smoke.log`, `_bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/04-timing-stress.log`. |
| G5 | Stakeholder acceptance for Epic 1 recorded | Alice (PO), Josh (Project Lead) | 2026-02-14 | done | Accepted on 2026-02-16 by stakeholder direction from Josh (Project Lead). Scope accepted: Epic 1 (Stories 1.1-1.7), based on retrospective completion and G3/G4 evidence set. |
| G6 | Dependency/version refresh checkpoint from Story 1.6 and 1.7 completed | Charlie (Dev) | 2026-02-14 | pending | Completion contract: Story `2-0-dependency-version-refresh-checkpoint-g6-closure` is `done` with evidence links for dependency/version matrix (MCP SDK, Jetty, JUnit, transitive-risk notes), automated test run pass, host-required smoke pass, and rollback reference (tag/branch/commit). |

## Additional Planning Item (From Retro Discussion)

| ID | Item | Owner | Assigned Date | Priority | Status | Notes |
|---|---|---|---|---|---|---|
| P1 | Epic 1 full functional run sheet and smoke test procedure | Bob (SM), Dana (QA) | 2026-02-14 | High | done | Run sheet created: `_bmad-output/implementation-artifacts/epic-1-functional-smoke-runsheet-2026-02-14.md` |

## Exit Criteria for Epic 2 Kickoff

Epic 2 can move from `backlog` to `in-progress` only when:
1. All gates `G1..G6` are marked `done`
2. Evidence is documented for each gate in this file
3. Stakeholder acceptance for Epic 1 is explicitly recorded
4. Epic 2 feature implementation starts at Story `2.3` only after gates `G1..G6` are complete

## Update Protocol

When progress changes:
1. Update `Status` (`pending` -> `in-progress` -> `done`)
2. Add date-stamped evidence in `Evidence / Notes`
3. Keep this file synchronized with `_bmad-output/implementation-artifacts/sprint-status.yaml`
