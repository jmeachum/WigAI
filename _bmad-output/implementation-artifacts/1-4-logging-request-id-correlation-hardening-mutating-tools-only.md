# Story 1.4: Logging + `request_id` Correlation Hardening (Mutating Tools Only)

Status: review

## Story

As a WigAI developer,
I want mutating tools to log each invocation with consistent correlation (`request_id` when provided) and outcome metadata,
so that I can reliably debug failures and performance issues without logging sensitive or large payloads.

## Acceptance Criteria

1. **Given** any MCP tool is invoked  
   **When** it executes  
   **Then** WigAI logs an “operation started” and “operation finished” entry including at minimum: `tool_name` and outcome (success or failure).  
2. **Given** a client supplies `request_id` in the tool arguments for a mutating tool  
   **When** the tool executes  
   **Then** all logs for that invocation include the `request_id` value (so I can correlate client→server→error).  
3. **Given** a baseline mutating tool is invoked (`transport_start`, `transport_stop`, `launch_clip`, `session_launchSceneByIndex`, `set_selected_device_parameter`, `set_selected_device_parameters`)  
   **When** it is called  
   **Then** it accepts an optional `request_id` field without breaking existing clients, and uses it for correlation in logs.  
4. **Given** a mutating tool fails  
   **When** it returns an error response  
   **Then** logs include the standardized `ErrorCode` (same code as returned in the MCP envelope) and the `request_id` if present.  
5. **Given** a tool has potentially large inputs (current or future)  
   **When** logging parameters  
   **Then** it logs summaries only (counts/shape) and does not log full payloads by default (especially future note payloads), aligning with the “no full payload logging unless debug” rule.  
6. **Given** unit tests exist for baseline tools and/or the error handler  
   **When** tests run  
   **Then** at least one baseline mutating tool test asserts that providing `request_id` results in it being included in the structured logging parameters/context for that operation.  

## Tasks / Subtasks

- [x] Add optional `request_id` to baseline mutating tool schemas (AC 3)
  - [x] `transport_start` / `transport_stop` schemas: add optional `request_id` string field
  - [x] `launch_clip` schema: add optional `request_id` string field
  - [x] `session_launchSceneByIndex` schema: add optional `request_id` string field
  - [x] `set_selected_device_parameter` schema: add optional `request_id` string field
  - [x] `set_selected_device_parameters` schema: add optional `request_id` string field
- [x] Centralize `request_id` extraction + correlation behavior in the unified handler (AC 1-4)
  - [x] Update `McpErrorHandler` to accept (or derive) a per-invocation correlation value from `req.arguments().get("request_id")` for mutating tools
  - [x] Ensure both start + finish logs include: `tool_name`, outcome, and `request_id` when provided (avoid "only in error" scenarios)
  - [x] On failures, ensure logs include the same `ErrorCode` returned in the MCP error envelope (not a reclassified code)
- [x] Enforce log hygiene for parameters (AC 5)
  - [x] Ensure large inputs are summarized (counts/shape) and never fully logged by default
  - [x] Add/extend a small "parameter redaction/summarization" helper for known large fields (future: note payloads) to avoid accidental logging regressions
- [x] Tests (AC 6)
  - [x] Add a unit test for one baseline mutating tool (recommended: `TransportToolTest` or `ClipToolTest`) that passes `request_id` and asserts it is used in structured logging context (e.g., passed to `StructuredLogger.startTimedOperation(...)` parameters or included in logged metadata)
  - [x] Add a failure-path test that asserts `ErrorCode` + `request_id` appear in the logging parameters/context for that invocation
- [x] Documentation hygiene (optional, only if contract docs expose request args)
  - [x] If `docs/reference/api-reference.md` documents these tools' request schemas, add `request_id` (optional) consistently for mutating tools

### Review Follow-ups (AI)
- [x] [AI-Review][HIGH] Include request_id in completion/failure logging so all invocation logs carry correlation data and ErrorCode [src/main/java/io/github/fabb/wigai/common/logging/StructuredLogger.java:228]
- [x] [AI-Review][HIGH] Fix missing file reference in Dev Agent Record File List (remove or add the referenced sprint artifact) [_bmad-output/implementation-artifacts/1-4-logging-request-id-correlation-hardening-mutating-tools-only.md:148]
- [x] [AI-Review][HIGH] Reconcile story File List vs git reality; file list claims changes with clean git state [_bmad-output/implementation-artifacts/1-4-logging-request-id-correlation-hardening-mutating-tools-only.md:146]
- [x] [AI-Review][MEDIUM] Sanitize logging parameters in executeWithValidation; avoid logging raw arguments [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:181]

## Dev Notes

### Quick Summary
- This story is “observability hardening” for mutating tools: accept optional `request_id`, propagate it into *every* structured log line for that invocation, and keep parameter logging safe by default.
- Treat `request_id` as “correlation only” (and/or idempotency key per architecture rules); do not change MCP response envelopes or tool behavior beyond accepting an optional field.

### Guardrails + Reuse
- Keep tool → controller → `BitwigApiFacade` layering; do not call Bitwig Extension API directly from tool classes. [Source: docs/project-context.md]
- Keep using the unified handler (`McpErrorHandler.executeWithErrorHandling(...)`) for tools; do not introduce bespoke logging wrappers per tool. [Source: docs/project-context.md; src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java]
- Mutating tools MUST accept optional `request_id` and dedupe by `(tool_name, request_id)` (short-lived, bounded in-memory). This story focuses on logging correlation; do not regress idempotency rules. [Source: docs/project-context.md; docs/architecture.md]
- Do not log full note payloads unless debug is explicitly enabled; log summaries only. [Source: docs/prd.md; docs/project-context.md; docs/architecture.md]

### Current Code Touchpoints (Expected)
- Unified handler + operation timing: `src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java`
- Structured logging: `src/main/java/io/github/fabb/wigai/common/logging/StructuredLogger.java`
- Baseline mutating tools (schemas + handlers):
  - `src/main/java/io/github/fabb/wigai/mcp/tool/TransportTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/SceneTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/DeviceParamTool.java`
- Tests:
  - `src/test/java/io/github/fabb/wigai/mcp/tool/TransportToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/ClipToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/DeviceParamToolTest.java`
  - `src/test/java/io/github/fabb/wigai/mcp/tool/SceneToolTest.java`

### Implementation Requirements (Non-Negotiable)
- Logging requirements (minimum):
  - Always log start + finish of the operation (success or error) with `tool_name`.
  - Include `request_id` in all logs for that invocation when present.
  - On failure, include the same `ErrorCode` that is returned in the MCP error envelope.
  - Never log full large payloads by default; prefer counts/shape and gate verbose output behind explicit debug. [Source: docs/prd.md; docs/project-context.md; docs/architecture.md]
- Performance + safety requirements (non-negotiable):
  - `request_id` extraction and parameter shaping MUST be CPU-only and bounded work (O(1) relative to payload size). No I/O, DNS, JSON serialization, or reflection-heavy logging on Bitwig-sensitive paths. [Source: docs/project-context.md]
  - Do NOT pass raw `req.arguments()` directly to logging. Always pass a sanitized/summarized `parameters` map (safe keys only), and never call `toString()` on raw argument maps. [Source: docs/prd.md; docs/project-context.md]

### Testing Contract (Required)
- Assertion point (required): For baseline mutating tools, the unified handler MUST call `StructuredLogger.startTimedOperation(operationId, tool_name, parameters)` where `parameters` includes `request_id` when it is present in the tool arguments.
- Backward compatibility: Tests MUST also cover absence of `request_id` (no crash, no change in behavior/envelope).
- Payload safety: Tests MUST ensure the logged `parameters` map is sanitized (no raw payloads / large arrays / large strings); use summaries only (e.g., counts/shape).

### Scope Clarification (Mutating Tools Only)
- This story adds `request_id` to JSON schemas and any explicit argument parsing ONLY for baseline mutating tools (the list in AC 3).
- Do not add `request_id` to read-only tool schemas as part of this story; if the unified handler can opportunistically include `request_id` when present, that is an implementation detail and must not change tool contracts.
- Do not rename tools in this story; in particular, `session_launchSceneByIndex` is a legacy tool name (non-`snake_case`) and must not be renamed as part of this logging-only change.

### Previous Story Intelligence (Prevent Regressions)
- From Story 1.3: Do not change MCP response envelopes while improving logging; keep `status` + `data|error` exactly as-is.
- Preserve `error.operation == tool_name` (tool name), not internal method identifiers.
- Avoid logging raw request payloads or argument maps; only log summaries and correlation fields.

### Envelope Invariants (Do Not Regress from Story 1.3)
- Responses MUST remain a single JSON text payload with top-level `status` + `data|error`; no alternate formatting paths.
- Do not introduce a second wrapper/envelope while adding logging; keep `McpResponseTestUtils.assertNotDoubleWrapped(...)` passing for all affected tools.
- Continue enforcing: `error.operation` equals the invoked MCP tool name (protect the existing `McpErrorHandler` behavior).
- `request_id` is correlation-only in this story: do not echo it back in MCP `data` payloads unless a future story explicitly requires it.

### Out of Scope (Prevent Scope Creep)
- No new tools, tool renames, or behavior changes beyond accepting optional `request_id` and logging correlation.
- No dependency upgrades or framework changes.
- No payload expansions beyond correlation-safe logging metadata.
- No logging of full payloads or PII-bearing fields; keep note/payload logging gated behind explicit debug only.

### Completion Checklist
- All baseline mutating tools accept optional `request_id` without breaking old clients (schema + parsing tolerant of absence). [Source: docs/epics.md; docs/architecture.md]
- At least one baseline mutating tool test asserts `request_id` is propagated to the structured logging context for that invocation. [Source: docs/epics.md; docs/test-design-epic-1.md]
- Log output remains payload-safe (no raw note payloads in normal operation). [Source: docs/prd.md; docs/project-context.md; docs/architecture.md]
- Regression guards for Story 1.3 remain green:
  - `McpResponseTestUtils.assertNotDoubleWrapped(...)` passes for affected tools (with and without `request_id`).
  - Error-path tests still assert `error.operation == tool_name` (not internal operation names).

### Context + Dependencies
- Epic 1: “Reliable MCP Control Surface” prioritizes correctness + operability; this story enables incident triage and performance debugging without compromising payload safety. [Source: docs/epics.md; docs/test-design-epic-1.md]
- Related rules (do not regress):
  - Response envelope stability (Story 1.3)
  - Canonical error code semantics (Story 1.6) [Source: docs/project-context.md]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- N/A

### Completion Notes List

- 2026-01-03: Story drafted as ready-for-dev; no implementation performed.
- 2026-01-03: Story clarified to remove ambiguity (non-blocking logging, explicit testing assertion point, previous-story regression guardrails).
- 2026-01-03: Implementation complete. Added `request_id` support to all 6 baseline mutating tools via new `executeWithErrorHandling` overload in `McpErrorHandler`. Implemented `extractLoggingParameters()` helper for sanitized parameter extraction (only `request_id` included, no raw payloads). Added 4 new tests to `TransportToolTest` covering: request_id propagation to logging context, backward compatibility without request_id, failure-path error code + request_id correlation, and transport_stop request_id handling. All existing tests remain green.
- 2026-01-03: Addressed 4 code review follow-ups: (1) Fixed TimedOperation to carry parameters through to success/failure logs so request_id appears in completion logging; (2) Added new StructuredLoggerTest with 4 tests validating request_id propagation; (3) Fixed executeWithValidation to use extractLoggingParameters() instead of raw arguments; (4) Corrected File List path references to match current file locations.

### File List

- _bmad-output/implementation-artifacts/1-4-logging-request-id-correlation-hardening-mutating-tools-only.md
- src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java
- src/main/java/io/github/fabb/wigai/common/logging/StructuredLogger.java
- src/main/java/io/github/fabb/wigai/mcp/tool/TransportTool.java
- src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java
- src/main/java/io/github/fabb/wigai/mcp/tool/SceneTool.java
- src/main/java/io/github/fabb/wigai/mcp/tool/DeviceParamTool.java
- src/test/java/io/github/fabb/wigai/mcp/tool/TransportToolTest.java
- src/test/java/io/github/fabb/wigai/common/logging/StructuredLoggerTest.java
- docs/reference/api-reference.md

## Change Log

- 2026-01-03: Story 1.4 implementation complete - Added request_id correlation support to baseline mutating tools
- 2026-01-03: Addressed code review findings - 4 items resolved (3 HIGH, 1 MEDIUM)
