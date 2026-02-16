# Dependency Upgrade Roadmap

Date: 2026-02-16
Scope: Post-Story 2.0 modernization execution path
Owner: Epic 6 workstream

## 1. Target Upgrade Matrix

| Area | Current | Candidate Target | Phase | Notes |
|---|---|---|---|---|
| Jetty runtime | 11.0.20 | 11.0.26 | 6.1 | Low-risk patch within Jetty 11 line; closes known gap to final 11.x release. |
| Jetty runtime | 11.x | 12.x | 6.2 -> 6.3 | Requires servlet-line compatibility review and migration planning. |
| Jakarta / Servlet API alignment | Explicit `jakarta.servlet-api:6.0.0` + Jetty 11 stack | Single aligned servlet line after migration decision | 6.2 -> 6.3 | Resolve dual-jar/classpath ambiguity and align with selected Jetty line. |
| MCP Java SDK BOM | 0.11.0 | 0.17.x | 6.2 -> 6.3 | Major gap; evaluate transport/session API compatibility and behavioral drift. |
| JUnit Jupiter | 5.10.0 | Latest 5.x then optional 6.x | 6.1 then 6.2 | Upgrade 5.x in patch phase; evaluate 6.x in spike before adoption. |
| Bitwig Extension API | 19 | 24 | 6.2 -> 6.3 | Evaluate binary/source compatibility and runtime constraints in Bitwig host. |

## 2. Execution Phases

### Phase 6.1 - Patch Refresh Baseline

Goals:
- Upgrade Jetty to 11.0.26.
- Upgrade JUnit to latest 5.x line.
- Keep MCP SDK and Bitwig API unchanged in this phase.

Required checks:
- `./gradlew test --rerun` passes.
- Host smoke: safe + mutation passes.
- No MCP envelope or error-code contract regressions.

Exit criteria:
- Patch changes merged with logs attached.
- Rollback reference recorded.

### Phase 6.2 - Major Migration Spike

Goals:
- Evaluate feasibility and required change scope for:
  - Jetty 12 migration,
  - Jakarta/Servlet alignment,
  - MCP SDK 0.17.x,
  - Bitwig API 24,
  - optional JUnit 6.

Required outputs:
- Compatibility matrix per target:
  - compile impact,
  - runtime impact,
  - test impact,
  - host smoke risk,
  - estimated change size,
  - go/no-go recommendation.
- Blockers list with required prerequisites.

Exit criteria:
- Approved target set for implementation (subset permitted).
- Documented fallback for each rejected or deferred target.

### Phase 6.3 - Major Upgrade Implementation

Goals:
- Implement approved major targets from Phase 6.2 only.

Required checks:
- Full regression suite pass.
- Host-required smoke pass.
- Focused contract tests for MCP envelopes/error semantics.
- Evidence that ambiguous/track-targeting guardrails remain intact.

Exit criteria:
- Approved upgrades merged with release notes and rollback reference.

### Phase 6.4 - Docs and Guardrails Refresh

Goals:
- Align architecture, API reference, test runbooks, and release guardrails with new baseline.

Required checks:
- Docs reflect final versions and known constraints.
- Kickoff/release checklists updated with new evidence paths.

Exit criteria:
- Documentation and guardrail artifacts synchronized with shipped runtime.

## 3. Go/No-Go Gates (Per Phase)

1. Build gate: project compiles and tests execute.
2. Contract gate: MCP envelope and canonical error semantics unchanged unless intentionally versioned.
3. Host gate: safe and mutation smoke pass against running Bitwig.
4. Stability gate: no new high-severity regressions in retry/idempotency/logging guardrails.
5. Rollback gate: baseline SHA/tag/branch recorded before promotion.

## 4. Blast-Radius Controls

- Ship patch and major upgrades in separate PRs.
- Limit each major target to its own change set where practical.
- Do not combine Bitwig API 24 and MCP SDK major changes in one blind step without spike-approved rationale.
- Require reviewer sign-off from Dev + QA for every promotion gate.

## 5. Blockers and Fallbacks

| Target | Potential Blocker | Fallback |
|---|---|---|
| Jetty 12 | Servlet-line incompatibilities or host behavior drift | Stay on 11.0.26 and defer Jetty 12 to next cycle |
| MCP SDK 0.17.x | Transport/session API differences causing runtime regressions | Stay on 0.11.0 with documented rationale and revisit post-Epic 2 |
| Bitwig API 24 | Extension API incompatibility with current implementation paths | Keep API 19 baseline and open compatibility spike follow-up |
| JUnit 6 | Test framework/plugin incompatibilities | Stay on latest 5.x |

## 6. Evidence Mapping

- Patch phase logs: `_bmad-output/implementation-artifacts/tests/epic-6-upgrade-*/`
- Major spike notes: `_bmad-output/implementation-artifacts/epic-6-migration-spike-*.md`
- Major implementation logs: `_bmad-output/implementation-artifacts/tests/epic-6-major-upgrade-*/`
- Rollback references: captured in each phase summary.

## 7. Current Recommendation

- Execute Phase 6.1 immediately (low risk).
- Start Phase 6.2 before any major upgrade implementation.
- Gate Phase 6.3 strictly on spike outputs and host-smoke validation.
