# Story 2.3: standard-track-targeting-contract-index-exact-name-selected-default

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an external AI agent developer,  
I want a consistent way to target tracks across WigAI tools,  
so that I can reliably act on the intended track without custom per-tool rules.

## Acceptance Criteria

1. **Given** a tool supports track targeting  
   **When** the request includes `track_index`  
   **Then** WigAI targets the track by 0-based index and returns `INVALID_PARAMETER_INDEX` if the index is out of bounds.

2. **Given** a tool supports track targeting  
   **When** the request includes `track_name`  
   **Then** WigAI targets the track by exact, case-insensitive name match (after trimming) and returns `TRACK_NOT_FOUND` if no exact match exists.

3. **Given** a tool supports track targeting  
   **When** neither `track_index` nor `track_name` is provided  
   **Then** WigAI targets the currently selected Bitwig track.

4. **Given** a tool supports track targeting  
   **When** neither `track_index` nor `track_name` is provided and no Bitwig track is currently selected  
   **Then** WigAI refuses the request with `TRACK_NOT_FOUND` and a message instructing the client to provide `track_index` or `track_name`.

5. **Given** a request provides both `track_index` and `track_name`  
   **When** the request is validated  
   **Then** WigAI treats `track_index` as authoritative and uses `track_name` as confirmation; it returns `INVALID_PARAMETER` only when `track_name` does not match the resolved indexed track (after trim + case-insensitive normalization).

## Tasks / Subtasks

- [x] Implement a shared track-targeting contract resolver for in-scope tools (AC: 1, 2, 3, 4, 5)
  - [x] Centralize target resolution order: `track_index` -> `track_name` (trim + case-insensitive exact) -> selected track fallback.
  - [x] Support dual-selector confirmation semantics: when both are present, resolve by `track_index` and validate normalized `track_name` against resolved track.
  - [x] Ensure selected-track fallback returns `TRACK_NOT_FOUND` when no selected track exists.
  - [x] Keep ambiguity behavior from Story 2.2 intact where duplicate exact names require explicit `track_index` confirmation.

- [x] Apply the shared contract to current Epic 2 in-scope track-targeting surfaces (AC: 1, 2, 3, 4, 5)
  - [x] `get_track_details`: align parsing/behavior/messages to the standard contract.
  - [x] `list_devices_on_track`: align parsing/behavior/messages to the standard contract.
  - [x] `get_device_details`: align track-targeting behavior with the standard contract in identifier mode.
  - [x] `launch_clip`: preserve existing ambiguity-safe behavior while aligning exact-name normalization expectations for non-ambiguous paths.

- [x] Harden facade/controller behavior to remove contract drift points (AC: 1, 2, 3, 4, 5)
  - [x] Update `BitwigApiFacade` track-name resolution helpers that are currently case-sensitive-only to exact case-insensitive + trimmed semantics.
  - [x] Ensure consistent index-bound checks and canonical index error mapping (`INVALID_PARAMETER_INDEX`) at tool boundary and facade/controller boundary.
  - [x] Ensure selected-track fallback path uses project-absolute index semantics consistently where surfaced in response payloads.

- [x] Add and/or update tests proving runtime contract behavior and regressions (AC: 1, 2, 3, 4, 5)
  - [x] Positive tests: index targeting, normalized exact-name targeting, selected-track fallback.
  - [x] Negative tests: out-of-bounds index, both selectors provided, no selected track fallback failure.
  - [x] Regression tests: duplicate-name ambiguity remains explicit and non-mutating unless `track_index` confirmation is provided.

- [x] Update docs and evidence artifacts in lockstep (AC: 1, 2, 3, 4, 5)
  - [x] Update `docs/reference/api-reference.md` to reflect exact, case-insensitive + trim track-name targeting contract and dual-selector confirmation semantics.
  - [x] Add story execution evidence references under `_bmad-output/implementation-artifacts/tests/` in completion notes when implemented.
  - [x] Synchronize `_bmad-output/implementation-artifacts/sprint-status.yaml` transitions during implementation (`ready-for-dev` -> `in-progress` -> `review` -> `done`).

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] Restore `get_track_details` selected-track fallback behavior when the selected project track is outside the materialized track-bank window; preserve cursor-based fallback semantics for selected-track requests. [src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java:65]
- [x] [AI-Review][MEDIUM] Resolve contradictory `get_device_details` documentation for the no-identifiers path so selected-device default behavior and constraint rules are consistent. [docs/reference/api-reference.md:768]
- [x] [AI-Review][MEDIUM] Reconcile story File List with staged changes (`_bmad-output/planning-artifacts/epics.md` currently missing from this story artifact). [_bmad-output/implementation-artifacts/2-3-standard-track-targeting-contract-index-exact-name-selected-default.md:293]
- [x] [AI-Review][MEDIUM] Reconcile story File List with actual staged changes by removing or restoring `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineToolEnvelopeAtddTest.java` so story tracking matches git evidence. [_bmad-output/implementation-artifacts/2-3-standard-track-targeting-contract-index-exact-name-selected-default.md:324]
- [x] [AI-Review][MEDIUM] Add a targeted unit test for selected-track fallback when the selected track exists but is outside the materialized track-bank window, validating the cursor-based fallback payload path. [src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java:1418]

## Dev Notes

### Developer Context Section

- Story 2.3 is the first Epic 2 implementation story after kickoff gates G1-G6 closure.
- This story defines and operationalizes the standard track-targeting contract that Story 2.4 (`resolve_track`) and Story 2.5 (mutating-tool rollout) build on.
- Scope focus:
  - Standardize target resolution order and validation contract.
  - Remove behavior drift across tools that currently implement track targeting differently.
  - Keep Story 2.2 ambiguity guardrails intact while normalizing exact-name matching semantics.
- Out of scope:
  - Fuzzy matching candidate ranking (`resolve_track`) and ambiguity list UX (Story 2.4).
  - Broad mutating-tool contract rollout + docs flow examples (Story 2.5).

### Contract Semantics DoR

- Contract scope:
  - Track selector priority and fallback behavior are canonical and consistent across in-scope tools.
  - Dual-selector requests are supported with explicit confirmation semantics (`track_index` authoritative, `track_name` confirmation).
  - Exact-name matching semantics are deterministic: trim + case-insensitive exact match.
- Input contract:
  - `track_index` must be an integer, non-negative, within bounds (`INVALID_PARAMETER_INDEX` when invalid).
  - `track_name` must be non-empty after trim; exact normalized match required.
  - Providing both selectors is valid; return `INVALID_PARAMETER` only when provided `track_name` does not match the track resolved by `track_index`.
- Output/error contract:
  - Canonical MCP envelope (`status` + `data|error`) remains unchanged.
  - `error.operation` remains MCP tool name.
  - Missing selected fallback target returns `TRACK_NOT_FOUND`.
- Pass criteria:
  - All in-scope tools implement identical selector semantics and fallback behavior.
  - Duplicate-name ambiguity behavior from Story 2.2 remains explicit and safe.
  - Tests and docs demonstrate the same contract without contradiction.

### Runtime/Test/Docs Lockstep

- Runtime evidence required:
  - Shared resolver logic is applied consistently in `get_track_details`, `list_devices_on_track`, and `get_device_details` track-targeting paths.
  - `launch_clip` remains ambiguity-safe and compatible with standardized normalization rules.
- Test evidence required:
  - Unit/contract tests cover all AC paths (success + error) and ambiguity regression.
  - Error code mapping assertions verify index vs selector conflict semantics.
- Docs evidence required:
  - `docs/reference/api-reference.md` examples and parameter notes align exactly with implemented behavior.
- Parity check:
  - Runtime behavior, tests, and docs use the same selector precedence and error semantics.
- Failure criteria:
  - Any tool diverges from selector precedence/normalization/dual-selector confirmation rules.
  - Any mismatch between docs, tests, and runtime for contract behavior.

### Technical Requirements

- Canonical error semantics from `_bmad-output/project-context.md` are mandatory:
  - `INVALID_PARAMETER_INDEX` for index bounds/type-to-index conversion failures.
  - `INVALID_PARAMETER` for selector mismatch/ambiguous selection requiring explicit disambiguation.
  - `TRACK_NOT_FOUND` for missing exact-name matches and selected-track fallback absence.
- MCP tool handlers must remain on `McpErrorHandler` unified execution paths.
- Do not introduce ad-hoc envelopes or tool-specific incompatible targeting logic.
- Name matching rule for this story: exact after normalization (`trim` + case-insensitive compare); no fuzzy behavior.
- Preserve project-absolute track index semantics in response payloads where index is surfaced.

### Architecture Compliance

- Layering boundaries:
  - MCP tools parse/validate args and delegate.
  - Controllers own domain behavior and orchestration.
  - `BitwigApiFacade` owns Bitwig API access and track lookup behavior.
- Keep ambiguity safety enforcement from Story 2.2 intact while adding standard target resolution contract.
- Do not add direct Bitwig API calls in MCP tool classes.
- Keep deterministic behavior and canonical error handling as first-class design constraints.

### Library / Framework Requirements

- Runtime/test baseline for this story:
  - Java 21
  - Bitwig Extension API 19
  - MCP Java SDK BOM `0.11.0`
  - Jetty `11.0.20`
  - JUnit Jupiter `5.10.0`
- Latest-version context from G6 checkpoint (planning input only, not scope):
  - MCP SDK latest checked: `0.17.2`
  - Jetty 11 final: `11.0.26` (11.x EOL context captured)
  - JUnit latest checked: `5.14.3`
- No dependency upgrade work is in scope for Story 2.3.

### File Structure Requirements

- Expected runtime files likely touched:
  - `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsTool.java`
  - `src/main/java/io/github/fabb/wigai/features/ClipSceneController.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java`
- Expected tests likely touched:
  - `src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/ClipToolTest.java`
  - `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java`
- Required docs/evidence files:
  - `docs/reference/api-reference.md`
  - `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`
  - `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Testing Requirements

- Unit and contract test scenarios (mandatory):
  - `track_index` valid path resolves deterministic target.
  - `track_index` invalid/overflow/negative maps to `INVALID_PARAMETER_INDEX`.
  - `track_name` trim + case-insensitive exact-name matching works.
  - `track_name` exact-name miss maps to `TRACK_NOT_FOUND`.
  - No selectors uses selected-track fallback and succeeds when selected exists.
  - No selectors with no selected track maps to `TRACK_NOT_FOUND`.
  - Both selectors present with matching resolved track succeeds.
  - Both selectors present with name/index mismatch maps to `INVALID_PARAMETER`.
  - Duplicate exact names remain ambiguity-safe and require explicit `track_index` confirmation for mutation-capable path behavior.
- Documentation parity checks (mandatory):
  - Examples and parameter semantics in API docs match runtime behavior.
  - Error code table and behavior examples remain consistent with project context.

### Previous Story Intelligence

- Story 2.2 established strict ambiguity-safe behavior for duplicate exact-name tracks:
  - No implicit mutating action on ambiguity.
  - Deterministic candidate guidance and explicit `track_index` confirmation model.
- Reuse from Story 2.2:
  - Canonical error semantics discipline from `_bmad-output/project-context.md`.
  - Strong regression coverage expectations and evidence capture discipline.
- Forward implication:
  - Story 2.3 must standardize deterministic targeting semantics without weakening ambiguity guardrails introduced in Story 2.2.

### Git Intelligence Summary

- Recent commit history indicates Epic 2 kickoff governance completion and Story 2.2 closure.
- The current codebase already contains ambiguity-safe primitives (`findTrackIndexByName`, candidate guidance in `ClipTool`) that Story 2.3 should reuse rather than replace.
- Main drift risk identified from current code scan:
  - Multiple tool/facade paths still describe/implement case-sensitive track-name matching and inconsistent selector behavior.
  - Story 2.3 should resolve this by converging to one standard contract.

### Latest Tech Information

- G6 dependency checkpoint captured current-vs-latest awareness and deferred upgrades to avoid churn during Epic 2 targeting work.
- Story 2.3 implementation implication:
  - Keep changes focused on contract behavior/tests/docs.
  - Avoid coupling this story to dependency modernization work.
- Latest context source:
  - `_bmad-output/implementation-artifacts/dependency-version-refresh-checkpoint-2026-02-16.md`

### Project Context Reference

- Story source of truth:
  - `_bmad-output/planning-artifacts/epics.md` (Story 2.3 AC set)
- Canonical error/code and implementation rules:
  - `_bmad-output/project-context.md`
- Architecture constraints:
  - `_bmad-output/planning-artifacts/architecture.md`
- Kickoff gate and Epic 2 sequencing context:
  - `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`
- Prior-story context:
  - `_bmad-output/implementation-artifacts/2-2-duplicate-track-name-ambiguity-behavior-defined-and-tested.md`

### Story Completion Status

- Status set to `ready-for-dev`.
- Completion note: Ultimate context engine analysis completed - comprehensive developer guide created.

### Project Structure Notes

- Planned changes align to existing tool/controller/facade layering and avoid structural refactors.
- No conflicts with current project structure identified; work is focused on behavior convergence and contract lockstep.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-23-Standard-Track-Targeting-Contract-IndexExact-NameSelected-Default]
- [Source: _bmad-output/planning-artifacts/architecture.md]
- [Source: _bmad-output/project-context.md#Critical-Implementation-Rules]
- [Source: _bmad-output/project-context.md#Error-Code-Semantics-Single-Source-of-Truth]
- [Source: _bmad-output/implementation-artifacts/2-2-duplicate-track-name-ambiguity-behavior-defined-and-tested.md]
- [Source: _bmad-output/implementation-artifacts/2-1-contract-semantics-dor-and-runtime-test-docs-lockstep-gate-activation.md]
- [Source: _bmad-output/implementation-artifacts/dependency-version-refresh-checkpoint-2026-02-16.md]
- [Source: src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackTool.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsTool.java]
- [Source: src/main/java/io/github/fabb/wigai/features/ClipSceneController.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java]
- [Source: docs/reference/api-reference.md]

## Dev Agent Record

### Agent Model Used

- GPT-5 Codex (dev-story workflow)

### Debug Log References

- Workflow engine: `_bmad/core/tasks/workflow.xml`
- Workflow config: `_bmad/bmm/workflows/4-implementation/dev-story/workflow.yaml`
- Workflow instructions: `_bmad/bmm/workflows/4-implementation/dev-story/instructions.xml`
- Workflow checklist: `_bmad/bmm/workflows/4-implementation/dev-story/checklist.md`

### Completion Notes List

- Added shared selector parsing and normalization contract in `src/main/java/io/github/fabb/wigai/common/validation/TrackTargetingContract.java` (`track_index` -> `track_name` -> selected fallback, dual-selector validation, canonical index error mapping).
- Updated in-scope tools to consume shared contract semantics:
  - `src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsTool.java`
- Hardened runtime behavior and drift points:
  - `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java` now resolves normalized exact-name track matches, index-authoritative dual selectors, and selected-track fallback with guidance (`Provide track_index or track_name`).
  - `src/main/java/io/github/fabb/wigai/features/ClipSceneController.java` and `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java` preserve ambiguity-safe launch behavior while aligning non-ambiguous name confirmation to normalized exact matching.
- Updated docs contract lockstep in `docs/reference/api-reference.md`.
- Added/updated regression coverage for AC paths and error-code semantics in:
  - `src/test/java/io/github/fabb/wigai/common/validation/TrackTargetingContractTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/ClipToolTest.java`
  - `src/test/java/io/github/fabb/wigai/features/ClipSceneControllerTest.java`
  - `src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java`
  - `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java`
- Test evidence artifact added: `_bmad-output/implementation-artifacts/tests/story-2-3-contract-targeting-test-evidence-2026-02-16.md`.
- Validation executed:
  - `./gradlew test --tests "*TrackTargetingContractTest" --tests "*GetTrackDetailsToolTest" --tests "*ListDevicesOnTrackToolTest" --tests "*GetDeviceDetailsToolTest" --tests "*ClipToolTest" --tests "*ClipSceneControllerTest" --tests "*BitwigApiFacadeTest" --tests "*ErrorContractComplianceTest"` (pass)
  - `./gradlew test` (pass)
- Sprint tracking synchronized to in-progress then review for story key `2-3-standard-track-targeting-contract-index-exact-name-selected-default`.
- ✅ Resolved review finding [HIGH]: `get_track_details` now uses selected-track retrieval for selector-less requests, preserving cursor fallback semantics when selected project index is outside the current track-bank window.
- ✅ Resolved review finding [MEDIUM]: corrected contradictory `get_device_details` no-identifiers documentation and made selected-device default/`get_for_selected_device=false` constraints explicit.
- ✅ Resolved review finding [MEDIUM]: reconciled story artifact File List with staged changes by including `_bmad-output/planning-artifacts/epics.md`.
- ✅ Resolved review finding [MEDIUM]: reconciled File List with git evidence by removing stale `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineToolEnvelopeAtddTest.java` tracking from this story artifact.
- ✅ Resolved review finding [MEDIUM]: added targeted unit coverage for cursor-based selected-track fallback when selected project index is outside the materialized track-bank window (`testGetSelectedTrackDetails_SelectedTrackOutsideMaterializedBankUsesCursorFallbackPayload`).

### File List

- `_bmad-output/implementation-artifacts/2-3-standard-track-targeting-contract-index-exact-name-selected-default.md` (updated)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated - Story 2.3: ready-for-dev -> in-progress -> review)
- `_bmad-output/planning-artifacts/epics.md` (updated)
- `_bmad-output/implementation-artifacts/tests/story-2-3-contract-targeting-test-evidence-2026-02-16.md` (added)
- `src/main/java/io/github/fabb/wigai/common/validation/TrackTargetingContract.java` (added)
- `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java` (updated)
- `src/main/java/io/github/fabb/wigai/features/ClipSceneController.java` (updated)
- `src/main/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsTool.java` (updated)
- `src/main/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackTool.java` (updated)
- `src/main/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsTool.java` (updated)
- `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java` (updated)
- `docs/reference/api-reference.md` (updated)
- `src/test/java/io/github/fabb/wigai/common/validation/TrackTargetingContractTest.java` (added)
- `src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/features/ClipSceneControllerTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/mcp/tool/GetTrackDetailsToolTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackToolTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsToolTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/mcp/tool/ClipToolTest.java` (updated)

## Senior Developer Review (AI)

- Date: 2026-02-16
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 1 HIGH, 2 MEDIUM findings; action items added under `Review Follow-ups (AI)`.
- Verification: `./gradlew test --rerun-tasks` (pass)
- Date: 2026-02-16
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: Follow-up review accepted item 2 as non-issue; 2 MEDIUM items remain open and tracked under `Review Follow-ups (AI)`.
- Verification: `./gradlew test --rerun-tasks` (pass)
- Date: 2026-02-16
- Reviewer: Josh
- Outcome: Approved
- Summary: No remaining HIGH/MEDIUM findings. Runtime, tests, docs, and story tracking are in lockstep for Story 2.3.
- Verification: `./gradlew test --rerun-tasks` (pass)

## Change Log

- 2026-02-16: Implemented Story 2.3 standardized track-targeting contract across runtime/tools/tests/docs; full regression suite passing; story advanced to `review`.
- 2026-02-16: Senior developer review completed; follow-up action items added; story moved back to `in-progress`.
- 2026-02-16: Addressed code review findings - 3 items resolved; selector-less `get_track_details` selected-track fallback restored, docs contradiction removed, and story file list reconciled.
- 2026-02-16: Follow-up review accepted change-log snapshot convention; added 2 MEDIUM review action items and moved story to `in-progress`.
- 2026-02-16: Addressed follow-up review findings - removed stale `BaselineToolEnvelopeAtddTest` story tracking and added targeted selected-track-outside-bank cursor-fallback unit test; story returned to `review`.
- 2026-02-16: Final code review pass found no remaining HIGH/MEDIUM issues; story moved to `done`.
