# WigAI Project Overview

## Executive Summary

**WigAI** is a sophisticated Bitwig Studio extension that bridges creative music production with AI capabilities through the Model Context Protocol (MCP). The extension enables AI agents to interact with Bitwig's rich API, controlling tracks, devices, scenes, and clips programmatically.

**Project Type:** Bitwig Studio Extension (Backend/Java)  
**Repository Structure:** Monolithic Java/Gradle project  
**Primary Language:** Java 21 (LTS)  
**Build System:** Gradle (Kotlin DSL)  
**Target Platform:** Bitwig Studio 5.2.7+  

## Architecture Overview

### High-Level Components

```
WigAI Extension
├── MCP Server Layer (Model Context Protocol)
│   ├── McpServerManager - Orchestrates MCP server lifecycle
│   ├── Tool Registry - Dynamically registers available tools
│   └── Error Handling - Standardized error responses
│
├── HTTP/Server Layer
│   ├── JettyServerManager - Embedded HTTP server (SSE/Streamable HTTP)
│   └── Servlet Infrastructure - Request routing
│
├── Bitwig Integration Layer
│   ├── BitwigApiFacade - Abstraction over Bitwig's complex API
│   ├── Feature Controllers - Domain-specific controllers
│   │   ├── ClipSceneController
│   │   ├── DeviceController
│   │   ├── TransportController
│   │   └── SceneBankFacade
│   └── ControllerHost Integration - Direct API access
│
├── Configuration System
│   ├── ConfigManager (Interface) - Configuration contract
│   └── PreferencesBackedConfigManager - Bitwig preferences integration
│
└── Common Infrastructure
    ├── Logger - Unified logging
    ├── Error Handling - Exception hierarchy
    ├── Validation - Input validation utilities
    └── Data Models - Shared data structures
```

### Component Responsibilities

| Component | Purpose | Key Files |
|-----------|---------|-----------|
| **MCP Server** | Implements Model Context Protocol spec; exposes tools to AI agents | `mcp/` directory |
| **HTTP Server** | Hosts MCP endpoints via SSE (Server-Sent Events) | `server/JettyServerManager.java` |
| **Bitwig Facade** | Simplifies complex Bitwig API interactions; handles threading constraints | `bitwig/BitwigApiFacade.java` |
| **Feature Controllers** | Domain-specific business logic for clips, devices, scenes, transport | `features/` directory |
| **Config Manager** | Manages extension settings and Bitwig preferences integration | `config/` directory |
| **Extension Lifecycle** | Bootstrap, initialization, cleanup | `WigAIExtension.java` |

## Technology Stack

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Language** | Java | 21 LTS | Mandated by Bitwig API |
| **Build** | Gradle | 8.x | Dependency management |
| **Core API** | Bitwig Extension API | 19 | DAW integration |
| **Protocol** | MCP Java SDK | 0.11.0+ | AI agent communication |
| **Server** | Jetty 11 | 11.0.20 | Embedded HTTP server |
| **Servlet API** | Jakarta EE | 6.0.0 | Web standards compliance |
| **Testing** | JUnit Jupiter | 5.10.0 | Unit testing framework |
| **Packaging** | Shadow JAR + .bwextension | N/A | Bitwig extension format |

## Key Features

### Model Context Protocol (MCP) Integration
- Full MCP 0.11.0 compliance
- Dynamic tool registration system
- JSON-RPC 2.0 message handling
- Streaming responses via SSE (Streamable HTTP planned)

### Bitwig Studio Integration
- Thread-safe API access (respects Bitwig's single-threaded constraint)
- Controllers for: Tracks, Clips, Scenes, Devices, Transport
- Real-time state access and manipulation
- Error recovery and validation

### Configuration & Preferences
- Seamless Bitwig preferences integration
- Runtime configuration changes
- Observer pattern for config updates

### Error Handling
- Standardized error codes (e.g., `BITWIG_API_ERROR`, `INVALID_PARAMETER`)
- Proper HTTP status mapping
- Detailed error logging

## Development Workflow

### Prerequisites
- Java 21 JDK installed
- Gradle 8.x (wrapper included)
- Bitwig Studio 5.2.7+ for testing
- IDE: IntelliJ IDEA or VS Code (with Java extensions)

### Building
```bash
./gradlew build          # Build and create .bwextension
./gradlew shadowJar      # Create fat JAR with dependencies
./gradlew bwextension    # Create Bitwig extension package
```

### Testing
```bash
./gradlew test           # Run JUnit tests
./gradlew check          # Run all checks (tests + linting)
```

### Extension Installation
The `.bwextension` file (created in `build/extensions/`) is copied to:
- **macOS:** `~/Library/Application Support/Bitwig Studio/Extensions/`
- **Windows:** `%APPDATA%\Bitwig Studio\Extensions\`
- **Linux:** `~/.config/Bitwig Studio/Extensions/`

## Project Status

Delivery status is tracked in `../../_bmad-output/implementation-artifacts/sprint-status.yaml`, which is
the source of truth. The summary below was accurate on 2026-08-19.

### Shipped
- Epic 1 — baseline MCP hardening: smoke-test harness, localhost binding guardrails, standardized
  response envelopes, request-id log correlation, non-blocking bounded retry, canonical error codes,
  request-id-keyed idempotency for mutating tools.
- Epic 2 — track targeting contract: contract-semantics DoR and lockstep gate, duplicate-track-name
  ambiguity behavior, standard track targeting (index / exact name / selected default), the
  `resolve_track` tool with deterministic fuzzy matching, and rollout across existing mutating tools.

### Next
- Epic 3 — launcher clip creation and scene launch tools. **Gated**: five carry-forward items from the
  Epic 2 retrospective must close first. See
  `../../_bmad-output/implementation-artifacts/epic-3-kickoff-checklist-2026-02-17.md`.
- Epic 4 — deterministic note payload contracts and clip note writing.
- Epic 5 — batch clip create/write with per-item results.
- Epic 6 — dependency migration: Jetty 12, Jakarta, MCP SDK 0.17, Bitwig API 24.
- Epic 7 — devcontainer with repo-local MCP tooling.

## Documentation Map

### Architecture & Design
- [Architecture](../../_bmad-output/planning-artifacts/architecture.md) — solutioning-phase system design
- [Component Architecture Deep Dive](./component-architecture-deep-dive.md) — detailed component breakdown
- [Component View](./component-view.md) — component interactions
- [Project Structure](./project-structure.md) — directory and package layout
- [Sequence Diagrams](./sequence-diagrams.md) — placeholder; flows are currently in the deep dive

### API & Integration
- [API Reference](./api-reference.md) — full MCP API specification (authoritative)
- [MCP Tools Reference](../mcp-tools-reference.md) — per-tool quick reference
- [Data Models](./data-models.md) — request/response structures

### Requirements & Planning
- [PRD](../../_bmad-output/planning-artifacts/prd.md) — current-cycle product requirements
- [Epics & Stories](../../_bmad-output/planning-artifacts/epics.md) — Epics 1-7
- [Project Brief](../../_bmad-output/planning-artifacts/project-brief.md) — vision, users, MVP scope
- [Cycle 1 archive](../../_bmad-output/planning-artifacts/archive/cycle-1-2025-12-15/) — superseded baseline

### Development
- [Operational Guidelines](./operational-guidelines.md) — coding standards **and** testing strategy
- [Tech Stack](./tech-stack.md) — technology decisions and justification
- [Git Workflow](../engineering/git-workflow.md) — branch types and PR targeting
- [Environment Variables](./environment-vars.md) — configuration parameters
- [Deployment](./infra-deployment.md) — distribution and installation
- [Semantic Versioning](./semantic-versioning-guide.md) — Nyx and Conventional Commits
- [CI](../ci.md) — GitHub Actions workflows and local equivalents

### Testing
- [MCP Smoke Test Runbook](../engineering/mcp-smoke-test-runbook.md) — `./gradlew mcpSmokeTest`
- [Host Functional Test Matrix](../engineering/mcp-host-functional-test-matrix.md) — manual per-tool coverage
- [MCP Endpoints Verification](./testing/mcp-endpoints-verification.md)

### Workflow Tracking
- [Sprint Status](../../_bmad-output/implementation-artifacts/sprint-status.yaml) — epic/story status of record
- [BMM Workflow Status](../../_bmad-output/planning-artifacts/bmm-workflow-status.yaml) — planning-phase progress

## Getting Started

### For New Developers
1. **Read:** [Project Brief](../../_bmad-output/planning-artifacts/project-brief.md) for context
2. **Review:** [Component Architecture Deep Dive](./component-architecture-deep-dive.md) and
   [Project Structure](./project-structure.md)
3. **Setup:** follow the build instructions above, then `./scripts/ci-local.sh` to mirror CI locally
4. **Standards:** read [Operational Guidelines](./operational-guidelines.md) before your first change

### For Feature Development
1. **Check:** [API Reference](./api-reference.md) for existing tool contracts
2. **Find the story:** `../../_bmad-output/implementation-artifacts/<epic>-<n>-<slug>.md`
3. **Respect the lockstep gate:** runtime code, tests, and API docs must change together — see
   `../../_bmad-output/implementation-artifacts/epic-2-contract-semantics-dor-lockstep-standard-2026-02-16.md`
4. **Test:** follow the patterns in `src/test/java/`, including `contract/` and `integration/`

### For API Integration
1. **Start:** [MCP Tools Reference](../mcp-tools-reference.md)
2. **Confirm against:** [API Reference](./api-reference.md), which is authoritative where the two differ
3. **Connect:** `http://localhost:61169/mcp`

## Key References

- **Bitwig Extension API:** https://resources.bitwig.com/studios/controller-api/
  (local scraped v19 copy: [bitwig-api/v19/index.md](./bitwig-api/v19/index.md))
- **Model Context Protocol:** https://modelcontextprotocol.io/
- **MCP Java SDK:** https://github.com/modelcontextprotocol/java-sdk
- **Jakarta EE:** https://jakarta.ee/

---

**Last Updated:** 2026-08-19
**Status:** Active Development
