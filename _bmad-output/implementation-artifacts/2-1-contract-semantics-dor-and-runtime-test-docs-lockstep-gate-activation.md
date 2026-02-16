# Story 2.1: contract-semantics-dor-and-runtime-test-docs-lockstep-gate-activation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI Scrum/quality team,
I want every Epic 2 story to include explicit Contract Semantics DoR and Runtime/Test/Docs lockstep criteria before development begins,
so that story execution and review cannot drift from documented behavioral contracts.

## Acceptance Criteria

1. **Given** an Epic 2 story is prepared for implementation  
   **When** the story is moved to ready-for-dev  
   **Then** it contains a Contract Semantics DoR section with explicit behavioral constraints and error-contract expectations.

2. **Given** an Epic 2 story is ready for review  
   **When** runtime behavior, tests, and docs are evaluated  
   **Then** lockstep criteria are present and enforced in that story’s completion checks.

3. **Given** kickoff gate G1 is evaluated  
   **When** at least one Epic 2 story demonstrates these sections with explicit pass criteria references  
   **Then** G1 can be marked `done` with evidence links in the kickoff checklist.

## Tasks / Subtasks

- [x] Define and codify Contract Semantics DoR requirements for Epic 2 stories (AC: 1)
  - [x] Created reusable Contract Semantics DoR + lockstep standard artifact: `_bmad-output/implementation-artifacts/epic-2-contract-semantics-dor-lockstep-standard-2026-02-16.md`.
  - [x] Required explicit behavioral constraints and error-contract expectations in Story 2.1 context before closure.
  - [x] Added explicit pass/fail checks and reviewer checklist criteria.

- [x] Define Runtime/Test/Docs lockstep completion criteria and apply to in-scope Epic 2 stories (AC: 2)
  - [x] Added Runtime/Test/Docs lockstep evidence requirements for Epic 2 story usage.
  - [x] Added completion checks and reviewer prompts for lockstep verification with artifact-only implementation scope.
  - [x] Added explicit lockstep drift failure criteria.

- [x] Capture G1 evidence and close kickoff gate (AC: 3)
  - [x] Demonstrated Story 2.1 includes Contract Semantics DoR + Runtime/Test/Docs lockstep sections with pass criteria references.
  - [x] Updated `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md` gate `G1` to `done` with evidence links.
  - [x] Synchronized `_bmad-output/implementation-artifacts/sprint-status.yaml` to story status `done`.

## Dev Notes

### Developer Context Section

- This is a contract/governance story for Epic 2 kickoff gate `G1`. It defines enforcement criteria; it does not add new MCP runtime functionality.
- Scope is process lockstep: every Epic 2 implementation story must carry explicit Contract Semantics DoR and Runtime/Test/Docs lockstep checks before work can be considered complete.
- This story defines the review contract that future implementation stories must satisfy across runtime behavior, tests, and docs.
- This story itself is artifact/process-only and must not introduce source-code or automated-test code changes.
- Do not mark gate `G1` done during create-story generation; this story only prepares a ready-for-dev implementation context.

### Technical Requirements

- Contract Semantics DoR requirements to enforce in implementation:
  - Explicit behavioral constraints for the target story.
  - Explicit error-contract expectations (error code + envelope semantics).
  - Explicit pass/fail checks that a reviewer can verify without interpretation.
- Runtime/Test/Docs lockstep requirements to enforce in implementation:
  - Define evidence criteria for runtime behavior, tests, and docs parity.
  - Define reviewer checks that confirm parity before story closure.
  - Declare mismatch between runtime/tests/docs evidence as a completion blocker.
- Canonical error semantics are sourced from `_bmad-output/project-context.md`:
  - Use `INVALID_PARAMETER_INDEX` for index bounds errors.
  - Use `INVALID_RANGE` for numeric value-range errors.
- Canonical MCP envelope requirements:
  - Use unified handler path (`McpErrorHandler.executeWithErrorHandling(...)`).
  - Preserve `status` + `data|error` shape and `error.operation` equals tool name.
- Explicit non-code scope rule:
  - No modifications under `src/` are allowed for Story 2.1 implementation.

### Architecture Compliance

- This story references architecture contracts; it does not modify architecture implementation.
- The enforced contract to document for future stories:
  - MCP tools delegate; controllers own domain logic; Bitwig API remains behind facade.
  - MCP tool names and JSON fields remain `snake_case`.
  - No bespoke response envelopes; canonical `ErrorCode` semantics remain authoritative.
- Implementation scope boundary for this story:
  - Allowed: `_bmad-output/` planning/implementation artifacts.
  - Not allowed: runtime source changes under `src/main/java` or test code changes under `src/test/java`.

### Library / Framework Requirements

- Current project baselines (must remain unchanged by this story):
  - Java 21
  - Bitwig Extension API 19
  - MCP Java SDK BOM `0.11.0`
  - Jetty `11.0.20`
  - JUnit Jupiter `5.10.0`
- Latest-version awareness context as of February 16, 2026 (for planning/guardrails only):
  - MCP Java SDK artifacts are published through `0.17.2` (Maven Central index date: January 22, 2026).
  - Jetty line `11.0.26` exists and is marked EOL on Jetty downloads; Jetty 12 is the community-supported line.
- JUnit GA line is `6.0.2` (January 6, 2026), with `5.14.1` available for projects staying on JUnit 5.
- This story must not perform dependency upgrades; it defines process controls only.

### File Structure Requirements

- Primary story artifact to update:
  - `_bmad-output/implementation-artifacts/2-1-contract-semantics-dor-and-runtime-test-docs-lockstep-gate-activation.md`
- Reusable standard artifact created by this story:
  - `_bmad-output/implementation-artifacts/epic-2-contract-semantics-dor-lockstep-standard-2026-02-16.md`
- Sprint tracking file to update after story generation:
  - `_bmad-output/implementation-artifacts/sprint-status.yaml` (`2-1-...` from `backlog` -> `ready-for-dev`)
- Evidence and gate file referenced for implementation of this story:
  - `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md` (G1 evidence target, not updated by create-story)
- Contract source files to treat as authoritative inputs:
  - `_bmad-output/project-context.md`
  - `_bmad-output/planning-artifacts/epics.md`
  - `_bmad-output/planning-artifacts/architecture.md`
  - `docs/reference/api-reference.md`
- Scope guardrails:
  - No file edits under `src/main/java` for this story.
  - No file edits under `src/test/java` for this story.

### Testing Requirements

- Story implementation validation is artifact-based (no new code tests in this story):
  - Contract Semantics DoR section exists with explicit pass/fail checks.
  - Runtime/Test/Docs lockstep section exists with explicit evidence requirements.
  - Evidence links for G1 are defined and traceable to kickoff checklist expectations.
  - Diff scope confirms no runtime or test source files were modified.
- Failure conditions:
  - Missing DoR or lockstep sections in story context.
  - Missing explicit pass/fail criteria and evidence-link requirements.
  - Any `src/` changes introduced while implementing Story 2.1.

### Previous Story Intelligence

- Story `2.0` established the current Epic 2 kickoff rigor:
  - Gate updates require explicit evidence links and date-stamped artifacts.
  - Sprint-status synchronization must happen alongside checklist updates.
  - Dependency/version context belongs in dedicated checkpoint artifacts, not mixed into feature behavior stories.
- Reuse the same evidence discipline from Story `2.0`:
  - deterministic artifact paths under `_bmad-output/implementation-artifacts/`
  - explicit pass/fail evidence wording
  - rollback/provenance references when applicable

### Git Intelligence Summary

- Recent commit pattern is planning-and-governance heavy, not runtime feature implementation.
- Last 5 commits focus on:
  - Epic 2 kickoff synchronization
  - G6 dependency checkpoint evidence capture
  - dependency roadmap and sprint artifact updates
- Actionable continuity guidance:
  - Keep this story implementation constrained to contract/process artifacts.
  - Maintain existing artifact naming conventions (`YYYY-MM-DD` suffix, gate-specific files).
  - Preserve sprint-status and kickoff checklist synchronization discipline.

### Latest Tech Information

- Latest dependency ecosystem signals (as of February 16, 2026) reinforce why this story should enforce strict lockstep:
  - MCP Java SDK has advanced significantly beyond project pin `0.11.0`; newer line `0.17.x` indicates active contract surface evolution.
  - Jetty 11 is EOL while Jetty 12 is the community-supported line; contract and docs rigor are required to reduce upgrade risk.
  - JUnit has active 6.x and 5.14.x maintenance lines; test contract clarity is essential before modernization work.
- Implementation implication for Story 2.1:
  - Strengthen contract governance now so future upgrade stories can validate runtime/tests/docs in lockstep and avoid behavioral drift.

### Project Context Reference

- Contract Semantics source of truth: `_bmad-output/project-context.md` (Error Code Semantics + Critical Implementation Rules)
- Story source of truth: `_bmad-output/planning-artifacts/epics.md` (Story 2.1 AC1-AC3)
- Architecture guardrails source: `_bmad-output/planning-artifacts/architecture.md` (layering, envelopes, validation conventions)
- Kickoff gate source: `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md` (G1 completion contract)

### Project Structure Notes

- Alignment with existing structure is direct:
  - Contract/runtime/doc lockstep criteria are documented against existing architecture boundaries.
- No structural conflicts detected for this story:
  - Work is primarily in planning/artifact files.
  - Runtime and test code changes are out of scope for Story 2.1 implementation.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-21-Contract-Semantics-DoR--RuntimeTestDocs-Lockstep-Gate-Activation-G1]
- [Source: _bmad-output/planning-artifacts/architecture.md]
- [Source: _bmad-output/project-context.md#Error-Code-Semantics-Single-Source-of-Truth]
- [Source: _bmad-output/implementation-artifacts/2-0-dependency-version-refresh-checkpoint-g6-closure.md]
- [Source: _bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md]
- [Source: docs/reference/api-reference.md]

## Dev Agent Record

### Agent Model Used

- GPT-5 Codex (create-story workflow)

### Debug Log References

- Workflow engine: `_bmad/core/tasks/workflow.xml`
- Workflow config: `_bmad/bmm/workflows/4-implementation/create-story/workflow.yaml`
- Workflow instructions: `_bmad/bmm/workflows/4-implementation/create-story/instructions.xml`
- Workflow checklist: `_bmad/bmm/workflows/4-implementation/create-story/checklist.md`
- Story artifact: `_bmad-output/implementation-artifacts/2-1-contract-semantics-dor-and-runtime-test-docs-lockstep-gate-activation.md`
- Sprint tracking: `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Completion Notes List

- Story selection resolved from explicit user input `CS 2-1`.
- Epic status synchronized to `in-progress` for Epic 2 first-story creation context.
- Story context includes Contract Semantics DoR and Runtime/Test/Docs lockstep implementation guardrails.
- Previous-story and git intelligence were incorporated from Story `2.0` and most recent commit history.
- Latest dependency context captured as governance input only; no runtime upgrades performed.
- Story scope hardened to artifact/process-only with explicit prohibition on `src/` edits.
- Reusable Epic 2 DoR/lockstep standard artifact created for downstream stories.
- Kickoff gate `G1` updated to `done` with evidence links.
- Story status progressed to `done` with sprint status synchronized.
- Validation check confirmed artifact-only scope (no `src/main/java` or `src/test/java` file changes).

### File List

- `_bmad-output/implementation-artifacts/2-1-contract-semantics-dor-and-runtime-test-docs-lockstep-gate-activation.md` (created/updated)
- `_bmad-output/implementation-artifacts/epic-2-contract-semantics-dor-lockstep-standard-2026-02-16.md` (new)
- `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md` (updated - G1 -> done with evidence)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated - Story 2.1 -> done)
