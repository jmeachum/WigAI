# Development Guide — WigAI Core

## Prerequisites
- **Java 21 SDK** and a compatible JDK distribution (Adoptium, Azul, etc.).
- **Gradle wrapper (`./gradlew`)** — run everything through the bundled wrapper so the correct Gradle version (9.x) plus the Shadow plugin is used.
- **Bitwig Studio 5.2.7 or later** — required to load the extension, validate the MCP endpoint, and exercise the smoke harness.
- **Nyx + Conventional Commits** — commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) so Nyx can derive semantic versions automatically.

## Local workflow
1. Run `./gradlew test` to execute the JUnit Jupiter suites (the smoke/ATDD suites are excluded unless you run the tagged tasks). This verifies controllers, the MCP server, and the Bitwig API facades.
2. Build the extension with `./gradlew bwextension` or `./gradlew build`. This creates `build/extensions/WigAI.bwextension` (packaged from the Shadow JAR) ready for installation into Bitwig Studio.
3. Copy the `.bwextension` file into `~/Documents/Bitwig Studio/Extensions/`, restart Bitwig, and enable the extension to expose the MCP server.
4. Adjust MCP host/port preferences inside Bitwig (persisted via `PreferencesBackedConfigManager`). Defaults are `localhost` and `61169`, but Jetty normalizes them to concrete loopback addresses (`127.0.0.1`/`::1`).

## Testing & smoke harness
- Run `./gradlew atddRedTest` to execute the red-phase ATDD scenario suite (loopback + binding guardrails). Tags prevent these from running accidentally during regular CI.
- Run `./gradlew mcpSmokeTest -PmcpHost=localhost -PmcpPort=61169` to drive the MCP smoke harness over SSE. Use `WIGAI_SMOKE_TEST_MUTATIONS=true` to exercise mutation flags described in `docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md`.
- For quick CI-like checks, run `./scripts/test-changed.sh` (runs `./gradlew test` only if code/build files changed) or `./scripts/ci-local.sh` (runs `./gradlew test` followed by `./gradlew bwextension -x test`).

## Debugging & diagnostics
- Jetty logs bind attempts and errors via `JettyServerManager`; look for `JettyServerManager: Refusing to bind to non-loopback host` if you misconfigure `mcpHost`.
- The MCP `status` tool (`/mcp/call` with `{"tool":"status","arguments":{}}`) reports transport state, selected device info, and any `partial_failures` so you can see what data is unavailable.
- `ParameterValidator` + `McpErrorHandler` ensures errors always include `operation`, `code`, and `message` — use those fields when driving regression tests or diagnosing invalid arguments.

## CI & release notes
- CI workflows are defined in `.github/workflows/` and summarized in `docs/ci.md`. Pull requests must target `develop/cycle-*`; branch naming and PR gating follow `docs/engineering/git-workflow.md`.
- Releases happen automatically when `develop/cycle-*` merges to `main`. Follow the conventional-commit rules so Nyx can compute the next semantic version and publish the release (see `README.md` and `CONTRIBUTING.md`).
- Documentation changes (`docs/**`, `.bmad/**`, `.claude/**`, etc.) skip automated tests; code changes must pass `./gradlew test` before merging.

## References
- Development & deployment playbook: `_bmad-output/development-deployment-guide.md`
- Architecture overview: `_bmad-output/architecture.md`
- MCP API contracts: `_bmad-output/api-contracts-core.md`
- CI summary: `docs/ci.md`
- Git workflow + branch rules: `docs/engineering/git-workflow.md`
