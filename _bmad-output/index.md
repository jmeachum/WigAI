# Project Documentation Index — WigAI Core

## Project Overview
- **Type:** Monolith backend Bitwig extension exposing MCP tools.
- **Primary language:** Java 21.
- **Architecture:** Layered Jetty → MCP → controller pattern built on a Bitwig API facade, documented in `_bmad-output/architecture.md`.
- **Entry point:** `WigAIExtension` initializes Jetty, MCP server, and controllers so `/mcp` becomes the single entry path for AI clients.

## Quick Reference
- **Tech stack:** Java 21 + Gradle 9 + Shadow 8.3.0 for packaging, Jetty 11.0.20 for the embedded server, Bitwig Extension API 19 / MCP Java SDK 0.11.0 for the tool surface, and JUnit Jupiter 5.10.0 plus custom Gradle tasks for QA.
- **Entry point call chain:** `WigAIExtension` → `JettyServerManager` → `McpServerManager` → controllers (`TransportController`, `ClipSceneController`, `DeviceController`) → `BitwigApiFacade`.
- **Architecture pattern:** Loopback-only Jetty binding, structured MCP tool layer, and consistent error envelopes keep Bitwig API usage localized and observable through the `status` tool.

## Generated Documentation
- [Project Overview](./project-overview.md)
- [Architecture](./architecture.md)
- [Source Tree Analysis](./source-tree-analysis.md)
- [Component Inventory](./component-inventory.md)
- [API Contracts](./api-contracts-core.md)
- [Data Models](./data-models-core.md)
- [Development Guide](./development-guide.md)
- [Development & Deployment Playbook](./development-deployment-guide.md)

## Existing Documentation
- [docs/architecture.md](../docs/architecture.md) — architecture summary maintained in the documentation suite.
- [docs/project-context.md](../docs/project-context.md) — technology stack, workflow rules, and agent guidance.
- [docs/engineering/git-workflow.md](../docs/engineering/git-workflow.md) — branch naming and PR policy (important for BMAD cycles).
- [docs/ci.md](../docs/ci.md) — GitHub Actions workflows referenced in the deployment playbook.
- [docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md](../docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md) — loopback binding guardrails and defaults.
- [docs/traceability-matrix-1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md](../docs/traceability-matrix-1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md) — quality criteria for MCP responses.
- [docs/mcp-tools-reference.md](../docs/mcp-tools-reference.md) — additional MCP tooling reference.

## Getting Started
1. Clone the current `develop/cycle-*` branch, create a working branch (e.g., `implementation/feature-name`), and run `./gradlew test` followed by `./gradlew bwextension` to build the extension.
2. Drop `build/extensions/WigAI.bwextension` into `~/Documents/Bitwig Studio/Extensions/`, restart Bitwig, and enable WigAI; the MCP server will answer at `http://localhost:61169/mcp` (Jetty normalizes `localhost` to `127.0.0.1` or `[::1]`).
3. Drive the MCP API using the catalog in `_bmad-output/api-contracts-core.md`, respecting the shared success/error payloads.
4. Refer to `_bmad-output/development-guide.md` for day-to-day workflows, `_bmad-output/project-overview.md` for orientation, and `_bmad-output/source-tree-analysis.md` to navigate the repository.

Use this index as the primary entry point for AI-assisted documentation and to orient teammates when they continue the workflow.
