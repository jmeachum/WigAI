# Story 1.6: Align Index Validation Error Codes with Canonical Semantics

Status: drafted

## Story

As an external AI agent developer,
I want index validation errors to use semantically correct error codes (`INVALID_PARAMETER_INDEX` for index bounds, not `INVALID_RANGE`),
so that my client can distinguish between "wrong index position" vs "value outside allowed range" and provide appropriate user feedback.

## Background

This story resolves technical debt identified during Story 1.3 code review. The canonical error code semantics were established in `docs/project-context.md` **during** the 1.3 review cycle, after implementation was complete. Several index validation paths use `INVALID_RANGE` but should use `INVALID_PARAMETER_INDEX` per the now-canonical semantics:

> **Index vs Range Clarification (from project-context.md):**
> - `INVALID_PARAMETER_INDEX` — for *index arguments* (track_index, scene_index, parameter_index, clip_index) when negative or exceeding valid bounds
> - `INVALID_RANGE` — for *numeric values* (parameter value 0.0-1.0, tempo, etc.) when outside allowed range
> - Rule: If the argument *selects an item by position*, use `INVALID_PARAMETER_INDEX`. If it *sets a numeric value*, use `INVALID_RANGE`.

## Acceptance Criteria

1. **Given** a tool receives a negative `clip_index`, `scene_index`, or `track_index`
   **When** validation fails
   **Then** the error response uses `INVALID_PARAMETER_INDEX` (not `INVALID_RANGE`).

2. **Given** a tool receives an out-of-bounds index (e.g., `track_index` exceeding track count)
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

- [ ] Update `ParameterValidator` to use `INVALID_PARAMETER_INDEX` for negative/out-of-bounds index arguments
- [ ] Update `GetClipsInSceneTool` scene_index validation to use `INVALID_PARAMETER_INDEX`
- [ ] Update `ListDevicesOnTrackTool` track_index validation to use `INVALID_PARAMETER_INDEX`
- [ ] Update `BitwigApiFacade` index validation paths (if any use `INVALID_RANGE` for indices)
- [ ] Update `docs/reference/api-reference.md` error documentation for affected tools
- [ ] Update unit tests to expect `INVALID_PARAMETER_INDEX` for index errors
- [ ] Add `ErrorContractComplianceTest` coverage for index validation paths

## Dev Notes

### Source Items (from Story 1.3 Review Follow-ups)

This story resolves the following deferred items:

| Item | Priority | File | Issue |
|------|----------|------|-------|
| #79 | HIGH | ParameterValidator.java:218 | Use `INVALID_PARAMETER_INDEX` for negative clip_index/scene_index |
| #80 | HIGH | BitwigApiFacade.java:1498 | Align `list_devices_on_track` track_index error to `INVALID_PARAMETER_INDEX` |
| #81 | MEDIUM | GetClipsInSceneTool.java:40 | Align negative scene_index validation error code |
| #83 | LOW | ErrorCode.java:85 | Improve `fromException` classification to avoid `OPERATION_FAILED` for known patterns |

### Guardrails + Reuse

- Follow the canonical error code semantics in `docs/project-context.md` (lines 47-92) as the Single Source of Truth.
- Use existing `ParameterValidator` patterns; do not add new validation helpers.
- Keep changes minimal and focused on error code alignment only.
- Do not change tool behavior, response shapes, or add new functionality.

### Technical Requirements

- `INVALID_PARAMETER_INDEX` is already defined in `ErrorCode.java`; no new error codes needed.
- Validation occurs in multiple layers (tool → controller → facade); audit all paths for affected tools.
- Update `docs/reference/api-reference.md` to match implementation changes.

### Affected Tools (Scope)

Based on Story 1.3 review findings:
- `get_clips_in_scene` — scene_index validation
- `list_devices_on_track` — track_index validation
- `launch_clip` — clip_index validation (if applicable)
- Any other baseline tools with index parameters

### Out of Scope

- No new tools or tool renames.
- No payload expansions.
- No dependency upgrades.
- Do not change `INVALID_RANGE` usage for actual numeric values (e.g., parameter value 0.0-1.0).

### Testing Requirements

- Update existing unit tests to expect `INVALID_PARAMETER_INDEX` for index errors.
- Add regression tests in `ErrorContractComplianceTest` if not already covered.
- All tests must pass: `./gradlew test`

### References

- Canonical error code semantics: `docs/project-context.md` (lines 47-92)
- Story 1.3 deferred items: `docs/sprint-artifacts/1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md` (lines 79-83)
- API reference: `docs/reference/api-reference.md`
- ParameterValidator: `src/main/java/io/github/fabb/wigai/common/validation/ParameterValidator.java`
- ErrorCode enum: `src/main/java/io/github/fabb/wigai/common/error/ErrorCode.java`

## Dev Agent Record

### Context Reference

- Story file: `docs/sprint-artifacts/1-6-align-index-validation-error-codes-with-canonical-semantics.md`
- Project context: `docs/project-context.md`

### Agent Model Used

(To be filled by Dev agent)

### Debug Log References

(To be filled by Dev agent)

### Completion Notes List

(To be filled by Dev agent)

### File List

(To be filled by Dev agent after implementation)
