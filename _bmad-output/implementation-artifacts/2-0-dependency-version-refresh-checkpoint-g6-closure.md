# Story 2.0: dependency-version-refresh-checkpoint-g6-closure

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As the WigAI delivery team,
I want a dedicated dependency/version refresh checkpoint with captured evidence,
so that Epic 2 kickoff gate G6 can be closed with auditable technical readiness.

## Acceptance Criteria

1. **Given** Epic 1 Stories 1.6 and 1.7 are complete  
   **When** this checkpoint story is executed  
   **Then** a dependency/version matrix is recorded with current baselines for MCP SDK, Jetty, JUnit, and key transitive-risk notes.

2. **Given** dependency/version baselines are reviewed  
   **When** regression verification runs  
   **Then** evidence links are recorded for a full automated test suite pass.

3. **Given** host verification is required for kickoff readiness  
   **When** smoke verification runs against a running Bitwig instance  
   **Then** evidence links are recorded for host-required smoke pass results.

4. **Given** release safety requires rollback readiness  
   **When** checkpoint evidence is finalized  
   **Then** a rollback point reference (tag/branch/commit) is recorded.

5. **Given** all checkpoint evidence is complete  
   **When** kickoff artifacts are synchronized  
   **Then** G6 in `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md` is updated to `done` and sprint tracking remains synchronized.

## Tasks / Subtasks

- [x] Create checkpoint evidence folder and summary artifact (AC: 1, 2, 3, 4, 5)
  - [x] Create `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/`
  - [x] Create `_bmad-output/implementation-artifacts/dependency-version-refresh-checkpoint-2026-02-16.md`

- [x] Capture current dependency baseline from build files (AC: 1)
  - [x] Record pinned versions from `build.gradle.kts` for MCP SDK BOM, Jetty, and JUnit
  - [x] Record relevant transitive-risk notes (servlet/API compatibility, release line support)

- [x] Capture latest-available versions from primary sources (AC: 1)
  - [x] MCP Java SDK latest release and date from official repo releases
  - [x] Jetty latest release and support posture for Jetty 11 line
  - [x] JUnit latest release and date

- [x] Run full automated regression suite and capture evidence (AC: 2)
  - [x] Execute `./gradlew test --rerun`
  - [x] Save command output to `01-gradle-test.log` in checkpoint evidence folder

- [x] Run host-required smoke verification and capture evidence (AC: 3)
  - [x] Execute `./gradlew mcpSmokeTest`
  - [x] Execute `WIGAI_SMOKE_TEST_MUTATIONS=true ./gradlew mcpSmokeTest`
  - [x] Save logs to `02-safe-smoke.log` and `03-mutation-smoke.log`

- [x] Create rollback reference (AC: 4)
  - [x] Record baseline commit SHA before changes
  - [x] Record rollback tag or branch pointer used for promotion safety

- [x] Synchronize kickoff artifacts (AC: 5)
  - [x] Update G6 status to `done` with evidence links in `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`
  - [x] Update `_bmad-output/implementation-artifacts/sprint-status.yaml` story status progression as work moves from `ready-for-dev` -> `in-progress` -> `review` -> `done`

## Dev Notes

### Developer Context Section

- This story is a kickoff-readiness evidence story and should not alter product behavior or API contracts.
- Scope is validation, evidence capture, and gate synchronization for G6 only.
- Keep artifacts reproducible and date-stamped so kickoff audits are deterministic.

### Technical Requirements

- Current pinned baseline (from `build.gradle.kts`):
  - MCP Java SDK BOM: `0.11.0`
  - Jetty: `11.0.20`
  - JUnit Jupiter: `5.10.0`
- Evidence must distinguish:
  - current pinned baseline,
  - latest available upstream,
  - recommended action (upgrade now/defer) with risk statement.
- No dependency upgrade is required by this story unless explicitly approved; this story can close with documented defer decisions plus passing regression/smoke evidence.

### Architecture Compliance

- Preserve current architecture decisions and error envelope contracts.
- Do not modify runtime tool behavior while executing checkpoint evidence.
- Any suggested upgrades must include compatibility risk notes with Bitwig extension runtime and existing MCP transport implementation.

### Library / Framework Requirements

- Use current project stack for verification runs:
  - Java 21
  - Bitwig Extension API 19
  - MCP BOM `0.11.0`
  - Jetty `11.0.20`
  - JUnit `5.10.0`
- Latest-available check targets:
  - MCP Java SDK release line (GitHub releases)
  - Jetty release/support posture (GitHub releases + official issue note)
  - JUnit release line (GitHub releases)

### File Structure Requirements

- Primary artifacts to create/update:
  - `_bmad-output/implementation-artifacts/dependency-version-refresh-checkpoint-2026-02-16.md`
  - `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/01-gradle-test.log`
  - `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/02-safe-smoke.log`
  - `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/03-mutation-smoke.log`
  - `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`

### Testing Requirements

- Automated regression: full `./gradlew test --rerun` must pass.
- Host-required smoke: both safe and mutation harness runs must pass against a running Bitwig instance.
- G6 may be moved to `done` only when all required evidence paths are captured and readable.

### Project Structure Notes

- This checkpoint is planning/evidence work under `_bmad-output/implementation-artifacts` and does not require source tree refactoring.
- Keep sprint-status and kickoff checklist synchronized after each status transition.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-20-DependencyVersion-Refresh-Checkpoint-Closure-for-Epic-2-Kickoff-G6]
- [Source: _bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md]
- [Source: _bmad-output/implementation-artifacts/sprint-status.yaml]
- [Source: _bmad-output/implementation-artifacts/epic-1-functional-smoke-runsheet-2026-02-14.md]
- [Source: build.gradle.kts]
- [Source: _bmad-output/project-context.md]
- [Source: https://github.com/modelcontextprotocol/java-sdk/releases]
- [Source: https://github.com/jetty/jetty.project/releases]
- [Source: https://github.com/junit-team/junit-framework/releases]
- [Source: https://github.com/jetty/jetty.project/issues/13918]

## Dev Agent Record

### Agent Model Used

- GPT-5 Codex (create-story workflow)
- Claude Opus 4.6 (dev-story workflow)

### Debug Log References

- Workflow runner: `_bmad/core/tasks/workflow.xml`
- Workflow config: `_bmad/bmm/workflows/4-implementation/create-story/workflow.yaml`
- Workflow instructions: `_bmad/bmm/workflows/4-implementation/create-story/instructions.xml`
- Story template: `_bmad/bmm/workflows/4-implementation/create-story/template.md`
- Validation checklist: `_bmad/bmm/workflows/4-implementation/create-story/checklist.md`

### Completion Notes List

- Story selected automatically from sprint status first backlog key: `2-0-dependency-version-refresh-checkpoint-g6-closure`.
- Story context compiled from epics, architecture, PRD, project context, kickoff checklist, and recent commit history.
- Latest dependency context reviewed from primary release sources (MCP SDK, Jetty, JUnit) with checkpoint-date references.
- Story generated as implementation-ready kickoff checkpoint with explicit evidence deliverables and gate-close criteria.
- (dev-story 2026-02-16) All 7 tasks executed: evidence folder created, dependency matrix with baselines and latest-available populated, transitive-risk analysis from `./gradlew dependencies` output, regression suite PASS (696/0/100%), safe smoke PASS, mutation smoke PASS with transport state verification, rollback reference recorded at `8c297d5` on `develop/cycle-2`, G6 updated to `done` in kickoff checklist with full evidence links.
- All dependency upgrades deferred with documented risk statements. No source code changes required by this checkpoint story.

### Change Log

- 2026-02-16: Story executed. All checkpoint evidence captured and gate G6 closed. Sprint status synchronized.
- 2026-08-19: Status header corrected `review` -> `done` to match the tracker. The story completed on 2026-02-16 (G6 closed, evidence captured) and the tracker was updated then, but the header was never advanced.

### File List

- `_bmad-output/implementation-artifacts/2-0-dependency-version-refresh-checkpoint-g6-closure.md` (updated)
- `_bmad-output/implementation-artifacts/dependency-version-refresh-checkpoint-2026-02-16.md` (new)
- `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/01-gradle-test.log` (new)
- `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/02-safe-smoke.log` (new)
- `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/03-mutation-smoke.log` (new)
- `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md` (updated - G6 → done)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated - story status transitions)
