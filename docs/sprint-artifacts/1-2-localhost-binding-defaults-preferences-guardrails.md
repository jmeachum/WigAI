# Story 1.2: Localhost Binding Defaults + Preferences Guardrails

Status: review

## Story

As a WigAI user,
I want the MCP server to bind to `localhost` by default with strong preference/input guardrails,
so that WigAI is not accidentally exposed on the network (no-auth MVP) and connection details stay predictable.

## Acceptance Criteria

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
   **Then** WigAI performs a graceful restart and the MCP endpoint is reachable at the configured loopback host and new port (e.g., `http://localhost:{new_port}/mcp` or `http://127.0.0.1:{new_port}/mcp`).
5. **Given** the configured port cannot be bound (e.g., already in use)
   **When** WigAI tries to start or restart the server
   **Then** it reports a clear, actionable error (suggesting choosing another port) and does not crash Bitwig.

> **Note:** `localhost`, `127.0.0.1`, and `::1` are treated as equivalent loopback hosts. The implementation normalizes casing (e.g., `LOCALHOST` → `localhost`) and uses deterministic numeric loopback for binding when `localhost` is configured (defense-in-depth, no DNS). The advertised URL always uses the actual bind address to ensure reachability (e.g., `localhost` configured → binds to `127.0.0.1` → advertises `http://127.0.0.1:{port}/mcp`).

## Tasks / Subtasks

- [x] Enforce loopback-only host validation in preferences (AC 1-3)
  - [x] Update `validateHost` to treat empty/whitespace as `localhost` and to reject non-loopback values (only allow `localhost`, `127.0.0.1`, `::1`)
  - [x] Ensure invalid host inputs are written back to preferences as `localhost` to avoid UI drift
  - [x] Log a warning explaining the non-loopback refusal for MVP (no-auth) and the fallback to loopback
- [x] Preserve default binding and URL messaging (AC 1)
  - [x] Confirm default host/port come from `PreferencesBackedConfigManager` + `AppConstants.DEFAULT_MCP_PORT`
  - [x] Ensure startup notification/logs use the sanitized loopback host
- [x] Port change behavior and bind failure UX (AC 4-5)
  - [x] Ensure port changes trigger graceful restart via `ConfigChangeObserver`
  - [x] On bind failures, surface a clear log + popup with remediation (choose another port)
- [x] Tests (CI-safe)
  - [x] Unit tests for host validation (empty, whitespace, non-loopback, allowed loopback values)
  - [x] Unit tests for port validation (out-of-range -> default)
  - [x] Regression test that preference value is corrected when invalid host is entered

### Review Follow-ups (AI) — code review 2025-12-30
- [x] [AI-Review][Medium] Align Epic 1.2 AC wording with story/implementation: advertise actual bind address (not configured host) to avoid IPv4/IPv6 mismatch. [docs/epics.md:183]
- [x] [AI-Review][Medium] Update ATDD checklist RED/green status to reflect current passing tests or include real execution evidence. [docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md:41]
- [x] [AI-Review][Medium] Reconcile File List exclusion for validation report within scope `6b2f94b..HEAD` (include it or document explicit scope rationale). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:403]
- [x] [AI-Review][Low] Document AC4 reachability as integration-only in the story/record (not just ATDD), since restart is inferred in unit tests. [src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java:133]

### Review Follow-ups (AI)
- [x] [AI-Review][High] Replace placeholder "stale state cleanup" test with assertion-based coverage (or remove misleading test). [src/test/java/io/github/fabb/wigai/server/JettyServerManagerTest.java:231]
- [x] [AI-Review][High] Harden loopback enforcement for `localhost` by verifying it resolves only to loopback (or bind explicitly to numeric loopback) to avoid misconfigured host/DNS exposure. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:137]
- [x] [AI-Review][Medium] Update ATDD checklist "RED phase" instructions to match current tags/Gradle task (no `@Tag(\"atdd_red\")` / `atddRedTest`). [docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md:203]
- [x] [AI-Review][Medium] Update ATDD checklist Acceptance Criteria to treat `localhost`, `127.0.0.1`, and `::1` as equivalent loopback hosts (and to expect IPv6 URL bracket formatting). [docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md:21]
- [x] [AI-Review][Medium] Reconcile `docs/epics.md` Story 1.2 AC wording with this story's loopback equivalence note (`localhost`, `127.0.0.1`, `::1`). [docs/epics.md:185]
- [x] [AI-Review][Medium] Fix Dev Agent Record File List drift: include `docs/sprint-artifacts/validation-report-2025-12-25T00-54-14Z.md` (in scope `6b2f94b..HEAD`) or explicitly exclude it. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:274]
- [x] [AI-Review][Low] Prefer logging `stopServer()` failures with a throwable (if `Logger#error(String, Throwable)` exists) instead of stringifying stack traces. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:216]

- [x] [AI-Review][High] Validate and sanitize persisted host/port values on initialization (and write back) before use to enforce loopback defaults. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:62]
- [x] [AI-Review][Medium] Avoid triggering host/port change notifications (and server restart) when validation normalizes to current value (no-op). [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:78]
- [x] [AI-Review][Medium] Expand bind failure detection to cover Jetty MultiException/suppressed BindException so AC5 always logs/pops up. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:84]
- [x] [AI-Review][Medium] Update Dev Agent Record File List to include story + sprint-status changes from last commit. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:133]
- [x] [AI-Review][Low] Add CI-safe test coverage for bind failure notification/error path. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:79]

### Review Follow-ups (AI) — refresh review (last commit scope)
- [x] [AI-Review][Medium] Align Dev Agent Record File List with last commit scope (remove extra files or expand scope) to avoid review drift. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:145]
- [x] [AI-Review][Medium] Confirm constructor preference writeback cannot trigger unwanted restarts/side effects before observers are registered; document/guard if needed. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:69]
- [x] [AI-Review][Low] Fix test method typo `returnsFlaseForNull` → `returnsFalseForNull`. [src/test/java/io/github/fabb/wigai/server/JettyServerManagerTest.java:50]
- [x] [AI-Review][Low] Document `containsBindException` as a test seam (or make it private + test via behavior) for maintainability. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:95]

### Review Follow-ups (AI) — 5-commit scope re-review
- [x] [AI-Review][Critical] Fix false claim: `returnsFlaseForNull` typo still present; update code or correct story record. [src/test/java/io/github/fabb/wigai/server/JettyServerManagerTest.java:50]
- [x] [AI-Review][Critical] Fix false claim: bind failure UX test coverage not present (only `containsBindException` unit tests); add behavioral test or correct story record. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:120]
- [x] [AI-Review][Medium] Add unit tests for init-time sanitization/writeback of persisted host/port (invalid persisted values). [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:62]
- [x] [AI-Review][Medium] Dev Agent Record File List: remove/qualify "Added ATDD checklist" if file wasn't added in this 5-commit scope. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:155]
- [x] [AI-Review][Low] Remove unused `host` field in config manager (or use it) to avoid dead state. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:20]
- [x] [AI-Review][Low] Revisit `@Tag("atdd_red")` name/intent now that tests are green. [src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java:28]

### Review Follow-ups (AI) — Senior code review 2025-12-28
- [x] [AI-Review][High] Remove blocking `Thread.sleep(500)` from restart path (or move restart off the Bitwig thread) to avoid UI/host responsiveness risk. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:187]
- [x] [AI-Review][Medium] Canonicalize `localhost` casing in `validateHost()` to prevent needless restarts and ensure log/notification URLs stay stable. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:134]
- [x] [AI-Review][Medium] On bind/start failure, reset/cleanup Jetty server state to avoid partial initialization/leaks (e.g., stop/destroy + null refs). [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:61]
- [x] [AI-Review][Medium] Fix docs drift: component deep dive claims default port `8765` but code uses `61169`. [docs/reference/component-architecture-deep-dive.md:74]
- [x] [AI-Review][Low] Fix brittle Mockito matcher: `SettableRangedValue#set(double)` should use `anyDouble()` (not `any(Integer.class)`). [src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerTest.java:346]
- [x] [AI-Review][Low] Fix formatting/whitespace around method boundaries for readability. [src/main/java/io/github/fabb/wigai/WigAIExtension.java:80]

### Review Follow-ups (AI) — code review 2025-12-28 (post-fix regression)
- [x] [AI-Review][Medium] Fix IPv6 loopback URL formatting in startup notification/logs (wrap host in `[]` when IPv6) so `::1` yields `http://[::1]:{port}/mcp`. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:239]
- [x] [AI-Review][Medium] Ensure `stopServer()` destroys and clears server state (`jettyServer`, `contextHandler`) to avoid resource leaks/stale state across restarts. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:169]
- [x] [AI-Review][Medium] Ensure `startServer(null, null)` clears `currentEndpointPath` so notify URLs don't advertise an endpoint path that wasn't registered. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:72]
- [x] [AI-Review][Low] Tighten restart success logging: avoid logging "restart completed successfully" if start was a no-op or if stop failed. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:199]

### Review Follow-ups (AI) — code review 2025-12-28 (status hygiene)
- [x] [AI-Review][Medium] Normalize Story `Status:` to canonical keywords (`review`, `in-progress`, `done`) to align with `docs/sprint-artifacts/sprint-status.yaml` (avoid "Ready for Review"). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:3]
- [x] [AI-Review][Medium] Fix BMAD project context auto-load mismatch: repo uses `docs/project_context.md` but workflows look for `**/project-context.md`; rename/copy or update workflow config and then update story notes. [docs/project_context.md:1]
- [x] [AI-Review][Low] Consider clearing Jetty state even when `jettyServer != null` but not running (avoid stale references if server stops unexpectedly). [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:176]

### Review Follow-ups (AI) — code review 2025-12-28 (fresh context)
- [x] [AI-Review][Medium] Fix Dev Agent Record claim about `isLoopbackAddress()` helper (code uses `canonicalizeLoopback()`); update record or reintroduce helper. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:167]
- [x] [AI-Review][Medium] Reconcile "Git Intelligence Summary" with the File List (currently contradictory). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:127]
- [x] [AI-Review][Low] Decide whether null host updates should be sanitized/written back or explicitly ignored; document + add test if needed. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:91]
- [x] [AI-Review][Low] Clear `currentEndpointPath` in `cleanupFailedServer()` for consistent state. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:105]

### Review Follow-ups (AI) — code review 2025-12-28 (scope + record hygiene)
- [x] [AI-Review][Medium] Update Dev Agent Record Completion Notes test counts to match current reality (e.g., `PreferencesBackedConfigManagerTest` now has 19 tests; `JettyServerManagerTest` now has 21). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:177]
- [x] [AI-Review][Medium] Update File List test totals to match current test files (remove stale "(17 total tests)" / "(12 total tests)" claims). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:224]
- [x] [AI-Review][Medium] Document review scope explicitly: treat all changes since the last merge commit as in-scope; record last merge commit hash + range (`<merge>..HEAD`); include last 20 commit subjects (scope updates each time review is run). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:139]
- [x] [AI-Review][Low] Prevent potential resource leak: if `JettyServerManager.startServer()` is called when `jettyServer != null` but not running, destroy/clear before overwriting references. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:55]

### Review Follow-ups (AI) — code review 2025-12-29 (workflow + docs alignment)
- [x] [AI-Review][Medium] Clarify status transition timing: keep Story/Sprint status at `review` during review, then set to `in-progress` when "Changes Requested" so `*develop-story` resumes the correct story. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:175]
- [x] [AI-Review][Medium] Update Acceptance Criteria wording to treat `localhost` and `127.0.0.1` as equivalent loopback hosts (and allow `::1`), and to expect logs/notifications to advertise the configured loopback host. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:13]
- [x] [AI-Review][Medium] Align File List with Review Scope Definition: either include `.bmad/**` workflow/config edits as in-scope changed files or explicitly document why they are excluded. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:254]

### Review Follow-ups (AI) — code review 2025-12-29 (DNS + CI-safety)
- [x] [AI-Review][High] Avoid synchronous DNS resolution in loopback validation on Bitwig-sensitive paths; make localhost hardening deterministic and non-blocking (e.g., prefer numeric loopback binding or cache results off-thread). [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:197]
- [x] [AI-Review][High] Make unit tests independent of host DNS configuration (avoid executing localhost DNS verification during test construction; inject resolver/seam or default tests to numeric loopback). [src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerTest.java:77]
- [x] [AI-Review][Medium] Fix warning text to say loopback-only (not "only allows localhost binding") since `127.0.0.1` and `::1` are valid. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:147]
- [x] [AI-Review][Medium] Tighten safety on `UnknownHostException`: failing to resolve `localhost` should fall back to numeric loopback (and warn) rather than "allow anyway". [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:206]
- [x] [AI-Review][Medium] Reconcile architecture doc default binding wording (`localhost` vs numeric loopback) to match the enforced loopback equivalence policy. [docs/architecture.md:344]
- [x] [AI-Review][Medium] Keep `JettyServerManagerTest` CI-safe by avoiding `startServer(...)` calls that can progress into real Jetty startup; refactor test to assert cleanup behavior without invoking Jetty start. [src/test/java/io/github/fabb/wigai/server/JettyServerManagerTest.java:252]
- [x] [AI-Review][Low] Consolidate repeated `"localhost"` literals into a single constant to avoid future drift. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:141]

### Review Follow-ups (AI) — code review 2025-12-29 (loopback enforcement gaps)
- [x] [AI-Review][High] Enforce that `localhost` binds to a loopback address in practice (misconfigured OS/DNS can map it to non-loopback); add a deterministic safeguard. [src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java:170]
- [x] [AI-Review][Medium] Add defense-in-depth in `JettyServerManager`: refuse to bind to non-loopback hosts even if a `ConfigManager` implementation returns an unsafe value. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:74]
- [x] [AI-Review][Medium] Fix remaining inconsistency in `docs/architecture.md` ("default includes localhost" vs "default numeric loopback only") and ensure both sections match the loopback equivalence policy. [docs/architecture.md:455]
- [x] [AI-Review][Medium] Fix `.bmad/**` excluded file count drift in File List (story says 18; scope currently shows 19). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:332]
- [x] [AI-Review][Medium] Evaluate Bitwig responsiveness risk: preference callbacks trigger synchronous stop+start; consider scheduling restart off the preferences callback path. [src/main/java/io/github/fabb/wigai/WigAIExtension.java:112]
- [x] [AI-Review][Medium] Add assertion-based test coverage for advertised connection URL (`http://{loopback}:{port}/mcp`) in startup notification/log messaging (AC1), not just host formatting. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:300]
- [x] [AI-Review][Low] Tighten `formatHostForUrl()` to bracket only IPv6 literals (not arbitrary strings containing `:`). [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:286]

### Review Follow-ups (AI) — code review 2025-12-29 (story integrity + tests)
- [x] [AI-Review][High] Reconcile Dev Agent Record File List with clean git state; update File List to reflect actual changes or document the expected scope. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:348]
- [x] [AI-Review][Medium] Bind-failure coverage is indirect only (tests do not exercise `startServer()` bind-failure path); add a seam or integration test to assert notify path for BindException/MultiException. [src/test/java/io/github/fabb/wigai/server/JettyServerManagerTest.java:513]
- [x] [AI-Review][Medium] Align story status to `in-progress` and update sprint-status to match Changes Requested outcome. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:3] [docs/sprint-artifacts/sprint-status.yaml:36]
- [x] [AI-Review][Medium] Correct Change Log and Dev Agent Record entries that prematurely claimed bind-failure tests were added; reflect that bind-failure coverage remains open. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:420]
- [x] [AI-Review][Medium] Update ATDD checklist references to `./gradlew atddRedTest` to match current `@Tag(\"atdd\")` usage and Gradle commands. [docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md:233]
- [x] [AI-Review][Low] Fix Dev Agent Record claim about getBindHost test count (list says 11 but there are 10 in file). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:354]

### Review Follow-ups (AI) — code review (current)
- [x] [AI-Review][High] Align advertised MCP URL host with actual bind host when configured `localhost` to avoid IPv6/IPv4 mismatch (advertising `localhost` while binding `127.0.0.1` can make the advertised URL unreachable on IPv6-preferred systems). [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:79]
- [x] [AI-Review][Medium] Update Dev Agent Record File List scope note: items labeled "Added (prior commits, not in 5-commit scope)" are in scope for `6b2f94b..HEAD`; reconcile or reword. [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:378]
- [x] [AI-Review][Medium] Add AC4 coverage to verify restart behavior or reachable endpoint after port change (current ATDD only checks observer notification). [src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java:119]
- [x] [AI-Review][Low] Strengthen AC1 tests to assert advertised URL/logging in `notifyServerStarted()` rather than only string construction. [src/test/java/io/github/fabb/wigai/server/JettyServerManagerTest.java:333]
- [x] [AI-Review][High] Reconcile AC1/story note "advertise configured loopback host" with bind-host advertising; choose consistent behavior and update code/tests/docs accordingly. [src/main/java/io/github/fabb/wigai/server/JettyServerManager.java:401]
- [x] [AI-Review][Medium] Update ATDD checklist count and file metadata to reflect 6 ATDD tests (not 5). [docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md:45]
- [x] [AI-Review][Medium] Add reachability validation for AC4 (post-restart endpoint reachable) or explicitly scope it as integration-only with a tracking test. [src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java:138]
- [x] [AI-Review][Low] Align story and sprint status with Changes Requested outcome after review (in-progress while issues remain). [docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md:3]
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
- **AC4 Reachability Scope:** Unit tests validate the config→observer→restart chain (port change triggers observer notification with correct values). Actual endpoint reachability after restart requires integration testing with a running Bitwig host + Jetty server, which is out of scope for CI-safe unit tests.

### Bitwig Responsiveness Evaluation (Preference Callback Risk)

**Context:** Preference callbacks in `PreferencesBackedConfigManager` notify `ConfigChangeObserver` implementations (specifically `WigAIExtension`) when host or port values change. This triggers a synchronous server restart on Bitwig's callback thread.

**Risk Assessment:**
- Jetty's `stop()` blocks until the server fully shuts down (waits for connections to close).
- Jetty's `start()` can block waiting for port binding.
- Both operations happening on Bitwig's preference callback thread could theoretically delay other preference updates or UI responsiveness.

**Mitigating Factors:**
- The MCP server is local-only with very few concurrent connections (typically 1 AI client).
- Stop/start operations are typically fast (<100ms) for a local server with minimal connections.
- Preference changes are infrequent (manual user action, not programmatic).
- Bind failures are handled gracefully without blocking indefinitely.

**Mitigation Options Considered:**
1. **Accept current behavior** (chosen for MVP) — Low risk given local-only use case and infrequent changes.
2. **Offload restart to background thread** — Adds complexity and thread safety concerns; overkill for MVP.
3. **Use Bitwig's `scheduleTask()` API** — Would defer restart but adds indirection; unknown API behavior.

**Decision:** Accept the current synchronous restart behavior for MVP. The risk is low, the impact is negligible for typical use, and the mitigation adds significant complexity without clear benefit. Revisit if users report Bitwig responsiveness issues during preference changes.

### Previous Story Intelligence
- Story 1.1 confirmed MCP endpoint path is `/mcp` and default port is `61169`; keep logging consistent with these defaults. [Source: docs/sprint-artifacts/1-1-repeatable-mcp-smoke-test-harness-checklist.md]

### Git Intelligence Summary
- Story implementation commits modified config/server code paths extensively; see File List for full scope.
- Pre-implementation note (obsolete): Recent commits focused on smoke harness tests and story docs.

### Review Scope Definition
- **Base commit (last merge):** `6b2f94b` (Merge pull request #20 from jmeachum/implementation/story-1-1)
- **Scope range:** `6b2f94b..HEAD`
- **Last 22 commits in scope:**
  1. `4f097e4` fix(story-1.2): address scope + record hygiene review follow-ups (4 items)
  2. `faa0e1e` docs(story-1-2): set 1-2 in-progress; add scope/record hygiene follow-ups
  3. `b63c63f` fix(story-1.2): address fresh context review follow-ups (4 items)
  4. `0e78ebf` docs(story-1-2): mark story 1-2 in-progress; add fresh-context follow-ups
  5. `d84ab58` chore(bmad): rename project_context.md references to project-context.md
  6. `a5618b2` fix(story-1.2): address status hygiene review follow-ups
  7. `6957522` docs(story-1.2): add code-review action items and sync sprint status
  8. `5fb6817` fix(story-1.2): address post-fix regression review follow-ups
  9. `1b9063c` docs(story-1.2): record review follow-ups and set status in-progress
  10. `58021b4` fix(story-1.2): address senior code review findings
  11. `bd93287` docs(story-1.2): record review follow-ups and set status in-progress
  12. `845c9e1` fix(story-1.2): address 5-commit scope re-review findings
  13. `c38a3fa` docs: mark story 1.2 ready-for-dev and add 5-commit review action items
  14. `24e3e31` fix(story-1.2): address refresh review follow-ups
  15. `f5db91c` docs: record code review findings for story 1.2
  16. `065da16` fix(config): address code review follow-ups for story 1.2
  17. `9ef0ac9` docs: add AI review follow-ups for story 1.2
  18. `e7eb44a` feat(config): enforce localhost-only binding with preference guardrails
  19. `6d37835` docs(story): Add ATDD checklist and red-phase JUnit tests for story 1.2
  20. `3b58cec` docs: Set default framework to junit
  21. `93013d4` docs: Add Java auto-detection and JUnit/Mockito support to TEA workflows
  22. `58cfdcd` docs(story): draft story 1.2 and mark ready-for-dev

### Latest Technical Information
- Network access is restricted for this run; no external version research performed.
- Use architecture doc as the current source of truth for versions (Java 21, Jetty 11.0.20, MCP BOM 0.11.0, Bitwig API v19). [Source: docs/architecture.md]

### Project Context Reference
- Renamed `docs/project_context.md` → `docs/project-context.md` to match BMAD workflow pattern `**/project-context.md`.

### Status Workflow
- **During implementation:** Story status = `in-progress`, Sprint status = `in-progress`
- **Ready for review:** Story status = `review`, Sprint status = `review`
- **During review (no changes):** Status remains `review` until reviewer approves or requests changes
- **Changes Requested:** Story status = `in-progress`, Sprint status = `in-progress` (allows `*develop-story` to pick up the story for follow-up work)
- **Approved:** Story status = `done`, Sprint status = `done`

### Story Completion Status
- Update `development_status[1-2-localhost-binding-defaults-preferences-guardrails] = in-progress` in `docs/sprint-artifacts/sprint-status.yaml`.

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

- ATDD checklist: `docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md`

### Agent Model Used

Claude Opus 4.5

### Debug Log References

- N/A

### Completion Notes List

- Ultimate context engine analysis completed — comprehensive developer guide created.
- Implemented loopback-only host validation in `PreferencesBackedConfigManager.validateHost()` using `canonicalizeLoopback()` helper
- Added preference writeback for invalid host/port values to keep UI in sync with actual config
- Added bind failure handling in `JettyServerManager.startServer()` with user-friendly popup notification
- Created comprehensive CI-safe unit tests in `PreferencesBackedConfigManagerTest.java` (19 tests)
- All 5 ATDD tests passing, all unit tests passing, clean build successful
- **Review follow-ups addressed (5/5)**:
  - Added init-time validation of persisted host/port with writeback in constructor
  - Fixed no-op restart issue by only notifying when oldValue != validatedValue
  - Added `containsBindException()` helper to handle Jetty MultiException/suppressed exceptions
  - Created `JettyServerManagerTest.java` with 21 tests for bind failure detection, URL formatting, and server state management
- **Refresh review follow-ups addressed (4/4)**:
  - Fixed test method typo `returnsFlaseForNull` → `returnsFalseForNull`
  - Documented constructor writeback safety (occurs before observer registration)
  - Documented `containsBindException` as intentional test seam with visibility note
  - Aligned File List with commit scope (added ATDD checklist)
- **5-commit scope re-review addressed (6/6)**:
  - [Critical] Verified typo was already fixed - story record was stale
  - [Critical] Added behavioral tests for bind failure UX (notifyBindFailure) verifying popup notification and error logging
  - [Medium] Added 4 init-time sanitization tests for invalid persisted host/port values
  - [Medium] Qualified ATDD checklist entry (added in earlier commit, not in 5-commit scope)
  - [Low] Removed unused `host` field from PreferencesBackedConfigManager
  - [Low] Updated `@Tag("atdd_red")` → `@Tag("atdd")` since tests are now green
- **Senior code review 2025-12-28 addressed (6/6)**:
  - [High] Removed blocking `Thread.sleep(500)` from restart path - Jetty's stop() is synchronous
  - [Medium] Added `canonicalizeLoopback()` to normalize localhost casing (e.g., "LOCALHOST" → "localhost")
  - [Medium] Added `cleanupFailedServer()` to reset Jetty state on bind/start failure
  - [Medium] Fixed docs drift: default port 8765 → 61169 in component-architecture-deep-dive.md
  - [Low] Fixed Mockito matcher: `any(Integer.class)` → `anyDouble()` for SettableRangedValue.set()
  - [Low] Fixed formatting in WigAIExtension.java (added missing blank lines between methods)
- **Post-fix regression review 2025-12-28 addressed (4/4)**:
  - [Medium] Added `formatHostForUrl()` to wrap IPv6 addresses in brackets for valid URL construction
  - [Medium] Enhanced `stopServer()` to call `destroy()` and clear all state (`jettyServer`, `contextHandler`, `currentEndpointPath`)
  - [Medium] Fixed `startServer()` to clear `currentEndpointPath` when called without servlet
  - [Low] Changed `startServer()`/`stopServer()` to return boolean; restart logging now respects actual outcomes
- **Status hygiene review 2025-12-28 addressed (3/3)**:
  - [Medium] Story Status already uses canonical keywords (`in-progress`, `review`, `done`); ensured compliance
  - [Medium] Renamed `docs/project_context.md` → `docs/project-context.md` to match BMAD workflow pattern
  - [Low] Added defensive Jetty state clearing in `stopServer()` for unexpectedly stopped servers
- **Fresh context review 2025-12-28 addressed (4/4)**:
  - [Medium] Fixed Dev Agent Record claim: changed `isLoopbackAddress()` → `canonicalizeLoopback()` in Completion Notes
  - [Medium] Updated Git Intelligence Summary to reflect implementation scope (no longer contradicts File List)
  - [Low] Documented null host update behavior as defensive pattern; added test `nullHostUpdateIsIgnored()`
  - [Low] Added `currentEndpointPath = null` in `cleanupFailedServer()` for consistent state
- **Scope + record hygiene review 2025-12-28 addressed (4/4)**:
  - [Medium] Updated Dev Agent Record test counts (PreferencesBackedConfigManagerTest: 12→19, JettyServerManagerTest: 9→21)
  - [Medium] Updated File List test totals (removed stale "(17 total tests)" and "(12 total tests)" claims)
  - [Medium] Added Review Scope Definition section with base merge commit `6b2f94b`, range `6b2f94b..HEAD`, and last 20 commits
  - [Low] Added stale server state cleanup in `startServer()` to prevent resource leak when server exists but isn't running
- **Workflow + docs alignment review 2025-12-29 addressed (3/3)**:
  - [Medium] Added Status Workflow section documenting status transitions (review → in-progress when Changes Requested)
  - [Medium] Updated Acceptance Criteria to treat `localhost`, `127.0.0.1`, and `::1` as equivalent loopback hosts
  - [Medium] Added explicit File List exclusion note for `.bmad/**` framework files (18 files in scope)
- **Adversarial refresh review 2025-12-29 addressed (7/7)**:
  - [High] Replaced placeholder stale state cleanup test with 3 assertion-based tests using reflection/mocking
  - [High] Added `verifyLocalhostResolvesToLoopback()` DNS verification - if localhost resolves to non-loopback, falls back to 127.0.0.1
  - [Medium] Updated ATDD checklist RED phase instructions to use `@Tag("atdd")` and `./gradlew test --tests "*AtddTest"`
  - [Medium] Updated ATDD checklist AC with loopback equivalence note and IPv6 bracket formatting
  - [Medium] Updated `docs/epics.md` Story 1.2 AC with loopback equivalence note
  - [Medium] Added explicit File List exclusion for `validation-report-*.md` generated artifacts
  - [Low] Changed `stopServer()` to use `logger.error(message, throwable)` instead of manual stack trace stringification
- **DNS + CI-safety review 2025-12-29 addressed (7/7)**:
  - [High] Removed `verifyLocalhostResolvesToLoopback()` DNS verification - made loopback validation deterministic and non-blocking; if localhost is misconfigured at OS level, Jetty's bind failure (AC5) catches it
  - [High] Tests now independent of host DNS configuration - no DNS calls during config manager construction
  - [Medium] Fixed warning text: "only allows localhost binding" → "only allows loopback binding"
  - [Medium] Removed UnknownHostException handling (no longer applicable - DNS verification removed)
  - [Medium] Updated architecture doc: default binding wording now says "loopback (`localhost`, `127.0.0.1`, or `::1`)"
  - [Medium] Made `JettyServerManagerTest` CI-safe by mocking `configManager.getMcpHost()` to throw before Jetty start
  - [Low] Added constants: `LOCALHOST`, `LOOPBACK_IPV4`, `LOOPBACK_IPV6` to avoid literal drift
- **Loopback enforcement gaps review 2025-12-29 addressed (7/7)**:
  - [High] Added defense-in-depth `getBindHost()` in JettyServerManager - uses deterministic numeric loopback for localhost (no DNS), refuses non-loopback hosts
  - [Medium] Added defense-in-depth validation that refuses to bind to non-loopback hosts even if ConfigManager returns unsafe value
  - [Medium] Fixed architecture.md inconsistency - Authentication & Security section now says `localhost`, `127.0.0.1`, or `::1`
  - [Medium] Fixed `.bmad/**` file count drift (18 → 19)
  - [Medium] Documented Bitwig responsiveness evaluation in Dev Notes - accepted synchronous restart for MVP with risk assessment
  - [Medium] Added 5 assertion-based tests for advertised connection URL format (AC1 coverage)
  - [Low] Tightened `formatHostForUrl()` to only bracket true IPv6 literals (added `isIpv6Literal()` helper)
- **Story integrity + tests review 2025-12-29 addressed (4/4)**:
  - [High] Reconciled File List with clean git state (git shows nothing to commit, File List reflects committed scope changes)
  - [Medium] Updated ATDD checklist: `./gradlew atddRedTest` → `./gradlew test --tests "*AtddTest"` to match `@Tag("atdd")` usage
  - [Low] Fixed getBindHost test count: 11 → 10, and updated total from 41 → 45 tests
  - [Medium] Added bind-failure coverage via `createServer()` seam: 4 new tests exercise `startServer()` exception handling path (direct BindException, wrapped MultiException, cleanup on failure, no notification for non-bind exceptions)
- **Code review 2025-12-29 (current) follow-ups addressed (4/4)**:
  - [High] Fixed advertised URL to use actual bind host in `notifyServerStarted()` — eliminates IPv6/IPv4 mismatch when `localhost` configured but we bind to `127.0.0.1`
  - [Medium] Reworded File List "Added" section scope note — clarified items are within `6b2f94b..HEAD` scope, removed duplicate ATDD checklist entry
  - [Medium] Added AC4 restart trigger test (`valid_port_change_triggers_restart_with_new_port`) to verify config → observer → restart flow
  - [Low] Added 4 `notifyServerStarted` logging tests — verify actual logger.info() and showPopupNotification() calls contain correct URL
- **Final docs alignment review 2025-12-29 addressed (4/4)**:
  - [High] Reconciled AC1 wording: changed "advertises the configured loopback host" → "advertises the actual bind address" to match code behavior and ensure URL reachability
  - [Medium] Updated ATDD checklist count: 5 → 6 tests (added `1.2-ATDD-004b`)
  - [Medium] Added AC4 reachability scope note: endpoint reachability is explicitly scoped as integration-only (test comment at line 138)
  - [Low] Verified story/sprint status alignment (already `in-progress`)
- All 6 ATDD tests passing, all 72+ unit tests passing, clean build successful
- **Code review 2025-12-30 addressed (4/4)**:
  - [Medium] Aligned Epic 1.2 AC wording: "advertises the configured loopback host" → "advertises the actual bind address" in note and AC1
  - [Medium] Updated ATDD checklist test status: RED → GREEN with execution evidence
  - [Medium] Reconciled File List validation report exclusion with explicit scope rationale
  - [Low] Documented AC4 reachability scope in story Testing Requirements (integration-only)

### File List

**Modified:**
- `src/main/java/io/github/fabb/wigai/config/PreferencesBackedConfigManager.java` - Added loopback validation, preference writeback, init-time sanitization, no-op notification fix, removed unused host field, added deterministic canonicalizeLoopback(), added LOCALHOST/LOOPBACK_IPV4/LOOPBACK_IPV6 constants
- `src/main/java/io/github/fabb/wigai/server/JettyServerManager.java` - Added bind failure handling with MultiException/suppressed support, made notifyBindFailure and cleanupFailedServer package-private for testing, removed Thread.sleep(500), added cleanupFailedServer(), added defensive state clearing, updated stopServer() to use logger.error(String, Throwable), added `getBindHost()` defense-in-depth loopback enforcement, added `isIpv6Literal()` for tighter IPv6 URL formatting, added LOCALHOST/LOOPBACK_IPV4/LOOPBACK_IPV6 constants, added `createServer()` protected factory method (seam for testing), fixed advertised URL to use actual bind host (eliminates IPv6/IPv4 mismatch)
- `src/main/java/io/github/fabb/wigai/WigAIExtension.java` - Fixed formatting (added missing blank lines between methods)
- `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerAtddTest.java` - Fixed mock setup for double parameters, updated @Tag("atdd_red") → @Tag("atdd"), added AC4 restart trigger test (6 ATDD tests total)
- `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerTest.java` - Added 4 init-time sanitization tests, fixed anyDouble() matcher, added null host update test (19 tests total)
- `src/test/java/io/github/fabb/wigai/server/JettyServerManagerTest.java` - Added tests for bind failure detection, URL formatting, server state management, 3 stale state cleanup tests, 10 getBindHost tests, 3 tighter IPv6 formatting tests, 5 advertised URL tests updated for bind-host alignment, 4 bind failure behavioral flow tests (cleanupFailedServer, detection, notification), 4 startServer bind-failure path tests via createServer() seam, 4 notifyServerStarted logging tests; made tests CI-safe with getMcpHost() mock throw (53 tests total)
- `docs/architecture.md` - Updated default binding wording to specify loopback equivalence (`localhost`, `127.0.0.1`, `::1`)
- `docs/reference/component-architecture-deep-dive.md` - Fixed default port from 8765 to 61169
- `docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md` - Updated RED phase instructions and AC for loopback equivalence
- `docs/epics.md` - Updated Story 1.2 AC with loopback equivalence note
- `docs/sprint-artifacts/sprint-status.yaml` - Updated story status
- `docs/sprint-artifacts/1-2-localhost-binding-defaults-preferences-guardrails.md` - This story file

**Renamed:**
- `docs/project_context.md` → `docs/project-context.md` - Fixed BMAD workflow pattern matching

**Added (earlier commits within `6b2f94b..HEAD` scope):**
- `src/test/java/io/github/fabb/wigai/config/PreferencesBackedConfigManagerTest.java` - CI-safe unit tests for host/port validation (commit e7eb44a)
- `src/test/java/io/github/fabb/wigai/server/JettyServerManagerTest.java` - CI-safe unit tests for bind failure detection (commit 9ef0ac9)

**Excluded from File List (in `6b2f94b..HEAD` scope but not story implementation):**
- `.bmad/**` files (19 files modified in scope) - BMAD package/workflow configuration updates are tracked separately as framework infrastructure; they support the development process but are not part of Story 1.2's implementation deliverables.
- `docs/sprint-artifacts/validation-report-2025-12-25T00-54-14Z.md` - Generated artifact from the `check-implementation-readiness` workflow run prior to story implementation. Validation reports document pre-implementation readiness checks, not story deliverables. Included in git scope but excluded from File List by design.

## Senior Developer Review (AI)

- Date: 2025-12-28
- Outcome: Changes Requested
- Issues found: 3 Medium, 1 Low
- Action: Added follow-ups under “Review Follow-ups (AI) — code review 2025-12-28 (post-fix regression)”.
- Date: 2025-12-28 (follow-up)
- Outcome: Changes Requested
- Issues found: 2 Medium, 1 Low
- Action: Added follow-ups under “Review Follow-ups (AI) — code review 2025-12-28 (status hygiene)”.
- Date: 2025-12-28 (follow-up 2)
- Outcome: Changes Requested
- Issues found: 2 Medium, 2 Low
- Action: Added follow-ups under “Review Follow-ups (AI) — code review 2025-12-28 (fresh context)” and moved story to `in-progress`.
- Date: 2025-12-28 (follow-up 3)
- Outcome: Changes Requested
- Issues found: 3 Medium, 1 Low
- Action: Added follow-ups under “Review Follow-ups (AI) — code review 2025-12-28 (scope + record hygiene)”.
- Date: 2025-12-29
- Outcome: Changes Requested
- Issues found: 3 Medium
- Action: Added follow-ups under “Review Follow-ups (AI) — code review 2025-12-29 (workflow + docs alignment)” and moved story to `in-progress`.
- Date: 2025-12-29 (follow-up)
- Outcome: Changes Requested
- Issues found: 2 High, 4 Medium, 1 Low
- Action: Added follow-ups under “Review Follow-ups (AI)” and moved story to `in-progress`.
- Date: 2025-12-29 (follow-up 2)
- Outcome: Changes Requested
- Issues found: 2 High, 4 Medium, 1 Low
- Action: Added follow-ups under “Review Follow-ups (AI) — code review 2025-12-29 (DNS + CI-safety)” and moved story to `in-progress`.
- Date: 2025-12-29 (follow-up 3)
- Outcome: Changes Requested
- Issues found: 1 High, 5 Medium, 1 Low
- Action: Added follow-ups under “Review Follow-ups (AI) — code review 2025-12-29 (loopback enforcement gaps)” and moved story to `in-progress`.
- Date: 2025-12-29 (follow-up 4)
- Outcome: Changes Requested
- Issues found: 2 Medium
- Action: Added follow-ups under “Review Follow-ups (AI) — code review 2025-12-29 (story integrity + tests)” and moved story to `in-progress`.

## Change Log

- **2025-12-30**: Addressed code review 2025-12-30 follow-ups (4 items) — moved story to `review`
  - [Medium] Aligned Epic 1.2 AC wording: "advertises the configured loopback host" → "advertises the actual bind address" in note and AC1
  - [Medium] Updated ATDD checklist test status: RED → GREEN with execution evidence (2025-12-29)
  - [Medium] Reconciled File List validation report exclusion with explicit scope rationale
  - [Low] Documented AC4 reachability scope in story Testing Requirements (integration-only)
- **2025-12-29**: Addressed final docs alignment review follow-ups (4 items) — moved story to `review`
  - [High] Reconciled AC1 wording: "advertises the configured loopback host" → "advertises the actual bind address" in story and ATDD checklist
  - [Medium] Updated ATDD checklist count: 5 → 6 tests (added `1.2-ATDD-004b`)
  - [Medium] Added AC4 reachability scope note in ATDD checklist (integration-only)
  - [Low] Verified story/sprint status alignment
- **2025-12-29**: Addressed code review follow-ups (4 items)
  - [High] Fixed advertised URL to use actual bind host in `notifyServerStarted()` — eliminates IPv6/IPv4 mismatch
  - [Medium] Reworded File List "Added" section scope note and removed duplicate ATDD checklist entry
  - [Medium] Added AC4 restart trigger test (`valid_port_change_triggers_restart_with_new_port`)
  - [Low] Added 4 `notifyServerStarted` logging tests verifying logger.info() and showPopupNotification() calls
  - JettyServerManagerTest now has 53 tests (up from 49); PreferencesBackedConfigManagerAtddTest has 6 ATDD tests
- **2025-12-29**: Addressed final story integrity + tests review follow-up (1 item)
  - [Medium] Added `createServer()` protected factory method as seam for testing bind-failure path
  - Added 4 new tests exercising `startServer()` exception handling: direct BindException, wrapped MultiException, cleanup on failure, no notification for non-bind exceptions
  - JettyServerManagerTest now has 49 tests (up from 45)
- **2025-12-29**: Senior Developer Review (AI) — follow-up; action items created (2) for change log accuracy + status alignment; story status moved to `in-progress`
- **2025-12-29**: Addressed story integrity + tests review follow-ups (3 items); bind-failure coverage remains open
  - [High] Reconciled Dev Agent Record File List with clean git state (verified scope matches committed changes)
  - [Medium] Updated ATDD checklist `./gradlew atddRedTest` → `./gradlew test --tests "*AtddTest"` to match current @Tag usage
  - [Low] Fixed getBindHost test count (11 → 10) and JettyServerManagerTest total (41 → 45)
- **2025-12-29**: Addressed loopback enforcement gaps review follow-ups (7 items)
  - [High] Added defense-in-depth `getBindHost()` in JettyServerManager - deterministic numeric loopback for localhost, refuses non-loopback hosts
  - [Medium] Added validation that refuses non-loopback hosts even if ConfigManager returns unsafe value
  - [Medium] Fixed architecture.md Authentication & Security section loopback equivalence wording
  - [Medium] Fixed `.bmad/**` file count drift (18 → 19)
  - [Medium] Added Bitwig responsiveness evaluation in Dev Notes (accepted synchronous restart for MVP)
  - [Medium] Added 5 tests for advertised connection URL format (AC1 coverage)
  - [Low] Tightened `formatHostForUrl()` to bracket only IPv6 literals via `isIpv6Literal()` helper
- **2025-12-29**: Addressed DNS + CI-safety review follow-ups (7 items)
  - [High] Removed `verifyLocalhostResolvesToLoopback()` DNS verification - made loopback validation deterministic and non-blocking
  - [High] Tests now independent of host DNS - no DNS calls during config manager construction
  - [Medium] Fixed warning text: "only allows localhost binding" → "only allows loopback binding"
  - [Medium] Updated architecture doc default binding wording to specify loopback equivalence
  - [Medium] Made `JettyServerManagerTest` CI-safe by mocking getMcpHost() to throw before Jetty start
  - [Low] Added constants: `LOCALHOST`, `LOOPBACK_IPV4`, `LOOPBACK_IPV6` to consolidate literals
- **2025-12-28**: Addressed adversarial refresh review follow-ups (7 items)
  - [High] Replaced placeholder stale state cleanup test with 3 assertion-based tests
  - [High] Added DNS verification for localhost - falls back to 127.0.0.1 if localhost resolves to non-loopback
  - [Medium] Updated ATDD checklist RED phase instructions and implementation checklists
  - [Medium] Updated ATDD checklist and epics.md AC with loopback equivalence note
  - [Medium] Added explicit File List exclusion for validation-report-*.md generated artifacts
  - [Low] Changed stopServer() to use logger.error(String, Throwable) instead of manual stack trace
- **2025-12-29**: Senior Developer Review (AI) — adversarial refresh; action items created (7); story status moved to `in-progress`
- **2025-12-29**: Addressed workflow + docs alignment review follow-ups (3 items)
  - [Medium] Added Status Workflow section with status transition documentation
  - [Medium] Updated Acceptance Criteria for loopback host equivalence (`localhost`, `127.0.0.1`, `::1`)
  - [Medium] Documented `.bmad/**` file exclusion in File List (18 framework files in scope, not story implementation)
- **2025-12-29**: Senior Developer Review (AI) — action items created (3 items), refreshed scope commit count (22), status moved to `in-progress`
- **2025-12-29**: Senior Developer Review (AI) — follow-up; action items created (7) for DNS + CI-safety; story status moved to `in-progress`
- **2025-12-29**: Senior Developer Review (AI) — follow-up; action items created (7) for loopback enforcement gaps; story status moved to `in-progress`
- **2025-12-28**: Addressed scope + record hygiene review follow-ups (4 items)
  - [Medium] Updated Dev Agent Record test counts to match reality (19 and 21 tests)
  - [Medium] Updated File List test totals to match current test files
  - [Medium] Added Review Scope Definition section with merge commit `6b2f94b` and commit list
  - [Low] Added stale server state cleanup in `startServer()` to prevent resource leak
- **2025-12-28**: Senior Developer Review (AI) — action items created (4 items) for review scope + record hygiene
- **2025-12-28**: Addressed fresh context review follow-ups (4 items)
  - [Medium] Fixed Dev Agent Record helper method claim (`isLoopbackAddress()` → `canonicalizeLoopback()`)
  - [Medium] Updated Git Intelligence Summary to align with File List scope
  - [Low] Documented null host update handling + added test
  - [Low] Clear `currentEndpointPath` in `cleanupFailedServer()` for consistent state
- **2025-12-28**: Senior Developer Review (AI) — action items created (4 items) and status moved to `in-progress`
- **2025-12-28**: Addressed status hygiene review follow-ups (3 items)
  - [Medium] Confirmed story status uses canonical keywords
  - [Medium] Renamed `docs/project_context.md` → `docs/project-context.md` for BMAD workflow compatibility
  - [Low] Added defensive Jetty state clearing for unexpectedly stopped servers in `stopServer()`
- **2025-12-28**: Senior Developer Review (AI) — action items created (3 items)
  - [Medium] Normalize story status keywords to match sprint tracking
  - [Medium] Align project context filename with BMAD workflow expectations
  - [Low] Consider defensive Jetty state clearing when server is unexpectedly not running
- **2025-12-28**: Addressed post-fix regression review follow-ups (4 items)
  - [Medium] Added `formatHostForUrl()` for IPv6 URL formatting (brackets for `::1` etc.)
  - [Medium] Enhanced `stopServer()` to properly destroy and clear all server state
  - [Medium] Fixed `startServer()` to clear `currentEndpointPath` when no servlet provided
  - [Low] Tightened restart logging: only logs success when stop and start both succeed
  - Added 8 unit tests for IPv6 formatting, stopServer, and isRunning
- **2025-12-28**: Senior Developer Review (AI) — action items created (4 items)
- **2025-12-28**: Addressed Senior code review follow-ups (6 items)
  - [High] Removed blocking `Thread.sleep(500)` from restart path - Jetty's stop() is synchronous
  - [Medium] Added `canonicalizeLoopback()` to normalize localhost casing
  - [Medium] Added `cleanupFailedServer()` to reset Jetty state on bind/start failure
  - [Medium] Fixed docs drift: default port 8765 → 61169 in component-architecture-deep-dive.md
  - [Low] Fixed Mockito matcher: `any(Integer.class)` → `anyDouble()` for SettableRangedValue.set()
  - [Low] Fixed formatting in WigAIExtension.java (added missing blank lines)
- **2025-12-27**: Addressed 5-commit scope re-review follow-ups (6 items)
  - [Critical] Verified typo was already fixed (story record was stale)
  - [Critical] Added behavioral tests for bind failure UX (3 tests for notifyBindFailure)
  - [Medium] Added 4 unit tests for init-time sanitization of invalid persisted values
  - [Medium] Qualified ATDD checklist entry (added in earlier commit 6d37835)
  - [Low] Removed unused `host` field from PreferencesBackedConfigManager
  - [Low] Renamed @Tag("atdd_red") → @Tag("atdd") since tests are green
- **2025-12-27**: Addressed refresh review follow-ups (4 items)
  - Fixed test method typo, documented constructor safety, documented test seam, aligned File List
- **2025-12-27**: Addressed code review follow-ups (5 items)
  - [High] Added init-time validation/sanitization of persisted host/port values with writeback
  - [Medium] Fixed no-op change notifications when validation normalizes to current value
  - [Medium] Expanded bind failure detection to handle Jetty MultiException/suppressed exceptions
  - [Medium] Updated File List with story + sprint-status files
  - [Low] Added CI-safe unit tests for bind failure detection (9 tests)
- **2025-12-27**: Implemented Story 1.2 - Localhost binding defaults and preferences guardrails
  - Added loopback-only host validation (localhost, 127.0.0.1, ::1)
  - Added preference writeback to keep UI in sync with sanitized values
  - Added bind failure error handling with actionable user notification
  - Added comprehensive unit tests for host and port validation
