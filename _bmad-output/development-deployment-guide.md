# Development & Deployment Playbook — WigAI core

## Development instructions

### Prerequisites
- **Java 21 SDK** — Gradle 9+ compiles against Java 21 (see `build.gradle.kts` and the `java` block). Install via SDKMAN! or your platform package manager.
- **Gradle wrapper (`./gradlew`)** — The project relies on the bundled wrapper so you never need a global Gradle install; it already pulls in Shadow `8.3.0` and Gradle 9+ features.
- **Bitwig Studio 5.2.7+** — Required to load the extension and exercise the MCP endpoint.
- **Nyx + Conventional commits** — Releases and semantic versions are driven by Nyx (as noted in `README.md` and `CONTRIBUTING.md`); follow the conventional-commit syntax so the release automation works.

### Local workflow
1. **Run the unit suite:** `./gradlew test` (all tags except `atdd_red` and host-required scenarios run by default). Tests enforce canonical error codes and logger expectations.
2. **Package the extension:** `./gradlew build` or `./gradlew bwextension` produces `build/extensions/WigAI.bwextension`; copy that file into your Bitwig `Extensions/` directory to load the extension locally.
3. **Adjust MCP settings:** The Bitwig preference UI persists values via `PreferencesBackedConfigManager`. Defaults are `localhost` and `61169` (see `AppConstants.DEFAULT_MCP_PORT`), but Jetty always enforces loopback-only binding (`127.0.0.1` or `::1`). The MCP endpoint remains `/mcp`, so the advertised URL is `http://localhost:61169/mcp` (or normalized IPv4/IPv6 variants).
4. **Smoke test tooling:** Run `./gradlew mcpSmokeTest -PmcpHost=localhost -PmcpPort=61169` to drive the Model Context Protocol harness from Java. Set `WIGAI_SMOKE_TEST_MUTATIONS=true` to exercise mutation flags as documented in the ATDD checklist (`AR/1.1`).
5. **ATDD red-phase:** `./gradlew atddRedTest` exercises priority-one acceptance criteria before release; these tests intentionally bind to loopback and assert error-handling behaviors (see `docs/atdd-checklist-1-2-localhost-binding-defaults-preferences-guardrails.md`).
6. **Selective CI run locally:** Use `./scripts/test-changed.sh` to mimic the PR-validation workflow — it diffs against `origin/main` and runs `./gradlew test` only when code/build files change. For a full CI replica, run `./scripts/ci-local.sh`, which executes `./gradlew test` followed by `./gradlew bwextension -x test`.

## Deployment configuration

### CI/CD pipelines
- Workflows live under `.github/workflows/` and are summarized in `docs/ci.md`:
  - `pr-validation.yml` runs on PR branches and executes the reusable `build-and-test.yml`
  - `build-and-test.yml` runs `./gradlew test` and the extension build, producing `build/extensions/WigAI.bwextension`
  - `release.yml` fires on `main` and uses Nyx to derive the semantic version (Conventional Commits are mandatory)
  - `branch-policy.yml` enforces the BMAD branch naming rules; `develop/cycle-*` is treated as the canonical base for new work.
- CI uploads Gradle test reports (`build/reports/tests/test/`, `build/test-results/test/`) and the `.bwextension` artifact so reviewers can download runnable builds.

### Runtime expectations
- Jetty binds to `localhost`, `127.0.0.1`, or `::1` only; non-loopback addresses are rejected by `JettyServerManager.getBindHost(...)` and documented in `docs/traceability-…-preferences-guardrails.md`.
- The MCP servlet exposes `/mcp` with SSE and JSON payloads; the `StatusTool` in `McpServerManager` provides telemetry (`wigai_version`, transport state, selected device info).
- Environment variables:
  - `WIGAI_SMOKE_TEST_MUTATIONS` (default `false`) toggles extra mutations inside `scripts/ci-local.sh` and `mcpSmokeTest` to ensure coverage for edge cases.
  - Gradle properties such as `-PmcpHost` and `-PmcpPort` override the default values for smoke-test tooling and when running MC operations from `./gradlew mcpSmokeTest`.

## Contribution guidelines
- Follow the instructions in `CONTRIBUTING.md`: branch from the current `develop/cycle-*`, push an `implementation/*` or `docs/*` branch, and target the cycle branch instead of `main`.
- Adhere to Conventional Commits so Nyx can compute the next version automatically; breaking changes use `feat!:`, `fix!:`, or `BREAKING CHANGE:` footers.
- Read `docs/engineering/git-workflow.md` for details about branch naming, review workflows, and when to rebase vs. merge.
- Documentation-only changes (anything under `docs/`, `.bmad/`, `.claude/`, etc.) skip CI tests, but code changes must pass the standard `./gradlew test` suite before merge.
- Once your `.bwextension` is built, install it into Bitwig’s extensions directory and verify the MCP endpoint at `http://localhost:61169/mcp` before signing off on a PR.

This guide keeps the backend tooling consistent with the existing pipeline and makes it easier to spin up local development servers that mirror the GitHub Actions release process.
