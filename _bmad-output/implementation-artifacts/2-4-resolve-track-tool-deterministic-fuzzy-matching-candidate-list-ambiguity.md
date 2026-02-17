# Story 2.4: resolve-track-tool-deterministic-fuzzy-matching-candidate-list-ambiguity

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an external AI agent developer,
I want WigAI to resolve a fuzzy track query into candidate tracks,
so that I can ask the user to confirm the intended track before executing actions.

## Acceptance Criteria

1. **Given** the client calls `resolve_track` with a required `query` string  
   **When** WigAI searches tracks  
   **Then** matching is deterministic and uses simple, predictable rules: case-insensitive exact match, then case-insensitive prefix match, then case-insensitive substring match.

2. **Given** `resolve_track` finds one or more matches  
   **When** it returns success  
   **Then** `data` includes an ordered `candidates` list where each entry includes at minimum `track_index`, `track_name`, and `match_type` (`exact`, `prefix`, or `substring`).

3. **Given** `resolve_track` finds multiple plausible matches  
   **When** it returns success  
   **Then** it clearly signals ambiguity (e.g., `ambiguous=true`) and does not select a track implicitly.

4. **Given** multiple tracks share the same exact `track_name`  
   **When** `resolve_track` returns  
   **Then** it is treated as ambiguous and the client must confirm using `track_index` (even if the name matches exactly).

5. **Given** `resolve_track` finds no matches  
   **When** it returns  
   **Then** it returns a standardized error with `TRACK_NOT_FOUND` and an actionable message (e.g., suggesting `list_tracks`).

## Tasks / Subtasks

- [x] Implement a new read-only MCP tool `resolve_track` with strict schema + deterministic output contract (AC: 1, 2, 3, 4, 5)
  - [x] Add `ResolveTrackTool` in `src/main/java/io/github/fabb/wigai/mcp/tool/` with required `query` input, optional non-mutating `limit` only if explicitly approved in implementation scope, and unified envelope handling via `McpErrorHandler`.
  - [x] Register `resolve_track` in `McpServerManager.allToolSpecifications(...)` so runtime/tool-discovery/test-discovery stay in lockstep.
  - [x] Ensure the tool always returns canonical MCP envelope shapes (`status` + `data|error`) and `error.operation="resolve_track"` on failure paths.

- [x] Implement deterministic candidate resolution in runtime layer without mutating behavior (AC: 1, 2, 3, 4)
  - [x] Add/extend facade/controller method(s) to scan tracks and compute match class by precedence: `exact` > `prefix` > `substring`.
  - [x] Guarantee deterministic ordering across runs: primary sort by `match_type` precedence, secondary by ascending `track_index`.
  - [x] Ensure candidate entries include `track_index`, `track_name`, and `match_type` exactly.
  - [x] Set `ambiguous=true` whenever result set has more than one candidate; never auto-select or mutate.

- [x] Preserve and align ambiguity contract semantics from Epic 2 Story 2.2/2.3 (AC: 3, 4)
  - [x] Keep duplicate exact-name behavior explicitly ambiguous with index-confirmation expectation.
  - [x] Keep candidate data machine-parseable and structurally aligned with existing ambiguity contracts (`track_index`, `track_name`).
  - [x] Do not weaken existing mutating-tool safety behavior (`launch_clip` ambiguity refusal remains intact).

- [x] Implement no-match and validation error behavior with canonical error codes/messages (AC: 5)
  - [x] Missing `query` -> `MISSING_REQUIRED_PARAMETER`.
  - [x] Empty/blank `query` -> `EMPTY_PARAMETER`.
  - [x] No matches -> `TRACK_NOT_FOUND` with actionable guidance (reference `list_tracks`).
  - [x] Keep index/range semantics untouched for unrelated selectors (`INVALID_PARAMETER_INDEX` vs `INVALID_RANGE`) per project-context canonical definitions.

- [x] Add full runtime/tests/docs lockstep coverage for `resolve_track` (AC: 1, 2, 3, 4, 5)
  - [x] Add dedicated tool-level tests (`ResolveTrackToolTest`) for schema, envelope, and success/error contract.
  - [x] Add facade/controller tests for deterministic precedence, deterministic order, duplicate-name ambiguity signaling, and no-match behavior.
  - [x] Update `docs/reference/api-reference.md` with request/response/error examples for `resolve_track`.
  - [x] Update smoke/contract surfaces as needed (tool registration parity, harness expectations, and error contract tests) so CI detects drift immediately.

### Review Follow-ups (AI)

- [x] [AI-Review][MEDIUM] `resolveTrack` does not paginate track bank — only scans first materialized window, unlike `getAllTracksInfo` which scrolls. Tracks beyond visible window silently missed. [BitwigApiFacade.java:333]
- [x] [AI-Review][MEDIUM] `testSuccessfulCallReturnsOrderedCandidates` validates trim via mock argument matching (`eq("drum")`) rather than explicit trim-contract assertion — brittle if `validateNotEmpty` trim behavior changes. [ResolveTrackToolTest.java:83-88]
- [x] [AI-Review][MEDIUM] `INVALID_PARAMETER_TYPE` listed in api-reference.md errors for `resolve_track` but never thrown by runtime or tested — docs/runtime lockstep violation. [api-reference.md:484]
- [x] [AI-Review][LOW] No single-candidate test for `ambiguous=false` — multi-candidate and no-match are covered but the unambiguous happy path is not explicitly tested. [BitwigApiFacade.java:374]
- [x] [AI-Review][LOW] Story Completion Status subsection still says `ready-for-dev` while story Status is `review` — stale text. [story:244]
- [x] [AI-Review][LOW] `ResolveTrackCandidate.matchPrecedence()` default case (`Integer.MAX_VALUE`) is unreachable dead code. [BitwigApiFacade.java:284]

### Review Follow-ups (AI) — Re-review

- [x] [AI-Review][LOW] ErrorContractComplianceTest Javadoc header (lines 36-43) omits `INVALID_PARAMETER_TYPE` from its taxonomy summary despite the file now testing it via `InvalidParameterTypeContract`. [ErrorContractComplianceTest.java:36-43]
- [x] [AI-Review][LOW] `testResolveTrack_PaginatesBeyondVisibleWindow` serves double duty as both pagination verification AND the only single-candidate `ambiguous=false` assertion — coupling risk if pagination test is refactored. [BitwigApiFacadeTest.java:1507]
- [x] [AI-Review][LOW] Story Completion Status note still says "Ultimate context engine analysis completed - comprehensive developer guide created" — stale dev-agent boilerplate. [story:254]

## Dev Notes

### Developer Context Section

- Story 2.4 is the first Epic 2 story that introduces fuzzy matching as a dedicated non-mutating primitive.
- Story 2.2 established ambiguity-safe behavior for duplicate names; Story 2.3 standardized selector semantics (`track_index`, `track_name`, selected fallback).
- Story 2.4 must extend those guardrails, not bypass them:
  - Provide deterministic candidate discovery.
  - Preserve explicit confirmation flow via `track_index`.
  - Keep mutating tools unchanged unless explicitly part of Story 2.5.
- Out of scope:
  - Applying this new flow to all mutating tools (Story 2.5).
  - Dependency upgrade/migration work (Epic 6 and dependency refresh checkpoint artifacts).

### Contract Semantics DoR

- Contract scope:
  - `resolve_track` is read-only and never performs a mutating side effect.
  - Candidate generation uses deterministic, documented matching precedence and ordering.
  - Ambiguity is explicit (`ambiguous=true`) and never auto-resolved.
- Input contract:
  - `query` is required and non-empty after trim.
  - Matching is case-insensitive and normalization-aware; exact/prefix/substring are applied in that order.
- Output/error contract:
  - Success includes ordered `candidates` with required keys: `track_index`, `track_name`, `match_type`.
  - No-match returns `TRACK_NOT_FOUND` with actionable guidance.
  - `error.operation` is always `resolve_track`.
- Pass criteria:
  - Repeated runs over unchanged host state produce identical candidate ordering.
  - Duplicate exact-name matches are explicitly ambiguous and require index confirmation by client follow-up flow.
  - Runtime behavior, tests, and docs all encode the same rules.

### Runtime/Test/Docs Lockstep

- Runtime evidence required:
  - Tool registration path includes `resolve_track`.
  - Deterministic precedence/ordering logic is implemented once (shared runtime path), not duplicated inconsistently.
- Test evidence required:
  - AC-level tests cover exact/prefix/substring precedence, ambiguity signaling, and no-match error semantics.
  - Contract tests assert response envelope and required candidate keys.
  - Regression tests ensure Story 2.2/2.3 ambiguity and selector contracts remain intact.
- Docs evidence required:
  - API reference documents schema, match precedence, candidate shape, ambiguity semantics, and no-match error example.
  - Docs examples align with tested runtime payloads exactly.
- Failure criteria:
  - Any nondeterministic ordering.
  - Any implicit track selection hidden behind success responses.
  - Any runtime/docs/tests mismatch on required candidate fields or error code semantics.

### Technical Requirements

- Keep implementation within existing layered boundaries:
  - MCP tool parses + validates args.
  - Feature/controller/facade layer owns resolution behavior.
  - No direct Bitwig API calls from MCP tool classes.
- Reuse existing normalization primitives where possible (`TrackTargetingContract.normalizeTrackName`, `namesMatchNormalized`).
- Deterministic matching contract:
  - `exact`: normalized full-string equality.
  - `prefix`: normalized candidate starts with normalized query.
  - `substring`: normalized candidate contains normalized query.
  - Sort by `exact` then `prefix` then `substring`, then by ascending `track_index`.
- Candidate payload minimum:
  - `track_index` (int), `track_name` (string), `match_type` (`exact|prefix|substring`).
- Error semantics:
  - `MISSING_REQUIRED_PARAMETER` for absent `query`.
  - `EMPTY_PARAMETER` for blank `query`.
  - `TRACK_NOT_FOUND` for zero matches.
  - Keep canonical semantics from `_bmad-output/project-context.md` for all other codes.

### Architecture Compliance

- Register tool through the same authoritative registration list used by production and tests:
  - `McpServerManager.allToolSpecifications(...)`.
- Keep unified error handling:
  - Use `McpErrorHandler.executeWithValidation(...)` or equivalent canonical path.
- Keep payload consistency:
  - Top-level envelope conventions remain unchanged.
  - Candidate object keys and order should be stable to simplify clients/tests.
- Preserve existing Story 2.2/2.3 guardrails:
  - `launch_clip` ambiguity safety remains explicit and unbroken.
  - Selector confirmation semantics are not redefined in this story.

### Library / Framework Requirements

- Current project baselines remain in scope for implementation:
  - Java 21
  - Bitwig Extension API 19
  - MCP Java SDK BOM `0.11.0`
  - Jetty `11.0.20`
  - JUnit Jupiter `5.10.0`
- Latest ecosystem check (for planning awareness only, not upgrade scope in this story):
  - MCP Java SDK latest release shown as `v0.17.2` (January 22, 2026) in GitHub releases.
  - Jetty latest release shown as `12.1.6` (January 30, 2026) in GitHub releases.
  - Jetty maintainers state that starting **January 1, 2026**, Jetty 9/10/11 are no longer published to Maven Central.
  - JUnit release notes indicate latest stable line beyond project pin (`6.0.x` with newer stable listed), while project currently remains on JUnit 5.x.
- Implementation implication:
  - Keep Story 2.4 strictly focused on behavior/tests/docs contract delivery.
  - Defer dependency migration to dedicated upgrade stories/checkpoints.

### File Structure Requirements

- Expected runtime files likely touched:
  - `src/main/java/io/github/fabb/wigai/mcp/tool/ResolveTrackTool.java` (new)
  - `src/main/java/io/github/fabb/wigai/mcp/McpServerManager.java` (tool registration update)
  - `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java` (deterministic candidate resolution support)
  - `src/main/java/io/github/fabb/wigai/common/validation/TrackTargetingContract.java` (optional shared matching helpers if needed)
- Expected test files likely touched:
  - `src/test/java/io/github/fabb/wigai/mcp/tool/ResolveTrackToolTest.java` (new)
  - `src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java` (resolution behavior coverage)
  - `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java` (contract parity)
  - `src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTest.java` (if envelope/error-path coverage expands)
  - `src/test/java/io/github/fabb/wigai/smoke/McpSmokeHarness*.java` (if baseline/harness expectations are updated)
- Required docs/artifacts:
  - `docs/reference/api-reference.md`
  - `_bmad-output/implementation-artifacts/sprint-status.yaml`
  - `_bmad-output/planning-artifacts/epics.md` (source of AC truth)

### Testing Requirements

- Deterministic matching tests:
  - Exact match beats prefix/substring.
  - Prefix beats substring.
  - Same-match-type candidates are ordered by ascending `track_index`.
- Ambiguity tests:
  - Multiple candidates -> `ambiguous=true` with no implicit target selection.
  - Duplicate exact-name tracks always produce ambiguity and candidate list.
- Validation/error tests:
  - Missing query -> `MISSING_REQUIRED_PARAMETER`.
  - Empty query -> `EMPTY_PARAMETER`.
  - No match -> `TRACK_NOT_FOUND` with actionable message.
  - `error.operation` equals `resolve_track`.
- Regression/compatibility tests:
  - Existing Story 2.2/2.3 behavior for `launch_clip` and selector semantics remains unchanged.
  - Tools/list registration includes `resolve_track`; docs examples match runtime payload shape.

### Previous Story Intelligence

- From Story 2.2:
  - Ambiguous track naming must never imply mutation.
  - Candidate guidance (`track_index`, `track_name`) is the established disambiguation pattern.
- From Story 2.3:
  - Canonical targeting semantics are standardized across in-scope tools.
  - Exact-name normalization is trim + case-insensitive; keep this normalization consistent.
- Practical implication for Story 2.4:
  - `resolve_track` is the discovery primitive for safe user confirmation, not an auto-selector.
  - Do not create parallel/competing selector semantics or ad-hoc candidate schemas.

### Git Intelligence Summary

- Recent history (`a314e86`, `4d3f5f2`, `83f57eb`, `8c297d5`, `eac9111`) shows strong emphasis on contract lockstep and Epic 2 safety gates.
- Existing runtime already has ambiguity-aware exact-name candidate logic in `BitwigApiFacade`; Story 2.4 should extend this capability for deterministic fuzzy tiers rather than reinventing from scratch.
- There is no existing `resolve_track` tool registered in current MCP tool list; Story 2.4 introduces it as a new read-only capability.
- Registration/test-discovery single-source pattern in `McpServerManager.allToolSpecifications(...)` must remain authoritative.

### Latest Tech Information

- Web verification snapshot for planning awareness (captured during story prep):
  - MCP Java SDK GitHub releases list `v0.17.2` as latest (published January 22, 2026).
  - Jetty GitHub releases list `12.1.6` as latest (published January 30, 2026).
  - Jetty maintainers updated EOL guidance stating no further Jetty 9/10/11 Maven Central publishing starting January 1, 2026.
  - JUnit 6.0.2 release notes (January 6, 2026) indicate newer stable release availability in the 6.0.x line.
- Story 2.4 execution guardrail:
  - Treat these as risk/roadmap context only.
  - Keep this story focused on deterministic `resolve_track` behavior and lockstep docs/tests.

### Project Context Reference

- Story source of truth:
  - `_bmad-output/planning-artifacts/epics.md` (Story 2.4 acceptance criteria)
- Canonical implementation and error-contract rules:
  - `_bmad-output/project-context.md`
- Architecture/layering guardrails:
  - `_bmad-output/planning-artifacts/architecture.md`
- Previous implementation intelligence:
  - `_bmad-output/implementation-artifacts/2-2-duplicate-track-name-ambiguity-behavior-defined-and-tested.md`
  - `_bmad-output/implementation-artifacts/2-3-standard-track-targeting-contract-index-exact-name-selected-default.md`

### Story Completion Status

- Status set to `review`.
- Completion note: Story implementation and re-review follow-up resolution completed; ready for code review.

### Project Structure Notes

- Planned implementation fits existing MCP tool -> controller/facade layering.
- No structural refactor is required; work is additive and contract-focused.
- Main drift risk is docs/tests/runtime mismatch on candidate schema or ordering; lockstep checks above are mandatory.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-24-resolve_track-Tool-Deterministic-Fuzzy-Matching--Candidate-List--Ambiguity]
- [Source: _bmad-output/project-context.md#Critical-Implementation-Rules]
- [Source: _bmad-output/project-context.md#Error-Code-Semantics-Single-Source-of-Truth]
- [Source: _bmad-output/planning-artifacts/architecture.md]
- [Source: _bmad-output/implementation-artifacts/2-2-duplicate-track-name-ambiguity-behavior-defined-and-tested.md]
- [Source: _bmad-output/implementation-artifacts/2-3-standard-track-targeting-contract-index-exact-name-selected-default.md]
- [Source: src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/McpServerManager.java]
- [Source: src/main/java/io/github/fabb/wigai/common/validation/TrackTargetingContract.java]
- [Source: src/test/java/io/github/fabb/wigai/smoke/McpSmokeHarness.java]
- [Source: docs/reference/api-reference.md]
- [Source: https://github.com/modelcontextprotocol/java-sdk/releases]
- [Source: https://github.com/jetty/jetty.project/releases]
- [Source: https://github.com/jetty/jetty.project/issues/13918]
- [Source: https://docs.junit.org/6.0.2/release-notes.html]

## Dev Agent Record

### Agent Model Used

- GPT-5 Codex (dev-story workflow)

### Debug Log References

- Workflow engine: `_bmad/core/tasks/workflow.xml`
- Workflow config: `_bmad/bmm/workflows/4-implementation/dev-story/workflow.yaml`
- Workflow instructions: `_bmad/bmm/workflows/4-implementation/dev-story/instructions.xml`
- Workflow checklist: `_bmad/bmm/workflows/4-implementation/dev-story/checklist.md`
- Validation command: `./gradlew test --tests io.github.fabb.wigai.mcp.tool.ResolveTrackToolTest --tests io.github.fabb.wigai.bitwig.BitwigApiFacadeTest --tests io.github.fabb.wigai.contract.ErrorContractComplianceTest --tests io.github.fabb.wigai.smoke.McpSmokeHarnessSafeModeTest --tests io.github.fabb.wigai.smoke.McpSmokeHarnessAtddTest`
- Red/Green command: `./gradlew test --tests 'io.github.fabb.wigai.bitwig.BitwigApiFacadeTest.testResolveTrack_PaginatesBeyondVisibleWindow'`
- Red/Green command: `./gradlew test --tests io.github.fabb.wigai.mcp.tool.ResolveTrackToolTest`
- Red/Green command: `./gradlew test --tests io.github.fabb.wigai.mcp.tool.ResolveTrackToolTest --tests io.github.fabb.wigai.contract.ErrorContractComplianceTest`
- Validation command: `./gradlew test --tests io.github.fabb.wigai.contract.ErrorContractComplianceTest`
- Red/Green command: `./gradlew test --tests io.github.fabb.wigai.bitwig.BitwigApiFacadeTest.testResolveTrack_SingleCandidateSetsAmbiguousFalse`
- Regression command: `./gradlew test`

### Completion Notes List

- Implemented read-only MCP tool `resolve_track` with strict required `query` schema and canonical envelope/error behavior via `McpErrorHandler.executeWithValidation`.
- Added deterministic fuzzy resolution runtime path in `BitwigApiFacade.resolveTrack(...)` with precedence `exact > prefix > substring` and stable ordering by `track_index`.
- Enforced ambiguity semantics: result payload always includes ordered `candidates`; `ambiguous=true` when candidate count > 1; no implicit target selection/mutation.
- Implemented canonical no-match behavior: `TRACK_NOT_FOUND` with actionable `list_tracks` guidance.
- Added lockstep coverage across tool tests, facade tests, contract tests, and smoke harness baseline expectations.
- Updated API reference with request/response/error examples for `resolve_track`.
- ✅ Resolved review finding [MEDIUM]: paginated `resolveTrack` candidate discovery across the full track bank with deterministic project-indexed candidates beyond the first visible page.
- ✅ Resolved review finding [MEDIUM]: hardened `ResolveTrackToolTest` by explicitly asserting trimmed query propagation to facade via captured argument.
- ✅ Resolved review finding [MEDIUM]: added explicit `resolve_track` invalid-type coverage (tool + contract tests) to keep `INVALID_PARAMETER_TYPE` docs/runtime/tests in lockstep.
- ✅ Resolved review finding [LOW]: added explicit single-candidate `ambiguous=false` assertion coverage in `BitwigApiFacadeTest`.
- ✅ Resolved review finding [LOW]: updated Story Completion Status subsection from `ready-for-dev` to `review` to remove stale status text.
- ✅ Resolved review finding [LOW]: removed dead default branch from `ResolveTrackCandidate.matchPrecedence()` and replaced it with explicit unsupported-value guard.
- ✅ Resolved re-review finding [LOW]: aligned `ErrorContractComplianceTest` taxonomy Javadoc with implemented `INVALID_PARAMETER_TYPE` contract coverage.
- ✅ Resolved re-review finding [LOW]: added dedicated non-pagination single-candidate `ambiguous=false` coverage in `BitwigApiFacadeTest`.
- ✅ Resolved re-review finding [LOW]: refreshed Story Completion Status note to reflect final implementation/re-review state.
- Full regression suite passed on 2026-02-17 via `./gradlew test`.

### File List

- `src/main/java/io/github/fabb/wigai/mcp/tool/ResolveTrackTool.java` (new)
- `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java` (updated)
- `src/main/java/io/github/fabb/wigai/mcp/McpServerManager.java` (updated)
- `src/test/java/io/github/fabb/wigai/mcp/tool/ResolveTrackToolTest.java` (new)
- `src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/smoke/McpSmokeHarness.java` (updated)
- `src/test/java/io/github/fabb/wigai/smoke/McpSmokeHarnessTestSupport.java` (updated)
- `src/test/java/io/github/fabb/wigai/smoke/McpSmokeHarnessSafeModeTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/smoke/McpSmokeHarnessAtddTest.java` (updated)
- `src/test/java/io/github/fabb/wigai/mcp/McpServerManagerTest.java` (updated)
- `docs/reference/api-reference.md` (updated)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated - Story 2.4: ready-for-dev -> in-progress -> review)
- `_bmad-output/implementation-artifacts/2-4-resolve-track-tool-deterministic-fuzzy-matching-candidate-list-ambiguity.md` (updated)

## Change Log

- 2026-02-16: Third review (Claude Opus 4.6): All 9 prior findings verified resolved. 0 new findings. Story promoted to done.
- 2026-02-16: Re-review (Claude Opus 4.6): All 6 prior findings verified resolved. 0 HIGH, 0 MEDIUM, 3 LOW new findings. Action items created under Review Follow-ups (AI) — Re-review.
- 2026-02-17: Implemented Story 2.4 `resolve_track` runtime + MCP tool + docs/tests lockstep; validated with full regression suite.
- 2026-02-17: Addressed code review findings for Story 2.4 (6 items resolved: 3 MEDIUM, 3 LOW); revalidated with full regression suite.
- 2026-02-17: Addressed re-review findings for Story 2.4 (3 LOW items resolved); revalidated with full regression suite.
- 2026-02-16: Code review (Claude Opus 4.6): 0 HIGH, 3 MEDIUM, 3 LOW findings. 6 action items created under Review Follow-ups (AI). Key: track bank pagination gap, docs/runtime `INVALID_PARAMETER_TYPE` mismatch, missing single-candidate test.
