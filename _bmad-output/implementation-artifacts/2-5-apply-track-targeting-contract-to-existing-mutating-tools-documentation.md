# Story 2.5: apply-track-targeting-contract-to-existing-mutating-tools-documentation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an external AI agent developer,
I want mutating tools to support the standard track targeting contract and have examples of the "resolve -> confirm -> act" flow,
so that automation remains safe and predictable as the tool surface expands.

## Acceptance Criteria

1. **Given** an existing mutating tool operates on a track (e.g., `launch_clip`)
   **When** the client provides `track_index`
   **Then** the tool accepts it as an alternative to `track_name` without breaking existing clients that still send `track_name`.

2. **Given** a mutating tool supports both `track_index` and `track_name`
   **When** both are provided in the same request
   **Then** the tool uses `track_index` as authoritative targeting and uses `track_name` as explicit confirmation (after trim + case-insensitive normalization), returning `INVALID_PARAMETER` only when the selectors do not refer to the same resolved track.

3. **Given** Epic 2 is implemented
   **When** documentation is updated
   **Then** `docs/reference/api-reference.md` includes a clear example showing the recommended workflow:
   1. Call `resolve_track` with a fuzzy query to get candidates.
   2. Ask the user to confirm the intended track.
   3. Call the mutating tool using `track_index` for unambiguous targeting.

## Tasks / Subtasks

- [x] Update mutating-tool selector contract for `launch_clip` to support `track_index` as an alternative selector (AC: 1, 2)
  - [x] Update `launch_clip` input schema and parsing in `ClipTool` so `track_index`-only and `track_name`-only requests are both valid.
  - [x] Keep backward compatibility for existing clients that send `track_name` + `clip_index`.
  - [x] When both selectors are present, route through dual-selector confirmation semantics (`track_index` authoritative, `track_name` confirmation).
  - [x] Return `INVALID_PARAMETER` only when both selectors are present and mismatch.

- [x] Apply selector-contract behavior in runtime/controller flow without weakening ambiguity guardrails (AC: 1, 2)
  - [x] Update `ClipSceneController` launch flow to support index-only targeting path (`track_index` + `clip_index`) for mutating actions.
  - [x] Keep name-only path deterministic: normalized exact-name match; unresolved duplicate-name ambiguity remains refusal with candidate guidance.
  - [x] Ensure success payload includes deterministic target metadata (`track_index`, resolved `track_name`, `clip_index`).

- [x] Keep Story 2.3 and Story 2.5 semantics explicitly aligned (AC: 2)
  - [x] Document and enforce that Story 2.5 mutating-tool behavior uses the same dual-selector confirmation semantics as the broader track-targeting contract.
  - [x] Prevent drift where any Story 2.5 text implies mutual-exclusion or selector-conflict errors for valid dual-selector requests.

- [x] Update API reference with recommended resolve-confirm-act workflow and explicit selector semantics (AC: 3)
  - [x] Update `launch_clip` parameter docs to state:
    - `track_index` can be used alone.
    - `track_name` can be used alone.
    - When both are present, `track_index` is authoritative and `track_name` is confirmation.
  - [x] Add/confirm a concrete end-to-end example: `resolve_track` candidates -> user confirmation -> mutating call using `track_index`.
  - [x] Clarify error-path differences:
    - dual-selector mismatch -> `INVALID_PARAMETER`
    - name-only ambiguity -> `INVALID_PARAMETER` with candidate guidance

- [x] Add runtime/tests/docs lockstep coverage and evidence (AC: 1, 2, 3)
  - [x] Extend `ClipToolTest` for selector alternatives (`track_index`-only success, `track_name`-only success, both selectors matching success, both selectors mismatch -> `INVALID_PARAMETER`).
  - [x] Extend `ClipSceneControllerTest` for index-only launch path, matching dual-selector success path, mismatch path, and name-only ambiguity refusal.
  - [x] Add/adjust contract tests (for example `ErrorContractComplianceTest`) to ensure error-code semantics and envelope details remain canonical.
  - [x] If smoke/ATDD checks assert request shape examples, update them in lockstep with the selector semantics.

### Review Follow-ups (AI) — Round 1

- [x] [AI-Review][HIGH] Add `_bmad-output/planning-artifacts/architecture.md` and `_bmad-output/planning-artifacts/epics.md` to File List — both are staged in git but missing from the Dev Agent Record File List
- [x] [AI-Review][MEDIUM] Add change log entry or completion note documenting that `epics.md` AC 2 wording was updated from "refuses with INVALID_PARAMETER" to dual-selector confirmation semantics — this is a material requirement text change
- [x] [AI-Review][MEDIUM] Add `ClipSceneControllerTest` case for index-only path with clip_index out of bounds: `launchClipWithSelectors(trackIndex, null, outOfBoundsClipIndex)` → `INVALID_PARAMETER_INDEX`
- [x] [AI-Review][MEDIUM] Add explicit `ClipSceneControllerTest` case for dual-selector matching success with exact name match (no trim/case difference) to satisfy the story's claimed lockstep coverage
- [x] [AI-Review][MEDIUM] Verify `ClipTool` success response always includes `track_name` field — current code omits it when both `result.getTrackName()` and `args.trackName()` are null [ClipTool.java:91-95]
- [x] [AI-Review][LOW] Replace `{{agent_model_name_version}}` placeholder in Dev Agent Record with actual model identifier
- [x] [AI-Review][LOW] Update or remove "Story Completion Status" section that still says "ready-for-dev" while story Status is "review"

### Review Follow-ups (AI) — Round 2

- [x] [AI-Review][MEDIUM] Sprint-status.yaml not synced: story says `review` but sprint tracker said `in-progress`
- [x] [AI-Review][MEDIUM] API docs don't document nullable `track_name` in `launch_clip` success response for index-only path — added field notes to api-reference.md
- [x] [AI-Review][LOW] `track_index` conditional inclusion asymmetry in success response — made `track_index` unconditionally included (same pattern as `track_name`)

### Review Follow-ups (AI) — Round 3

- [x] [AI-Review][MEDIUM] Dev Agent Record Completion Notes don't document round 2 fixes — add entries for: (a) API docs field notes for nullable `track_name`/`track_index`, (b) `track_index` unconditional inclusion
- [x] [AI-Review][LOW] Story Completion Status section (line ~251) only references round 1 follow-ups — update to note round 2 follow-ups also completed
- [x] [AI-Review][LOW] Change Log entries not in chronological order — dates alternate 02-17/02-16/02-17/02-16; sort consistently

## Dev Notes

### Developer Context Section

- Story 2.5 closes Epic 2 by applying safe, deterministic targeting semantics to existing mutating tools and documenting the operational workflow for MCP clients.
- Story 2.4 (`resolve_track`) is now available as the discovery primitive. Story 2.5 turns that into a documented and testable client workflow (`resolve -> confirm -> act`).
- Scope focus for this story is the currently shipped mutating track-targeted surface (`launch_clip`) plus docs/test lockstep.
- Out of scope:
  - New mutating tool creation (Epic 3+ scope)
  - Dependency modernization work (Epic 6 scope)

### Contract Semantics DoR

- Contract scope (mutating-tool Story 2.5 scope):
  - Selector alternatives: `track_index` or `track_name` are both supported.
  - Dual-selector confirmation: when both selectors are present, `track_index` is authoritative and `track_name` confirms the resolved indexed track after trim + case-insensitive normalization.
  - Name-based ambiguity remains safe: duplicate exact-name matches never mutate without explicit index confirmation.
- Input contract:
  - `clip_index` required and non-negative.
  - At least one selector must be provided (`track_index` or `track_name`).
  - `track_index` must satisfy canonical index validation semantics.
  - `track_name` must satisfy non-empty normalized exact-name semantics.
- Output/error contract:
  - Canonical MCP envelope (`status` + `data|error`) via unified handler.
  - `error.operation` must remain `launch_clip`.
  - `INVALID_PARAMETER` only for selector mismatch when both are present.
  - Ambiguous name-only resolution remains `INVALID_PARAMETER` with deterministic candidate guidance in `error.details`.
- Pass criteria:
  - Index-only targeting succeeds for mutating call path.
  - Backward-compatible name-only path still succeeds when unambiguous.
  - Dual-selector matching requests succeed; mismatch requests fail with `INVALID_PARAMETER`.
  - Runtime behavior, tests, and docs agree on the same selector rules.

### Runtime/Test/Docs Lockstep

- Runtime evidence required:
  - `launch_clip` implementation accepts index-only and name-only selector alternatives.
  - `launch_clip` dual-selector requests use index-authoritative confirmation behavior.
  - Name-only ambiguity refusal behavior remains unchanged and non-mutating.
- Test evidence required:
  - Tool-level validation and envelope tests for index-only, name-only, dual-selector match, and dual-selector mismatch.
  - Controller-level tests for index-only launch success and ambiguity-safe refusal behavior.
  - Error-contract coverage for mismatch and ambiguity details.
- Docs evidence required:
  - API reference parameter/behavior sections match runtime/test behavior exactly.
  - Recommended `resolve_track -> user confirm -> launch_clip(track_index)` flow is shown with concrete request/response examples.
- Failure criteria:
  - Runtime rejects valid dual-selector requests.
  - Runtime accepts dual-selector mismatch without `INVALID_PARAMETER`.
  - Docs/tests disagree with runtime selector semantics.

### Technical Requirements

- Current implementation context to update:
  - `ClipTool` currently requires `track_name` and treats `track_index` as optional confirmation.
  - `ClipSceneController` currently has `launchClip(trackName, clipIndex)` and `launchClip(trackName, clipIndex, trackIndex)` entry points.
- Required behavior changes:
  - Add selector parsing that supports index-only requests.
  - Preserve dual-selector confirmation semantics (`track_index` authoritative; `track_name` confirmation).
  - Return `INVALID_PARAMETER` only for selector mismatch when both are present.
  - Preserve canonical index validation errors (`INVALID_PARAMETER_INDEX` / `INVALID_PARAMETER_TYPE`) for malformed `track_index`.
  - Preserve ambiguity-safe behavior for name-only requests when duplicate exact names exist.
- Response consistency:
  - Success response should include resolved targeting details usable by clients for follow-up automation (`track_index`, resolved `track_name`, `clip_index`).
  - Error responses must stay on unified `McpErrorHandler` path.

### Architecture Compliance

- Maintain layering boundaries:
  - Tool layer: parse/validate request and delegate.
  - Controller layer: enforce domain behavior.
  - Facade layer: Bitwig API interactions and track lookup behavior.
- Do not introduce direct Bitwig API calls in MCP tool classes.
- Keep snake_case MCP names/fields and canonical envelope semantics.
- Keep ambiguity refusal non-mutating and deterministic.

### Library / Framework Requirements

- In-scope project baselines:
  - Java 21
  - Bitwig Extension API 19
  - MCP Java SDK BOM `0.11.0`
  - Jetty `11.0.20`
  - JUnit Jupiter `5.10.0`
- Latest ecosystem context (planning awareness only, not scope):
  - MCP Java SDK latest release listed: `v0.17.2` (January 22, 2026).
  - Jetty latest release listed: `12.1.6` (February 6, 2026).
  - Jetty maintainers indicate Jetty 9/10/11 are no longer published to Maven Central starting January 1, 2026.
  - JUnit release notes list latest stable as `6.0.3`, with `5.14.3` as latest in JUnit 5 line.
- Scope guardrail:
  - Do not couple Story 2.5 behavior work to dependency upgrade work.

### File Structure Requirements

- Expected runtime files likely touched:
  - `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java`
  - `src/main/java/io/github/fabb/wigai/features/ClipSceneController.java`
  - `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java` (only if helper changes are required for index-only metadata or selector handling)
  - `src/main/java/io/github/fabb/wigai/common/validation/TrackTargetingContract.java` (only if mutating-tool-specific selector rules are centralized)
- Expected tests likely touched:
  - `src/test/java/io/github/fabb/wigai/mcp/tool/ClipToolTest.java`
  - `src/test/java/io/github/fabb/wigai/features/ClipSceneControllerTest.java`
  - `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java`
  - `src/test/java/io/github/fabb/wigai/smoke/McpSmokeHarness*.java` (if request examples/expectations require updates)
- Required docs/artifacts:
  - `docs/reference/api-reference.md`
  - `_bmad-output/implementation-artifacts/sprint-status.yaml`
  - `_bmad-output/planning-artifacts/epics.md` (AC source)

### Testing Requirements

- Selector contract tests:
  - `track_index`-only + valid `clip_index` launches successfully.
  - `track_name`-only + valid `clip_index` launches successfully when unambiguous.
  - Both selectors matching succeed (index authoritative; name confirmation).
  - Both selectors mismatching return `INVALID_PARAMETER` and perform no mutation.
  - Neither selector present returns request validation error and performs no mutation.
- Ambiguity safety tests:
  - Duplicate-name name-only requests return `INVALID_PARAMETER` with `confirmation_parameter=track_index` and deterministic `candidates`.
  - Ambiguity path does not call launch methods.
- Validation/error tests:
  - Malformed `track_index` values map to canonical error codes.
  - `error.operation` remains `launch_clip`.
- Docs lockstep checks:
  - `launch_clip` docs reflect index-authoritative dual-selector confirmation semantics.
  - `resolve -> confirm -> act` workflow example is consistent with tested runtime behavior.

### Previous Story Intelligence

- Story 2.2 established ambiguity-safe mutation behavior:
  - Ambiguous name-only targeting must refuse mutation.
  - Candidate guidance uses `track_index` confirmation semantics.
- Story 2.3 established shared track-targeting contract across non-mutating surfaces:
  - Dual-selector confirmation semantics are standard and should remain consistent for Story 2.5.
- Story 2.4 introduced `resolve_track`:
  - Deterministic candidate list (`exact`/`prefix`/`substring`) is now available for pre-mutation confirmation workflow.

### Git Intelligence Summary

- Recent commits indicate Epic 2 progression and strong lockstep discipline:
  - `bc583a2`: added `resolve_track` and deterministic fuzzy matching.
  - `a314e86`: standardized track-targeting contract roll-out.
  - `4d3f5f2`: contract semantics DoR and runtime/test/docs lockstep gate activation.
- Practical implication:
  - Story 2.5 should be a narrow, contract-focused adaptation with explicit docs/test parity updates.

### Latest Tech Information

- Official-source snapshot used for story preparation:
  - MCP Java SDK releases: `v0.17.2` latest listed (Jan 22, 2026).
  - Jetty releases: `12.1.6` latest listed (Feb 6, 2026).
  - Jetty issue/update: Jetty 9/10/11 no longer published to Maven Central starting Jan 1, 2026.
  - JUnit release notes: latest stable `6.0.3`; JUnit 5 line latest `5.14.3`.
- Implementation implication:
  - Keep Story 2.5 scoped to behavior/docs/tests.
  - Keep dependency modernization in dedicated Epic 6 path.

### Project Context Reference

- Story source of truth:
  - `_bmad-output/planning-artifacts/epics.md` (Story 2.5 AC set)
- Canonical implementation and error semantics:
  - `_bmad-output/project-context.md`
- Architecture guardrails:
  - `_bmad-output/planning-artifacts/architecture.md`
- Previous story context:
  - `_bmad-output/implementation-artifacts/2-2-duplicate-track-name-ambiguity-behavior-defined-and-tested.md`
  - `_bmad-output/implementation-artifacts/2-3-standard-track-targeting-contract-index-exact-name-selected-default.md`
  - `_bmad-output/implementation-artifacts/2-4-resolve-track-tool-deterministic-fuzzy-matching-candidate-list-ambiguity.md`

### Story Completion Status

- Status updated to `review` after all AI-review follow-up actions were completed and validated.
- Completion note: Review follow-ups were applied across rounds 1-3 (tests/tool payload consistency, API-doc nullable field notes, track-index inclusion consistency, and story tracking metadata), and regression gates remained green.

### Project Structure Notes

- Work is additive and localized to existing tool/controller/docs/test layers.
- Main regression risk is selector-rule drift between runtime and docs; lockstep checks above are mandatory.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-25-Apply-Track-Targeting-Contract-to-Existing-Mutating-Tools--Documentation]
- [Source: _bmad-output/project-context.md#Error-Code-Semantics-Single-Source-of-Truth]
- [Source: _bmad-output/planning-artifacts/architecture.md]
- [Source: _bmad-output/implementation-artifacts/2-2-duplicate-track-name-ambiguity-behavior-defined-and-tested.md]
- [Source: _bmad-output/implementation-artifacts/2-3-standard-track-targeting-contract-index-exact-name-selected-default.md]
- [Source: _bmad-output/implementation-artifacts/2-4-resolve-track-tool-deterministic-fuzzy-matching-candidate-list-ambiguity.md]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java]
- [Source: src/main/java/io/github/fabb/wigai/features/ClipSceneController.java]
- [Source: src/main/java/io/github/fabb/wigai/common/validation/TrackTargetingContract.java]
- [Source: src/test/java/io/github/fabb/wigai/mcp/tool/ClipToolTest.java]
- [Source: src/test/java/io/github/fabb/wigai/features/ClipSceneControllerTest.java]
- [Source: docs/reference/api-reference.md]
- [Source: https://github.com/modelcontextprotocol/java-sdk/releases]
- [Source: https://github.com/jetty/jetty.project/releases]
- [Source: https://github.com/jetty/jetty.project/issues/13918]
- [Source: https://docs.junit.org/current/release-notes/index.html]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `./gradlew test --tests io.github.fabb.wigai.mcp.tool.ClipToolTest --tests io.github.fabb.wigai.features.ClipSceneControllerTest --tests io.github.fabb.wigai.contract.ErrorContractComplianceTest` (initial red run: expected compile failures before implementation)
- `./gradlew test --tests io.github.fabb.wigai.mcp.tool.ClipToolTest --tests io.github.fabb.wigai.features.ClipSceneControllerTest --tests io.github.fabb.wigai.contract.ErrorContractComplianceTest` (green after implementation)
- `./gradlew test` (full regression suite passed)
- `./gradlew check` (quality gate passed)
- `./gradlew test --tests io.github.fabb.wigai.mcp.tool.ClipToolTest --tests io.github.fabb.wigai.features.ClipSceneControllerTest` (review follow-up coverage tests passed)
- `./gradlew test` (full regression suite passed after review follow-up changes)
- `./gradlew check` (quality gate re-validated after follow-up changes)
- `./gradlew test` (final regression re-run at completion gate; up-to-date pass)

### Completion Notes List

- Implemented selector alternatives for `launch_clip` in `ClipTool`: `clip_index` + (`track_index` or `track_name`), including schema updates and missing-selector validation.
- Preserved backward compatibility for existing `track_name` + `clip_index` callers and dual-selector path (`track_index` authoritative, `track_name` confirmation).
- Added index-only runtime path via `ClipSceneController.launchClipWithSelectors(...)`; ambiguity-safe name-only behavior remains unchanged.
- Extended success metadata path so launch responses can include deterministic targeting details (`track_index`, resolved `track_name`, `clip_index`).
- Updated API reference `launch_clip` contract and added explicit `resolve -> confirm -> act` workflow example.
- Added/updated lockstep tests:
  - `ClipToolTest`: index-only success path and missing-selector validation
  - `ClipSceneControllerTest`: index-only controller success and resolved track-name metadata
  - `ErrorContractComplianceTest`: missing-selector and dual-selector mismatch contract coverage
- Full regression and quality gates passed (`test`, `check`).
- Added review follow-up coverage for index-only clip-index bounds and dual-selector exact-match success in `ClipSceneControllerTest`.
- Verified `launch_clip` success payload always includes `track_name` key.
- Recorded that Story 2.5 AC2 wording in `_bmad-output/planning-artifacts/epics.md` is aligned to dual-selector confirmation semantics.

### File List

- `_bmad-output/implementation-artifacts/2-5-apply-track-targeting-contract-to-existing-mutating-tools-documentation.md` (updated)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated)
- `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java` (updated)
- `src/main/java/io/github/fabb/wigai/features/ClipSceneController.java` (updated)
- `src/test/java/io/github/fabb/wigai/mcp/tool/ClipToolTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/features/ClipSceneControllerTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java` (updated)
- `docs/reference/api-reference.md` (updated)
- `_bmad-output/planning-artifacts/architecture.md` (updated, pre-existing staged change tracked for review completeness)
- `_bmad-output/planning-artifacts/epics.md` (updated, AC2 wording alignment tracked for review completeness)

### Change Log

- 2026-02-16: Code review (AI) — 7 findings (1 HIGH, 4 MEDIUM, 2 LOW). Action items added to Tasks/Subtasks. Status → in-progress pending follow-up fixes.
- 2026-02-16: Code review round 2 (AI) — 4 findings (2 MEDIUM, 2 LOW). All 3 code/doc issues fixed inline; 1 LOW (unstaged changes) deferred to dev. Sprint-status sync confirmed. API docs updated for nullable `track_name`/`track_index` in success response. `track_index` inclusion made unconditional.
- 2026-02-16: Code review round 3 (AI) — 3 findings (1 MEDIUM, 2 LOW). Action items added to Tasks/Subtasks for Dev Agent Record completeness and Change Log ordering. Story remains at `review`.
- 2026-02-17: Implemented Story 2.5 selector-contract rollout for `launch_clip` with runtime/test/docs lockstep and deterministic resolve-confirm-act documentation.
- 2026-02-17: Resolved all Story 2.5 AI-review follow-ups (file-list completeness, AC2 wording tracking note, additional controller coverage, `track_name` success-key consistency, and round-3 Dev Agent Record/Change Log alignment fixes).
