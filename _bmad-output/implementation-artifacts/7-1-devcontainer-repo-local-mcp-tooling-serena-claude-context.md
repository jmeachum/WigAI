# Story 7.1: devcontainer-repo-local-mcp-tooling-serena-claude-context

Status: in-progress

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI contributor,
I want a repo-local devcontainer with repo-local MCP server definitions for upstream `serena` and `claude-context` tools that run inside the container,
so that onboarding is deterministic and context tooling reduces prompt token usage during implementation.

## Acceptance Criteria

1. **Given** a fresh clone of WigAI
   **When** the contributor opens the project in a Dev Container
   **Then** the container builds successfully and the workspace is ready for Java/Gradle development without manual host-only setup steps.

2. **Given** the devcontainer is running
   **When** initialization completes
   **Then** repo-local install scripts install/configure upstream tools from:
   - `serena`: `https://github.com/oraios/serena`
   - `claude-context`: `https://github.com/zilliztech/claude-context`
   and pinned versions are tracked in repo-managed configuration.

3. **Given** repo-local MCP configuration is used
   **When** an MCP-capable client reads workspace MCP config
   **Then** `.vscode/mcp.json` contains server entries for:
   - existing `WigAI` HTTP endpoint
   - `serena` (stdio)
   - `claude-context` (stdio)
   and both stdio entries execute upstream-installed entrypoints from the install-script workflow (not repo-local replacement wrapper servers).

4. **Given** Bitwig runs on the host and tools run in-container
   **When** the containerized MCP client attempts to call WigAI
   **Then** `WigAI` connectivity is documented and validated for container networking (for example, host alias strategy instead of container-local `localhost`).

5. **Given** the MCP server entries are configured
   **When** healthcheck scripts are run inside the devcontainer
   **Then** each upstream-installed MCP server (`serena`, `claude-context`) starts successfully and returns a non-error handshake/metadata response.

6. **Given** a baseline prompt-only workflow and an MCP-assisted workflow are run for representative repo tasks
   **When** token usage is measured with the same tasks and acceptance boundaries
   **Then** results are recorded in a repo artifact showing reduced input-token usage with the new MCP context tooling (or clear analysis if reduction target is missed).

7. **Given** this setup is intended for team reuse
   **When** documentation is reviewed
   **Then** setup, troubleshooting, and usage guidance exists in-repo, including upstream source references, install-script execution, in-container MCP health checks, and token-usage benchmark steps.

8. **Given** security and repo hygiene requirements
   **When** the implementation is complete
   **Then** no secrets are committed, required environment variables are documented, and local override guidance is provided for developer-specific credentials.

## Tasks / Subtasks

- [ ] Create baseline devcontainer with deterministic tooling (AC: 1, 2)
  - [ ] Add `.devcontainer/devcontainer.json` with workspace settings and extension/tool recommendations relevant to WigAI.
  - [ ] Add `.devcontainer/Dockerfile` (or image reference) with Java 21 + Gradle-compatible toolchain and runtime dependencies required for upstream MCP installation tooling.
  - [ ] Add `.devcontainer/postCreate.sh` (or equivalent) to perform idempotent setup steps and validate core commands.
  - [ ] Add version pinning source for upstream MCP tools (for example `.devcontainer/mcp/versions.env`).

- [ ] Implement upstream install-script path for MCP tools (AC: 2, 3, 5)
  - [ ] Add `scripts/mcp/install-serena.sh` to install/configure upstream `serena` in-container.
  - [ ] Add `scripts/mcp/install-claude-context.sh` to install/configure upstream `claude-context` in-container.
  - [ ] Ensure install scripts are idempotent and consume pinned versions from repo-managed config.
  - [ ] Record upstream source URLs in implementation docs and verification output.

- [ ] Update repo-local MCP configuration and checks for upstream entrypoints (AC: 3, 4, 5)
  - [ ] Update `.vscode/mcp.json` so `serena` and `claude-context` stdio commands call upstream-installed entrypoints.
  - [ ] Ensure commands/paths referenced in MCP entries resolve from inside the devcontainer workspace.
  - [ ] Preserve or improve `WigAI` host connectivity from container (host aliasing and documented URL strategy).
  - [ ] Update healthcheck scripts to verify upstream tool startup/handshake.
  - [ ] Remove or deprecate repo-local replacement wrapper servers from canonical implementation path.

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
- Current repo-local MCP config exists at `.vscode/mcp.json` and currently includes `WigAI` plus context tooling entries.
- Post-revert gap: `.vscode/mcp.json` still references deleted local wrapper script paths for `serena`/`claude-context`; this must be corrected as part of this story's upstream-install implementation.
- The requested operating model is explicit:
  - MCP config must remain repo-local.
  - `serena` and `claude-context` must run inside the devcontainer.
  - Canonical tools are upstream projects, not repo-local replacement implementations:
    - `https://github.com/oraios/serena`
    - `https://github.com/zilliztech/claude-context`

### Technical Requirements

- Devcontainer must support current project build/test workflow:
  - `./gradlew test`
  - `./gradlew build`
- Tool installation for `serena` and `claude-context` must be reproducible via repo-local install scripts and pinned by versioned repo config.
- `.vscode/mcp.json` stdio command entries for `serena` and `claude-context` must target upstream-installed entrypoints.
- Repo-local replacement wrapper servers are not the canonical path after this course correction.
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
  - `scripts/mcp/install-serena.sh` (new)
  - `scripts/mcp/install-claude-context.sh` (new)
  - `README.md` (updated)
  - `docs/engineering/devcontainer-mcp-setup.md` (new)
  - `scripts/mcp/*` healthcheck or bootstrap scripts (new/updated)
  - `_bmad-output/implementation-artifacts/tests/<date>-devcontainer-mcp-token-benchmark.md` (new evidence)

### Testing Requirements

- Container provisioning tests:
  - Devcontainer opens successfully from clean state.
  - Post-create setup completes without manual intervention.
- MCP readiness tests:
  - `serena` handshake/metadata check passes in-container via upstream-installed entrypoint.
  - `claude-context` handshake/metadata check passes in-container via upstream-installed entrypoint.
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
- [Source: `https://github.com/oraios/serena`]
- [Source: `https://github.com/zilliztech/claude-context`]

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
- Correct-course decision applied: canonical approach switched to upstream `serena`/`claude-context` via repo-local install scripts.
- Story reopened to `in-progress` pending upstream-install implementation and validation.

### Change Log

- 2026-02-22: Initial implementation-ready story created.
- 2026-02-22: Correct Course approved; story contract updated to upstream install-script approach and status set to `in-progress`.
- 2026-08-19: Status header (`ready-for-dev`) and tracker (`backlog`) reconciled to `in-progress`, the value the 2026-02-22 correction approved. Both had been missed when that correction was applied.

### File List

- `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context.md` (new)
