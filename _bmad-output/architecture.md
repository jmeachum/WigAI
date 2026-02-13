# WigAI Architecture

## Document Scope and Lineage

- This file (`_bmad-output/architecture.md`) is the canonical architecture output for the current `document-project` run.
- Historical, deeper planning context remains in `_bmad-output/planning-artifacts/architecture.md`.
- Both are intentionally retained:
  - Current scan architecture for active implementation guidance
  - Historical planning architecture for broader rationale and legacy context

## Executive Summary

WigAI is a single-part Java 21 Bitwig Studio extension that exposes a local MCP endpoint so AI agents can control transport, clip/scene actions, and selected-device parameters. The runtime is an embedded Jetty server hosting an MCP servlet, with tool handlers mapped to controller/facade layers.

## Technology Stack

- Language: Java 21
- Build: Gradle 8.13 (Kotlin DSL)
- Packaging: Shadow fat-jar + `.bwextension` artifact
- Host API: Bitwig Extension API v19
- Protocol: MCP Java SDK (BOM 0.11.0)
- HTTP server: Jetty 11
- Test stack: JUnit Jupiter 5 + MCP test utilities

## Architecture Pattern

- Style: Layered monolith, service/API-centric.
- Layers:
  - Lifecycle/entry: `WigAIExtensionDefinition`, `WigAIExtension`
  - Hosting/transport: `JettyServerManager`, MCP servlet setup
  - Protocol/API: `McpServerManager`, `mcp/tool/*`
  - Feature controllers: `features/*`
  - Host integration: `BitwigApiFacade`
  - Cross-cutting: `common/*` (error, retry, logging, validation)

## Key Architectural Decisions and Guardrails

- Modular monolith inside Bitwig extension runtime:
  - Single deployable extension artifact with explicit internal boundaries.
- Tool-based API model:
  - MCP tools are the public contract; keep names and request/response shapes stable within a release line.
- Facade boundary for host integration:
  - Bitwig API access is centralized in facade/controller layers, not spread across tool handlers.
- Non-destructive defaults for mutating behavior:
  - Mutation workflows should prefer safe defaults and explicit opt-ins for destructive actions.
- Idempotency and correlation for mutating operations:
  - `request_id` should be supported and propagated for retry safety and diagnostics.

## Idempotency Contract Invariants

- `request_id` keying contract:
  - `request_id` is optional; dedupe applies only when present and valid.
  - Accepted dedupe-keying values are non-empty printable ASCII (`32..126`) strings.
  - `request_id` length greater than `1024` MUST skip dedupe and execute normally.
  - Logging sanitization (truncation/scrubbing) is separate from dedupe-keying semantics.
- Payload consistency invariant:
  - For accepted dedupe requests, the server computes a deterministic payload fingerprint from non-correlation arguments.
  - Numeric values are compared by canonical decimal semantics (JSON number meaning), not by Java runtime subtype (`Integer`/`Long`/`Float`/`Double`/`BigDecimal`).
  - Reusing the same `(tool_name, request_id)` with a different payload fingerprint MUST return `INVALID_PARAMETER` and MUST NOT return a stale cached result.
- Mutating-only gating source-of-truth:
  - Dedupe gating MUST be enforced in the shared MCP execution path, not by per-tool convention.
  - Mutating operation parity checks MUST derive from the authoritative MCP tool registration path.
- Required test style:
  - Use structural schema assertions (`properties` + `required`) for `request_id`; do not use schema substring matching.
  - Use registration-discovery parity tests for mutating allowlist coverage; avoid manual dual-list maintenance.
  - Include integration tests for hit/miss, TTL expiry, payload mismatch rejection, and invalid `request_id` fallback behavior.
  - Keep workspace/story artifacts clean: generated ad-hoc files outside tracked source/doc paths MUST NOT be committed.

## Data Architecture

- Persistence: none detected (no DB schemas/migrations).
- Data model: transient runtime state from Bitwig, shaped into records and map payloads.
- Core records:
  - `ParameterInfo`
  - `ParameterSetting`
  - `ParameterSettingResult`
- Aggregated response objects are produced by controllers/facade and serialized via MCP tool responses.

## API Design

- API surface: MCP tool contracts on `/mcp`.
- Standard response envelope:
  - success: `{ "status": "success", "data": ... }`
  - error: `{ "status": "error", "error": { code, message, operation } }`
- Registered tools include status, transport control, scene/clip launch, track/device listing/detail, and parameter mutation/query.

## Error Handling Strategy

- Three-layer handling model:
  - Facade/controller layers classify and raise structured errors
  - MCP layer normalizes envelopes via shared error handler
- Canonical error envelope:
  - `status=error` with `code`, `message`, and `operation`
- Error taxonomy:
  - Centralized in `ErrorCode` enum and reused across tools
- Reliability behavior:
  - Retry policy is explicit and bounded; read vs mutating operations can use different retry modes
- Logging discipline:
  - Correlation-friendly structured logging; avoid verbose payload logging by default

## Component Overview

- `WigAIExtension`:
  - Initializes config, MCP manager, Jetty manager.
  - Reacts to host/port config changes with controlled restart.
- `JettyServerManager`:
  - Starts/stops/restarts server.
  - Enforces loopback-only binding.
- `McpServerManager`:
  - Registers MCP capabilities and tools.
  - Wires controllers and facade.
- `Feature controllers`:
  - `TransportController`, `DeviceController`, `ClipSceneController`.
- `BitwigApiFacade`:
  - Single integration boundary to Bitwig host APIs.

## Source Tree

Primary runtime folders:
- `src/main/java/io/github/fabb/wigai/`
- `src/main/java/io/github/fabb/wigai/mcp/tool/`
- `src/main/java/io/github/fabb/wigai/features/`
- `src/main/java/io/github/fabb/wigai/bitwig/`
- `src/main/java/io/github/fabb/wigai/config/`
- `src/main/java/io/github/fabb/wigai/common/`

## Development Workflow

- Local loop:
  - `./gradlew test`
  - `./gradlew build`
  - Install `build/extensions/WigAI.bwextension` into Bitwig extensions
  - Verify MCP endpoint connectivity at localhost
- Branch/PR discipline:
  - Conventional commits
  - PR validation + branch policy gates in GitHub Actions

## Deployment Architecture

- Delivery artifact: `WigAI.bwextension`.
- Deployment target: Bitwig desktop extension environment.
- Runtime exposure: local Jetty endpoint `/mcp`, loopback constrained.
- Release automation: GitHub Actions + Nyx on `main` merges.

## Security and Operational Posture

- MVP model is local-first and no-auth:
  - Security relies on strict loopback binding (`localhost` / `127.0.0.1` / `::1`)
- Defense-in-depth:
  - Host validation and server bind safeguards both enforce loopback-only constraints
- Operational contract:
  - Widening network exposure should be treated as an architectural change requiring explicit auth and threat-model updates

## Testing Strategy

- Unit/integration tests: JUnit suite (`./gradlew test`).
- Host-sensitive verification: MCP smoke harness (`./gradlew mcpSmokeTest`) when Bitwig runtime is available.
- CI strategy:
  - Code-change-aware validation workflow
  - Reusable build/test workflow
  - Release workflow with semantic version publishing
