# Story 1.6: Align Index Validation Error Codes with Canonical Semantics

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an external AI agent developer,
I want index validation errors to use semantically correct error codes (`INVALID_PARAMETER_INDEX` for index bounds, not `INVALID_RANGE`),
so that my client can distinguish between "wrong index position" vs "value outside allowed range" and provide appropriate user feedback.

## Acceptance Criteria

1. **Given** a tool receives a negative `clip_index`, `scene_index`, or `track_index`
   **When** validation fails
   **Then** the error response uses `INVALID_PARAMETER_INDEX` (not `INVALID_RANGE`).

2. **Given** a tool receives an out-of-bounds index (for example, `track_index` exceeding track count)
   **When** validation fails
   **Then** the error response uses `INVALID_PARAMETER_INDEX`.

3. **Given** `docs/reference/api-reference.md` documents error codes for index parameters
   **When** Story 1.6 is complete
   **Then** all index parameter errors reference `INVALID_PARAMETER_INDEX` (not `INVALID_RANGE`).

4. **Given** unit tests assert error codes for index validation
   **When** they run
   **Then** they expect `INVALID_PARAMETER_INDEX` for index bounds errors.

5. **Given** `ErrorContractComplianceTest` validates error code usage
   **When** tests run
   **Then** index validation paths are covered and pass.

## Tasks / Subtasks

- [ ] Audit and fix index-validation error code usage in shared validation helpers (AC: 1, 2, 4, 5)
- [ ] Update `ParameterValidator` index methods to emit `INVALID_PARAMETER_INDEX` for index bounds violations (`parameter_index`, `clip_index`, `scene_index`) (AC: 1, 2, 4, 5)
- [ ] Fix index error-code usage in `BitwigApiFacade` track/device index validation paths currently returning `INVALID_RANGE` for index arguments (AC: 1, 2, 4, 5)
- [ ] Fix index error-code usage in MCP tool-level guards where index args are validated directly (`GetDeviceDetailsTool` and any other index-guarding tools) (AC: 1, 2, 4, 5)
- [ ] Align API documentation to canonical index semantics in `docs/reference/api-reference.md` for all affected index parameters (`clip_index`, `scene_index`, `track_index`, `device_index`) (AC: 3)
- [ ] Update and expand tests to lock contract behavior:
  - [ ] `ParameterValidatorTest` expects `INVALID_PARAMETER_INDEX` for index bounds checks (AC: 4)
  - [ ] `BitwigApiFacadeTest` expects `INVALID_PARAMETER_INDEX` for track/device index bounds failures (AC: 4)
  - [ ] `ErrorContractComplianceTest` index scenarios align with canonical semantics for `launch_clip` and `session_launchSceneByIndex` negative-index paths (AC: 5)
- [ ] Run `./gradlew test` and confirm no envelope or operation-name regressions (AC: 4, 5)

## Dev Notes

### Developer Context Section

- This story is an error-contract alignment story, not a feature expansion story. Keep changes tightly scoped to index error semantics and related docs/tests.
- Canonical rule to enforce everywhere:
  - Use `INVALID_PARAMETER_INDEX` for index selectors (`track_index`, `scene_index`, `clip_index`, `parameter_index`, `device_index`) when negative or out of bounds.
  - Use `INVALID_RANGE` only for numeric value ranges (for example, normalized parameter `value` outside `0.0-1.0`).
- Preserve existing MCP envelope and operation semantics:
  - Keep `status` + `data|error` response shape unchanged.
  - Keep `error.operation` equal to the MCP tool name.
- Preserve behavior compatibility:
  - Do not alter tool names, request schemas, or targeting rules.
  - Do not introduce dependency upgrades as part of this story.
- Audit all validation layers because index checks are currently split across:
  - `ParameterValidator` helpers
  - Tool-level argument parsing
  - `BitwigApiFacade` bounds checks

### Technical Requirements

- Index argument validation must map to `ErrorCode.INVALID_PARAMETER_INDEX` in all affected code paths:
  - Negative index values
  - Index values exceeding available bounds
- Numeric value validation must continue to map to `ErrorCode.INVALID_RANGE` (do not regress value-range semantics).
- Shared validator requirements:
  - `validateParameterIndex(...)`, `validateClipIndex(...)`, and `validateSceneIndex(...)` must emit `INVALID_PARAMETER_INDEX`.
  - Generic `validateRange(...)` should remain `INVALID_RANGE` for true value-range checks.
- Tool/facade requirements:
  - Any direct index guards in `GetDeviceDetailsTool` and `BitwigApiFacade` that currently emit `INVALID_RANGE` for index selectors must be updated.
  - Maintain existing error messages and context maps where possible; only change code semantics and wording where needed for correctness.
- Documentation requirements:
  - `docs/reference/api-reference.md` must list `INVALID_PARAMETER_INDEX` for index-argument errors and reserve `INVALID_RANGE` for numeric values.
- Verification requirements:
  - Unit and contract tests must assert the updated index semantics.
  - No changes to success payload shape or MCP tool names.

### Architecture Compliance

- Enforce existing layer boundaries:
  - MCP tools handle argument parsing/validation and delegate to controllers/facades.
  - Bitwig API calls remain encapsulated in `BitwigApiFacade`.
  - Shared validation logic remains centralized in `ParameterValidator`.
- All tool failures must continue flowing through `McpErrorHandler` for standardized envelopes.
- Respect canonical error taxonomy as defined in `_bmad-output/project-context.md` (single source of truth).
- Keep naming and request-field conventions unchanged (`snake_case`).
- Do not add alternate error-handling paths, new abstractions, or cross-cutting refactors in this story.

### Library / Framework Requirements

- Use currently pinned project dependencies for this story (from `build.gradle.kts`):
  - Java 21
  - Bitwig Extension API 19
  - MCP Java SDK BOM 0.11.0
  - Jetty 11.0.20
  - JUnit Jupiter 5.10.0
- Latest-available ecosystem notes (verified February 11, 2026):
  - MCP Java SDK has newer releases available (0.17.x line).
  - Jetty 11 line is listed as EOL; Jetty 12 is the supported line.
  - JUnit 6.x is available; newer 5.x releases also exist.
- Story scope rule:
  - Do not perform dependency or transport upgrades in Story 1.6.
  - If any version updates are needed later, handle in a separate hardening story to avoid mixing contract semantics work with platform migration risk.

### File Structure Requirements

- Primary code files expected to change:
  - `src/main/java/io/github/fabb/wigai/common/validation/ParameterValidator.java`
  - `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsTool.java`
- Primary test files expected to change:
  - `src/test/java/io/github/fabb/wigai/common/validation/ParameterValidatorTest.java`
  - `src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java`
  - `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java`
- Primary documentation file expected to change:
  - `docs/reference/api-reference.md`
- Optional additional touchpoints (only if discovered by grep during implementation):
  - Any MCP tool or facade class emitting `INVALID_RANGE` for index selectors.
  - Matching tests asserting the old index error code.
- Do not move files or reorganize package structure in this story.

### Testing Requirements

- Update unit tests to assert index semantics explicitly:
  - `ParameterValidatorTest`: `validateParameterIndex`, `validateClipIndex`, `validateSceneIndex` failure paths must assert `INVALID_PARAMETER_INDEX`.
  - `BitwigApiFacadeTest`: track/device/clip/scene index out-of-bounds and negative cases must assert `INVALID_PARAMETER_INDEX` when argument is an index selector.
  - `GetDeviceDetailsTool` related tests (if present) must assert `INVALID_PARAMETER_INDEX` for invalid `track_index`/`device_index` values.
- Update contract tests:
  - `ErrorContractComplianceTest` index scenarios for `launch_clip` and `session_launchSceneByIndex` negative index inputs should expect `INVALID_PARAMETER_INDEX`.
  - Keep value-range scenarios (for example device parameter `value`) expecting `INVALID_RANGE`.
- Execute and verify:
  - Run `./gradlew test`
  - Confirm no regressions in response envelope structure (`status` + `data|error`) and `error.operation` values.
- If tests reveal additional index-selector paths using `INVALID_RANGE`, include them in this story’s fix set before completion.

### Previous Story Intelligence

- From Story 1.4 and 1.5 implementation patterns:
  - Keep all tool responses routed through `McpErrorHandler` and avoid bespoke response construction.
  - Preserve structured logging and `request_id` behavior for mutating tools; this story must not regress correlation/logging flows.
- From Story 1.5 completion and review learnings:
  - Prefer narrow, centralized fixes over broad refactors (shared validation and bounded call sites).
  - Regression risk is highest where tests implicitly codified previous behavior; update tests deliberately and comprehensively.
- Practical carry-forward for Story 1.6:
  - Focus on semantic correctness without changing runtime behavior of successful operations.
  - Preserve operation names and existing payload keys while correcting error code selection.

### Git Intelligence Summary

- Recent implementation baseline:
  - `6903e84` (Story 1.5): retry hardening touched `McpErrorHandler` and added substantial test coverage; preserve these behaviors.
  - `4fa4dd0`: broad framework/documentation refresh; avoid coupling Story 1.6 changes to BMAD framework deltas.
  - `7290955` (Story 1.3): prior response-envelope and error-contract alignment introduced many current error semantics in tools/tests/docs.
- Regression hotspots identified from current code state:
  - Index selectors still emitting `INVALID_RANGE` in:
    - `ParameterValidator` index helpers
    - `BitwigApiFacade` track/device index bounds checks
    - `GetDeviceDetailsTool` direct index guards
  - Contract/docs drift:
    - `docs/reference/api-reference.md` contains multiple index error entries still documented as `INVALID_RANGE`
    - `ErrorContractComplianceTest` currently expects `INVALID_RANGE` for some negative index scenarios
- Implementation strategy implied by commit history:
  - Prefer minimal targeted edits in validator/facade/tool + explicit corresponding test/doc updates in same change set.

### Latest Tech Information

- Verification date: February 11, 2026.
- MCP Java SDK:
  - Project currently pins `0.11.0`.
  - Newer stable releases are available in the `0.17.x` line.
- MCP transport context:
  - Current MCP spec revisions describe Streamable HTTP as replacing prior HTTP+SSE transport model.
  - Java SDK docs currently list support for STDIO, Streamable HTTP, and SSE.
- Jetty:
  - Project currently pins `11.0.20`.
  - Jetty 11 is listed as EOL on official distribution pages; Jetty 12 is the supported line.
- JUnit:
  - Project currently pins `5.10.0`.
  - Newer 5.x and 6.x releases are available.
- Story 1.6 guidance from latest-tech checks:
  - Do not mix version/platform upgrades into this story.
  - Keep this story focused on canonical index error semantics, with any dependency modernization deferred to a separate hardening effort.

### Story Completion Status

- Status confirmed: `ready-for-dev`.
- Completion note: Ultimate context engine analysis completed - comprehensive developer guide created.
- Validation note: `_bmad/core/tasks/validate-workflow.xml` is not present in this repository snapshot, so checklist validation was performed manually against `_bmad/bmm/workflows/4-implementation/create-story/checklist.md`.

### Project Structure Notes

- Code organization alignment:
  - Validation helpers remain in `common/validation`.
  - Bitwig-bound index checks remain in `bitwig/BitwigApiFacade`.
  - Tool argument guards remain in `mcp/tool`.
  - Tests mirror source structure under `src/test/java/io/github/fabb/wigai/...`.
- Naming and schema alignment:
  - Maintain MCP tool and request-field `snake_case` conventions.
  - Preserve existing operation names used for MCP error envelopes.
- Detected variance to resolve in this story:
  - Canonical project-context semantics require `INVALID_PARAMETER_INDEX` for selector indices, but several current code/doc/test paths still use `INVALID_RANGE`.
  - This story explicitly reconciles that drift without altering broader architecture or packaging.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-16-Align-Index-Validation-Error-Codes-with-Canonical-Semantics]
- [Source: _bmad-output/project-context.md#Error-Code-Semantics-Single-Source-of-Truth]
- [Source: _bmad-output/planning-artifacts/architecture.md#114-Layer-Specific-Error-Handling]
- [Source: docs/reference/api-reference.md]
- [Source: src/main/java/io/github/fabb/wigai/common/validation/ParameterValidator.java]
- [Source: src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsTool.java]
- [Source: src/test/java/io/github/fabb/wigai/common/validation/ParameterValidatorTest.java]
- [Source: src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java]
- [Source: src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java]
- [Source: build.gradle.kts]
- [Source: https://modelcontextprotocol.io/specification/2025-06-18/basic/transports]
- [Source: https://modelcontextprotocol.io/sdk/java/mcp-server]
- [Source: https://github.com/modelcontextprotocol/java-sdk/releases]
- [Source: https://repo1.maven.org/maven2/io/modelcontextprotocol/sdk/mcp/]
- [Source: https://jetty.org/download.html]
- [Source: https://github.com/junit-team/junit-framework/releases]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex (create-story workflow)

### Debug Log References

- Workflow runner: `_bmad/core/tasks/workflow.xml`
- Workflow config: `_bmad/bmm/workflows/4-implementation/create-story/workflow.yaml`
- Workflow instructions: `_bmad/bmm/workflows/4-implementation/create-story/instructions.xml`
- Story template: `_bmad/bmm/workflows/4-implementation/create-story/template.md`
- Validation checklist: `_bmad/bmm/workflows/4-implementation/create-story/checklist.md`
- Primary analyzed artifacts:
  - `_bmad-output/planning-artifacts/epics.md`
  - `_bmad-output/planning-artifacts/architecture.md`
  - `_bmad-output/planning-artifacts/prd.md`
  - `_bmad-output/project-context.md`
  - `_bmad-output/implementation-artifacts/1-5-non-blocking-execution-bounded-retry-verification-baseline-tools.md`

### Completion Notes List

- Story selection set by user to `1-6-align-index-validation-error-codes-with-canonical-semantics`.
- Story context regenerated from template and completed with implementation-ready requirements.
- Acceptance criteria, tasks, guardrails, architecture compliance, file boundaries, tests, and references were synchronized to current project artifacts.
- Latest technical context was verified and documented with concrete date: February 11, 2026.
- Story remains scoped to canonical index error semantics; dependency/platform upgrades explicitly deferred.
- Ultimate context engine analysis completed - comprehensive developer guide created.

### File List

- `_bmad-output/implementation-artifacts/1-6-align-index-validation-error-codes-with-canonical-semantics.md` (updated)
