# Story 1.4: Logging + `request_id` Correlation Hardening (Mutating Tools Only)

Status: ready-for-dev

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

- [ ] Add optional `request_id` to baseline mutating tool schemas (AC 3)
  - [ ] `transport_start` / `transport_stop` schemas: add optional `request_id` string field
  - [ ] `launch_clip` schema: add optional `request_id` string field
  - [ ] `session_launchSceneByIndex` schema: add optional `request_id` string field
  - [ ] `set_selected_device_parameter` schema: add optional `request_id` string field
  - [ ] `set_selected_device_parameters` schema: add optional `request_id` string field
- [ ] Centralize `request_id` extraction + correlation behavior in the unified handler (AC 1-4)
  - [ ] Update `McpErrorHandler` to accept (or derive) a per-invocation correlation value from `req.arguments().get("request_id")` for mutating tools
  - [ ] Ensure both start + finish logs include: `tool_name`, outcome, and `request_id` when provided (avoid “only in error” scenarios)
  - [ ] On failures, ensure logs include the same `ErrorCode` returned in the MCP error envelope (not a reclassified code)
- [ ] Enforce log hygiene for parameters (AC 5)
  - [ ] Ensure large inputs are summarized (counts/shape) and never fully logged by default
  - [ ] Add/extend a small “parameter redaction/summarization” helper for known large fields (future: note payloads) to avoid accidental logging regressions
- [ ] Tests (AC 6)
  - [ ] Add a unit test for one baseline mutating tool (recommended: `TransportToolTest` or `ClipToolTest`) that passes `request_id` and asserts it is used in structured logging context (e.g., passed to `StructuredLogger.startTimedOperation(...)` parameters or included in logged metadata)
  - [ ] Add a failure-path test that asserts `ErrorCode` + `request_id` appear in the logging parameters/context for that invocation
- [ ] Documentation hygiene (optional, only if contract docs expose request args)
  - [ ] If `docs/reference/api-reference.md` documents these tools’ request schemas, add `request_id` (optional) consistently for mutating tools

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

### Completion Checklist
- All baseline mutating tools accept optional `request_id` without breaking old clients (schema + parsing tolerant of absence). [Source: docs/epics.md; docs/architecture.md]
- At least one baseline mutating tool test asserts `request_id` is propagated to the structured logging context for that invocation. [Source: docs/epics.md; docs/test-design-epic-1.md]
- Log output remains payload-safe (no raw note payloads in normal operation). [Source: docs/prd.md; docs/project-context.md; docs/architecture.md]

### Context + Dependencies
- Epic 1: “Reliable MCP Control Surface” prioritizes correctness + operability; this story enables incident triage and performance debugging without compromising payload safety. [Source: docs/epics.md; docs/test-design-epic-1.md]
- Related rules (do not regress):
  - Response envelope stability (Story 1.3)
  - Canonical error code semantics (Story 1.6) [Source: docs/project-context.md]

## Dev Agent Record

### Agent Model Used

GPT-5.2 (Codex CLI)

### Debug Log References

- N/A (story preparation only)

### Completion Notes List

- 2026-01-03: Story drafted as ready-for-dev; no implementation performed.

### File List

- docs/sprint-artifacts/1-4-logging-request-id-correlation-hardening-mutating-tools-only.md
