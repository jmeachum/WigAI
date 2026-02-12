# Development Guide

## Prerequisites

- Java 21
- Gradle wrapper (`./gradlew`, Gradle 8.13)
- Bitwig Studio 5.2.7+
- Optional Node.js for auxiliary docs scraper (`bitwig-api-doc-scraper/`)

## Setup

1. Clone repository
2. Ensure JDK 21 is active
3. Run baseline verification:
   - `./gradlew test`
   - `./gradlew build`

## Key Commands

- Test suite: `./gradlew test`
- Build extension: `./gradlew build`
- Build extension only: `./gradlew bwextension -x test`
- ATDD red suite: `./gradlew atddRedTest`
- Host smoke harness:
  - `./gradlew mcpSmokeTest -PmcpHost=localhost -PmcpPort=61169 -PmcpEndpointPath=/mcp`

## Local Runtime Verification

1. Build artifact
2. Install `build/extensions/WigAI.bwextension` into Bitwig Extensions directory
3. Enable extension in Bitwig
4. Verify endpoint response on `http://localhost:61169/mcp`

## Development Workflow Expectations

- Use conventional commits
- Keep changes aligned with established layer boundaries
- Run tests before PR
- Follow cycle branch and PR-target rules from contribution guide

## Project Areas

- Runtime code: `src/main/java/io/github/fabb/wigai/`
- MCP tools: `src/main/java/io/github/fabb/wigai/mcp/tool/`
- Tests: `src/test/java/`
- CI/CD definitions: `.github/workflows/`
