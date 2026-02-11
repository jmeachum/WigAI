# Story 1.5: Non-Blocking Execution + Bounded Retry Verification (Baseline Tools)

Status: done

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

- [x] Implement bounded retry policy for baseline mutating tool execution paths (AC: 1, 2, 3, 4)
- [x] Define retryable vs non-retryable classification using canonical error taxonomy (AC: 2, 3)
- [x] Ensure retry execution is bounded and non-blocking in practice (no unbounded loops, no indefinite wait) (AC: 1, 2)
- [x] Add structured retry logging with attempt number, max attempts, final outcome, duration, and correlation metadata (AC: 4)
- [x] Extend smoke harness for timing-stress verification (host-required) (AC: 5)
- [x] Add/extend tests for retry behavior and non-retry behavior (AC: 2, 3, 4)
- [x] Update API docs only if behavior/error semantics change externally (avoid contract drift) (AC: 3, 4)

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] Enforce per-tool timeout with cancellable execution in timing-stress harness so hung `callTool` invocations cannot block forever (AC: 5) [src/test/java/io/github/fabb/wigai/smoke/McpTimingStressHarness.java:77]
- [x] [AI-Review][HIGH] Bound retry runtime when `task.execute()` blocks (hard timeout enforcement, not only between attempts) to prevent indefinite hangs (AC: 1, 2) [src/main/java/io/github/fabb/wigai/common/retry/RetryExecutor.java:64]
- [x] [AI-Review][HIGH] Restrict default retry to baseline mutating paths only; preserve no-retry behavior for read-only overload/callers (scope regression) (AC: 2, 3) [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:103]
- [x] [AI-Review][MEDIUM] Update Dev Agent Record File List to include `_bmad-output/implementation-artifacts/tests/test-summary.md` for git/story traceability [_bmad-output/implementation-artifacts/1-5-non-blocking-execution-bounded-retry-verification-baseline-tools.md:223]
- [x] [AI-Review][MEDIUM] Add regression test proving no-arguments/read-only `executeWithErrorHandling(operation, logger, task)` path does not retry [src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTest.java:307]
- [x] [AI-Review][CRITICAL] Cancel timed-out `client.callTool()` futures in timing-stress harness timeout path to prevent hung invocations from continuing after deadline (AC: 5) [src/test/java/io/github/fabb/wigai/smoke/McpTimingStressHarness.java:83]
- [x] [AI-Review][HIGH] When total timeout is exceeded before another attempt, throw a timeout-typed error (e.g., `BITWIG_TIMEOUT`) instead of rethrowing stale prior exception code (AC: 2) [src/main/java/io/github/fabb/wigai/common/retry/RetryExecutor.java:73]
- [x] [AI-Review][MEDIUM] Preserve interruption semantics during backoff by throwing interruption/timeout-context failure rather than prior operation failure to improve diagnosability (AC: 1, 2) [src/main/java/io/github/fabb/wigai/common/retry/RetryExecutor.java:128]

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

- Story creation: GPT-5 Codex
- Implementation: Claude Opus 4.6

### Debug Log References

- Workflow execution: create-story → dev-story
- Target story key: `1-5-non-blocking-execution-bounded-retry-verification-baseline-tools`

### Completion Notes List

- Story selected automatically from first `backlog` entry in sprint status.
- Story context synthesized from epics, architecture, project context, previous story intelligence, git patterns, and targeted web version checks.
- Validation task file `_bmad/core/tasks/validate-workflow.xml` is not present in this repository version; manual checklist-style validation performed against workflow intent.
- Created `RetryPolicy` in `common/retry/` — bounded retry configuration with retryable/non-retryable classification using canonical `ErrorCode` taxonomy.
- Created `RetryExecutor` in `common/retry/` — bounded retry executor with exponential backoff, total timeout, structured logging integration, and fail-fast for non-retryable errors.
- Integrated retry into `McpErrorHandler.executeWithErrorHandling()` — all baseline mutating tools (using the arguments-accepting overload) get `RetryPolicy.DEFAULT` (3 attempts, 100ms backoff, 2s total timeout). Read-only overload remains unchanged (no retry).
- No individual tool or controller modifications required — retry is centralized in the `McpErrorHandler` layer.
- Created `McpTimingStressHarness` — timing-stress smoke harness extension that validates per-tool deadline compliance and actionable envelope format.
- API docs (`docs/reference/api-reference.md`) reviewed — no updates needed; error envelope format and error codes are unchanged externally.
- Test suite: 533 tests, 0 failures (up from 480 post-Story 1.4, +46 new tests for retry policy, executor, McpErrorHandler integration, and timing-stress).
- ✅ Resolved review finding [HIGH]: Timing-stress harness now wraps `client.callTool()` in `CompletableFuture.supplyAsync()` + `future.get(deadline)` so hung invocations are cancelled after the deadline.
- ✅ Resolved review finding [HIGH]: `RetryExecutor.executeWithRetry()` now enforces hard timeout via `CompletableFuture` per attempt — if `task.execute()` blocks beyond remaining timeout budget, it throws `BITWIG_TIMEOUT` (retryable, bounded).
- ✅ Resolved review finding [HIGH]: 3-arg `McpErrorHandler.executeWithErrorHandling(operation, logger, task)` now delegates with `RetryPolicy.NONE` (was incorrectly routing through `RetryPolicy.DEFAULT`). Read-only tools (status, list_tracks, get_device_details, etc.) are no longer retried.
- ✅ Resolved review finding [MEDIUM]: File List updated to include `test-summary.md`.
- ✅ Resolved review finding [MEDIUM]: Added `testExecuteWithErrorHandling_ThreeArgOverload_DoesNotRetryRetryableFailure` regression test proving the read-only path does not retry. Added `testHardTimeout_TaskBlocksIndefinitely_EventuallyThrows` test proving hung operations are bounded. Updated interrupted-backoff test to match new `CompletableFuture` threading model.
- ✅ Resolved review finding [CRITICAL]: Timing-stress harness now calls `future.cancel(true)` on timed-out `client.callTool()` futures, hoisting `future` declaration above the try block for scope access.
- ✅ Resolved review finding [HIGH]: `RetryExecutor` total-timeout-exceeded path now throws `BitwigApiException(BITWIG_TIMEOUT)` instead of rethrowing the stale prior exception, giving callers a typed timeout error code.
- ✅ Resolved review finding [MEDIUM]: Interrupted backoff in `RetryExecutor` now throws `BitwigApiException(BITWIG_TIMEOUT, "retry backoff interrupted")` instead of rethrowing the stale last exception, preserving interruption context for diagnosability.
- Test suite: 543 tests, 0 failures (+2 new: `testTotalTimeoutExceeded_ThrowsBitwigTimeoutNotStaleException`, `testInterruptedDuringBackoff_ThrowsBitwigTimeoutNotStaleException`).

### File List

- `src/main/java/io/github/fabb/wigai/common/retry/RetryPolicy.java` (NEW)
- `src/main/java/io/github/fabb/wigai/common/retry/RetryExecutor.java` (NEW)
- `src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java` (MODIFIED)
- `src/test/java/io/github/fabb/wigai/common/retry/RetryPolicyTest.java` (NEW)
- `src/test/java/io/github/fabb/wigai/common/retry/RetryExecutorTest.java` (NEW)
- `src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTest.java` (MODIFIED)
- `src/test/java/io/github/fabb/wigai/smoke/McpTimingStressHarness.java` (NEW)
- `src/test/java/io/github/fabb/wigai/smoke/McpTimingStressTest.java` (NEW)
- `_bmad-output/implementation-artifacts/1-5-non-blocking-execution-bounded-retry-verification-baseline-tools.md` (MODIFIED)
- `_bmad-output/implementation-artifacts/tests/test-summary.md` (MODIFIED)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (MODIFIED)

## Change Log

- Addressed code review findings — 5 items resolved (Date: 2026-02-10)
- Code review pass recorded new follow-up action items (1 CRITICAL, 1 HIGH, 1 MEDIUM); status moved to in-progress (Date: 2026-02-11)
- Addressed remaining code review findings — 3 items resolved (1 CRITICAL, 1 HIGH, 1 MEDIUM) (Date: 2026-02-10)
