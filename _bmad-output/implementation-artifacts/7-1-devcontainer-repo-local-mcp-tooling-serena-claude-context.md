# Story 7.1: devcontainer-repo-local-mcp-tooling-codegraphcontext

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI contributor,
I want a repo-local devcontainer with repo-local MCP server definitions for upstream `codegraphcontext` tool that runs inside the container,
so that onboarding is deterministic and context tooling reduces prompt token usage during implementation.

## Acceptance Criteria

1. **Given** a fresh clone of WigAI
   **When** the contributor opens the project in a Dev Container
   **Then** the container builds successfully and the workspace is ready for Java/Gradle development without manual host-only setup steps.

2. **Given** the devcontainer is running
   **When** initialization completes
   **Then** repo-local install scripts install/configure upstream `codegraphcontext` from:
   - `codegraphcontext`: `https://github.com/CodeGraphContext/CodeGraphContext`
   and pinned versions are tracked in repo-managed configuration.

3. **Given** repo-local MCP configuration is used
   **When** an MCP-capable client reads workspace MCP config
   **Then** `.vscode/mcp.json` contains server entries for:
   - existing `WigAI` HTTP endpoint
   - `codegraphcontext` (stdio)
   and the stdio entry executes upstream-installed entrypoint from the install-script workflow (not repo-local replacement wrapper server).

4. **Given** Bitwig runs on the host and tools run in-container
   **When** the containerized MCP client attempts to call WigAI
   **Then** `WigAI` connectivity is documented and validated for container networking (for example, host alias strategy instead of container-local `localhost`).

5. **Given** the MCP server entries are configured
   **When** healthcheck scripts are run inside the devcontainer
   **Then** the upstream-installed MCP server (`codegraphcontext`) starts successfully and returns a non-error handshake/metadata response.

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

- [x] Create baseline devcontainer with deterministic tooling (AC: 1, 2)
  - [x] Add `.devcontainer/devcontainer.json` with workspace settings and extension/tool recommendations relevant to WigAI.
  - [x] Add `.devcontainer/Dockerfile` (or image reference) with Java 21 + Gradle-compatible toolchain and runtime dependencies required for upstream MCP installation tooling.
  - [x] postCreateCommand in devcontainer.json performs idempotent setup steps and validates core commands.
  - [x] Add version pinning source for upstream MCP tools in `scripts/mcp/install-codegraphcontext.sh`.

- [x] Implement upstream install-script path for MCP tools (AC: 2, 3, 5)
  - [x] Add `scripts/mcp/install-codegraphcontext.sh` to install/configure upstream `codegraphcontext` in-container.
  - [x] Ensure install scripts are idempotent and consume pinned versions from repo-managed config.
  - [x] Record upstream source URLs in implementation docs and verification output.

- [x] Update repo-local MCP configuration and checks for upstream entrypoints (AC: 3, 4, 5)
  - [x] Update `.vscode/mcp.json` so `codegraphcontext` stdio commands call upstream-installed entrypoints.
  - [x] Ensure commands/paths referenced in MCP entries resolve from inside the devcontainer workspace.
  - [x] Preserve or improve `WigAI` host connectivity from container (host aliasing and documented URL strategy).
  - [x] Add healthcheck script `scripts/mcp/healthcheck.sh` to verify upstream tool startup/handshake.
  - [x] No repo-local replacement wrapper servers in canonical implementation path; using upstream entrypoints only.

- [x] Extend repo-local MCP config to Claude Code CLI and Codex CLI, and automate indexing (AC: 2, 3, 5, 7, 8)
  - [x] Add `.mcp.json` (Claude Code's own project-scoped MCP config format — distinct from `.vscode/mcp.json`, which only VS Code reads) so Claude Code auto-detects the server on open.
  - [x] Commit `mcp.json` at repo root: discovered that `codegraphcontext` itself reads this file as a project-default config source (env vars, ignore rules, tool allowlist) on every invocation from the repo root — it is not just a copy/paste artifact. Normalized its `command` field to the PATH-relative `codegraphcontext` (the wizard that generates it writes a container-specific absolute interpreter path).
  - [x] Commit `src/.cgcignore` (generated by codegraphcontext for the indexed path).
  - [x] `install-codegraphcontext.sh` now indexes `./src` and starts a background `cgc watch src --poll --sync-on-start` process so the graph stays current as files change, instead of only printing a manual command to run later.
  - [x] `install-codegraphcontext.sh` idempotently registers the server with Codex CLI via `codex mcp add` (Codex has no project-scoped MCP config; registration is global per-user in `~/.codex/config.toml`, persisted via the devcontainer's `/home/vscode` volume mount).
  - [x] Document all four MCP config surfaces (`mcp.json`, `.mcp.json`, `.vscode/mcp.json`, `~/.codex/config.toml`), the indexing/watch workflow, and a DB-lock troubleshooting case (concurrent `cgc index` + `cgc watch` against the embedded database) in `docs/engineering/devcontainer-mcp-setup.md`.

- [x] Add token-usage validation workflow and evidence capture (AC: 6)
  - Note: Token benchmarking removed from scope per course correction 2026-08-19.

- [x] Documentation and adoption guardrails (AC: 7, 8)
  - [x] Update `README.md` with devcontainer quick start and MCP tooling summary.
  - [x] Add a focused setup guide at `docs/engineering/devcontainer-mcp-setup.md` including troubleshooting for container networking to host Bitwig MCP endpoint.
  - [x] Document required environment variables and local secret injection patterns in `.env.example` without committing credentials.
  - [x] Add a "verification checklist" section in setup guide for developers to run after opening in container.

## Dev Notes

### Developer Context Section

- This story is a developer-experience and tooling story; it should not change WigAI runtime behavior or MCP tool contracts implemented in Java source.
- Current repo-local MCP config exists at `.vscode/mcp.json` and currently includes `WigAI` plus `codegraphcontext` context tooling entries.
- Story scope rescoped from `serena`/`claude-context` to `codegraphcontext` after initial implementation.
- The requested operating model is explicit:
  - MCP config must remain repo-local.
  - `codegraphcontext` must run inside the devcontainer.
  - Canonical tool is the upstream project, not repo-local replacement implementation:
    - `https://github.com/CodeGraphContext/CodeGraphContext`

### Technical Requirements

- Devcontainer must support current project build/test workflow:
  - `./gradlew test`
  - `./gradlew build`
- Tool installation for `codegraphcontext` must be reproducible via repo-local install scripts and pinned by versioned repo config.
- `.vscode/mcp.json` stdio command entries for `codegraphcontext` must target upstream-installed entrypoints.
- Repo-local replacement wrapper servers are not the canonical path.
- MCP launch commands must avoid host-specific absolute paths.
- Networking requirement:
  - Containerized MCP clients must be able to reach host Bitwig MCP endpoint reliably; document the canonical endpoint strategy.
- Healthcheck commands must return non-zero on failure so CI/local automation can gate readiness.

### Architecture Compliance

- Preserve existing layering and runtime source architecture; this story focuses on infrastructure/config/docs for development workflow.
- Do not alter MCP envelope semantics, tool APIs, or Bitwig control logic as part of this story.
- Keep changes scoped to:
  - `.devcontainer/`
  - `.vscode/mcp.json`
  - `.mcp.json` and `mcp.json` (repo-root MCP config for Claude Code and for `codegraphcontext`'s own project defaults; added to extend repo-local config coverage beyond VS Code — see Change Log 2026-08-20)
  - documentation
  - support scripts for setup/verification

### Library / Framework Requirements

- Java baseline must remain aligned with project requirements (Java 21).
- Any added runtime/package managers for MCP helper servers must use explicit version pinning and deterministic install steps.
- Document exact versions selected for:
  - `codegraphcontext` MCP server
  - any required runtime hosts (for example Node/Python/uv/pnpm/etc., if applicable)

### File Structure Requirements

- Required new/updated files (minimum expected):
  - `.devcontainer/devcontainer.json` (done)
  - `.devcontainer/Dockerfile` or equivalent image declaration (done)
  - `.vscode/mcp.json` (done)
  - `.mcp.json` (done — Claude Code project-scoped MCP config)
  - `mcp.json` (done — `codegraphcontext` project-default config, read by the tool itself)
  - `src/.cgcignore` (done — generated by `codegraphcontext` for the indexed path)
  - `scripts/mcp/install-codegraphcontext.sh` (done)
  - `README.md` (updated)
  - `docs/engineering/devcontainer-mcp-setup.md` (new)
  - `scripts/mcp/*` healthcheck or bootstrap scripts (new/updated)
  - `_bmad-output/implementation-artifacts/tests/<date>-devcontainer-mcp-token-benchmark.md` (new evidence)

### Testing Requirements

- Container provisioning tests:
  - Devcontainer opens successfully from clean state.
  - Post-create setup completes without manual intervention.
- MCP readiness tests:
  - `codegraphcontext` handshake/metadata check passes in-container via upstream-installed entrypoint.
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
- [Source: `.mcp.json`]
- [Source: `mcp.json`]
- [Source: `docs/engineering/devcontainer-mcp-setup.md`]
- [Source: `README.md`]
- [Source: `CONTRIBUTING.md`]
- [Source: `docs/reference/project-structure.md`]
- [Source: `_bmad-output/planning-artifacts/architecture.md`]
- [Source: `https://github.com/CodeGraphContext/CodeGraphContext`]

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
- 2026-08-20: While documenting indexing usage, discovered `codegraphcontext` reads a repo-root `mcp.json` as its own project-default config source (env vars, ignore rules, tool allowlist) on every invocation — this was previously an untracked byproduct file and is now committed deliberately.
- 2026-08-20: Found and fixed a database-lock race during manual testing: running `cgc index` while the background watcher already holds the embedded database fails. Resolved by relying solely on `cgc watch --sync-on-start` (which performs the initial index/resync itself) instead of running `index` and `watch` back to back.
- 2026-08-20: Found and fixed a PID-reuse false-positive in the watcher idempotency check: because this repo's own paths/scripts frequently contain the substring "codegraphcontext" (including the install script's own shell invocation), a loose `grep` against `/proc/<pid>/cmdline` could match an unrelated process. Fixed with an exact argv-token match.
- 2026-08-20: Verified live end-to-end in the devcontainer — fresh run, idempotent re-run, PID-reuse scenario, and Codex registration/deregistration — before committing.

### Change Log

- 2026-02-22: Initial implementation-ready story created.
- 2026-02-22: Correct Course approved; story contract updated to upstream install-script approach and status set to `in-progress`.
- 2026-08-19: Story rescoped from `serena`/`claude-context` to `codegraphcontext` as canonical tool.
- 2026-08-20: Added `.mcp.json` (Claude Code project MCP config) and committed `mcp.json` (codegraphcontext's own project-default config) at repo root; extended repo-local MCP config coverage beyond `.vscode/mcp.json` to Claude Code CLI and, via `~/.codex/config.toml` registration, Codex CLI. `install-codegraphcontext.sh` now indexes `./src` and runs a background `cgc watch` process instead of only printing a manual next-step command. Documented all of this in `docs/engineering/devcontainer-mcp-setup.md` and `README.md`.

### File List

- `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context.md` (new)
- `.mcp.json` (new, 2026-08-20)
- `mcp.json` (new, 2026-08-20)
- `src/.cgcignore` (new, 2026-08-20)
- `scripts/mcp/install-codegraphcontext.sh` (updated, 2026-08-20 — auto-index/watch, Codex registration)
- `docs/engineering/devcontainer-mcp-setup.md` (updated, 2026-08-20)
- `README.md` (updated, 2026-08-20)
