# ATDD Checklist - Epic 1, Story 1.2: Localhost Binding Defaults + Preferences Guardrails

**Date:** 2025-12-27T12:57:42-07:00
**Author:** Josh
**Primary Test Level:** Unit (config validation) + lightweight integration (observer notifications)

---

## Story Summary

Story 1.2 enforces loopback-only binding defaults and preference guardrails so the MCP server is never exposed on the network in the no-auth MVP. It also requires clear warnings, consistent preference UI behavior, and graceful restarts on valid port changes.

**As a** WigAI user
**I want** the MCP server to bind to localhost by default with guardrails on host/port preferences
**So that** WigAI is not accidentally exposed on the network and connection details remain predictable

---

## Acceptance Criteria

> **Note:** `localhost`, `127.0.0.1`, and `::1` are treated as equivalent loopback hosts. The implementation normalizes casing (e.g., `LOCALHOST` → `localhost`) and uses deterministic numeric loopback for binding when `localhost` is configured (defense-in-depth, no DNS). The advertised URL always uses the actual bind address to ensure reachability (e.g., `localhost` configured → binds to `127.0.0.1` → advertises `http://127.0.0.1:{port}/mcp`). IPv6 addresses are formatted with brackets in URLs (e.g., `http://[::1]:61169/mcp`).

1. **Given** WigAI is enabled for the first time in Bitwig
   **When** the MCP server starts
   **Then** it binds to a loopback address (`localhost`, `127.0.0.1`, or `::1`) on the default port `61169` and advertises the actual bind address in logs/notification (e.g., `http://127.0.0.1:61169/mcp` or `http://[::1]:61169/mcp`).
2. **Given** the user edits "MCP Host" in Bitwig preferences
   **When** the host value is empty or whitespace
   **Then** it is sanitized to `localhost` and the server remains reachable at a loopback address.
3. **Given** the user attempts to set "MCP Host" to a non-loopback value (e.g., `0.0.0.0`, `192.168.x.x`, a public hostname)
   **When** the setting is applied
   **Then** WigAI refuses for MVP (no-auth) and reverts to `localhost`, logging a clear warning explaining why.
4. **Given** the user changes "MCP Port" to another valid port (1024–65535)
   **When** the setting is applied
   **Then** WigAI performs a graceful restart and the MCP endpoint is reachable at the loopback address and new port (e.g., `http://127.0.0.1:{new_port}/mcp` or `http://[::1]:{new_port}/mcp`).
5. **Given** the configured port cannot be bound (e.g., already in use)
   **When** WigAI tries to start or restart the server
   **Then** it reports a clear, actionable error (suggesting choosing another port) and does not crash Bitwig.

---

## Failing Tests Created (RED Phase)

This repo is Java/JUnit-based (not Playwright/Cypress). For this story, acceptance tests map to CI-safe unit tests that exercise the configuration guardrails in `PreferencesBackedConfigManager`.

### JUnit Tests (6 tests)

**File:** `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java` (183 lines)

- ✅ **Test:** `1.2-ATDD-001 defaults_to_localhost_and_default_port_on_first_load`
  - **Status:** GREEN - init-time sanitization validates and writes back defaults
  - **Verifies:** Default host/port are loopback-safe for first enable
- ✅ **Test:** `1.2-ATDD-002 empty_or_whitespace_host_is_sanitized_and_written_back`
  - **Status:** GREEN - empty/whitespace hosts are sanitized to `localhost` and persisted
  - **Verifies:** Empty/whitespace host is sanitized and persisted to avoid UI drift
- ✅ **Test:** `1.2-ATDD-003 non_loopback_host_is_rejected_and_reverted`
  - **Status:** GREEN - non-loopback hosts are rejected with warning and reverted
  - **Verifies:** Non-loopback values are refused with warning and reverted to `localhost`
- ✅ **Test:** `1.2-ATDD-004 valid_port_change_notifies_observers`
  - **Status:** GREEN - port changes notify observers for graceful restart
  - **Verifies:** Valid port changes notify observers for restart
- ✅ **Test:** `1.2-ATDD-004b valid_port_change_triggers_restart_with_new_port`
  - **Status:** GREEN - config→observer→restart chain validated
  - **Verifies:** Valid port changes trigger restart with correct port values (AC4 config→observer→restart chain; actual endpoint reachability requires integration testing with a running server)
- ✅ **Test:** `1.2-ATDD-005 invalid_port_reverts_to_default_and_is_written_back`
  - **Status:** GREEN - invalid ports revert to default and persist
  - **Verifies:** Out-of-range port values fall back to `AppConstants.DEFAULT_MCP_PORT` and persist to preferences

### E2E Tests (0 tests)

Not applicable: WigAI is a Bitwig extension with no browser UI.

### API Tests (0 tests)

Not applicable: no REST API surface in scope.

### Component Tests (0 tests)

Not applicable: no UI component layer in scope.

---

## Data Factories Created

Not applicable for Story 1.2 (no persisted domain entities required).

---

## Fixtures Created

Not applicable (JUnit tests are isolated with Mockito-based stubs).

---

## Mock Requirements

Use Mockito stubs for Bitwig interfaces:

- `ControllerHost` + `Preferences`
- `SettableStringValue` for host preference
- `SettableRangedValue` for port preference

No external services required.

---

## Required data-testid Attributes

Not applicable (no UI tests).

---

## Implementation Checklist

### Test: `1.2-ATDD-001 defaults_to_localhost_and_default_port_on_first_load`

**File:** `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java`

**Tasks to make this test pass:**

- [x] Sanitize host/port on initialization (apply `validateHost`/`validatePort` to initial values)
- [x] Ensure defaults remain `localhost` + `AppConstants.DEFAULT_MCP_PORT` on first enable
- [x] Run test: `./gradlew test --tests "*AtddTest"`
- [x] ✅ Test passes (green phase)

**Estimated Effort:** 0.5–1.0 hours

---

### Test: `1.2-ATDD-002 empty_or_whitespace_host_is_sanitized_and_written_back`

**File:** `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java`

**Tasks to make this test pass:**

- [x] Update `validateHost` to treat empty/whitespace as `localhost`
- [x] On invalid host input via preference observer, write back `localhost`
- [x] Run test: `./gradlew test --tests "*AtddTest"`
- [x] ✅ Test passes (green phase)

**Estimated Effort:** 0.5–1.0 hours

---

### Test: `1.2-ATDD-003 non_loopback_host_is_rejected_and_reverted`

**File:** `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java`

**Tasks to make this test pass:**

- [x] Enforce loopback-only host allowlist (`localhost`, `127.0.0.1`, `::1`)
- [x] Reject non-loopback values and revert preferences to `localhost`
- [x] Log a clear warning describing the refusal for MVP (no-auth)
- [x] Run test: `./gradlew test --tests "*AtddTest"`
- [x] ✅ Test passes (green phase)

**Estimated Effort:** 1.0–2.0 hours

---

### Test: `1.2-ATDD-004 valid_port_change_notifies_observers`

**File:** `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java`

**Tasks to make this test pass:**

- [x] Ensure `ConfigChangeObserver` is notified on valid port change
- [x] Confirm `WigAIExtension` performs graceful restart on observer callback
- [x] Run test: `./gradlew test --tests "*AtddTest"`
- [x] ✅ Test passes (green phase)

**Estimated Effort:** 0.5–1.0 hours

---

### Test: `1.2-ATDD-005 invalid_port_reverts_to_default_and_is_written_back`

**File:** `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java`

**Tasks to make this test pass:**

- [x] On invalid port input via preference observer, revert to `AppConstants.DEFAULT_MCP_PORT`
- [x] Persist the fallback port value back into preferences
- [x] Log a clear warning for the invalid port
- [x] Run test: `./gradlew test --tests "*AtddTest"`
- [x] ✅ Test passes (green phase)

**Estimated Effort:** 0.5–1.0 hours

---

## Running Tests

```bash
# Run all tests including ATDD tests (now GREEN after implementation)
./gradlew test

# Run ATDD tests specifically
./gradlew test --tests "*AtddTest"
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

- ✅ Acceptance criteria mapped to atomic tests
- ✅ Tests written and tagged `@Tag("atdd")`
- ✅ Implementation checklist created

**Verification:**

- Run `./gradlew test` to verify all ATDD tests pass (now GREEN).

---

### GREEN Phase (DEV Team - Next Steps)

1. Implement host validation guardrails in `PreferencesBackedConfigManager`.
2. Ensure invalid host/port values are written back to preferences for UI consistency.
3. Keep graceful restart flow intact in `WigAIExtension`.

---

### REFACTOR Phase (DEV Team - After All Tests Pass)

1. Simplify validation logic and ensure warnings are consistent.
2. Add shared helper methods for loopback checks to avoid duplication.
3. Confirm logs and notifications are actionable.

---

## Next Steps

1. Review this checklist in planning/standup.
2. Implement guardrails to make `./gradlew test --tests "*AtddTest"` green.
3. Verify bind-failure messaging behavior during manual testing.

---

## Knowledge Base References Applied

- `junit-mockito.md` - JUnit 5 + Mockito patterns and lifecycle
- `test-quality.md` - Determinism, isolation, explicit assertions
- `test-levels-framework.md` - Unit vs integration selection
- `data-factories.md` - Factory patterns (not required for this story)

---

## Test Execution Evidence

### GREEN Phase Verification (2025-12-29)

**Command:** `./gradlew test --tests "*AtddTest"`

**Results:**

```
> Task :test
BUILD SUCCESSFUL in 1s
4 actionable tasks: 1 executed, 3 up-to-date
```

All 6 ATDD tests pass. Implementation is complete.

### Full Suite Verification (2025-12-29)

**Command:** `./gradlew test`

**Results:**

```
BUILD SUCCESSFUL in 3s
4 actionable tasks: 2 executed, 2 up-to-date
```

Full unit suite (including ATDD tests) passes after the test split.

---

## Notes

- Validation targets: `src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java`
- Restart behavior: `src/main/java/io/github/fabb/wigai/WigAIExtension.java`
- Default port: `src/main/java/io/github/fabb/wigai/common/AppConstants.java`
- Server start notifications: `src/main/java/io/github/fabb/wigai/server/JettyServerManager.java`
