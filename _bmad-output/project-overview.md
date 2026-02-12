# WigAI Project Overview

## Project Purpose

WigAI is a Bitwig Studio extension that runs a local MCP server so external AI agents can control core music-production actions (transport control, scene/clip operations, and selected device parameter operations) through a stable tool interface.

## Executive Summary

This repository is a single-part Java backend extension (monolith) optimized for local-first operation inside the Bitwig runtime. It exposes a loopback-bound MCP endpoint (`/mcp`) via embedded Jetty and maps tool calls through controller/facade layers for predictable host interaction and error handling.

## Quick Classification

| Attribute | Value |
|---|---|
| Repository Type | Monolith |
| Project Type | Backend Bitwig Extension |
| Primary Language | Java 21 |
| Build System | Gradle (Kotlin DSL, wrapper 8.13) |
| Protocol | MCP Java SDK (BOM 0.11.0) |
| Runtime Server | Jetty 11 |
| Packaging Artifact | `build/extensions/WigAI.bwextension` |

## Architecture at a Glance

- Layered service/API-centric architecture
- Core runtime flow:
  1. Bitwig loads extension definition
  2. Extension initializes config + MCP + Jetty managers
  3. MCP tools are registered and exposed on `/mcp`
  4. Tool operations route through feature controllers into Bitwig API facade

## Primary Documentation Links

- Architecture (current): `./architecture.md`
- Architecture (historical/planning): `./planning-artifacts/architecture.md`
- Source tree analysis: `./source-tree-analysis.md`
- Component inventory: `./component-inventory.md`
- Development guide: `./development-guide.md`
- Deployment guide: `./deployment-guide.md`
- Contribution guide: `./contribution-guide.md`
- API contracts: `./api-contracts.md`
- Data models: `./data-models.md`

## Operational Notes

- Security posture is local-first, loopback-only for MVP no-auth mode.
- API and error envelopes are standardized for tool reliability and integration stability.
- CI/CD uses branch-policy and PR-validation gates with Nyx-based release publishing on `main`.
