# Story 7.1: devcontainer-repo-local-mcp-tooling-serena-claude-context

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI contributor,
I want a repo-local devcontainer with repo-local MCP server definitions for `serena` and `claude-context` that run inside the container,
so that onboarding is deterministic and context tooling reduces prompt token usage during implementation.

## Acceptance Criteria

1. **Given** a fresh clone of WigAI
   **When** the contributor opens the project in a Dev Container
   **Then** the container builds successfully and the workspace is ready for Java/Gradle development without manual host-only setup steps.

2. **Given** the devcontainer is running
   **When** initialization completes
   **Then** all runtime dependencies required by `serena` MCP and `claude-context` MCP are installed in-container and version-pinned in repo-managed configuration.

3. **Given** repo-local MCP configuration is used
   **When** an MCP-capable client reads workspace MCP config
   **Then** `.vscode/mcp.json` contains server entries for:
   - existing `bitwigMcp` HTTP endpoint
   - `serena` (stdio)
   - `claude-context` (stdio)
   and both new entries execute commands that run inside the devcontainer.

4. **Given** Bitwig runs on the host and tools run in-container
   **When** the containerized MCP client attempts to call WigAI
   **Then** `bitwigMcp` connectivity is documented and validated for container networking (for example, host alias strategy instead of container-local `localhost`).

5. **Given** the MCP server entries are configured
   **When** healthcheck scripts are run inside the devcontainer
   **Then** each MCP server (`serena`, `claude-context`) starts successfully and returns a non-error handshake/metadata response.

6. **Given** a baseline prompt-only workflow and an MCP-assisted workflow are run for representative repo tasks
   **When** token usage is measured with the same tasks and acceptance boundaries
   **Then** results are recorded in a repo artifact showing reduced input-token usage with the new MCP context tooling (or clear analysis if reduction target is missed).

7. **Given** this setup is intended for team reuse
   **When** documentation is reviewed
   **Then** setup, troubleshooting, and usage guidance exists in-repo, including how to run in-container MCP health checks and how to execute the token-usage benchmark.

8. **Given** security and repo hygiene requirements
   **When** the implementation is complete
   **Then** no secrets are committed, required environment variables are documented, and local override guidance is provided for developer-specific credentials.

## Tasks / Subtasks

- [ ] Create baseline devcontainer with deterministic tooling (AC: 1, 2)
  - [ ] Add `.devcontainer/devcontainer.json` with workspace settings and extension/tool recommendations relevant to WigAI.
  - [ ] Add `.devcontainer/Dockerfile` (or image reference) with Java 21 + Gradle-compatible toolchain and runtime dependencies needed by MCP helper servers.
  - [ ] Add `.devcontainer/postCreate.sh` (or equivalent) to perform idempotent setup steps and validate core commands.
  - [ ] Add version pinning source for MCP helper tools (for example `.devcontainer/mcp/versions.env`).

- [ ] Add repo-local MCP server definitions for container execution (AC: 3, 4, 5)
  - [ ] Update `.vscode/mcp.json` to include `serena` and `claude-context` server entries with stdio launch definitions.
  - [ ] Ensure commands/paths referenced in MCP entries resolve from inside the devcontainer workspace.
  - [ ] Preserve or improve `bitwigMcp` host connectivity from container (host aliasing and documented URL strategy).
  - [ ] Add lightweight server startup/handshake checks under `scripts/` for repeatable validation.

- [ ] Add token-usage validation workflow and evidence capture (AC: 6)
  - [ ] Define 2-3 representative developer tasks where context retrieval is frequently needed.
  - [ ] Capture baseline token usage without `serena`/`claude-context` assistance.
  - [ ] Capture token usage with MCP-assisted workflow enabled.
  - [ ] Save benchmark inputs/results to a dated artifact under `_bmad-output/implementation-artifacts/tests/`.
  - [ ] Document interpretation rules and pass/fail threshold in the benchmark artifact.

- [ ] Documentation and adoption guardrails (AC: 7, 8)
  - [ ] Update `README.md` with devcontainer quick start and MCP tooling summary.
  - [ ] Add a focused setup guide (for example `docs/engineering/devcontainer-mcp-setup.md`) including troubleshooting for container networking to host Bitwig MCP endpoint.
  - [ ] Document required environment variables and local secret injection patterns without committing credentials.
  - [ ] Add a short "verification checklist" section developers can run after opening in container.

## Dev Notes

### Developer Context Section

- This story is a developer-experience and tooling story; it should not change WigAI runtime behavior or MCP tool contracts implemented in Java source.
- Current repo-local MCP config exists at `.vscode/mcp.json` and currently includes only `bitwigMcp`.
- The requested operating model is explicit:
  - MCP config must remain repo-local.
  - `serena` and `claude-context` must run inside the devcontainer.

### Technical Requirements

- Devcontainer must support current project build/test workflow:
  - `./gradlew test`
  - `./gradlew build`
- Tool installation for `serena` and `claude-context` must be reproducible and pinned by versioned repo config.
- MCP launch commands for both tools must avoid host-specific absolute paths.
- Networking requirement:
  - Containerized MCP clients must be able to reach host Bitwig MCP endpoint reliably; document the canonical endpoint strategy.
- Healthcheck commands must return non-zero on failure so CI/local automation can gate readiness.

### Architecture Compliance

- Preserve existing layering and runtime source architecture; this story focuses on infrastructure/config/docs for development workflow.
- Do not alter MCP envelope semantics, tool APIs, or Bitwig control logic as part of this story.
- Keep changes scoped to:
  - `.devcontainer/`
  - `.vscode/mcp.json`
  - documentation
  - support scripts for setup/verification

### Library / Framework Requirements

- Java baseline must remain aligned with project requirements (Java 21).
- Any added runtime/package managers for MCP helper servers must use explicit version pinning and deterministic install steps.
- Document exact versions selected for:
  - `serena` MCP server
  - `claude-context` MCP server
  - any required runtime hosts (for example Node/Python/uv/pnpm/etc., if applicable)

### File Structure Requirements

- Required new/updated files (minimum expected):
  - `.devcontainer/devcontainer.json` (new)
  - `.devcontainer/Dockerfile` or equivalent image declaration (new/updated)
  - `.devcontainer/postCreate.sh` (new)
  - `.vscode/mcp.json` (updated)
  - `README.md` (updated)
  - `docs/engineering/devcontainer-mcp-setup.md` (new)
  - `scripts/mcp/*` healthcheck or bootstrap scripts (new)
  - `_bmad-output/implementation-artifacts/tests/<date>-devcontainer-mcp-token-benchmark.md` (new evidence)

### Testing Requirements

- Container provisioning tests:
  - Devcontainer opens successfully from clean state.
  - Post-create setup completes without manual intervention.
- MCP readiness tests:
  - `serena` handshake/metadata check passes in-container.
  - `claude-context` handshake/metadata check passes in-container.
  - Host Bitwig MCP endpoint reachability from container is validated/documented.
- Benchmark tests:
  - Baseline vs MCP-assisted token measurements are recorded for selected tasks.
  - Result analysis includes objective pass/fail criteria and interpretation notes.

### Project Structure Notes

- Keep all implementation assets repo-local and portable across machines.
- Avoid introducing machine-specific absolute paths in devcontainer or MCP definitions.
- Keep benchmark artifacts in `_bmad-output/implementation-artifacts/tests/` to preserve auditability.

### References

- [Source: `.vscode/mcp.json`]
- [Source: `README.md`]
- [Source: `CONTRIBUTING.md`]
- [Source: `docs/reference/project-structure.md`]
- [Source: `_bmad-output/planning-artifacts/architecture.md`]

## Dev Agent Record

### Agent Model Used

- GPT-5 Codex (SM create-story via chat mode adaptation)

### Debug Log References

- `_bmad/core/tasks/workflow.xml`
- `_bmad/bmm/workflows/4-implementation/create-story/workflow.yaml`
- `_bmad/bmm/workflows/4-implementation/create-story/instructions.xml`

### Completion Notes List

- Story created from user-requested backlog item in SM chat mode.
- Scope constrained to repo-local MCP config and in-container execution for `serena` + `claude-context`.
- Story status initialized as `ready-for-dev`.

### Change Log

- 2026-02-22: Initial implementation-ready story created.

### File List

- `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context.md` (new)
