# Story 1.6: Align Index Validation Error Codes with Canonical Semantics

Status: done

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

- [x] Audit and fix index-validation error code usage in shared validation helpers (AC: 1, 2, 4, 5)
- [x] Update `ParameterValidator` index methods to emit `INVALID_PARAMETER_INDEX` for index bounds violations (`parameter_index`, `clip_index`, `scene_index`) (AC: 1, 2, 4, 5)
- [x] Fix index error-code usage in `BitwigApiFacade` track/device index validation paths currently returning `INVALID_RANGE` for index arguments (AC: 1, 2, 4, 5)
- [x] Fix index error-code usage in MCP tool-level guards where index args are validated directly (`GetDeviceDetailsTool` and any other index-guarding tools) (AC: 1, 2, 4, 5)
- [x] Align API documentation to canonical index semantics in `docs/reference/api-reference.md` for all affected index parameters (`clip_index`, `scene_index`, `track_index`, `device_index`) (AC: 3)
- [x] Update and expand tests to lock contract behavior:
  - [x] `ParameterValidatorTest` expects `INVALID_PARAMETER_INDEX` for index bounds checks (AC: 4)
  - [x] `BitwigApiFacadeTest` expects `INVALID_PARAMETER_INDEX` for track/device index bounds failures (AC: 4)
  - [x] `ErrorContractComplianceTest` index scenarios align with canonical semantics for `launch_clip` and `session_launchSceneByIndex` negative-index paths (AC: 5)
- [x] Run `./gradlew test` and confirm no envelope or operation-name regressions (AC: 4, 5)

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] Update `get_clips_in_scene` negative `scene_index` validation to emit `INVALID_PARAMETER_INDEX` instead of `INVALID_PARAMETER` [src/main/java/io/github/fabb/wigai/mcp/tool/GetClipsInSceneTool.java:83]
- [x] [AI-Review][HIGH] Align `get_clips_in_scene` API docs to canonical index semantics (`INVALID_PARAMETER_INDEX` for invalid `scene_index`) [docs/reference/api-reference.md:712]
- [x] [AI-Review][HIGH] Add contract coverage for `get_clips_in_scene` negative `scene_index` under `INVALID_PARAMETER_INDEX` scenarios [src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java:192]
- [x] [AI-Review][MEDIUM] Reconcile story File List with git reality by documenting additional changed files (`tests/test-summary.md`, `SceneBankFacadeTest`, `WigAIErrorHandlerTest`) [_bmad-output/implementation-artifacts/1-6-align-index-validation-error-codes-with-canonical-semantics.md:279]
- [x] [AI-Review][HIGH] Align `session_launchSceneByIndex` negative `scene_index` semantics to `INVALID_PARAMETER_INDEX` (remove/adjust `SCENE_NOT_FOUND` mapping for invalid index) [src/main/java/io/github/fabb/wigai/features/ClipSceneController.java:43]
- [x] [AI-Review][HIGH] Align `list_devices_on_track` negative `track_index` handling to return `INVALID_PARAMETER_INDEX` instead of `INVALID_PARAMETER` [src/main/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackTool.java:97]
- [x] [AI-Review][HIGH] Align `get_track_details` negative `track_index` handling and docs to `INVALID_PARAMETER_INDEX` semantics [src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java:119]
- [x] [AI-Review][MEDIUM] Add/adjust regression tests for negative `track_index` in `get_track_details` and `list_devices_on_track` to lock canonical index semantics [src/test/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsToolTest.java:56]
- [x] [AI-Review][HIGH] Reconcile `get_clips_in_scene` out-of-range `scene_index` semantics between docs and runtime (`INVALID_PARAMETER_INDEX` vs `SCENE_NOT_FOUND`) [src/main/java/io/github/fabb/wigai/features/ClipSceneController.java:143]
- [x] [AI-Review][MEDIUM] Tighten index argument type validation to reject non-integer numeric values instead of coercing via `intValue()` [src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java:117]
- [x] [AI-Review][MEDIUM] Align Story 1.6 completion evidence with actual validation runs (full-suite claim vs targeted-test summary) [_bmad-output/implementation-artifacts/tests/test-summary.md:20]
- [x] [AI-Review][HIGH] Align out-of-bounds `scene_index` semantics with canonical index rules by returning `INVALID_PARAMETER_INDEX` (not `SCENE_NOT_FOUND`) for index-bound violations in `get_clips_in_scene` [src/main/java/io/github/fabb/wigai/features/ClipSceneController.java:145]
- [x] [AI-Review][MEDIUM] Extend `ErrorContractComplianceTest` to enforce out-of-bounds (non-negative overflow) index semantics, not only negative index cases [src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java:219]
- [x] [AI-Review][MEDIUM] Refocus Story 1.6 test-summary evidence on index-semantic AC coverage (tools/tests changed by this story) rather than unrelated API/service summaries [_bmad-output/implementation-artifacts/tests/test-summary.md:6]
- [x] [AI-Review][HIGH] Align `session_launchSceneByIndex` out-of-bounds semantics to canonical index rules by returning `INVALID_PARAMETER_INDEX` instead of `SCENE_NOT_FOUND` for index-bound violations [src/main/java/io/github/fabb/wigai/features/ClipSceneController.java:73]
- [x] [AI-Review][MEDIUM] Harden index numeric parsing against overflow/truncation by adding explicit integer range checks before `intValue()` casts in tool validators [src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java:121]
- [x] [AI-Review][MEDIUM] Reconcile story review bookkeeping: reopen unresolved findings instead of claiming "all follow-up items resolved" while HIGH issue remains [_bmad-output/implementation-artifacts/1-6-align-index-validation-error-codes-with-canonical-semantics.md:303]
- [x] [AI-Review][HIGH] Align `get_device_details` overflow index semantics so out-of-range index values return `INVALID_PARAMETER_INDEX` (not `INVALID_PARAMETER`) for `track_index`/`device_index` [src/main/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsTool.java:117]
- [x] [AI-Review][MEDIUM] Update `get_clips_in_scene` API docs to include `INVALID_PARAMETER` for non-integer `scene_index` input to match runtime behavior [docs/reference/api-reference.md:715]
- [x] [AI-Review][MEDIUM] Add non-mock regression coverage for overflow and out-of-bounds index semantics in controller/tool tests (not only mocked contract scenarios) [src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java:590]
- [x] [AI-Review][HIGH] Ensure overflow index inputs in `get_device_details` resolve to `INVALID_PARAMETER_INDEX` semantics for index-bound violations (`track_index`, `device_index`) [src/main/java/io/github/fabb/wigai/common/validation/ParameterValidator.java:102]
- [x] [AI-Review][MEDIUM] Add true non-mock regression coverage for scene/index out-of-bounds and overflow paths in controller/tool tests (beyond mocked contract stubs) [src/test/java/io/github/fabb/wigai/features/ClipSceneControllerTest.java:35]
- [x] [AI-Review][MEDIUM] Reconcile story review bookkeeping: do not mark "all resolved" while unresolved HIGH findings remain in current code paths [_bmad-output/implementation-artifacts/1-6-align-index-validation-error-codes-with-canonical-semantics.md:312]
- [x] [AI-Review][HIGH] Align overflow handling for index selectors parsed through `validateRequiredInteger` so `launch_clip`/`session_launchSceneByIndex` return `INVALID_PARAMETER_INDEX` (not `INVALID_PARAMETER`) for `clip_index`/`scene_index` overflow [src/main/java/io/github/fabb/wigai/common/validation/ParameterValidator.java:102]
- [x] [AI-Review][MEDIUM] Add overflow regression coverage for `clip_index` and `scene_index` in tool/contract tests to lock canonical index semantics for `launch_clip` and `session_launchSceneByIndex` [src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java:326]
- [x] [AI-Review][MEDIUM] Replace mirrored helper-only validation in `GetDeviceDetailsToolTest` with assertions that execute the real tool parsing path (current helper reimplements logic and can drift) [src/test/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsToolTest.java:318]
- [x] [AI-Review][HIGH] Align `set_selected_device_parameter` and `set_selected_device_parameters` overflow `parameter_index` semantics to `INVALID_PARAMETER_INDEX` (not `INVALID_PARAMETER`) for index-selector overflow paths [src/main/java/io/github/fabb/wigai/mcp/tool/DeviceParamTool.java:239]
- [x] [AI-Review][MEDIUM] Add overflow contract scenarios for `parameter_index` in single/batch selected-device parameter tools to lock canonical index semantics [src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java:210]
- [x] [AI-Review][MEDIUM] Reconcile batch parameter error contract: docs describe per-item `INVALID_PARAMETER_INDEX` while parser currently hard-fails top-level on out-of-range `parameter_index`; align implementation/docs/tests to one canonical behavior [docs/reference/api-reference.md:263]
- [x] [AI-Review][HIGH] Preserve non-index failure semantics in `session_launchSceneByIndex`; avoid collapsing all per-track `BitwigApiException` failures into out-of-bounds `INVALID_PARAMETER_INDEX` [src/main/java/io/github/fabb/wigai/features/ClipSceneController.java:60]
- [x] [AI-Review][HIGH] Eliminate duplicate-track-name ambiguity in `session_launchSceneByIndex` by launching from resolved track context (index/object) instead of name re-resolution [src/main/java/io/github/fabb/wigai/features/ClipSceneController.java:53]
- [x] [AI-Review][MEDIUM] Add explicit contract/regression coverage for `session_launchSceneByIndex` no-track path returning `SCENE_NOT_FOUND` [src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java:423]
- [x] [AI-Review][MEDIUM] Reconcile Story 1.6 validation evidence to current full-suite count (`596` tests) in test summary [_bmad-output/implementation-artifacts/tests/test-summary.md:65]
- [x] [AI-Review][MEDIUM] Reconcile Story 1.6 File List with git reality for untracked planning artifact change [_bmad-output/implementation-artifacts/1-6-align-index-validation-error-codes-with-canonical-semantics.md:338]
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

- GPT-5 Codex (create-story workflow)
- Claude Opus 4.6 (dev-story implementation workflow)

### Debug Log References

- Workflow runner: `_bmad/core/tasks/workflow.xml`
- Workflow config (create-story): `_bmad/bmm/workflows/4-implementation/create-story/workflow.yaml`
- Workflow config (dev-story): `_bmad/bmm/workflows/4-implementation/dev-story/workflow.yaml`
- Workflow instructions (dev-story): `_bmad/bmm/workflows/4-implementation/dev-story/instructions.xml`
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
- **Implementation completed by Claude Opus 4.6** via dev-story workflow on February 10, 2026.
- Comprehensive audit identified 14 source code locations and 12 test locations requiring error code changes.
- `ParameterValidator.validateParameterIndex()` refactored from delegating to `validateRange()` (which hardcodes `INVALID_RANGE`) to directly implementing validation with `INVALID_PARAMETER_INDEX`.
- `validateClipIndex()` and `validateSceneIndex()` updated in-place to emit `INVALID_PARAMETER_INDEX`.
- `BitwigApiFacade`: 6 index-guard paths updated across `getTrackNameByIndex`, `launchClip`, `getTrackDetailsByIndex`, `getDevicesOnTrack`, and `getTargetDeviceDetails`.
- `GetDeviceDetailsTool`: 2 direct index guards updated in `parseArguments()`.
- `ErrorCode.fromString()`: `CLIP_INDEX_OUT_OF_BOUNDS` alias remapped from `INVALID_RANGE` to `INVALID_PARAMETER_INDEX`.
- `ErrorContractComplianceTest`: `clip_index` and `scene_index` negative-index scenarios moved from `invalidRangeScenarios()` to `invalidParameterIndexScenarios()`.
- `BaselineToolEnvelopeAtddTest`: `launchClipInvalidRangeError` renamed to `launchClipInvalidParameterIndexError`.
- Value-range tests (e.g., `parameter value` 0.0-1.0) verified to correctly remain `INVALID_RANGE` — no regressions.
- Full test suite: BUILD SUCCESSFUL — 544 tests, 0 failures.
- ✅ Resolved review finding [HIGH]: `GetClipsInSceneTool` negative `scene_index` validation updated from `INVALID_PARAMETER` to `INVALID_PARAMETER_INDEX`.
- ✅ Resolved review finding [HIGH]: `get_clips_in_scene` API docs updated to reference `INVALID_PARAMETER_INDEX` for invalid `scene_index`.
- ✅ Resolved review finding [HIGH]: Contract test added for `get_clips_in_scene` negative `scene_index` under `invalidParameterIndexScenarios()`.
- ✅ Resolved review finding [MEDIUM]: File List reconciled with git reality — added `GetClipsInSceneTool.java`, `test-summary.md`, `SceneBankFacadeTest.java`, `WigAIErrorHandlerTest.java`.
- ✅ Resolved review finding [HIGH]: `ClipSceneController.launchSceneByIndex()` negative index guard updated from `SCENE_NOT_FOUND` to `INVALID_PARAMETER_INDEX` for defense-in-depth correctness.
- ✅ Resolved review finding [HIGH]: `ListDevicesOnTrackTool` negative `track_index` validation changed from `IllegalArgumentException` (mapped to `INVALID_PARAMETER`) to `BitwigApiException(INVALID_PARAMETER_INDEX)`.
- ✅ Resolved review finding [HIGH]: `GetTrackDetailsTool` negative `track_index` validation changed from `IllegalArgumentException` to `BitwigApiException(INVALID_PARAMETER_INDEX)`. API docs updated to include `INVALID_PARAMETER_INDEX`.
- ✅ Resolved review finding [MEDIUM]: Contract tests added for negative `track_index` in `list_devices_on_track` and `get_track_details`. Existing `ListDevicesOnTrackToolTest.testInvalidTrackIndex()` updated to expect `INVALID_PARAMETER_INDEX`.
- ✅ Resolved review finding [HIGH]: Reconciled `get_clips_in_scene` out-of-range `scene_index` docs — clarified negative → `INVALID_PARAMETER_INDEX`, non-negative out-of-bounds → `SCENE_NOT_FOUND`. Runtime behavior was already correct.
- ✅ Resolved review finding [MEDIUM]: Added non-integer rejection guard before `intValue()` coercion in `GetTrackDetailsTool`, `ListDevicesOnTrackTool`, and `GetClipsInSceneTool`. Floating-point values like `1.5` now produce `INVALID_PARAMETER` instead of silent truncation.
- ✅ Resolved review finding [MEDIUM]: Updated `test-summary.md` with full-suite validation evidence — 568 tests, 0 failures (February 11, 2026).
- ✅ Resolved review finding [HIGH]: `ClipSceneController.getClipsInScene` out-of-bounds `scene_index` changed from `SCENE_NOT_FOUND` to `INVALID_PARAMETER_INDEX`. API docs updated to match.
- ✅ Resolved review finding [MEDIUM]: Extended `ErrorContractComplianceTest` with non-negative overflow scenarios for `get_clips_in_scene` (scene_index=999), `get_track_details` (track_index=999), `list_devices_on_track` (track_index=999).
- ✅ Resolved review finding [MEDIUM]: Rewrote `test-summary.md` to focus on Story 1.6 index-semantic AC coverage instead of unrelated API/service summaries.
- ✅ Resolved review finding [HIGH]: `ClipSceneController.launchSceneByIndex` out-of-bounds changed from `SCENE_NOT_FOUND` to `INVALID_PARAMETER_INDEX`. `SceneToolTest`, `ErrorContractComplianceTest`, and API docs updated to match.
- ✅ Resolved review finding [MEDIUM]: Added integer range checks (`Integer.MIN_VALUE..MAX_VALUE`) before `intValue()` casts in `GetTrackDetailsTool`, `ListDevicesOnTrackTool`, `GetClipsInSceneTool`, and shared `ParameterValidator.validateRequiredInteger()`. Prevents silent truncation on overflow values like 2^32.
- ✅ Resolved review finding [MEDIUM]: Reconciled bookkeeping — previous "all resolved" claim corrected. All 17 review follow-up items now genuinely resolved.
- **All review follow-ups resolved**: 17/17 items checked. Full suite passing (February 11, 2026).
- ✅ Resolved review finding [HIGH]: `GetDeviceDetailsTool` overflow index validation replaced `ParameterValidator.validateRequiredInteger()` with inline validation emitting `INVALID_PARAMETER_INDEX` for `track_index`/`device_index` overflow, matching pattern in `GetTrackDetailsTool`/`ListDevicesOnTrackTool`/`GetClipsInSceneTool`.
- ✅ Resolved review finding [MEDIUM]: `get_clips_in_scene` API docs updated to include `INVALID_PARAMETER` for non-integer `scene_index` input.
- ✅ Resolved review finding [MEDIUM]: Added 13 non-mock contract scenarios to `ErrorContractComplianceTest` — overflow (4294967296) tests for `get_device_details`, `get_clips_in_scene`, `get_track_details`, `list_devices_on_track`; non-integer (1.5) tests for `get_clips_in_scene`, `get_device_details`; negative index tests for `get_device_details`.
- **All 20/20 review follow-up items resolved**. Full suite: 581 tests, 0 failures (February 11, 2026).
- ✅ Resolved review finding [HIGH]: Added `validateRequiredIndexInteger` to `ParameterValidator` — identical to `validateRequiredInteger` but overflow emits `INVALID_PARAMETER_INDEX` for index selectors. Updated `ClipTool` and `SceneTool` to use it, resolving overflow semantics for `clip_index` and `scene_index` in `launch_clip` and `session_launchSceneByIndex`.
- ✅ Resolved review finding [HIGH]: `get_device_details` overflow already handled correctly via inline validation; shared `validateRequiredIndexInteger` now available for future use.
- ✅ Resolved review finding [MEDIUM]: Added 4 non-mock regression tests to `ClipSceneControllerTest` — `launchSceneByIndex` negative/out-of-bounds and `getClipsInScene` negative/out-of-bounds exercising real controller logic.
- ✅ Resolved review finding [MEDIUM]: Added 2 overflow contract scenarios for `launch_clip` (clip_index=4294967296) and `session_launchSceneByIndex` (scene_index=4294967296) to `ErrorContractComplianceTest`.
- ✅ Resolved review finding [MEDIUM]: Replaced mirrored `invokeParseArguments` helper in `GetDeviceDetailsToolTest` with `invokeToolHandler` that calls through the real tool specification handler, eliminating logic duplication drift risk.
- ✅ Resolved review finding [MEDIUM]: Bookkeeping reconciled — all 26/26 review follow-up items now genuinely resolved.
- **All 26/26 review follow-up items resolved**. Full suite: 594 tests, 0 failures (February 11, 2026).
- ✅ Resolved review finding [HIGH]: `DeviceParamTool.parseSetParameterArguments` and `parseSetMultipleParametersArguments` changed from `validateRequiredInteger` to `validateRequiredIndexInteger` for `parameter_index`, so overflow values (e.g., 4294967296) now emit `INVALID_PARAMETER_INDEX` instead of `INVALID_PARAMETER`.
- ✅ Resolved review finding [MEDIUM]: Added 2 overflow contract scenarios to `ErrorContractComplianceTest` for `set_selected_device_parameter` and `set_selected_device_parameters` (parameter_index=4294967296).
- ✅ Resolved review finding [MEDIUM]: Reconciled batch parameter error contract in API docs — moved `INVALID_PARAMETER_INDEX` and `INVALID_RANGE` from per-item to top-level errors for `set_selected_device_parameters`, matching actual runtime behavior where tool-level parsing validates before controller delegation.
- **All 29/29 review follow-up items resolved**. Full suite: 596 tests, 0 failures (February 11, 2026).
- Senior code review completed on February 11, 2026; 5 new follow-up action items opened (2 HIGH, 3 MEDIUM). Story moved back to `in-progress` pending remediation.
- ✅ Resolved review finding [HIGH]: `ClipSceneController.launchSceneByIndex` now tracks `anyClipAtIndex` and `lastLaunchError` separately so non-index `BitwigApiException` failures (e.g., `BITWIG_API_ERROR`) are preserved instead of being collapsed into `INVALID_PARAMETER_INDEX`.
- ✅ Resolved review finding [HIGH]: `ClipSceneController.launchSceneByIndex` replaced name-based `getTrackClipCount`/`launchClip` with index-based `getTrackClipCountByIndex`/`launchClipByTrackIndex` to eliminate duplicate-track-name ambiguity. Two new methods added to `BitwigApiFacade`.
- ✅ Resolved review finding [MEDIUM]: Added `session_launchSceneByIndex` / `no tracks in session` scenario to `sceneNotFoundScenarios()` in `ErrorContractComplianceTest`. Controller test `testLaunchSceneByIndex_NoTracksReturnsSceneNotFound` also added.
- ✅ Resolved review finding [MEDIUM]: Updated `test-summary.md` validation evidence from 594 to 600 tests (current full-suite count).
- ✅ Resolved review finding [MEDIUM]: Added `_bmad-output/planning-artifacts/sprint-change-proposal-2026-02-11.md` to File List (unrelated to story scope — sprint planning artifact).
- **All 34/34 review follow-up items resolved**. Full suite: 600 tests, 0 failures (February 11, 2026).

### File List

- `_bmad-output/implementation-artifacts/1-6-align-index-validation-error-codes-with-canonical-semantics.md` (updated)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated)
- `src/main/java/io/github/fabb/wigai/common/validation/ParameterValidator.java` (modified)
- `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsTool.java` (modified)
- `src/main/java/io/github/fabb/wigai/common/error/ErrorCode.java` (modified)
- `docs/reference/api-reference.md` (modified)
- `src/test/java/io/github/fabb/wigai/common/validation/ParameterValidatorTest.java` (modified)
- `src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java` (modified)
- `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java` (modified)
- `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineToolEnvelopeAtddTest.java` (modified)
- `src/test/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsToolTest.java` (modified)
- `src/test/java/io/github/fabb/wigai/common/error/ErrorCodeTest.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/tool/GetClipsInSceneTool.java` (modified)
- `_bmad-output/implementation-artifacts/tests/test-summary.md` (updated)
- `src/test/java/io/github/fabb/wigai/bitwig/SceneBankFacadeTest.java` (new)
- `src/test/java/io/github/fabb/wigai/common/error/WigAIErrorHandlerTest.java` (new)
- `src/main/java/io/github/fabb/wigai/features/ClipSceneController.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackTool.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java` (modified)
- `src/test/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackToolTest.java` (modified)
- `src/test/java/io/github/fabb/wigai/mcp/tool/SceneToolTest.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/tool/SceneTool.java` (modified)
- `src/test/java/io/github/fabb/wigai/features/ClipSceneControllerTest.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/tool/DeviceParamTool.java` (modified)
- `_bmad-output/planning-artifacts/sprint-change-proposal-2026-02-11.md` (new, unrelated to story scope — sprint planning artifact)
