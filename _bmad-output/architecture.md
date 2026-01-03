# Architecture — WigAI Core

## Executive summary
WigAI is a single-part Bitwig extension that boots an embedded Jetty server, registers MCP tools, and orchestrates Bitwig API calls through dedicated controllers. The extension entry point (`WigAIExtension`) drives configuration, Jetty lifecycle, and MCP server startup so that external AI clients can send SSE/JSON commands over `/mcp` without having to understand Bitwig internals.

## Technology stack
- **Java 21 / Gradle 9+ + Shadow 8.3.0** – Configured in `build.gradle.kts` to produce both the `wigai-all` shadow JAR and the `WigAI.bwextension` artifact.
- **Bitwig Extension API 19** – Accessed via `BitwigApiFacade`, which wraps transport, application, tracks, scenes, devices, and clip slots with defensive interest registration.
- **Jetty 11.0.20** – Hosts the MCP servlet over loopback-only endpoints and enforces host/port constraints before binding.
- **MCP Java SDK 0.11.0 + MCP tools (Status, transport, clip/scene, device/track introspection)** – Exposed through `McpServerManager` and normalized error handling via `McpErrorHandler` and shared `ErrorCode` definitions.
- **JUnit Jupiter 5.10.0 + custom Gradle tasks** – Drives unit suites, `atddRedTest`, and the host-bound `mcpSmokeTest` harness.

## Architecture pattern
WigAI follows a layered Bitwig-extension pattern:
1. **Entry layer:** `WigAIExtension` initializes logging, config, Jetty, and MCP server managers while reacting to preference changes.
2. **Infrastructure layer:** `JettyServerManager` enforces loopback binding (`localhost` → `127.0.0.1`/`::1`), registers servlets, and reports bind failures; `McpServerManager` wires the MCP servlet, SSE transport, tools, and structured logging.
3. **Application layer:** Controllers (`TransportController`, `DeviceController`, `ClipSceneController`) translate MCP commands into Bitwig API calls via `BitwigApiFacade`, which caches interests and handles pagination safely.
4. **Tool layer:** MCP specifications (`TransportTool`, `ClipTool`, `SceneTool`, `DeviceParamTool`, etc.) validate input payloads, call controllers, and always wrap responses with the shared `status`/`error` envelope for predictable clients.
This pattern keeps Bitwig API interactions localized, enables deterministic loopback binding, and surfaces telemetry through the `StatusTool` for orchestration.

## Data architecture
- **Configuration:** `PreferencesBackedConfigManager` persists MCP host/port defaults (using `AppConstants.DEFAULT_MCP_PORT = 61169`) and notifies observers. Jetty uses these values but ultimately enforces loopback binding for safety.
- **State + telemetry:** `BitwigApiFacade` collects transport state, selected track/device info, and clip slot metadata; these are fed into `StatusTool`, `list_tracks`, `list_devices_on_track`, and `get_clips_in_scene` responses.
- **Error handling:** All controllers funnel through `McpErrorHandler`, which maps `BitwigApiException`/`ErrorCode` instances into stable MCP envelopes, ensuring every failure includes `operation` and standardized codes (e.g., `INVALID_PARAMETER_INDEX`, `TRACK_NOT_FOUND`).

## API design
- All tool definitions live in `io.github.fabb.wigai.mcp.tool`. Each tool declares a JSON schema, uses `ParameterValidator`, and calls a controller via `McpErrorHandler.executeWithValidation` to guarantee consistent validation + error payloads.
- The server exposes `transport_start/stop`, `launch_clip`, `session_launchSceneByIndex`, `session_launchSceneByName`, `get_clips_in_scene`, `list_tracks`, `list_scenes`, `list_devices_on_track`, `get_track_details`, `get_device_details`, `get_selected_device_parameters`, `set_selected_device_parameter`, `set_selected_device_parameters`, and `status` (telemetry + partial failure reporting).
- Responses always look like `{ "status": "success", "data": ... }` on success or `{ "status": "error", "error": { "code", "message", "operation" } }` on failure, matching the canonical list documented in `_bmad-output/api-contracts-core.md`.

## Component overview
- `WigAIExtension` / `WigAIExtensionDefinition`: Bootstraps Jetty/MCP and exposes the extension to Bitwig.
- `JettyServerManager`: Creates the server, connectors, context handler, and ensures hosts are loopback-only; logs bind results.
- `McpServerManager`: Sets up SSE transport, registers `StatusTool`, transport tools, clip/scene tools, device/track tools, and ensures controllers reuse `BitwigApiFacade` for Bitwig-safe calls.
- `Controllers`: `TransportController`, `ClipSceneController`, and `DeviceController` encapsulate business logic, validations, and Bitwig interactions with structured logging.
- `BitwigApiFacade`: Central Bitwig API abstraction, handles property interest registration, track/device enumeration, clip info, and helper utilities for color formatting and pagination.
- `Common` utilities: `Logger`, `StructuredLogger`, `ErrorCode`, `ParameterValidator`, and shared data records (`ParameterInfo`, `ParameterSetting`, `ParameterSettingResult`).

## Source tree reference
The source tree map from `_bmad-output/source-tree-analysis.md` shows the location of each critical folder: `_bmad/` (workflows), `src/` (extension code), `docs/` (reference materials), `_bmad-output/` (generated artifacts), `scripts/`/`bitwig-api-doc-scraper/`, and the Gradle build configuration. Use that map to find entry points mentioned above.

## Development workflow
- Follow instructions in `_bmad-output/development-deployment-guide.md`: prerequisites are Java 21 + Shadow/Gradle wrapper, run `./gradlew test` and `./gradlew bwextension`, and optionally mirror CI with `scripts/ci-local.sh` or `scripts/test-changed.sh`.
- Active branches stay on `develop/cycle-*`; contributions must obey the `CONTRIBUTING.md` and `docs/engineering/git-workflow.md` branch naming + PR guidance.
- Conventional commits trigger Nyx semantic versions; tests must pass (CI runs the same `./gradlew test` suite and `./gradlew bwextension`) before merging to `develop/cycle-*`.

## Deployment architecture
- GitHub Actions hold CI logic (`.github/workflows/pr-validation.yml`, `build-and-test.yml`, `release.yml`, `branch-policy.yml`). The release pipeline on `main` uses Nyx to compute versions automatically.
- Jetty + MCP server binds to `/mcp` on loopback hosts; `JettyServerManager` handles `BindException` by logging warnings and notifying Bitwig.
- MCP endpoint is accessible via `http://localhost:61169/mcp` by default, with IPv4 and IPv6 variants documented in `docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md` and `docs/engineering/mcp-smoke-test-runbook.md`.

## Testing strategy
- `./gradlew test` runs JUnit Jupiter suites for controllers, facades, error handling, and tools; smoke/Atdd tasks are tagged to avoid accidental host-required runs.
- ATDD scenarios (`./gradlew atddRedTest`) check loopback binding, preference defaults, and MCP command validations.
- MCP smoke harness (`./gradlew mcpSmokeTest -PmcpHost=localhost -PmcpPort=61169`) drives the server over SSE and can be mutated using `WIGAI_SMOKE_TEST_MUTATIONS=true` to confirm resilience.
- `scripts/test-changed.sh` and `scripts/ci-local.sh` let developers execute CI-equivalent flows locally before pushing to `develop/cycle-*`.
