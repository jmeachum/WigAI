# Story 1.7: Idempotency / Dedupe for Mutating Tools (`request_id` Key)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI developer,
I want mutating tools to dedupe repeated requests using `(tool_name, request_id)`,
so that retries from clients do not cause double-apply of state-changing operations.

## Acceptance Criteria

1. **Given** a mutating tool is invoked with a `request_id`
   **When** the same `(tool_name, request_id)` pair is received again within the TTL window
   **Then** WigAI returns the cached result from the first invocation without re-executing the operation.

2. **Given** a mutating tool is invoked with a `request_id`
   **When** the TTL expires or the cache reaches max entries
   **Then** the entry is evicted and a subsequent request with the same `request_id` executes normally.

3. **Given** a mutating tool is invoked without a `request_id`
   **When** it executes
   **Then** no dedupe is applied (backward compatible; idempotency is opt-in via `request_id`).

4. **Given** the dedupe cache is implemented
   **When** it stores entries
   **Then** it uses bounded in-memory storage with configurable TTL (default: 60s) and max entries (default: 1000) to prevent unbounded memory growth.

5. **Given** a dedupe cache hit occurs
   **When** WigAI logs the operation
   **Then** logs indicate "dedupe hit" with the `request_id` and original outcome (no re-execution logged).

6. **Given** unit tests exist for the dedupe mechanism
   **When** they run
   **Then** they verify: cache hit returns cached result, cache miss executes normally, TTL expiry allows re-execution, and max entries eviction works correctly.

## Tasks / Subtasks

- [ ] Implement bounded in-memory idempotency cache keyed by `(tool_name, request_id)` with TTL + max entries (AC: 1, 2, 4)
  - [ ] Add a dedicated cache component with deterministic eviction and testable clock/time abstraction
  - [ ] Add defaults: `ttl_seconds=60`, `max_entries=1000` (constants/config-driven)
  - [ ] Ensure eviction policy is deterministic and bounded (oldest-expired first, then capacity)

- [ ] Integrate dedupe into mutating MCP execution path (AC: 1, 3, 5)
  - [ ] Apply dedupe only when `request_id` is present and operation is mutating
  - [ ] Return cached first result on hit without invoking controller/facade again
  - [ ] Preserve existing success/error envelope format and `error.operation` semantics
  - [ ] Add structured "dedupe hit" logging with operation + `request_id`

- [ ] Keep retry behavior and idempotency behavior coherent (AC: 1, 2, 3)
  - [ ] Ensure retries for the first request still use existing bounded retry policy
  - [ ] Ensure dedupe occurs across repeated tool calls, not only within a single retry cycle
  - [ ] Ensure requests without `request_id` remain unaffected

- [ ] Add/extend tests for idempotency cache and integration (AC: 1, 2, 3, 4, 5, 6)
  - [ ] Unit tests: hit, miss, TTL expiry, capacity eviction, non-mutating bypass
  - [ ] McpErrorHandler integration tests: repeated mutating call with same key does not re-execute task
  - [ ] Regression tests: no envelope changes, no operation-name regressions, no read-only dedupe side effects

- [ ] Update API docs and operational notes for idempotency behavior (AC: 1, 2, 3, 4, 5)
  - [ ] Document opt-in behavior (`request_id` required)
  - [ ] Document keying semantics (`tool_name` + `request_id`), TTL, and bounded storage defaults
  - [ ] Document expected logging behavior on cache hits

## Dev Notes

### Developer Context Section

- This story implements the actual idempotency behavior promised in Story 1.4; do not rework transport or error envelope architecture.
- Existing mutating tools already accept `request_id` and pass arguments through `McpErrorHandler`; the primary integration point should stay centralized in MCP execution plumbing.
- Keep implementation constrained to in-memory, bounded behavior; no persistence or cross-restart durability is required.
- Do not broaden scope into dependency upgrades or protocol migrations in this story.

### Technical Requirements

- Dedupe key must be exactly `(tool_name, request_id)` to avoid collisions across tools.
- Dedupe must be opt-in:
  - `request_id` absent or invalid -> execute normally (no cache lookup/write).
  - `request_id` present and valid -> apply dedupe policy for mutating tools only.
- Cached result should represent the full first outcome returned to the client (success or error), so duplicates return exactly what first execution returned.
- Bounded cache requirements:
  - Default TTL: 60 seconds.
  - Default max entries: 1000.
  - Memory usage must remain bounded under sustained traffic.
- Dedupe-hit behavior must not re-run controller/facade calls or side effects.
- Preserve existing response contract:
  - `{"status":"success","data":...}` on success.
  - `{"status":"error","error":{"code","message","operation"}}` on failure.
- Preserve request-id sanitization and logging safety rules already implemented in `McpErrorHandler`.

### Architecture Compliance

- Keep current layering intact:
  - MCP tools parse/validate args and delegate.
  - Controllers/facade own business logic and Bitwig interaction.
  - Unified response handling remains in `McpErrorHandler`.
- Implement dedupe in a reusable component at MCP/common layer, not duplicated inside each tool.
- Keep `RetryExecutor`/`RetryPolicy` behavior for first execution intact.
- Avoid introducing direct Bitwig API calls in tool code.
- Preserve existing `snake_case` schemas and tool names.

### Library / Framework Requirements

- Use currently pinned project stack for implementation:
  - Java 21
  - MCP Java SDK BOM 0.11.0
  - Jetty 11.0.20
  - JUnit Jupiter 5.10.0
- Latest ecosystem context (verified February 12, 2026):
  - MCP Java SDK latest release: `v0.17.2` (October 22, 2025).
  - MCP BOM latest on Maven Central: `0.17.2`.
  - Jetty latest release line is 12.x (latest shown: `12.1.4` on October 30, 2025); Jetty docs indicate 10.0.x/11.0.x no longer have community support.
  - JUnit latest release: `6.0.2` (October 30, 2025).
- Story scope rule:
  - Do not upgrade dependencies in Story 1.7.
  - If modernization is needed, handle it in the post-Epic-1 dependency-refresh checkpoint.

### File Structure Requirements

- Primary integration files:
  - `src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java`
  - `src/main/java/io/github/fabb/wigai/common/retry/RetryExecutor.java` (only if integration point needs retry ordering adjustments)
- Likely new component files (or equivalent):
  - `src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyCache.java`
  - `src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyKey.java`
  - `src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyEntry.java`
- Mutating tool coverage touchpoints (validate behavior, avoid per-tool duplication):
  - `src/main/java/io/github/fabb/wigai/mcp/tool/TransportTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/SceneTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/SceneByNameTool.java`
  - `src/main/java/io/github/fabb/wigai/mcp/tool/DeviceParamTool.java`
- Documentation:
  - `docs/reference/api-reference.md`

### Testing Requirements

- Add deterministic unit tests for cache semantics:
  - Cache miss -> executes and stores.
  - Cache hit within TTL -> returns cached result, zero re-execution.
  - TTL expiration -> key executes again.
  - Max entries exceeded -> oldest eligible entry evicted; behavior stays bounded.
- Add/extend `McpErrorHandler` tests for idempotency integration:
  - Same mutating tool + same `request_id` returns identical first result.
  - Same `request_id` across different tools does not collide.
  - Missing `request_id` never dedupes.
  - Read-only path (`executeWithErrorHandling(operation, logger, task)`) unaffected.
- Preserve existing regression guards:
  - Envelope format unchanged.
  - `error.operation` remains MCP tool name.
  - Retry semantics remain bounded and classification-based.
- Execute `./gradlew test` and record outcomes.

### Previous Story Intelligence

- From Story 1.6:
  - Keep contract-focused, narrow edits centered on shared layers.
  - Preserve canonical error semantics and avoid unrelated refactors.
- From Story 1.5:
  - `McpErrorHandler` already centralizes bounded retry with `RetryExecutor`; idempotency should integrate with this path, not bypass it unpredictably.
- From Story 1.4:
  - `request_id` intake, sanitization, and structured logging context already exist.
  - Tool schemas already describe `request_id` as optional and idempotency as separate behavior.
- Practical implication:
  - Implement idempotency centrally in MCP execution flow to prevent per-tool drift and reduce regression risk.

### Git Intelligence Summary

Recent commits (most relevant first):
- `bd75f98` - Story 1.6 implementation touched `McpErrorHandler`, mutating tool validation paths, and contract tests.
- `6903e84` - Story 1.5 introduced bounded retry (`RetryPolicy`, `RetryExecutor`) and retry-focused tests.
- `4fa4dd0` - Story 1.4 introduced `request_id` correlation/logging behaviors across tools and tests.
- `6da3697` and `0fdabf2` - documentation-heavy commits; low direct runtime impact for idempotency.

Actionable insights for Story 1.7:
- `McpErrorHandler` is the most stable central point for dedupe orchestration.
- Mutating tools already pass through this layer with tool name + arguments; avoid per-tool custom dedupe logic.
- Existing tests already assert envelope and operation behavior; extend rather than replace.

### Latest Tech Information

Verification date: February 12, 2026.

- MCP Java SDK:
  - Latest release observed: `v0.17.2` (GitHub releases).
  - Java SDK docs show support for streamable-http and SSE transports.
  - Maven Central lists `io.modelcontextprotocol.sdk:mcp-bom` latest as `0.17.2`.
- Jetty:
  - Latest release observed: `12.1.4` (GitHub releases).
  - Jetty operations guide indicates community support ended for 10.0.x and 11.0.x.
- JUnit:
  - Latest release observed: `6.0.2` (GitHub releases).

Implementation guidance for this story:
- Keep Story 1.7 focused on dedupe correctness with current pinned dependencies.
- Defer upgrades to the planned dependency-refresh checkpoint after Epic 1.

### Project Context Reference

Canonical rules applied from `_bmad-output/project-context.md`:
- Mutating tools require optional `request_id` with `(tool_name, request_id)` dedupe.
- MCP tools must use unified `McpErrorHandler` response path.
- Preserve non-destructive, deterministic behavior and bounded runtime.
- Keep logs structured; avoid leaking payload values.

### Story Completion Status

- Status confirmed: `ready-for-dev`.
- Completion note: Ultimate context engine analysis completed - comprehensive developer guide created.
- Validation note: `_bmad/core/tasks/validate-workflow.xml` is not present in this repository snapshot, so checklist validation was performed manually against `_bmad/bmm/workflows/4-implementation/create-story/checklist.md`.

### Project Structure Notes

- The repo already centralizes MCP execution concerns in `McpErrorHandler`; this is the preferred extension point for idempotency.
- Current mutating tool set requiring dedupe coverage includes:
  - `transport_start`, `transport_stop`, `launch_clip`, `session_launchSceneByIndex`, `session_launchSceneByName`, `set_selected_device_parameter`, `set_selected_device_parameters`.
- Existing `request_id` schema descriptions explicitly mention dedupe is handled separately, which aligns with this story.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-17-Idempotency--Dedupe-for-Mutating-Tools-request_id-Key]
- [Source: _bmad-output/implementation-artifacts/sprint-status.yaml]
- [Source: _bmad-output/project-context.md]
- [Source: _bmad-output/planning-artifacts/architecture.md]
- [Source: _bmad-output/planning-artifacts/prd.md]
- [Source: _bmad-output/implementation-artifacts/1-6-align-index-validation-error-codes-with-canonical-semantics.md]
- [Source: src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java]
- [Source: src/main/java/io/github/fabb/wigai/common/retry/RetryExecutor.java]
- [Source: src/main/java/io/github/fabb/wigai/common/retry/RetryPolicy.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/McpServerManager.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/TransportTool.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/ClipTool.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/SceneTool.java]
- [Source: src/main/java/io/github/fabb/wigai/mcp/tool/DeviceParamTool.java]
- [Source: src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTest.java]
- [Source: https://github.com/modelcontextprotocol/java-sdk/releases]
- [Source: https://github.com/modelcontextprotocol/java-sdk/tree/main/mcp-server]
- [Source: https://central.sonatype.com/artifact/io.modelcontextprotocol.sdk/mcp-bom]
- [Source: https://github.com/jetty/jetty.project/releases]
- [Source: https://jetty.org/docs/jetty/12/operations-guide/getting-started/index.html]
- [Source: https://github.com/junit-team/junit-framework/releases]

## Dev Agent Record

### Agent Model Used

- GPT-5 Codex (create-story workflow)

### Debug Log References

- Workflow runner: `_bmad/core/tasks/workflow.xml`
- Workflow config: `_bmad/bmm/workflows/4-implementation/create-story/workflow.yaml`
- Workflow instructions: `_bmad/bmm/workflows/4-implementation/create-story/instructions.xml`
- Story template: `_bmad/bmm/workflows/4-implementation/create-story/template.md`
- Validation checklist: `_bmad/bmm/workflows/4-implementation/create-story/checklist.md`

### Completion Notes List

- Story selection provided by user: `1-7`.
- Canonical story key resolved from sprint status: `1-7-idempotency-dedupe-for-mutating-tools-request-id-key`.
- Story context compiled from epics, architecture, PRD, project context, previous story, and recent git history.
- Latest technical context verified with concrete date and primary-source links.
- Story set to `ready-for-dev` and sprint status updated accordingly.

### File List

- `_bmad-output/implementation-artifacts/1-7-idempotency-dedupe-for-mutating-tools-request-id-key.md` (new)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated)
