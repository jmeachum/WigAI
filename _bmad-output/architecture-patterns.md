# Architecture Patterns

## Part: main

### Primary Architecture Pattern

- **Pattern:** Service/API-centric layered backend extension
- **Classification Basis:** `project_type_id=backend` plus concrete package layering (`server`, `mcp`, `features`, `bitwig`, `common`, `config`).

### Structural View

1. **Extension Lifecycle Layer**
- `WigAIExtension` is the host lifecycle entry point (`init`, `exit`, config-change restart hooks).

2. **Transport/Hosting Layer**
- `JettyServerManager` owns embedded Jetty server lifecycle and secure loopback binding enforcement.
- MCP endpoint mounted at `/mcp`.

3. **Protocol/API Layer**
- `McpServerManager` configures MCP server capabilities and registers tool contracts.
- Tool surface is implemented in `mcp/tool/` (`12` tool classes detected).

4. **Application/Feature Layer**
- Feature controllers (`TransportController`, `DeviceController`, `ClipSceneController`) hold command-level business behavior.

5. **Domain Adapter Layer**
- `BitwigApiFacade` abstracts Bitwig controller API interactions and mediates host integration.

6. **Cross-Cutting Layer**
- `common/*` packages provide logging, validation, retry, error normalization.
- `config/*` provides preference-backed runtime configuration and observer-driven restarts.

### Architectural Characteristics

- **Style:** Layered monolith with adapter/facade integration boundaries.
- **Interface model:** Tool-driven MCP command API over HTTP (streamable transport).
- **State model:** Primarily runtime host/session state; no repository-level persistent data model detected in core runtime.
- **Operational posture:** Localhost-bound service by design (defense-in-depth against non-loopback bind).

### Why this pattern fits

- The codebase is organized around service boundaries and protocol handlers rather than UI or distributed microservices.
- Tool endpoints are explicit and centrally registered, then delegated into controller/facade layers.
- Cross-cutting concerns (error handling, retries, validation, structured logging) are centralized and reused.
