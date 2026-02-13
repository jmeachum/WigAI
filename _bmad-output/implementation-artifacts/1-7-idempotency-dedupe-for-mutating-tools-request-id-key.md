# Story 1.7: Idempotency / Dedupe for Mutating Tools (`request_id` Key)

Status: done

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

- [x] Implement bounded in-memory idempotency cache keyed by `(tool_name, request_id)` with TTL + max entries (AC: 1, 2, 4)
  - [x] Add a dedicated cache component with deterministic eviction and testable clock/time abstraction
  - [x] Add defaults: `ttl_seconds=60`, `max_entries=1000` (constants/config-driven)
  - [x] Ensure eviction policy is deterministic and bounded (oldest-expired first, then capacity)

- [x] Integrate dedupe into mutating MCP execution path (AC: 1, 3, 5)
  - [x] Apply dedupe only when `request_id` is present and operation is mutating
  - [x] Return cached first result on hit without invoking controller/facade again
  - [x] Preserve existing success/error envelope format and `error.operation` semantics
  - [x] Add structured "dedupe hit" logging with operation + `request_id`

- [x] Keep retry behavior and idempotency behavior coherent (AC: 1, 2, 3)
  - [x] Ensure retries for the first request still use existing bounded retry policy
  - [x] Ensure dedupe occurs across repeated tool calls, not only within a single retry cycle
  - [x] Ensure requests without `request_id` remain unaffected

- [x] Add/extend tests for idempotency cache and integration (AC: 1, 2, 3, 4, 5, 6)
  - [x] Unit tests: hit, miss, TTL expiry, capacity eviction, non-mutating bypass
  - [x] McpErrorHandler integration tests: repeated mutating call with same key does not re-execute task
  - [x] Regression tests: no envelope changes, no operation-name regressions, no read-only dedupe side effects

- [x] Update API docs and operational notes for idempotency behavior (AC: 1, 2, 3, 4, 5)
  - [x] Document opt-in behavior (`request_id` required)
  - [x] Document keying semantics (`tool_name` + `request_id`), TTL, and bounded storage defaults
  - [x] Document expected logging behavior on cache hits

### Review Follow-ups (AI)

- [x] [AI-Review][High] Make dedupe check/write atomic to prevent concurrent duplicate side effects for the same `(tool_name, request_id)`. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:182]
- [x] [AI-Review][High] Preserve exact request-id key semantics; avoid truncation-based dedupe collisions for long IDs. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:315]
- [x] [AI-Review][Medium] Include original outcome in dedupe-hit logs (success/error) alongside `request_id`. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:188]
- [x] [AI-Review][Medium] Make idempotency TTL and max entries runtime-configurable while keeping defaults (60s/1000). [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:26]
- [x] [AI-Review][High] Add `request_id` support to `session_launchSceneByName` and route through mutating dedupe path to prevent duplicate scene launches on retries. [src/main/java/io/github/fabb/wigai/mcp/tool/SceneByNameTool.java:54]
- [x] [AI-Review][High] Harden idempotency cache bootstrap: invalid system properties must fail safe to defaults instead of throwing during static initialization. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:33]
- [x] [AI-Review][Medium] Bound raw `request_id` size used for cache keying to avoid oversized-key memory/CPU pressure while preserving collision-safe semantics. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:331]
- [x] [AI-Review][Medium] Enforce strict `maxEntries` under concurrent inserts (no temporary over-capacity) by making eviction+insert capacity control globally consistent. [src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyCache.java:108]
- [x] [AI-Review][High] Add `request_id` parameter documentation to the `session_launchSceneByName` API reference entry so mutating idempotency behavior is explicit for this tool. [docs/reference/api-reference.md:322]
- [x] [AI-Review][High] Document oversized `request_id` behavior (`length > 1024` skips dedupe) in the Idempotency section to align published contract with implementation. [docs/reference/api-reference.md:829]
- [x] [AI-Review][Medium] Replace placeholder-style `SceneByNameToolTest` assertions with handler-level execution tests that verify dedupe wiring and runtime behavior. [src/test/java/io/github/fabb/wigai/mcp/tool/SceneByNameToolTest.java:63]
- [x] [AI-Review][Medium] Avoid unnecessary eviction on existing-key overwrite in `IdempotencyCache.put()` by treating replacements separately from capacity-growth inserts. [src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyCache.java:117]
- [x] [AI-Review][High] Make eviction deterministic when `createdAt` ties occur (same-millisecond inserts) so oldest selection remains stable and task claim is accurate. [src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyCache.java:200]
- [x] [AI-Review][High] Enforce mutating-only dedupe semantics in shared execution path (not only by caller convention) or explicitly gate by operation classification. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:231]
- [x] [AI-Review][Medium] Reset static idempotency cache in `SceneByNameToolTest` setup to prevent cross-test contamination and order-dependent dedupe behavior. [src/test/java/io/github/fabb/wigai/mcp/tool/SceneByNameToolTest.java:36]
- [x] [AI-Review][High] Add regression coverage ensuring `MUTATING_OPERATIONS` allowlist stays in sync with all mutating tools that expose `request_id` to prevent dedupe bypass drift. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:295]
- [x] [AI-Review][Medium] Define and enforce idempotency behavior for mutating-tool paths that use `executeWithValidation(...)` so dedupe cannot be skipped by helper choice. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:440]
- [x] [AI-Review][Medium] Remove reflection-based cache reset in `SceneByNameToolTest` by exposing a test-safe reset hook/package-private helper and use it directly for stable isolation. [src/test/java/io/github/fabb/wigai/mcp/tool/SceneByNameToolTest.java:40]
- [x] [AI-Review][High] Replace static expected-set allowlist drift test with discovery-based coverage that derives mutating `request_id` tool names and asserts parity with `MUTATING_OPERATIONS` to prevent manual dual-list drift. [src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTest.java:41]
- [x] [AI-Review][Medium] Add `src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTestHooks.java` to story File List and keep File List in lockstep with git-changed files. [_bmad-output/implementation-artifacts/1-7-idempotency-dedupe-for-mutating-tools-request-id-key.md:324]
- [x] [AI-Review][Medium] Reconcile round-6 documentation records by updating Change Log and adding a matching `Senior Developer Review (AI) - Round 6` section so story audit trail is internally consistent. [_bmad-output/implementation-artifacts/1-7-idempotency-dedupe-for-mutating-tools-request-id-key.md:339]
- [x] [AI-Review][High] Enforce payload consistency for repeated `(tool_name, request_id)` calls (store and compare request fingerprint); reject mismatched payload replays instead of returning stale cached result. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:232]
- [x] [AI-Review][Medium] Reject control/non-printable `request_id` values for dedupe keying to align key semantics with logging-safe correlation and avoid invisible-key ambiguity. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:391]
- [x] [AI-Review][Medium] Replace schema string-substring detection (`contains("request_id")`) in allowlist parity test with structural JSON-schema inspection to avoid brittle false positives/negatives. [src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTest.java:80]
- [x] [AI-Review][High] Replace/augment 32-bit payload fingerprint hashing with collision-resistant payload canonicalization+digest (or exact normalized payload comparison) so different payloads cannot be incorrectly treated as identical dedupe hits. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:347]
- [x] [AI-Review][Medium] Route dedupe payload-mismatch rejections through the same structured timed-operation/error telemetry path as other failures (start/failure logging parity). [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:241]
- [x] [AI-Review][Medium] Reconcile story audit trail for round-8 records (add matching review section and align round-7 addressed date chronology). [_bmad-output/implementation-artifacts/1-7-idempotency-dedupe-for-mutating-tools-request-id-key.md:337]
- [x] [AI-Review][High] Make payload canonicalization unambiguous by escaping/delimiting keys and string values (or use canonical JSON serializer) so distinct payloads cannot serialize to the same canonical text before hashing. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:388]
- [x] [AI-Review][Medium] Normalize numeric values during payload fingerprinting (e.g., canonical decimal form) so semantically equivalent numbers like `1` and `1.0` do not trigger false payload-mismatch rejections. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:408]
- [x] [AI-Review][Medium] Reconcile round-9 audit records by adding matching change-log/review entries for the documented round-9 completion notes. [_bmad-output/implementation-artifacts/1-7-idempotency-dedupe-for-mutating-tools-request-id-key.md:344]
- [x] [AI-Review][High] Enforce strict printable-ASCII (`32..126`) validation for dedupe keying to match the published `request_id` contract and reject non-ASCII/control-adjacent ambiguity. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:573]
- [x] [AI-Review][Medium] Replace manual tool-spec enumeration in mutating allowlist parity tests with runtime registration/discovery from the authoritative MCP registration path to prevent silent drift. [src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTest.java:59]
- [x] [AI-Review][Medium] Replace `inputSchema().toString().contains("request_id")` with structural schema assertions that verify `request_id` exists in `properties` and remains optional (not in `required`). [src/test/java/io/github/fabb/wigai/mcp/tool/BaselineMutatingToolRequestIdSchemaAtddRedTest.java:48]
- [x] [AI-Review][High] Normalize floating-point numeric payload values using canonical textual forms (`Float.toString`/`Double.toString`) before decimal normalization so equivalent client payloads do not diverge (`0.1f` vs `0.1d`) and trigger false payload-mismatch errors. [src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java:447]
- [x] [AI-Review][Medium] Replace remaining schema substring assertion in `SceneByNameToolTest` with structural `properties`/`required` checks for `request_id` optionality to match the established schema-test contract. [src/test/java/io/github/fabb/wigai/mcp/tool/SceneByNameToolTest.java:61]
- [x] [AI-Review][Medium] Remove and prevent accidental commit of ad-hoc workspace artifacts (`io/` tree and compiled `.class` file) that are outside source/story file list tracking. [io/io/modelcontextprotocol/spec/McpSchema$JsonSchema.class:1]

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

- Status confirmed: `done`.
- Completion note: Story implementation and review cycles (through round 11) are complete with all follow-ups resolved.
- Validation note: Full test suite passed (`./gradlew test`) and sprint tracking was updated to `done` on 2026-02-13.

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
- Claude Opus 4.6 (dev-story workflow)

### Debug Log References

- Workflow runner: `_bmad/core/tasks/workflow.xml`
- Workflow config: `_bmad/bmm/workflows/4-implementation/create-story/workflow.yaml`
- Workflow instructions: `_bmad/bmm/workflows/4-implementation/create-story/instructions.xml`
- Story template: `_bmad/bmm/workflows/4-implementation/create-story/template.md`
- Validation checklist: `_bmad/bmm/workflows/4-implementation/create-story/checklist.md`

### Implementation Plan

- **IdempotencyKey**: Record `(toolName, requestId)` — composite key with validation in compact constructor.
- **IdempotencyEntry**: Record storing `CallToolResult` + `createdAt` timestamp with `isExpired()` check.
- **IdempotencyCache**: `ConcurrentHashMap`-backed bounded cache with `LongSupplier` clock for testability. Eviction: expired entries first, then oldest by `createdAt`.
- **McpErrorHandler integration**: Dedupe check wraps around retry+execute in the 5-arg `executeWithErrorHandling`. Cache hit returns immediately before `timedOperation`. Both success and error results are cached. Read-only 3-arg path passes `null` arguments → no dedupe.
- **Logging**: `logger.info` with structured "Dedupe hit" message including `request_id`.

### Completion Notes List

- Story selection provided by user: `1-7`.
- Canonical story key resolved from sprint status: `1-7-idempotency-dedupe-for-mutating-tools-request-id-key`.
- Story context compiled from epics, architecture, PRD, project context, previous story, and recent git history.
- Latest technical context verified with concrete date and primary-source links.
- Story set to `ready-for-dev` and sprint status updated accordingly.
- All 5 tasks implemented and tested. Full test suite passes with zero regressions.
- IdempotencyCache unit tests: 21 tests covering hit, miss, TTL expiry, capacity eviction, key validation, constructor validation, default constructor.
- McpErrorHandler integration tests: 11 new tests covering dedupe hit/miss, cross-tool isolation, no-request_id bypass, null/invalid request_id bypass, cached error results, TTL expiry re-execution, read-only path exclusion, retry-then-dedupe coherence, envelope format preservation, operation name preservation.
- API reference updated with Idempotency / Deduplication section and updated `request_id` parameter descriptions.
- ✅ Resolved review finding [High]: Atomic dedupe check/write via striped locking in `IdempotencyCache.getOrCompute()` — prevents concurrent duplicate side effects for same `(tool_name, request_id)`.
- ✅ Resolved review finding [High]: Cache keying uses full raw `request_id` (no truncation) via `extractRawRequestId()` — eliminates truncation-based collisions for long IDs.
- ✅ Resolved review finding [Medium]: Dedupe-hit log now includes `outcome=success|error` alongside `request_id`.
- ✅ Resolved review finding [Medium]: TTL and max entries are runtime-configurable via system properties `wigai.idempotency.ttl.millis` and `wigai.idempotency.max.entries`, with defaults preserved.
- New tests: 4 `getOrCompute` unit tests (miss, hit, expiry, concurrent atomicity), 9 `McpErrorHandler` tests (extractRawRequestId, long-ID non-collision, outcome logging, system property configurability). Total: 78 tests across both test files.
- ✅ Resolved review finding [High]: `SceneByNameTool` now includes `request_id` in schema and routes through 4-arg mutating `executeWithErrorHandling` path (retry + dedupe). Added to `BaselineMutatingToolRequestIdSchemaAtddRedTest`.
- ✅ Resolved review finding [High]: `createDefaultCache()` now uses `parseLongProperty`/`parseIntProperty` helper methods that catch `NumberFormatException` and reject non-positive values, falling back to defaults. Static initialization can no longer throw.
- ✅ Resolved review finding [Medium]: `extractRawRequestId()` rejects request_ids exceeding `MAX_RAW_REQUEST_ID_LENGTH` (1024 chars) — returns null (skip dedupe) rather than truncating, preserving collision-safe semantics.
- ✅ Resolved review finding [Medium]: `IdempotencyCache.put()` now uses a `ReentrantLock` (`capacityLock`) to serialize eviction+insert, preventing concurrent inserts from bypassing capacity control.
- New tests (round 3): 1 SceneByNameTool schema test, 4 McpErrorHandler bootstrap hardening tests (non-numeric TTL, non-numeric max, negative TTL, zero max), 3 extractRawRequestId boundary tests (exact max, oversized rejection, oversized dedupe skip), 1 concurrent capacity enforcement test.
- ✅ Resolved review finding [High]: API reference now documents `request_id` parameter for `session_launchSceneByName`.
- ✅ Resolved review finding [High]: Idempotency section now documents oversized `request_id` handling (`length > 1024` skips dedupe).
- ✅ Resolved review finding [Medium]: Replaced placeholder `SceneByNameToolTest` checks with handler-level execution tests (success/error/validation plus dedupe runtime behavior).
- ✅ Resolved review finding [Medium]: `IdempotencyCache.put()` now skips eviction when overwriting an existing key at capacity.
- New tests (round 4): `SceneByNameToolTest` rewritten for handler execution coverage; added `IdempotencyCacheTest.put_AtCapacity_ReplacingExistingKey_DoesNotEvictUnrelatedEntry`; red/green verified with targeted runs and full `./gradlew test`.
- ✅ Resolved review finding [High]: `IdempotencyCache` eviction now uses deterministic key ordering when `createdAt` timestamps tie, ensuring stable oldest selection.
- ✅ Resolved review finding [High]: `McpErrorHandler` now gates dedupe to explicit mutating operations in shared execution path; non-mutating operations skip dedupe even if `request_id` is provided.
- ✅ Resolved review finding [Medium]: `SceneByNameToolTest` now resets shared static idempotency cache in `@BeforeEach` to prevent cross-test dedupe contamination.
- New tests (round 5): `IdempotencyCacheTest.put_AtCapacity_TiedCreatedAt_EvictsDeterministicallyByKey`; `McpErrorHandlerTest.testDedupe_NonMutatingOperationWithRequestId_DoesNotDedupesOnSharedPath`; full `./gradlew test` green.
- ✅ Resolved review finding [High]: Added allowlist drift guard test (`testMutatingOperationsAllowlist_StaysInSyncWithMutatingRequestIdTools`) and a test-only accessor (`mutatingOperationsForTest`) so mutating `request_id` tool coverage remains aligned with `MUTATING_OPERATIONS`.
- ✅ Resolved review finding [Medium]: `executeWithValidation(...)` now enforces idempotency semantics for mutating operations by delegating to shared `executeWithErrorHandling(...)` path (dedupe + default retry), with `RetryPolicy.NONE` retained for non-mutating operations.
- ✅ Resolved review finding [Medium]: Replaced reflection-based cache reset in `SceneByNameToolTest` with direct test hook usage via new `McpErrorHandlerTestHooks.resetIdempotencyCache()`.
- New tests (round 6): `McpErrorHandlerTest.testMutatingOperationsAllowlist_StaysInSyncWithMutatingRequestIdTools`; `McpErrorHandlerTest.testExecuteWithValidation_MutatingOperationWithRequestId_Dedupes`; `McpErrorHandlerTest.testExecuteWithValidation_NonMutatingOperationWithRequestId_DoesNotDedupe`; full `./gradlew test` green.
- ✅ Resolved review finding [High]: Replaced static expected-set allowlist drift test with discovery-based coverage that instantiates all tool specifications, derives `request_id`-bearing tool names, and asserts parity with `MUTATING_OPERATIONS`.
- ✅ Resolved review finding [Medium]: Added `McpErrorHandlerTestHooks.java` to story File List.
- ✅ Resolved review finding [Medium]: Reconciled round-6 documentation records — added Change Log entry for round 6 resolution.
- New tests (round 7): `McpErrorHandlerTest.testMutatingOperationsAllowlist_StaysInSyncWithMutatingRequestIdTools` rewritten as discovery-based; full `./gradlew test` green.
- Senior code review (round 7) completed on 2026-02-13; 3 new follow-ups added (1 High, 2 Medium). No auto-fixes applied by request.
- ✅ Resolved review finding [High]: Payload consistency enforcement — `IdempotencyEntry` stores `payloadFingerprint`; `IdempotencyCache.getOrCompute` compares fingerprints on hit; `McpErrorHandler` computes fingerprint from non-correlation arguments and rejects mismatched replays with `INVALID_PARAMETER` error.
- ✅ Resolved review finding [Medium]: `extractRawRequestId()` now rejects `request_id` values containing control/non-printable characters (ASCII 0-31, 127) — returns null (skip dedupe) to align key semantics with logging-safe correlation.
- ✅ Resolved review finding [Medium]: Allowlist parity test replaced `toString().contains("request_id")` with structural `inputSchema().properties().containsKey("request_id")` inspection.
- New tests (round 8): 9 `McpErrorHandlerTest` tests (payload consistency: same/different/empty payload dedupe, fingerprint unit tests; control-char rejection: 4 extractRawRequestId tests + 1 integration test); 3 `IdempotencyCacheTest` tests (same/different fingerprint getOrCompute, entry fingerprint storage). Full `./gradlew test` green.
- ✅ Resolved review finding [High]: Replaced 32-bit `int` payload fingerprint with collision-resistant SHA-256 hex digest. `computePayloadFingerprint` now canonicalizes non-correlation arguments (sorted keys, deterministic recursive serialization with type-tagged strings) and produces a 64-char hex SHA-256 digest. `IdempotencyEntry.payloadFingerprint` changed from `int` to `String`; `IdempotencyCache` comparison updated to `Objects.equals`.
- ✅ Resolved review finding [Medium]: Dedupe payload-mismatch rejections now route through `startTimedOperation` → `failure(ErrorCode.INVALID_PARAMETER, ...)` before returning the error response, achieving logging parity with other failure paths.
- ✅ Resolved review finding [Medium]: Reconciled round-8 audit trail — corrected round-7 addressed date from 2026-02-12 to 2026-02-13; added round-8 Change Log entry.
- New tests (round 9): 3 `McpErrorHandlerTest` tests (`testComputePayloadFingerprint_ReturnsSha256HexDigest`, `testComputePayloadFingerprint_Deterministic`, `testDedupe_PayloadMismatch_RoutedThroughTimedOperationTelemetry`). Full `./gradlew test` green — 686 tests, 0 failures.
- ✅ Resolved review finding [High]: Payload canonicalization is now unambiguous via typed, length-delimited encoding for map keys and values, collection elements, and scalar types before SHA-256 hashing; delimiter-heavy collision candidates no longer fingerprint-identically.
- ✅ Resolved review finding [Medium]: Numeric values are now normalized to canonical decimal form during fingerprinting (e.g., `1`, `1.0`, and `1.00` hash identically) to prevent false payload-mismatch rejections.
- ✅ Resolved review finding [Medium]: Reconciled round-9 audit records by adding the missing "Addressed code review findings (round 9)" change-log entry.
- New tests (round 9 follow-up completion): 2 `McpErrorHandlerTest` tests (`testComputePayloadFingerprint_CollisionCandidatePayloads_DifferentFingerprint`, `testComputePayloadFingerprint_NormalizesEquivalentNumbers`). Full `./gradlew test` green.
- ✅ Resolved review finding [High]: `extractRawRequestId()` now enforces strict printable-ASCII (32..126) validation — characters above 126 (non-ASCII/Unicode) are rejected, matching the published `request_id` contract.
- ✅ Resolved review finding [Medium]: Allowlist parity test now derives tool specs from `McpServerManager.allToolSpecifications()` (the authoritative MCP registration path) instead of manually enumerating specs in the test.
- ✅ Resolved review finding [Medium]: `BaselineMutatingToolRequestIdSchemaAtddRedTest` assertion replaced with structural `inputSchema().properties().containsKey("request_id")` and optional-verification via `required()` list.
- New tests (round 10): 2 `McpErrorHandlerTest` tests (`testExtractRawRequestId_NonAsciiCharacters_ReturnsNull`, `testExtractRawRequestId_BoundaryPrintableAscii_Accepted`). Full `./gradlew test` green — 690 tests, 0 failures.
- ✅ Resolved review finding [High]: `normalizeNumber` for Float/Double now uses `new BigDecimal(Float.toString(...))` / `new BigDecimal(Double.toString(...))` instead of `BigDecimal.valueOf(floatValue.doubleValue())` — `0.1f` and `0.1d` now produce identical canonical forms and fingerprints.
- ✅ Resolved review finding [Medium]: `SceneByNameToolTest.specificationCreation_ExposesExpectedToolMetadata` assertion replaced with structural `inputSchema().properties().containsKey("request_id")` and optional-verification via `required()` list.
- ✅ Resolved review finding [Medium]: Removed stray `io/` workspace artifact tree (`.java` source + `.class` file) from project root.
- New tests (round 11): 2 `McpErrorHandlerTest` tests (`testComputePayloadFingerprint_FloatAndDoubleParity`, `testComputePayloadFingerprint_FloatAndDoubleParityNested`). Full `./gradlew test` green — 692 tests, 0 failures.
- Story finalized by reviewer and moved to `done` with sprint tracking synchronized.

### File List

- `_bmad-output/implementation-artifacts/1-7-idempotency-dedupe-for-mutating-tools-request-id-key.md` (updated)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated)
- `src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyKey.java` (new)
- `src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyEntry.java` (new)
- `src/main/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyCache.java` (new/modified)
- `src/main/java/io/github/fabb/wigai/mcp/McpErrorHandler.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/McpServerManager.java` (modified)
- `src/main/java/io/github/fabb/wigai/mcp/tool/SceneByNameTool.java` (modified)
- `src/test/java/io/github/fabb/wigai/mcp/idempotency/IdempotencyCacheTest.java` (new/modified)
- `src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTest.java` (modified)
- `src/test/java/io/github/fabb/wigai/mcp/tool/SceneByNameToolTest.java` (modified)
- `src/test/java/io/github/fabb/wigai/mcp/McpErrorHandlerTestHooks.java` (new)
- `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineMutatingToolRequestIdSchemaAtddRedTest.java` (modified)
- `docs/reference/api-reference.md` (modified)

### Change Log

- Implemented idempotency deduplication for mutating MCP tools via `(tool_name, request_id)` keying (Date: 2026-02-12)
- Senior code review completed; added 4 AI review follow-ups (2 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-12)
- Addressed code review findings — 4 items resolved (2 High, 2 Medium); story moved to `review` (Date: 2026-02-12)
- Senior code review (round 2) completed; added 4 AI review follow-ups (2 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-12)
- Addressed code review findings (round 2) — 4 items resolved (2 High, 2 Medium); story moved to `review` (Date: 2026-02-12)
- Senior code review (round 3) completed; added 4 AI review follow-ups (2 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-12)
- Addressed code review findings (round 3) — 4 items resolved (2 High, 2 Medium); story moved to `review` (Date: 2026-02-12)
- Senior code review (round 4) completed; added 3 AI review follow-ups (2 High, 1 Medium) and moved story to `in-progress` (Date: 2026-02-12)
- Addressed code review findings (round 4) — 3 items resolved (2 High, 1 Medium); story moved to `review` (Date: 2026-02-12)
- Senior code review (round 5) completed; added 3 AI review follow-ups (1 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-12)
- Addressed code review findings (round 5) — 3 items resolved (1 High, 2 Medium); story moved to `review` (Date: 2026-02-12)
- Senior code review (round 6) completed; added 3 AI review follow-ups (1 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-12)
- Addressed code review findings (round 6) — 3 items resolved (1 High, 2 Medium); story moved to `review` (Date: 2026-02-12)
- Senior code review (round 7) completed; added 3 AI review follow-ups (1 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-13)
- Addressed code review findings (round 7) — 3 items resolved (1 High, 2 Medium); story moved to `review` (Date: 2026-02-13)
- Senior code review (round 8) completed; added 3 AI review follow-ups (1 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-13)
- Addressed code review findings (round 8) — 3 items resolved (1 High, 2 Medium); story moved to `review` (Date: 2026-02-13)
- Senior code review (round 9) completed; added 3 AI review follow-ups (1 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-13)
- Addressed code review findings (round 9) — 3 items resolved (1 High, 2 Medium); story moved to `review` (Date: 2026-02-13)
- Senior code review (round 10) completed; added 3 AI review follow-ups (1 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-13)
- Addressed code review findings (round 10) — 3 items resolved (1 High, 2 Medium); story moved to `review` (Date: 2026-02-13)
- Senior code review (round 11) completed; added 3 AI review follow-ups (1 High, 2 Medium) and moved story to `in-progress` (Date: 2026-02-13)
- Addressed code review findings (round 11) — 3 items resolved (1 High, 2 Medium); story moved to `review` (Date: 2026-02-13)
- Final reviewer sign-off completed; story moved to `done` (Date: 2026-02-13)

### Round Traceability Matrix

| Round | Review Date | Findings Added | Findings Addressed | Net Open After Round | Story State |
|---|---|---:|---:|---:|---|
| 1 | 2026-02-12 | 4 (2 High, 2 Medium) | 4 | 0 | `review` |
| 2 | 2026-02-12 | 4 (2 High, 2 Medium) | 4 | 0 | `review` |
| 3 | 2026-02-12 | 4 (2 High, 2 Medium) | 4 | 0 | `review` |
| 4 | 2026-02-12 | 3 (2 High, 1 Medium) | 3 | 0 | `review` |
| 5 | 2026-02-12 | 3 (1 High, 2 Medium) | 3 | 0 | `review` |
| 6 | 2026-02-12 | 3 (1 High, 2 Medium) | 3 | 0 | `review` |
| 7 | 2026-02-13 | 3 (1 High, 2 Medium) | 3 | 0 | `review` |
| 8 | 2026-02-13 | 3 (1 High, 2 Medium) | 3 | 0 | `review` |
| 9 | 2026-02-13 | 3 (1 High, 2 Medium) | 3 | 0 | `review` |
| 10 | 2026-02-13 | 3 (1 High, 2 Medium) | 3 | 0 | `review` |
| 11 | 2026-02-13 | 3 (1 High, 2 Medium) | 3 | 0 | `done` |

### Current Open Gate (Round 11)

- Gate status: **CLOSED** (all 3 findings resolved; story moved to `done`).
- All Round 11 findings checked as resolved in `Review Follow-ups (AI)`.
- Targeted tests and full `./gradlew test` pass — 692 tests, 0 failures.
- Change-log entry added: `Addressed code review findings (round 11)`.

## Senior Developer Review (AI)

- Date: 2026-02-12
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 4 issues identified (2 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 2

- Date: 2026-02-12
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 4 additional issues identified (2 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 3

- Date: 2026-02-12
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 4 additional issues identified (2 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 4

- Date: 2026-02-12
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 3 additional issues identified (2 High, 1 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 5

- Date: 2026-02-12
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 3 additional issues identified (1 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 6

- Date: 2026-02-12
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 3 additional issues identified (1 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 7

- Date: 2026-02-13
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 3 additional issues identified (1 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 8

- Date: 2026-02-13
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 3 additional issues identified (1 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 9

- Date: 2026-02-13
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 3 additional issues identified (1 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 10

- Date: 2026-02-13
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 3 additional issues identified (1 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.

## Senior Developer Review (AI) - Round 11

- Date: 2026-02-13
- Reviewer: Josh
- Outcome: Changes Requested
- Summary: 3 additional issues identified (1 High, 2 Medium); no automatic fixes applied by request; action items added under `Review Follow-ups (AI)`.
