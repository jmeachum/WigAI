# Component Inventory — WigAI Core

## Server surface
- **`WigAIExtension` / `WigAIExtensionDefinition`**: Bitwig extension entry point that wires configuration, logging, and server lifecycle.
- **`JettyServerManager`**: Creates the embedded Jetty server, enforces loopback binding (`localhost`, `127.0.0.1`, `::1`), registers servlets, and reports bind failures to Bitwig/Log.
- **`McpServerManager`**: Builds the MCP servlet, registers structured `Status`, transport, clip/scene, track/device, and parameter tools, and reuses the `BitwigApiFacade` + controllers for operations.

## Controllers & tools
- **`TransportController` / `TransportTool`**: Start/stop Bitwig transport and expose `transport_start`/`transport_stop` MCP commands with no arguments.
- **`ClipSceneController`, `ClipTool`, `SceneTool`, `SceneByNameTool`, `GetClipsInSceneTool`**: Launch clips/scenes and inspect scene/clip state while enforcing index/name validation via `ParameterValidator`.
- **`DeviceController`, `DeviceParamTool`, `GetDeviceDetailsTool`**: Manage device parameter reads/sets, returning structured `ParameterInfo`, `ParameterSettingResult`, and detailed device maps.
- **`ListTracksTool`, `ListScenesTool`, `ListDevicesOnTrackTool`, `GetTrackDetailsTool`**: Query track, scene, and device inventories with filters or selected-context semantics.
- **`StatusTool`**: Returns telemetry (`wigai_version`, transport status, selected track/device, clip slot, partial failures) for monitoring.

## Infrastructure helpers
- **`BitwigApiFacade`**: Centralizes Bitwig API interactions (transport, application, track/scene/device enumeration) and caches `markInterested` subscriptions.
- **`Logger` / `StructuredLogger`**: Consistent logging that tracks operation IDs, durations, and error codes for MCP tool calls.
- **`McpErrorHandler` + `ErrorCode`**: Shared error envelope ensures every MCP response contains `operation`, `code`, `message`, and consistent statuses.
- **`ParameterValidator`**: Validates required arguments (indexes, ranges, strings) and throws `BitwigApiException` with the proper `ErrorCode` when rules are breached.

## Supporting tooling
- **`scripts/ci-local.sh` & `scripts/test-changed.sh`**: Local CI mirrors (full run and selective) for testing before pushing changes.
- **`build.gradle.kts`**: Declares Bitwig/MCP dependencies, configures Java 21, adds Shadow & custom tasks (`mcpSmokeTest`, `atddRedTest`, `bwextension`).
- **`README.md`, `CONTRIBUTING.md`, `docs/**`**: Host user guidance, BMAD process, CI reference (`docs/ci.md`), branch rules, traceability matrices, and test guides that complement in-repo docs.
- **`_bmad/` & `_bmad-output/`**: Automate BMAD workflows and store generated documentation (project scan report, API/data docs, architecture overviews, supporting guides).
