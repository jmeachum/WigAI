# Story 1.2: Localhost Binding Defaults + Preferences Guardrails

Status: ready-for-dev

## Story

As a WigAI user,
I want the MCP server to bind to `localhost` by default with strong preference/input guardrails,
so that WigAI is not accidentally exposed on the network (no-auth MVP) and connection details stay predictable.

## Acceptance Criteria

1. **Given** WigAI is enabled for the first time in Bitwig
   **When** the MCP server starts
   **Then** it binds to `localhost` on the default port `61169` and advertises `http://localhost:61169/mcp` in logs/notification.
2. **Given** the user edits "MCP Host" in Bitwig preferences
   **When** the host value is empty or whitespace
   **Then** it is sanitized to `localhost` and the server remains reachable at a loopback address.
3. **Given** the user attempts to set "MCP Host" to a non-loopback value (e.g., `0.0.0.0`, `192.168.x.x`, a public hostname)
   **When** the setting is applied
   **Then** WigAI refuses for MVP (no-auth) and reverts to `localhost`, logging a clear warning explaining why.
4. **Given** the user changes "MCP Port" to another valid port (1024–65535)
   **When** the setting is applied
   **Then** WigAI performs a graceful restart and the MCP endpoint is reachable at `http://localhost:{new_port}/mcp`.
5. **Given** the configured port cannot be bound (e.g., already in use)
   **When** WigAI tries to start or restart the server
   **Then** it reports a clear, actionable error (suggesting choosing another port) and does not crash Bitwig.

## Tasks / Subtasks

- [ ] Enforce loopback-only host validation in preferences (AC 1-3)
  - [ ] Update `validateHost` to treat empty/whitespace as `localhost` and to reject non-loopback values (only allow `localhost`, `127.0.0.1`, `::1`)
  - [ ] Ensure invalid host inputs are written back to preferences as `localhost` to avoid UI drift
  - [ ] Log a warning explaining the non-loopback refusal for MVP (no-auth) and the fallback to loopback
- [ ] Preserve default binding and URL messaging (AC 1)
  - [ ] Confirm default host/port come from `PreferencesBackedConfigManager` + `AppConstants.DEFAULT_MCP_PORT`
  - [ ] Ensure startup notification/logs use the sanitized loopback host
- [ ] Port change behavior and bind failure UX (AC 4-5)
  - [ ] Ensure port changes trigger graceful restart via `ConfigChangeObserver`
  - [ ] On bind failures, surface a clear log + popup with remediation (choose another port)
- [ ] Tests (CI-safe)
  - [ ] Unit tests for host validation (empty, whitespace, non-loopback, allowed loopback values)
  - [ ] Unit tests for port validation (out-of-range -> default)
  - [ ] Regression test that preference value is corrected when invalid host is entered
## Dev Notes

### Developer Context (Guardrails)
- The MCP server binds via Jetty `ServerConnector` using `ConfigManager.getMcpHost()` and `getMcpPort()`; loopback enforcement belongs in the config layer before Jetty starts. [Source: src/main/java/io/github/fabb/wigai/server/JettyServerManager.java]
- Preferences are the canonical config surface; invalid host inputs must be sanitized and written back so the UI reflects the actual binding. [Source: src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java]
- Host/port changes trigger server restart through `ConfigChangeObserver` in `WigAIExtension`; keep that flow intact. [Source: src/main/java/io/github/fabb/wigai/WigAIExtension.java]
- MVP security posture relies on localhost-only binding; no auth is required. [Source: docs/epics.md]

### Technical Requirements
- Default host is `localhost` and default port is `61169` (AppConstants). [Source: src/main/java/io/github/fabb/wigai/common/AppConstants.java]
- Empty or whitespace `MCP Host` inputs must sanitize to `localhost` and immediately update preferences.
- Non-loopback host inputs must be rejected for MVP and reverted to `localhost` with a clear warning.
- Port changes to 1024–65535 must trigger a graceful restart and result in a reachable `http://localhost:{port}/mcp`.
- Bind failures must not crash Bitwig; log and show a clear, actionable message.

### Architecture Compliance
- Keep config validation inside `PreferencesBackedConfigManager`; do not add new config systems.
- Preserve the existing lifecycle: preferences -> observer -> server restart -> Jetty binds using sanitized host/port.
- Use the existing `Logger` + popup notifications (no new logging frameworks).

### Library / Framework Requirements
- Java 21, Bitwig Extension API v19, Jetty 11.x, MCP Java SDK (per architecture). [Source: docs/architecture.md]
- Avoid heavy frameworks (brownfield continuation constraint). [Source: docs/epics.md]

### File Structure Requirements
- Config validation logic: `src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java`
- Server binding/notifications: `src/main/java/io/github/fabb/wigai/server/JettyServerManager.java`
- Restart orchestration: `src/main/java/io/github/fabb/wigai/WigAIExtension.java`
- Constants: `src/main/java/io/github/fabb/wigai/common/AppConstants.java`

### Project Structure Notes
- Aligns with current config + server boundaries; no new modules required.
- No known structure conflicts for this change.

### Testing Requirements
- Add CI-safe unit tests for host validation and preference correction.
- Add CI-safe unit tests for port validation fallback behavior.
- Prefer lightweight tests that do not require a running Bitwig host.

### Previous Story Intelligence
- Story 1.1 confirmed MCP endpoint path is `/mcp` and default port is `61169`; keep logging consistent with these defaults. [Source: docs/sprint-artifacts/1-1-repeatable-mcp-smoke-test-harness-checklist.md]

### Git Intelligence Summary
- Recent commits focus on smoke harness tests and story docs; no recent changes in config/server code paths.

### Latest Technical Information
- Network access is restricted for this run; no external version research performed.
- Use architecture doc as the current source of truth for versions (Java 21, Jetty 11.0.20, MCP BOM 0.11.0, Bitwig API v19). [Source: docs/architecture.md]

### Project Context Reference
- No `project-context.md` found in repository.

### Story Completion Status
- Update `development_status[1-2-localhost-binding-defaults-preferences-guardrails] = ready-for-dev` in `docs/sprint-artifacts/sprint-status.yaml`.

### References
- Epic + acceptance criteria: `docs/epics.md` (Story 1.2)
- PRD security + local binding constraints: `docs/prd.md`
- Architecture + config patterns: `docs/architecture.md`
- Config validation: `src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java`
- Server binding + notifications: `src/main/java/io/github/fabb/wigai/server/JettyServerManager.java`
- Restart on config change: `src/main/java/io/github/fabb/wigai/WigAIExtension.java`
- Default port constant: `src/main/java/io/github/fabb/wigai/common/AppConstants.java`
- Logger behavior: `src/main/java/io/github/fabb/wigai/common/Logger.java`

## Dev Agent Record

### Context Reference

- N/A

### Agent Model Used

GPT-5 (Codex CLI)

### Debug Log References

- N/A

### Completion Notes List

- Ultimate context engine analysis completed — comprehensive developer guide created.

### File List

- `src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java`
- `src/main/java/io/github/fabb/wigai/server/JettyServerManager.java`
- `src/main/java/io/github/fabb/wigai/WigAIExtension.java`
- `src/main/java/io/github/fabb/wigai/common/AppConstants.java`
- `src/main/java/io/github/fabb/wigai/common/Logger.java`
- `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerTest.java`
