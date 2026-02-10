# ATDD Checklist - Epic 1, Story 4: Logging + `request_id` Correlation Hardening (Mutating Tools Only)

**Date:** 2026-01-03
**Author:** Josh
**Primary Test Level:** Unit (JUnit, `@Tag("atdd_red")`)

---

## Story Summary

Add consistent structured logging for MCP tool invocations, with correlation via optional `request_id` for baseline mutating tools, while keeping log output payload-safe by default.

**As a** WigAI developer  
**I want** mutating tools to log each invocation with consistent correlation (`request_id` when provided) and outcome metadata  
**So that** I can debug failures and performance without logging sensitive/large payloads

---

## Acceptance Criteria

1. **Given** any MCP tool is invoked  
   **When** it executes  
   **Then** WigAI logs an “operation started” and “operation finished” entry including at minimum: `tool_name` and outcome (success or failure).  
2. **Given** a client supplies `request_id` in the tool arguments for a mutating tool  
   **When** the tool executes  
   **Then** all logs for that invocation include the `request_id` value.  
3. **Given** a baseline mutating tool is invoked (`transport_start`, `transport_stop`, `launch_clip`, `session_launchSceneByIndex`, `set_selected_device_parameter`, `set_selected_device_parameters`)  
   **When** it is called  
   **Then** it accepts an optional `request_id` field without breaking existing clients, and uses it for correlation in logs.  
4. **Given** a mutating tool fails  
   **When** it returns an error response  
   **Then** logs include the standardized `ErrorCode` and the `request_id` if present.  
5. **Given** a tool has potentially large inputs (current or future)  
   **When** logging parameters  
   **Then** it logs summaries only (counts/shape) and does not log full payloads by default.  
6. **Given** unit tests exist for baseline tools and/or the error handler  
   **When** tests run  
   **Then** at least one baseline mutating tool test asserts that providing `request_id` results in it being included in the structured logging parameters/context for that operation.  

---

## Failing Tests Created (RED Phase)

### Unit Tests (3 tests)

**File:** `src/test/java/io/github/fabb/wigai/mcp/tool/TransportToolRequestIdLoggingAtddRedTest.java`

- ✅ **Test:** `1.4-ATDD-001` — **Status:** RED  
  - **Failure reason (expected):** `StructuredLogger.startTimedOperation(..., parameters)` receives `null`/unsanitized parameters, so `request_id` cannot be correlated.
- ✅ **Test:** `1.4-ATDD-002` — **Status:** RED  
  - **Failure reason (expected):** error path logs do not carry `request_id` context because invocation parameters are not propagated/sanitized.

**File:** `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineMutatingToolRequestIdSchemaAtddRedTest.java`

- ✅ **Test:** `1.4-ATDD-003` — **Status:** RED  
  - **Failure reason (expected):** baseline mutating tool schemas do not define `request_id` in their `properties`.

---

## Data Factories Created

None (not required for logging correlation tests).

---

## Fixtures Created

None (Mockito-based unit tests).

---

## Mock Requirements

None (unit tests use mocked controllers + mocked `StructuredLogger`).

---

## Required data-testid Attributes

N/A (no UI selectors; this story is server-side logging).

---

## Implementation Checklist

### Test: `1.4-ATDD-001` / `1.4-ATDD-002`

**File:** `src/test/java/io/github/fabb/wigai/mcp/tool/TransportToolRequestIdLoggingAtddRedTest.java`

**Tasks to make these tests pass:**

- [ ] Add optional `request_id` to baseline mutating tool schemas (at least `transport_start` for these tests).
- [ ] Centralize `request_id` extraction + correlation in `McpErrorHandler` for mutating tools.
- [ ] Ensure `StructuredLogger.startTimedOperation(operationId, tool_name, parameters)` receives a **sanitized** parameters map that includes `request_id` when present.
- [ ] Ensure failure logs preserve the same `ErrorCode` used in the MCP error envelope, while retaining `request_id` correlation.
- [ ] Ensure log hygiene: never pass raw `req.arguments()` to logging; summarize/whitelist keys only.
- [ ] Run: `./gradlew atddRedTest --tests "*TransportToolRequestIdLoggingAtddRedTest"`
- [ ] ✅ Tests pass (green phase)

**Estimated Effort:** 2–4 hours

---

### Test: `1.4-ATDD-003`

**File:** `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineMutatingToolRequestIdSchemaAtddRedTest.java`

**Tasks to make this test pass:**

- [ ] Add optional `request_id` to each baseline mutating tool schema:
  - [ ] `transport_start`
  - [ ] `transport_stop`
  - [ ] `launch_clip`
  - [ ] `session_launchSceneByIndex` (legacy name; do not rename in this story)
  - [ ] `set_selected_device_parameter`
  - [ ] `set_selected_device_parameters`
- [ ] Run: `./gradlew atddRedTest --tests "*BaselineMutatingToolRequestIdSchemaAtddRedTest"`
- [ ] ✅ Test passes (green phase)

**Estimated Effort:** 1–2 hours

---

## Running Tests

```bash
# Run all red-phase ATDD tests (expected to fail until implementation is complete)
./gradlew atddRedTest

# Run only Story 1.4 red-phase tests
./gradlew atddRedTest --tests "*TransportToolRequestIdLoggingAtddRedTest" --tests "*BaselineMutatingToolRequestIdSchemaAtddRedTest"
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

- ✅ Tests written to enforce `request_id` correlation and schema compatibility for baseline mutating tools
- ✅ Tests assert log hygiene expectations (no raw/large payload logging)
- ✅ Tests tagged `@Tag("atdd_red")` (excluded from default `test` suite)

### GREEN Phase (DEV Team - Next Steps)

1. Implement `request_id` schema additions for baseline mutating tools.
2. Implement `request_id` propagation + parameter sanitization in `McpErrorHandler` (mutating tools).
3. Make one test pass at a time; keep MCP response envelopes stable.

### REFACTOR Phase (DEV Team - After Tests Pass)

- Refactor logging implementation to minimize duplication and ensure bounded CPU-only work on Bitwig-sensitive paths.

---

## Knowledge Base References Applied

- `test-quality.md` — deterministic tests, explicit assertions, log hygiene mindset (avoid hidden side effects)
- `test-levels-framework.md` — unit tests are primary for handler/logging behavior
- `test-healing-patterns.md` — preventative stance against flaky patterns (N/A for these unit tests, but applied as discipline)
- `timing-debugging.md` / `network-first.md` / `selector-resilience.md` — not directly applicable to this story; acknowledged but not used

---

## Test Execution Evidence

### Initial Test Run (RED Phase Verification)

**Command:** `./gradlew atddRedTest`

**Results:**

```
> Task :atddRedTest FAILED

BaselineMutatingToolRequestIdSchemaAtddRedTest > 1.4-ATDD-003 [P1] Given baseline mutating tool schemas, then they accept optional request_id without breaking clients FAILED
    org.opentest4j.AssertionFailedError

TransportToolRequestIdLoggingAtddRedTest > 1.4-ATDD-002 [P1] Given request_id for transport_start failure, when error returned, then logs include ErrorCode and request_id context FAILED
    org.opentest4j.AssertionFailedError

TransportToolRequestIdLoggingAtddRedTest > 1.4-ATDD-001 [P1] Given request_id for transport_start, when executed, then request_id is included in structured logging parameters FAILED
    org.opentest4j.AssertionFailedError

3 tests completed, 3 failed
```

**Summary:**

- Total tests: 3
- Passing: 0 (expected)
- Failing: 3 (expected)
- Status: ✅ RED phase verified

---

## Notes

- Tool renaming is out of scope for this story; `session_launchSceneByIndex` remains as-is.
- Network access for cross-checking external Playwright/Cypress/Pact docs is restricted in this environment; this checklist relies on project-local conventions and existing Gradle/JUnit patterns.
