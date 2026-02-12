# Sprint Change Proposal - Story 1-6 Cycle Stabilization

Date: 2026-02-11  
Project: WigAI  
Trigger Story: 1-6-align-index-validation-error-codes-with-canonical-semantics  
Mode: Incremental

## 1. Issue Summary

Repeated dev and code-review cycles on Story 1-6 keep surfacing the same class of defects: index-selector semantics are mostly aligned to `INVALID_PARAMETER_INDEX`, but a few paths still drift to non-canonical behavior.

Context and evidence:
- Story 1-6 logs 29 AI review follow-ups; 3 remain open.
- Current uncommitted cycle is broad (23 files changed), indicating multiple corrective passes.
- Remaining unresolved path centers on `parameter_index` overflow behavior in selected-device parameter tools and related contract/doc alignment.

Problem statement:
- The team fixed semantics incrementally by call site, but did not fully close the invariant across all index-bearing tool parse paths, tests, and docs at the same time.

## 2. Impact Analysis

### Epic Impact
- Epic 1 remains valid and in-progress.
- Story 1-6 is not ready for "done" yet.
- Story 1-7 should remain blocked until Story 1-6 closes all open findings.

### Story Impact
- Affected now: Story 1-6 only.
- At-risk if unresolved: Story 1-7 and future epics that reuse index parsing patterns.

### Artifact Conflicts
- Code conflict: `DeviceParamTool` still uses `validateRequiredInteger(...)` for `parameter_index` parse paths.
- Test conflict: `ErrorContractComplianceTest` lacks overflow scenarios for `parameter_index` in single/batch selected-device parameter tools.
- Documentation conflict: `set_selected_device_parameters` text is ambiguous about top-level validation failure vs per-item execution-time errors.

### Technical Impact
- No architecture rewrite required.
- No dependency or infrastructure change required.
- Targeted contract hardening across one tool, one contract-test file, and one API-doc section.

## 3. Recommended Approach

Selected path: **Direct Adjustment** (approved)

Why this path:
- Fastest closure of Story 1-6 with minimal risk.
- Preserves all already-correct fixes.
- Addresses the recurrence pattern by closing code + tests + docs together.

Effort/Risk/Timeline:
- Effort: Low
- Risk: Low
- Timeline impact: ~0.5-1 day, no epic resequencing required beyond holding Story 1-7 until closure

## 4. Detailed Change Proposals

### Stories / Workflow Artifacts

#### Proposal S1: Keep Story 1-6 in-progress until open findings are truly closed

OLD:
- Story notes contain repeated "all resolved" claims while 3 checklist items remain unchecked.

NEW:
- Maintain Story 1-6 as `in-progress` until all 3 remaining findings are resolved and validated.
- Update story/checklist/test-summary only after validation evidence is current and consistent.

Rationale:
- Prevents false closure and re-open cycles.

### Code Changes

#### Proposal C1: Use index-aware parsing in selected-device parameter tools

Artifact: `src/main/java/io/github/fabb/wigai/mcp/tool/DeviceParamTool.java`

OLD:
- `parseSetParameterArguments(...)` uses `validateRequiredInteger(...)` for `parameter_index`.
- `parseSetMultipleParametersArguments(...)` uses `validateRequiredInteger(...)` for each `parameter_index`.

NEW:
- Replace both with `validateRequiredIndexInteger(...)` so overflow semantics align with canonical index handling (`INVALID_PARAMETER_INDEX`).

Rationale:
- Closes unresolved HIGH finding and aligns behavior with `ClipTool`/`SceneTool` index handling.

### Test Changes

#### Proposal T1: Add overflow contract scenarios for parameter_index in single and batch tools

Artifact: `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java`

OLD:
- Contract tests cover bounds (`-1`, `8`, `10`) but not overflow (`4294967296`) for:
  - `set_selected_device_parameter`
  - `set_selected_device_parameters`

NEW:
- Add explicit overflow scenarios for both tools.
- Assert `INVALID_PARAMETER_INDEX` for overflow index-selector inputs.

Rationale:
- Prevents regression of this exact defect class.

### Documentation Changes

#### Proposal D1: Clarify top-level vs per-item error semantics for batch parameter setting

Artifact: `docs/reference/api-reference.md`

OLD:
- The section implies per-item `INVALID_PARAMETER_INDEX` outcomes but does not clearly separate request-validation failures from execution-time per-item results.

NEW:
- Clarify canonical behavior:
  - Request-shape/index validation failures return **top-level** errors.
  - Per-item `results[].error_code` applies to execution-stage item outcomes after request validation succeeds.

Rationale:
- Removes ambiguity that caused review churn and doc/runtime interpretation mismatches.

## 5. Implementation Handoff

### Scope Classification
- **Minor**: direct implementation by development team

### Handoff Recipients
- Development team
- Reviewer (code-review workflow)
- Scrum Master (story/sprint bookkeeping closure)

### Responsibilities
- Development:
  - Apply C1, T1, D1 changes.
  - Ensure story file reflects actual unresolved/resolved state.
- Review:
  - Confirm canonical semantics for overflow index-selector paths.
  - Confirm no envelope regressions.
- Scrum Master:
  - Keep Story 1-6 in-progress until closure evidence is complete.
  - Gate Story 1-7 start on Story 1-6 completion.

### Success Criteria
- `set_selected_device_parameter` overflow `parameter_index` returns `INVALID_PARAMETER_INDEX`.
- `set_selected_device_parameters` overflow `parameter_index` returns `INVALID_PARAMETER_INDEX`.
- Contract tests include both overflow scenarios and pass.
- API reference clearly distinguishes top-level validation errors vs per-item execution errors.
- Story 1-6 review checklist has no unresolved items, with consistent evidence.
- Full suite passes (`./gradlew test --rerun`) before closure.

