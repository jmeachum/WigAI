# Story 7.1: devcontainer-repo-local-mcp-tooling-codegraphcontext

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI contributor,
I want a repo-local devcontainer with a repo-local MCP server definition for upstream `codegraphcontext` running inside the container,
so that onboarding is deterministic and AI assistants can answer structural questions about the codebase from a code graph instead of re-reading source into the prompt.

**Upstream source:** <https://github.com/CodeGraphContext/CodeGraphContext>

**Canonical-tool rule:** upstream install only. No repo-local replacement or wrapper servers. Established by `sprint-change-proposal-2026-02-22.md` and still binding.

## Acceptance Criteria

1. **Given** a fresh clone of WigAI
   **When** the contributor opens the project in a Dev Container
   **Then** the container builds successfully and the workspace is ready for Java/Gradle development without manual host-only setup steps.

2. **Given** the `node:20-slim` base image is Debian 12 (bookworm), whose apt provides only Python 3.11 and no `python3.12` candidate
   **When** the devcontainer is built
   **Then** Python **3.12** is provisioned via the `ghcr.io/devcontainers/features/python:1` devcontainer feature (matching how Java 21 is already provisioned), `pip` is available for that interpreter, and the image keeps its non-root user with no privilege-escalation helper installed.

3. **Given** the devcontainer is running
   **When** the repo-local install script executes
   **Then** upstream `codegraphcontext` is installed from its canonical source at a pinned version recorded in repo-managed configuration, and no repo-local replacement or wrapper server is introduced.

4. **Given** Python 3.12 is provisioned per AC 2
   **When** `codegraphcontext` selects its graph database backend
   **Then** the **upstream default** (FalkorDB Lite on Unix with Python 3.12+) is used with no backend override, no external database service is required to run alongside the container, and the install script fails loudly rather than silently falling back if the interpreter is older than 3.12.

5. **Given** repo-local MCP configuration is used
   **When** an MCP-capable client reads workspace MCP config
   **Then** `.vscode/mcp.json` contains the existing `WigAI` HTTP entry plus a `codegraphcontext` stdio entry executing the upstream-installed entrypoint (`codegraphcontext mcp start`), and contains no entries pointing at scripts that do not exist.

6. **Given** Bitwig runs on the host and tools run in-container
   **When** the containerized MCP client attempts to call WigAI
   **Then** `WigAI` connectivity is documented and validated for container networking (host alias strategy such as `host.docker.internal`, not container-local `localhost`).

7. **Given** the tool is installed and configured
   **When** the repository is indexed inside the container
   **Then** indexing completes over the Java sources in `src/` and the result is queryable (for example via `codegraphcontext list`/`stats`).

8. **Given** the MCP server entry is configured
   **When** healthcheck scripts are run inside the devcontainer
   **Then** `codegraphcontext` starts successfully and returns a non-error handshake/metadata response.

9. **Given** indexing state and graph data are generated artifacts
   **When** the implementation is complete
   **Then** those artifacts are git-ignored, `~/.codegraphcontext/.env` is never committed, and any environment variables the chosen backend requires (for example `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`) are documented with local-override guidance and no committed secrets.

10. **Given** this setup is intended for team reuse
    **When** documentation is reviewed
    **Then** setup, troubleshooting, and usage guidance exists in-repo, including the upstream source reference, install-script execution, backend choice, and the indexing/watch step and when to re-index.

> AC 10 (token-usage benchmark) from the 2026-08-19 rescope was removed, and this AC 7 was relaxed from a scripted symbol-resolution check to queryable-indexing verification, per `sprint-change-proposal-2026-08-20.md`.

## Tasks / Subtasks

- [x] Establish the devcontainer baseline (AC 1)
  - [x] Confirm the container builds from a fresh clone and provides Java 21 and Gradle for the existing build.
  - [x] Verify `./gradlew test` runs in-container.

- [x] Provision Python 3.12 (AC 2)
  - [x] Added the `ghcr.io/devcontainers/features/python:1` feature at version `3.12` to `.devcontainer/devcontainer.json`.
  - [x] Chosen over apt (bookworm has no `python3.12` candidate), a base-image swap, and `uv`, because it matches the existing Java 21 feature pattern and ships `pip`.
  - [x] Verified in a built container: `python3 --version` reports 3.12.

- [x] Confirm the graph backend (AC 4)
  - [x] Upstream default (FalkorDB Lite) is used, unlocked by the 3.12 provisioning above. No override, no external service.
  - [x] The install script hard-fails below Python 3.12 so a silent fallback to a different backend cannot happen.

- [x] Write `scripts/mcp/install-codegraphcontext.sh` (AC 3)
  - [x] Installs pinned `codegraphcontext==0.6.3` (overridable via `CGC_VERSION`), idempotent on re-run.
  - [x] Guards on Python major/minor and on `pip` availability, with actionable errors.
  - [x] Executed successfully in-container; re-run confirmed idempotent (pip install skipped when already at pin).

- [x] Wire the MCP config (AC 5)
  - [x] Stale `serena`/`claude-context` wrapper entries removed.
  - [x] `codegraphcontext` stdio entry added to `.vscode/mcp.json`, invoking the upstream entrypoint `codegraphcontext mcp start` directly with no wrapper script.
  - [x] Verified against a running server (`codegraphcontext mcp start` starts cleanly; also exercised via `healthcheck.sh`).

- [x] Validate container-to-host connectivity (AC 6)
  - [x] `WigAI` HTTP entry uses the `host.docker.internal` alias; documented with Linux workarounds (host-gateway config, `WIGAI_HOST_IP`) in `docs/engineering/devcontainer-mcp-setup.md`.

- [x] Index the repository and confirm it's queryable (AC 7 — relaxed 2026-08-20, see note above)
  - [x] `codegraphcontext index src` (via the install script's `cgc watch --sync-on-start`, which performs the initial index itself) runs over the Java sources in `src/`.
  - [x] Confirmed queryable: the indexing run reported 1262 function nodes, 120 class nodes, and 10232 CALLS edges over 101 `.java` files; `codegraphcontext list`/`stats` documented as the ongoing way to inspect the graph.

- [x] Add the healthcheck script (AC 8)
  - [x] `scripts/mcp/healthcheck.sh` verifies a non-error handshake from `codegraphcontext` inside the container.

- [x] Handle hygiene and secrets (AC 9)
  - [x] Git-ignored the index/graph artifacts (`.codegraphcontext/`, `*.kuzu/`, `falkordb-lite.db`) and local env files (`.env`, `.env.local`, `.env.*.local`).
  - [x] FalkorDB Lite (the chosen backend) requires no external credentials; `.env.example` documents the optional overrides that do apply (`WIGAI_MCP_URL`, `CGC_VERSION`, `WIGAI_HOST_IP`) with local-override guidance and an explicit no-secrets-committed note.

- [x] Write the documentation (AC 10)
  - [x] `docs/engineering/devcontainer-mcp-setup.md` covers install, backend, indexing/watch, health checks, and troubleshooting (including a DB-lock case found during testing).

- [x] Extend repo-local MCP config to Claude Code CLI and Codex CLI, and automate indexing/watch (beyond AC 5 — additional value delivered this session, not contract-required)
  - [x] Add `.mcp.json` (Claude Code's own project-scoped MCP config format — distinct from `.vscode/mcp.json`, which only VS Code reads) so Claude Code auto-detects the server on open.
  - [x] Commit `mcp.json` at repo root: discovered that `codegraphcontext` itself reads this file as a project-default config source (env vars, ignore rules, tool allowlist) on every invocation from the repo root — it is not just a copy/paste artifact. Normalized its `command` field to the PATH-relative `codegraphcontext` (the wizard that generates it writes a container-specific absolute interpreter path).
  - [x] Commit `src/.cgcignore` (generated by codegraphcontext for the indexed path).
  - [x] `install-codegraphcontext.sh` now starts a background `cgc watch src --poll --sync-on-start` process so the graph stays current as files change, instead of only printing a manual command to run later.
  - [x] `install-codegraphcontext.sh` idempotently registers the server with Codex CLI via `codex mcp add` (Codex has no project-scoped MCP config; registration is global per-user in `~/.codex/config.toml`, persisted via the devcontainer's `/home/vscode` volume mount).
  - [x] Added the `ghcr.io/devcontainers/features/github-cli:1` devcontainer feature so `gh` (needed to open PRs from inside the container) is available after a rebuild — unrelated to MCP tooling but provisioned alongside this work.

## Dev Notes

### Developer Context Section

WigAI is a 101-file Java codebase under `io.github.fabb.wigai` with a layered structure (`server` -> `mcp` -> `features` -> `bitwig`, over a shared `common`). Structural questions — who calls `BitwigApiFacade`, which tools touch `TrackTargetingContract`, what breaks if an error code changes — currently cost a lot of prompt tokens because the answer requires reading many files. A code graph answers those directly, which is the point of this story.

Upstream reports support for 23 languages including Java and Kotlin, which covers both `src/` and the Gradle Kotlin DSL build scripts.

### Technical Requirements

- Upstream install only; no wrappers. See the canonical-tool rule above.
- The image's own Python was 3.11.2 with no `pip`, `pip3`, `uv`, or `pipx` on `PATH`. The Dockerfile installs Python deliberately as a stdlib-only dependency for ECC tooling. Rather than adding pip to that interpreter, the devcontainer now provisions a separate managed Python 3.12 via feature, leaving the stdlib-only contract of the base image intact.
- `node:20-slim` is Debian 12 (bookworm). `apt-cache policy python3.12` returns no candidate there, which is why apt was not a viable route.
- Upstream documents Python 3.10-3.14 support, `pip install codegraphcontext` as the primary install path, and `codegraphcontext mcp start` as the stdio MCP entrypoint. Pinned at `0.6.3`.
- Backend: upstream default (FalkorDB Lite), which the 3.12 provisioning makes available. KuzuDB, Neo4j, LadybugDB, and Nornic DB were the alternatives; none is needed, and avoiding an external database service keeps onboarding to a single container.
- Bitwig runs on the host, not in the container. The MCP endpoint it exposes is reachable at the host alias on port `61169`.
- `codegraphcontext` reads a repo-root `mcp.json` as its own project-default config source (env vars, ignore rules, tool allowlist) on every invocation from the repo root, at lowest precedence below `~/.codegraphcontext/.env` — discovered while wiring up Claude Code/Codex, not documented up front by upstream in an obvious place.
- Running `cgc index` while the background watcher already holds the embedded database fails with a lock error; only one of them may hold the database at a time. `cgc watch --sync-on-start` performs the initial index/resync itself, so the install script never runs both back to back.

### Architecture Compliance

This story touches no extension source code. It changes `.devcontainer/`, `scripts/mcp/`, `.vscode/mcp.json`, `.mcp.json` and `mcp.json` (repo-root MCP config for Claude Code and for `codegraphcontext`'s own project defaults — added to extend repo-local config coverage beyond VS Code), `.gitignore`, and documentation only. Nothing here may alter WigAI runtime behavior or the MCP tool contracts.

### Library / Framework Requirements

- `codegraphcontext==0.6.3` (upstream, pinned in `scripts/mcp/install-codegraphcontext.sh`)
- FalkorDB Lite (upstream default graph database backend; requires `redis-server`, provisioned in `.devcontainer/Dockerfile`)
- Python 3.12 (`ghcr.io/devcontainers/features/python:1`), the package installer for `codegraphcontext`
- GitHub CLI (`ghcr.io/devcontainers/features/github-cli:1`) — unrelated to MCP tooling but added this session for PR creation from inside the container

### File Structure Requirements

Touch points:

- `.devcontainer/Dockerfile` and `.devcontainer/devcontainer.json` (Python 3.12 + GitHub CLI features)
- `scripts/mcp/install-codegraphcontext.sh` (install, index, watch, Codex registration)
- `scripts/mcp/healthcheck.sh`
- `.vscode/mcp.json` (VS Code)
- `.mcp.json` (Claude Code project-scoped MCP config)
- `mcp.json` (`codegraphcontext`'s own project-default config, read by the tool itself)
- `src/.cgcignore` (generated by `codegraphcontext` for the indexed path)
- `.gitignore` (index/graph artifacts, local env files)
- `.env.example` (documented optional overrides)
- `docs/engineering/devcontainer-mcp-setup.md`
- `README.md`

### Testing Requirements

No JUnit coverage applies. Verification is:

- The healthcheck script returns a successful handshake for `codegraphcontext`.
- Indexing `src/` completes and is queryable (AC 7, relaxed 2026-08-20 — see the note under Acceptance Criteria).
- The background watcher picks up file changes (verified this session by editing a tracked file and confirming the watcher's sync log).
- The install script is idempotent on re-run (verified this session: fresh run, then a second run correctly detected the already-running watcher via an exact `/proc/<pid>/cmdline` argv match and the existing Codex registration, without restarting either).
- `./scripts/check-story-status.sh` and the existing CI jobs remain green.

### Project Structure Notes

`.gitignore` previously excluded all of `.vscode/`, which made a repo-managed `.vscode/mcp.json` impossible to commit. On 2026-08-19 the rule was changed to ignore `.vscode/*` with a `!.vscode/mcp.json` exception, so the shared MCP config is now trackable while per-developer files such as `settings.json` stay ignored. A plain `!` negation under an excluded directory does not work in git; the directory contents must be excluded instead.

The `.devcontainer/` present in the working tree at the time this story was rescoped is an ECC bootstrap container (`node:20-slim` plus `ecc-universal`), not a deliverable of this story.

### References

- [Source: `.vscode/mcp.json`]
- [Source: `.mcp.json`]
- [Source: `mcp.json`]
- [Source: `docs/engineering/devcontainer-mcp-setup.md`]
- [Source: `README.md`]
- Upstream project: <https://github.com/CodeGraphContext/CodeGraphContext>
- Scope replacement: `_bmad-output/planning-artifacts/sprint-change-proposal-2026-08-19.md`
- AC 7/AC 10 descope: `_bmad-output/planning-artifacts/sprint-change-proposal-2026-08-20.md`
- Canonical-tool rule: `_bmad-output/planning-artifacts/sprint-change-proposal-2026-02-22.md`
- Epic 7 definition: `_bmad-output/planning-artifacts/epics.md`
- Status authority rules: `docs/engineering/story-status-authority.md`

## Dev Agent Record

### Agent Model Used

- GPT-5 Codex (original create-story workflow)
- Claude Opus 5 (2026-08-19 rescope)
- Claude Sonnet 5 (2026-08-20: implementation completion — Claude Code/Codex MCP config, auto-indexing/watch, AC 7/AC 10 descope, PR)

### Debug Log References

- Upstream install/config facts read from the CodeGraphContext repository README on 2026-08-19.
- Container facts verified in-container: `python3 --version` -> 3.11.2 before provisioning; no `pip`, `pip3`, `uv`, or `pipx` on `PATH` before provisioning.
- 2026-08-20: `codegraphcontext` CLI help output (`cgc mcp --help`, `cgc watch --help`, `cgc config --help`) and its `cli/main.py`/`cli/config_manager.py`/`cli/setup_wizard.py` source, read in-container to confirm the `mcp.json` project-config precedence and the `cgc watch --sync-on-start` behavior before relying on either.

### Completion Notes List

- Story created from user-requested backlog item in SM chat mode.
- Correct-course decision applied 2026-02-22: canonical approach switched to upstream tools via repo-local install scripts; local wrapper servers classified as superseded.
- Scope replacement applied 2026-08-19: `serena` and `claude-context` removed from Epic 7 entirely and replaced by `codegraphcontext`. Story 7.2, created earlier the same day for `codegraphcontext`, was withdrawn and its acceptance criteria absorbed here.
- No implementation existed against any version of this story as of the 2026-08-19 rescope, so that rescope unwound nothing.
- 2026-08-20: While documenting indexing usage, discovered `codegraphcontext` reads a repo-root `mcp.json` as its own project-default config source (env vars, ignore rules, tool allowlist) on every invocation — this was previously an untracked byproduct file and is now committed deliberately.
- 2026-08-20: Found and fixed a database-lock race during manual testing: running `cgc index` while the background watcher already holds the embedded database fails. Resolved by relying solely on `cgc watch --sync-on-start` (which performs the initial index/resync itself) instead of running `index` and `watch` back to back.
- 2026-08-20: Found and fixed a PID-reuse false-positive in the watcher idempotency check: because this repo's own paths/scripts frequently contain the substring "codegraphcontext" (including the install script's own shell invocation), a loose `grep` against `/proc/<pid>/cmdline` could match an unrelated process. Fixed with an exact argv-token match.
- 2026-08-20: Verified live end-to-end in the devcontainer — fresh run, idempotent re-run, PID-reuse scenario, a real file-change pickup by the watcher, and Codex registration/deregistration — before committing.
- 2026-08-20: Product owner determined the token-usage benchmark (old AC 10) and the scripted symbol-resolution check (old AC 7) are not valuable at this time; descoped per `sprint-change-proposal-2026-08-20.md`. Every remaining acceptance criterion is implemented and verified, so Status moves to `review`.
- 2026-08-20: Reconciling this branch with `develop/cycle-2` (which had independently renamed this file and rewritten its acceptance criteria via a separate planning branch, PR #45) surfaced the AC 7/AC 10 gap above — this branch's prior copy of the story incorrectly claimed the benchmark had already been removed from scope; it had not, until this decision.

### Change Log

- 2026-02-22: Initial implementation-ready story created.
- 2026-02-22: Correct Course approved; story contract updated to upstream install-script approach and status set to `in-progress`.
- 2026-08-19: Status header (`ready-for-dev`) and tracker (`backlog`) reconciled to `in-progress`, the value the 2026-02-22 correction approved. Both had been missed when that correction was applied.
- 2026-08-19: Open technical decisions settled by the product owner: provision Python 3.12+ and use the upstream default graph backend. AC 2 and AC 4 rewritten from open questions into fixed constraints.
- 2026-08-19: Scope replaced. `serena` and `claude-context` removed; `codegraphcontext` adopted as the single context tool. Story renamed from `7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context` and Story 7.2 withdrawn into this one. Acceptance criteria grew from 8 to 11. Status remains `in-progress`.
- 2026-08-20: Implemented Python 3.12 provisioning, `scripts/mcp/install-codegraphcontext.sh`, and the `.vscode/mcp.json` `codegraphcontext` entry on `implementation/story-7-1`; verified in a rebuilt container.
- 2026-08-20: Added `.mcp.json` (Claude Code project MCP config) and committed `mcp.json` (codegraphcontext's own project-default config) at repo root; extended repo-local MCP config coverage beyond `.vscode/mcp.json` to Claude Code CLI and, via `~/.codex/config.toml` registration, Codex CLI. `install-codegraphcontext.sh` now indexes `./src` and runs a background `cgc watch` process instead of only printing a manual next-step command. Documented all of this in `docs/engineering/devcontainer-mcp-setup.md` and `README.md`.
- 2026-08-20: AC 10 (token-usage benchmark) removed and AC 7 relaxed (scripted symbol-resolution check dropped in favor of queryable-indexing verification), per `sprint-change-proposal-2026-08-20.md`. Status moved to `review`.

### File List

- `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext.md` (renamed from `...-serena-claude-context.md`, 2026-08-19; content updated 2026-08-20)
- `_bmad-output/planning-artifacts/sprint-change-proposal-2026-08-19.md` (new)
- `_bmad-output/planning-artifacts/sprint-change-proposal-2026-08-20.md` (new)
- `_bmad-output/planning-artifacts/epics.md` (updated — AC 7/AC 10 descope)
- `.devcontainer/devcontainer.json` (updated — Python 3.12 and GitHub CLI features)
- `.devcontainer/Dockerfile` (Java 21 + Python build deps + redis-server, non-root, no privilege-escalation helper)
- `.devcontainer/devcontainer-lock.json` (regenerated — feature digests)
- `scripts/mcp/install-codegraphcontext.sh` (new — install, index, watch, Codex registration)
- `scripts/mcp/healthcheck.sh` (new)
- `.vscode/mcp.json` (updated — `codegraphcontext` stdio entry)
- `.mcp.json` (new — Claude Code project MCP config)
- `mcp.json` (new — `codegraphcontext` project-default config)
- `src/.cgcignore` (new)
- `.gitignore` (updated — CodeGraphContext generated artifacts, local env files, `.vscode/mcp.json` exception)
- `.env.example` (new — documented optional overrides)
- `docs/engineering/devcontainer-mcp-setup.md` (new)
- `README.md` (updated)
