# Development Instructions

## Prerequisites

- Java 21 LTS
- Gradle (via wrapper, pinned to 8.13)
- Bitwig Studio 5.2.7+
- Optional: Node.js environment for `bitwig-api-doc-scraper/`

## Environment and Configuration

- Runtime server settings are managed via Bitwig Controller Preferences:
  - `MCP Host` (loopback-only: `localhost`, `127.0.0.1`, `::1`)
  - `MCP Port` (default `61169`)
- No mandatory `.env`-driven runtime config for the core Java extension was detected.

## Build and Test Commands

- Run tests:
  - `./gradlew test`

- Build extension artifact:
  - `./gradlew build`
  - Produces: `build/extensions/WigAI.bwextension`

- Build extension without tests (CI/build optimization path):
  - `./gradlew bwextension -x test`

- ATDD red-phase test subset:
  - `./gradlew atddRedTest`

- MCP smoke harness (requires running Bitwig host):
  - `./gradlew mcpSmokeTest -PmcpHost=localhost -PmcpPort=61169 -PmcpEndpointPath=/mcp`

## Local Run / Verification Flow

1. Build extension: `./gradlew build`
2. Install artifact to Bitwig extensions directory
3. Enable extension in Bitwig
4. Verify MCP endpoint responds at `http://localhost:61169/mcp`
5. Run smoke/functional checks as needed

## Project Structure for Development

- Core runtime code: `src/main/java/io/github/fabb/wigai/`
- MCP tool contracts: `src/main/java/io/github/fabb/wigai/mcp/tool/`
- Tests: `src/test/java/`
- CI workflow logic: `.github/workflows/`
- Engineering/process docs: `docs/engineering/`

## Common Development Tasks

- Add/modify a tool:
  - Implement/update tool class in `mcp/tool/`
  - Register tool in `McpServerManager`
  - Add/adjust tests in `src/test/java/`

- Change server/config behavior:
  - Update `config/*` and/or `server/JettyServerManager`
  - Validate loopback-only constraints and restart behavior

- Update release/versioning behavior:
  - Check Nyx config in `settings.gradle.kts`
  - Validate release workflow under `.github/workflows/release.yml`

## Notes

- CI skips heavy validation for docs/config-only PRs via path filtering, but still reports required status checks.
- Runtime security posture in MVP assumes localhost-bound deployment; avoid widening bind scope without an auth model.
