# Story 1.3: Standardize Baseline Tool Response Envelopes (Align With `status` Tool + API Reference)

Status: in-progress

## Story

As an external AI agent developer,
I want every WigAI MCP tool to return responses in a consistent, documented envelope,
so that my client can parse success/error reliably across tools (including `status`) and handle failures predictably.

## Acceptance Criteria

1. **Given** any baseline tool is invoked successfully  
   **When** it returns a response  
   **Then** the response JSON (single text content payload) has `status: "success"`, contains a `data` field with the tool-specific payload, and is not double-wrapped.
2. **Given** any baseline tool fails  
   **When** it returns an error  
   **Then** the response JSON has `status: "error"`, an `error` object with `code` (from `ErrorCode`), `message` (actionable), and `operation` (equal to the invoked tool name).
3. **Given** the `status` tool is invoked  
   **When** it succeeds  
   **Then** `data` is an object containing at minimum: `wigai_version`, `project_name`, `audio_engine_active`, `transport`, `project_parameters`, `selected_track`, `selected_device`, and `selected_clip_slot`.
4. **Given** one or more `status` sub-fetches fail (e.g., transport or selected device lookup)  
   **When** `status` returns  
   **Then** it still returns `status: "success"` with best-effort defaults, and includes `partial_failures` (array of strings) plus `status_note` (human-readable summary).
5. **Given** unit tests exist for baseline tools  
   **When** they run  
   **Then** they assert envelope compliance for both success and error paths, including a regression check preventing double-wrapping.
6. **Given** `docs/reference/api-reference.md` documents tool responses  
   **When** Story 1.3 is complete  
   **Then** the `status` section matches the real envelope + payload shape (including partial failure fields/behavior).

## Tasks / Subtasks

- [x] Audit baseline tools for response envelope compliance (success/error + no double-wrapping)
- [x] Normalize baseline tool handlers to use `McpErrorHandler.executeWithErrorHandling(...)` / `executeWithValidation(...)`
- [x] Align `status` payload fields and partial failure behavior with ACs
- [x] Expand `McpResponseTestUtils` coverage across baseline tools (success/error + no double-wrapping)
- [x] Update `docs/reference/api-reference.md` `status` response example and notes to match implementation

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] Ensure `status` reports `partial_failures` when Bitwig API sub-fetches fail; facade currently returns defaults without throwing, so failures are not surfaced. [src/main/java/io/github/fabb/wigai/mcp/tool/StatusTool.java:47]
- [x] [AI-Review][MEDIUM] Align envelope error codes for clip/scene tools or update docs/tests; current tests assert `OPERATION_FAILED` instead of documented `TRACK_NOT_FOUND`/`CLIP_INDEX_OUT_OF_BOUNDS`/`SCENE_NOT_FOUND`. [src/test/java/io/github/fabb/wigai/mcp/tool/BaselineToolEnvelopeAtddTest.java:128]
- [x] [AI-Review][MEDIUM] Update completion notes: `./gradlew atddRedTest` no longer runs after `@Tag("atdd")` promotion. [docs/sprint-artifacts/1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md:139]
- [ ] [AI-Review][HIGH] Align `launch_clip` error codes in docs/tests with implementation (`INVALID_RANGE`/`BITWIG_API_ERROR` mapping) or adjust implementation to match documented `CLIP_INDEX_OUT_OF_BOUNDS`/`INVALID_ARGUMENT`. [docs/reference/api-reference.md:283]
- [ ] [AI-Review][HIGH] Fix `get_selected_device_parameters` docs: currently claims “no errors” but implementation throws `DEVICE_NOT_SELECTED` when no device is selected. [docs/reference/api-reference.md:195]
- [ ] [AI-Review][MEDIUM] Fix `set_selected_device_parameter` docs: range validation returns `INVALID_RANGE`, not `INVALID_PARAMETER`. [docs/reference/api-reference.md:221]
- [ ] [AI-Review][MEDIUM] Resolve story status inconsistency (`Status: in-progress` header vs “ready-for-dev” note). [docs/sprint-artifacts/1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md:3]

## Dev Notes

### Quick Summary
- Use `McpErrorHandler` for all tool responses; single JSON text payload; no new response wrappers.
- Keep `status` payload fields and `partial_failures`/`status_note` exactly as defined; treat deviations as breaking.

### Guardrails + Reuse
- Preserve tool -> controller -> `BitwigApiFacade` layering; no direct Bitwig API calls; avoid business logic changes. [Source: docs/project-context.md]
- Use `ErrorCode` taxonomy + `snake_case`; `error.operation` equals invoked tool name. [Source: docs/architecture.md; docs/project-context.md]
- Log hygiene only; avoid extra serialization; keep response shapes/tool names unchanged. [Source: docs/project-context.md; docs/prd.md]
- Reuse `StatusTool` partial failure pattern and `McpErrorHandler`/`McpResponseTestUtils`; do not add new response helpers. [Source: src/main/java/io/github/fabb/wigai/mcp/tool/StatusTool.java; src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java; src/test/java/io/github/fabb/wigai/mcp/tool/McpResponseTestUtils.java]
- File locations: tools `src/main/java/io/github/fabb/wigai/mcp/tool/*Tool.java`, error handling `src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java`, tests `src/test/java/io/github/fabb/wigai/mcp/tool/*ToolTest.java`, API docs `docs/reference/api-reference.md`. [Source: docs/mcp-tools-reference.md]

### Requirements
- All baseline tools return a single JSON text payload with top-level `status` + `data|error`; no alternate formatting paths. [Source: src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java; docs/architecture.md]
- `status` success payload must include: `wigai_version`, `project_name`, `audio_engine_active`, `transport`, `project_parameters`, `selected_track`, `selected_device`, `selected_clip_slot`. [Source: docs/epics.md]
- `status` partial failure behavior: best-effort defaults + `partial_failures` array + `status_note`. [Source: src/main/java/io/github/fabb/wigai/mcp/tool/StatusTool.java]
- Update `docs/reference/api-reference.md` to match the `status` envelope + fields and preserve response contract stability (PRD NFR7). [Source: docs/epics.md; docs/prd.md]

### Completion Checklist
- Tests pass; assert envelope compliance + `assertNotDoubleWrapped` for success and error paths. [Source: src/test/java/io/github/fabb/wigai/mcp/tool/McpResponseTestUtils.java]
- `status` tests cover required fields and `partial_failures`/`status_note` when sub-fetches fail. [Source: src/main/java/io/github/fabb/wigai/mcp/tool/StatusTool.java]
- Error-path coverage includes `error.operation` check plus representative error codes across baseline tool categories (transport failure, device not selected, invalid index/range, track/scene/clip not found). [Source: docs/mcp-tools-reference.md; docs/architecture.md]
- `docs/reference/api-reference.md` `status` response example matches implementation (fields + partial failures). [Source: docs/epics.md]
- Record evidence of checklist execution (test command + date + result) in the Dev Agent Record Completion Notes List. [Source: docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md]

### Context + Dependencies
- Epic 1 establishes a reliable MCP control surface; this story standardizes envelopes for predictable parsing. [Source: docs/epics.md]
- Story 1.1: keep envelope parsing assumptions; Story 1.2: CI-safe tests and scope hygiene (avoid config/server paths unless required). [Source: docs/sprint-artifacts/1-1-repeatable-mcp-smoke-test-harness-checklist.md; docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md]

### Baseline Tool Surface (Scope)
- Core: `status`
- Transport: `transport_start`, `transport_stop`
- Device: `get_selected_device_parameters`, `set_selected_device_parameter`, `set_multiple_device_parameters`, `get_device_details`
- Clips/scenes: `launch_clip`, `launch_scene_by_index`, `launch_scene_by_name`
- Project inquiry: `list_tracks`, `get_track_details`, `list_devices_on_track`, `list_scenes`, `get_clips_in_scene`  
[Source: docs/mcp-tools-reference.md]

### Out of Scope (Prevent Scope Creep)
- No new tools, tool renames, or alternate response formats. [Source: docs/mcp-tools-reference.md]
- No dependency upgrades or framework changes. [Source: docs/architecture.md]
- No payload expansions beyond envelope requirements and `status` ACs. [Source: docs/epics.md]
- No new logging of full payloads or PII-bearing fields. [Source: docs/project-context.md]

### Latest Technical Information
- Summary: MCP Java SDK and Jetty release notes reviewed; no dependency upgrades in this story. Details are recorded in Dev Agent Record. [Source: docs/architecture.md]

### Git Intelligence Summary
- Base commit: `6b2f94be9a5958ae31d2b37f90192947e76997f6`
- Scope: `6b2f94be9a5958ae31d2b37f90192947e76997f6..HEAD`
- Summary: Scope is primarily docs/test refactors; no architecture or dependency changes detected. [Source: git log]

### Story Completion Status
- Story status set to `ready-for-dev` in this story file; sprint status will be updated in `docs/sprint-artifacts/sprint-status.yaml` during finalization. [Source: docs/sprint-artifacts/sprint-status.yaml]

### References

- Epic + acceptance criteria: `docs/epics.md`
- PRD response requirements: `docs/prd.md`
- Architecture envelope + error handling: `docs/architecture.md`
- MCP tool list: `docs/mcp-tools-reference.md`
- Project guardrails: `docs/project-context.md`
- Status implementation: `src/main/java/io/github/fabb/wigai/mcp/tool/StatusTool.java`
- Unified error handler: `src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java`
- Envelope test helpers: `src/test/java/io/github/fabb/wigai/mcp/tool/McpResponseTestUtils.java`
- API reference to update: `docs/reference/api-reference.md`

## Dev Agent Record

### Context Reference

- Story file: `docs/sprint-artifacts/1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md`
- Project context: `docs/project-context.md`
- McpErrorHandler: `src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java`
- McpResponseTestUtils: `src/test/java/io/github/fabb/wigai/mcp/tool/McpResponseTestUtils.java`
- BaselineToolEnvelopeAtddTest: `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineToolEnvelopeAtddTest.java`

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A - No debug issues encountered during implementation.

### Latest Technical Detail

- MCP Java SDK latest release: `v0.17.0` (2025-12-04). Release notes cite client transport configuration fixes, SSE event handling tweaks, and base URL normalization; no envelope-breaking changes called out. Keep `mcp-bom:0.11.0` pinned for this story. [Source: https://github.com/modelcontextprotocol/java-sdk/releases/tag/v0.17.0]
- Jetty 11 latest tagged release: `11.0.26` (2025-08-18) includes HTTP/2 CVE-2025-5115 fix and rate-control updates; Jetty 12.1.5 is the latest overall line. No Jetty upgrades in this story. [Sources: https://github.com/jetty/jetty.project/releases/tag/jetty-11.0.26, https://github.com/jetty/jetty.project/releases/tag/jetty-12.1.5]
- If envelope work touches transport semantics or response encoding, recheck SDK/Jetty release notes before changing dependencies. [Source: docs/architecture.md]
- Best-practice notes for current versions: do not rely on SSE session IDs in events (SDK removed them), normalize MCP base URLs in any test clients (avoid trailing slash issues), and schedule Jetty upgrades to at least 11.0.26 when dependency updates are allowed due to the HTTP/2 CVE fix. [Sources: https://github.com/modelcontextprotocol/java-sdk/releases/tag/v0.17.0, https://github.com/jetty/jetty.project/releases/tag/jetty-11.0.26]

### Completion Notes List

**2025-12-29 Implementation Summary:**

1. **Audit Complete**: All 15 baseline tools audited for envelope compliance:
   - All tools already use `McpErrorHandler.executeWithErrorHandling()` or `executeWithValidation()`
   - All return standardized `status: "success"` + `data` or `status: "error"` + `error` envelopes
   - No double-wrapping issues found

2. **Status Tool Verification**: StatusTool.java confirms:
   - Required fields present: `wigai_version`, `project_name`, `audio_engine_active`, `transport`, `project_parameters`, `selected_track`, `selected_device`, `selected_clip_slot`
   - Partial failure handling implemented with `partial_failures` array and `status_note` summary

3. **Test Evidence**:
   - `./gradlew test` - All tests PASSED including ATDD envelope tests (2025-12-29)
   - Test coverage includes success/error envelopes for all baseline tools
   - `assertNotDoubleWrapped()` regression checks in all tests
   - Note: ATDD tests now run as part of standard test suite after `@Tag("atdd")` promotion

4. **Documentation Updated**: `docs/reference/api-reference.md` updated to:
   - Wrap `status` response in standardized envelope (`status: "success"`, `data: {...}`)
   - Document partial failure response format with `partial_failures` and `status_note`
   - Add notes explaining partial failure behavior

5. **Test Promotion**: Changed `@Tag("atdd_red")` to `@Tag("atdd")` in `BaselineToolEnvelopeAtddTest.java` so tests now run in CI

**2025-12-29 Review Follow-up Implementation:**

6. **Partial Failures Now Surface Correctly** (HIGH priority):
   - Modified `BitwigApiFacade.java` methods (`getTransportStatus`, `getSelectedTrackInfo`, `getSelectedDeviceInfo`, `getSelectedClipSlotInfo`, `getProjectParameters`) to throw `BitwigApiException` on API errors instead of silently returning defaults
   - StatusTool's existing try-catch blocks now properly catch failures and populate `partial_failures` array
   - Distinguished between "nothing selected" (returns null, valid state) vs "API error" (throws exception, partial failure)

7. **Error Codes Aligned for Clip/Scene Tools** (MEDIUM priority):
   - Added `ErrorCode.fromString()` method to map string error codes to `ErrorCode` enum values
   - Updated `ClipTool.java`, `SceneTool.java`, `SceneByNameTool.java` to use the actual error code from result (e.g., `TRACK_NOT_FOUND`, `SCENE_NOT_FOUND`) instead of hardcoded `OPERATION_FAILED`
   - Updated test assertions in `BaselineToolEnvelopeAtddTest.java` to expect the correct error codes

8. **Test Evidence**: `./gradlew test` - All tests PASSED (2025-12-29)

### File List

**Modified:**
- `docs/reference/api-reference.md` - Updated status response documentation with envelope wrapper and partial failure behavior
- `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineToolEnvelopeAtddTest.java` - Changed @Tag("atdd_red") to @Tag("atdd"); updated error code assertions for clip/scene tools
- `docs/sprint-artifacts/sprint-status.yaml` - Updated story status to in-progress
- `docs/sprint-artifacts/1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md` - This story file (tasks marked complete, Dev Agent Record updated)
- `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java` - Modified facade methods to throw BitwigApiException on API errors (enables partial_failures)
- `src/main/java/io/github/fabb/wigai/common/error/ErrorCode.java` - Added `fromString()` method for string-to-enum mapping
- `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java` - Use actual error code from result instead of OPERATION_FAILED
- `src/main/java/io/github/fabb/wigai/mcp/tool/SceneTool.java` - Use actual error code from result instead of OPERATION_FAILED
- `src/main/java/io/github/fabb/wigai/mcp/tool/SceneByNameTool.java` - Use actual error code from result instead of RuntimeException
