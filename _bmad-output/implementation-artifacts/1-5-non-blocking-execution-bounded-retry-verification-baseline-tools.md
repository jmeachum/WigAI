# Story 1.5: Non-Blocking Execution + Bounded Retry Verification (Baseline Tools)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI user,
I want baseline tools to be responsive and resilient to transient Bitwig host-state timing issues,
so that WigAI remains trustworthy (no DAW freezes/crashes) and requests complete reliably within the expected time bounds.

## Acceptance Criteria

1. **Given** baseline tools are invoked under normal conditions  
   **When** they execute  
   **Then** they complete without blocking Bitwig responsiveness and typical invocations complete within the performance expectation for normal-sized requests.

2. **Given** a baseline tool encounters a transient, retryable Bitwig host-state timing/availability failure  
   **When** the operation is executed  
   **Then** WigAI retries using a bounded retry policy (max attempts + backoff) and either succeeds or returns a clear error without hanging indefinitely.

3. **Given** a baseline tool encounters a non-retryable failure (for example: invalid parameters, missing target track/device)  
   **When** the operation is executed  
   **Then** WigAI does not retry and returns the standardized error response immediately with an actionable message.

4. **Given** retries are performed  
   **When** WigAI logs the operation  
   **Then** logs clearly indicate retry attempts, final outcome (success/failure), and total duration for the invocation.

5. **Given** the smoke test harness from Story 1.1 is available  
   **When** it is run in a timing-stress mode (manual or scripted)  
   **Then** it can validate that tools do not hang and that failures are surfaced as bounded, actionable errors rather than Bitwig instability.

## Tasks / Subtasks

- [ ] Implement bounded retry policy for baseline mutating tool execution paths (AC: 1, 2, 3, 4)
- [ ] Define retryable vs non-retryable classification using canonical error taxonomy (AC: 2, 3)
- [ ] Ensure retry execution is bounded and non-blocking in practice (no unbounded loops, no indefinite wait) (AC: 1, 2)
- [ ] Add structured retry logging with attempt number, max attempts, final outcome, duration, and correlation metadata (AC: 4)
- [ ] Extend smoke harness for timing-stress verification (host-required) (AC: 5)
- [ ] Add/extend tests for retry behavior and non-retry behavior (AC: 2, 3, 4)
- [ ] Update API docs only if behavior/error semantics change externally (avoid contract drift) (AC: 3, 4)

## Dev Notes

### Story Foundation

- Epic context: this story hardens Epic 1 baseline operations for resilience and responsiveness while preserving current MCP contracts.  
  [Source: _bmad-output/planning-artifacts/epics.md]
- Baseline scope in current codebase includes mutating tools: `transport_start`, `transport_stop`, `launch_clip`, `session_launchSceneByIndex`, `set_selected_device_parameter`, `set_selected_device_parameters`.  
  [Source: src/main/java/io/github/fabb/wigai/mcp/tool/TransportTool.java]  
  [Source: src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java]  
  [Source: src/main/java/io/github/fabb/wigai/mcp/tool/SceneTool.java]  
  [Source: src/main/java/io/github/fabb/wigai/mcp/tool/DeviceParamTool.java]

### Developer Context Section

- Preserve MCP envelope invariants from Story 1.3: single JSON text payload with top-level `status` + `data|error`; no extra wrappers.
- Preserve Story 1.4 correlation/logging rules: all invocation lifecycle logs must remain correlation-safe (`request_id` when present), with sanitized argument summaries only.
- Do not conflate this story with Story 1.7 idempotency: retries here must be bounded and explicit; dedupe-by-request-id remains separate unless explicitly included by scope change.

### Technical Requirements

- Add retry orchestration where baseline mutating operations call Bitwig API (controller/facade layer), not in MCP tool schema/parsing layer.
- Retry policy must be bounded:
- Max attempts must be finite and explicit.
- Backoff strategy must be finite and predictable.
- Total runtime must remain bounded to prevent hangs.
- Retry classification must be deterministic:
- Retryable: transient host-state/timing/availability failures only.
- Non-retryable: validation/state issues (invalid args, missing track/device/scene/clip, etc.).
- On non-retryable failures, fail fast with standardized MCP error envelope.
- On retry exhaustion, return clear actionable error with unchanged envelope structure.

### Architecture Compliance

- Keep layering strict: MCP tools -> controllers -> `BitwigApiFacade`.
- No direct Bitwig API calls in MCP tool classes.
- Keep all tool responses routed via `McpErrorHandler`.
- Maintain canonical `error.operation == tool_name` behavior.
- Maintain canonical error semantics from project context (especially index vs range semantics).

### Library Framework Requirements

- Current pinned project versions:
- MCP Java SDK BOM: `0.11.0`
- Jetty: `11.0.20`
- JUnit Jupiter: `5.10.0`  
  [Source: build.gradle.kts]
- Latest-available checkpoints from primary sources (research date context: February 2026):
- MCP Java SDK: Sonatype artifact entries show `0.17.0` available; GitHub release page snapshot lists `v0.16.0` with improvements including error-handling and schema updates.  
  [Source: https://central.sonatype.com/artifact/io.modelcontextprotocol.sdk/mcp-bom/0.17.0]  
  [Source: https://github.com/modelcontextprotocol/java-sdk/releases]
- Jetty: official download page lists `12.1.6` as latest supported line and `11.0.26` marked EOL/unsupported (community support ended January 1, 2024).  
  [Source: https://jetty.org/download.html]
- JUnit: official repository metadata lists GA `6.0.2` (January 6, 2026), with release-page snapshot showing `6.0.1` as latest tagged stable in that crawl.  
  [Source: https://github.com/junit-team/junit-framework]  
  [Source: https://github.com/junit-team/junit5/releases]
- Scope guidance for this story:
- Do not upgrade dependencies unless needed for AC compliance.
- If retry behavior depends on library behavior differences, isolate via internal abstractions and document upgrade rationale separately.

### File Structure Requirements

- Primary implementation touchpoints:
- `src/main/java/io/github/fabb/wigai/features/TransportController.java`
- `src/main/java/io/github/fabb/wigai/features/ClipSceneController.java`
- `src/main/java/io/github/fabb/wigai/features/DeviceController.java`
- `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java`
- `src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java`
- `src/main/java/io/github/fabb/wigai/common/logging/StructuredLogger.java`
- Preferred new abstraction location (if needed): `src/main/java/io/github/fabb/wigai/common/` (for shared retry policy/executor) to avoid duplicated controller logic.
- Keep MCP schema files focused on argument contracts; do not bury retry logic in tool handlers.

### Testing Requirements

- Unit tests (required):
- Retryable failure then success path (attempt >1, eventual success).
- Retryable failure exhausting max attempts (bounded failure).
- Non-retryable failure path (zero retries).
- Logging assertions for retry attempts + final duration + outcome (with `request_id` propagation when supplied).
- Regression tests (required):
- Response envelope format unchanged from Story 1.3.
- Error `operation` remains MCP tool name.
- Smoke harness timing-stress (host-required):
- Add scripted mode/coverage in smoke harness layer to validate no hangs and actionable bounded failures.

### Previous Story Intelligence

- Reuse Story 1.4 logging and correlation foundation:
- `McpErrorHandler.extractLoggingParameters()` already sanitizes and summarizes arguments.
- `StructuredLogger.TimedOperation` already records start/finish with correlation parameters.
- Avoid reintroducing uncorrelated controller-level logs for mutating flows.
- Preserve Story 1.4 scope boundaries:
- `request_id` behavior remains correlation-focused in this flow.
- Dedupe/idempotency semantics are intentionally separate (Story 1.7).

### Git Intelligence Summary

- Most recent implementation commit (`4fa4dd0`, February 10, 2026) heavily refactored and stabilized Story 1.4 around request correlation and log hygiene.
- Established code patterns now expected by tests/review:
- Tools pass `req.arguments()` into `McpErrorHandler.executeWithErrorHandling(...)`.
- `StructuredLogger` logs operation lifecycle with sanitized parameters.
- High regression risk areas for this story:
- Breaking envelope format or `error.operation` behavior.
- Adding retry logic that bypasses existing structured logging paths.
- Introducing retries for validation/state failures (should fail fast).

### Latest Tech Information

- MCP SDK evolution since current pin suggests stronger protocol/transport handling in newer versions; if retry behavior surfaces SDK transport edge cases, review upgrade feasibility from `0.11.0` toward currently available series.
- Jetty 11 is now in EOL status according to official Jetty docs; this does not block Story 1.5, but performance/reliability follow-ups should track migration to supported line.
- JUnit ecosystem has moved to 6.x; current tests can remain on 5.10.0 for this story unless new assertion/tooling needs justify uplift.

### Project Context Reference

- Core requirements and constraints:
- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/planning-artifacts/architecture.md`
- `_bmad-output/project-context.md`
- Previous implementation intelligence:
- `_bmad-output/implementation-artifacts/1-4-logging-request-id-correlation-hardening-mutating-tools-only.md`
- Runtime and dependency baseline:
- `build.gradle.kts`

### Story Completion Status

- Story context generated with comprehensive implementation guardrails.
- Status is set to `ready-for-dev`.
- Sprint tracking should mark `1-5-non-blocking-execution-bounded-retry-verification-baseline-tools` as `ready-for-dev`.

### Project Structure Notes

- Keep changes aligned with existing modular boundaries and file conventions under `io.github.fabb.wigai.*`.
- Prefer additive abstractions over cross-cutting invasive changes in tool classes.
- If introducing shared retry utility, keep it deterministic, side-effect-light, and easy to unit test.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-15-Non-Blocking-Execution--Bounded-Retry-Verification-Baseline-Tools]
- [Source: _bmad-output/planning-artifacts/architecture.md]
- [Source: _bmad-output/project-context.md]
- [Source: _bmad-output/implementation-artifacts/1-4-logging-request-id-correlation-hardening-mutating-tools-only.md]
- [Source: src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java]
- [Source: src/main/java/io/github/fabb/wigai/common/logging/StructuredLogger.java]
- [Source: src/main/java/io/github/fabb/wigai/features/TransportController.java]
- [Source: src/main/java/io/github/fabb/wigai/features/ClipSceneController.java]
- [Source: src/main/java/io/github/fabb/wigai/features/DeviceController.java]
- [Source: src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java]
- [Source: build.gradle.kts]
- [Source: https://central.sonatype.com/artifact/io.modelcontextprotocol.sdk/mcp-bom/0.17.0]
- [Source: https://github.com/modelcontextprotocol/java-sdk/releases]
- [Source: https://jetty.org/download.html]
- [Source: https://github.com/junit-team/junit-framework]
- [Source: https://github.com/junit-team/junit5/releases]

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- Workflow execution: create-story
- Target story key: `1-5-non-blocking-execution-bounded-retry-verification-baseline-tools`

### Completion Notes List

- Story selected automatically from first `backlog` entry in sprint status.
- Story context synthesized from epics, architecture, project context, previous story intelligence, git patterns, and targeted web version checks.
- Validation task file `_bmad/core/tasks/validate-workflow.xml` is not present in this repository version; manual checklist-style validation performed against workflow intent.

### File List

- _bmad-output/implementation-artifacts/1-5-non-blocking-execution-bounded-retry-verification-baseline-tools.md
