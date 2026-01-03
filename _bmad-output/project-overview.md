# Project Overview — WigAI Core

## Purpose
WigAI exposes a Model Context Protocol endpoint (`/mcp`) from inside Bitwig Studio so external AI assistants can start/stop transport, launch clips and scenes, and inspect track/device state without embedding Bitwig-specific knowledge.

## Highlights
- **Loopback-only MCP server** — Jetty/`JettyServerManager` binds to `localhost`, `127.0.0.1`, or `::1` on port `61169` and reports the advertised URL to Bitwig.
- **Structured MCP tools** — `McpServerManager` registers the `status`, transport, scene/clip, track/device, and parameter tools, all of which rely on `McpErrorHandler` plus canonical `ErrorCode` values.
- **Layered architecture** — `WigAIExtension` boots Jetty and MCP, `BitwigApiFacade` centralizes Bitwig interactions, and controllers (`TransportController`, `ClipSceneController`, `DeviceController`) translate validated tool requests into API calls.

## Key references
- Source tree map: `_bmad-output/source-tree-analysis.md`
- Architecture + components: `_bmad-output/architecture.md`
- API contracts: `_bmad-output/api-contracts-core.md`
- Data models: `_bmad-output/data-models-core.md`
- Development & deployment details: `_bmad-output/development-deployment-guide.md`
- CI/CD summary: `docs/ci.md`
- Branching + contributions: `CONTRIBUTING.md`, `docs/engineering/git-workflow.md`
- Guardrails: `docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md`

## Getting started (outline)
1. Clone `develop/cycle-*` and branch under `implementation/*`, `docs/*`, or another BMAD-guided prefix.
2. Install Java 21, run `./gradlew test`, then `./gradlew bofextension` or `./gradlew build` to produce `build/extensions/WigAI.bwextension`.
3. Drop the `.bwextension` into Bitwig Studio’s extensions folder, activate the extension, and connect to `http://localhost:61169/mcp` (Jetty enforces loopback, so IPv4/6 responses appear as `127.0.0.1` or `[::1]`).
4. Refer to `_bmad-output/development-guide.md` for day-to-day commands (tests, smoke harness, selective CI) and `_bmad-output/api-contracts-core.md` for composing MCP requests.

## Output targets
- Generated artifacts live under `_bmad-output/` until they are merged into `docs/` for public consumption.
- Use this overview as the entry point when steering future documentation work or onboarding new contributors.
