# Story 7.1: devcontainer-repo-local-mcp-tooling-codegraphcontext

Status: in-progress

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
   **Then** indexing completes over the Java sources in `src/` and the result is queryable, verified by a check that resolves known symbols (for example `WigAIExtension`, `BitwigApiFacade`, `McpServerManager`) and their relationships.

8. **Given** the MCP server entry is configured
   **When** healthcheck scripts are run inside the devcontainer
   **Then** `codegraphcontext` starts successfully and returns a non-error handshake/metadata response.

9. **Given** indexing state and graph data are generated artifacts
   **When** the implementation is complete
   **Then** those artifacts are git-ignored, `~/.codegraphcontext/.env` is never committed, and any environment variables the chosen backend requires (for example `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`) are documented with local-override guidance and no committed secrets.

10. **Given** a baseline prompt-only workflow and an MCP-assisted workflow are run for representative repo tasks
    **When** token usage is measured with the same tasks and acceptance boundaries
    **Then** results are recorded in a repo artifact showing reduced input-token usage with the new MCP context tooling (or clear analysis if the reduction target is missed).

11. **Given** this setup is intended for team reuse
    **When** documentation is reviewed
    **Then** setup, troubleshooting, and usage guidance exists in-repo, including the upstream source reference, install-script execution, backend choice, the indexing step and when to re-index, in-container MCP health checks, and token-usage benchmark steps.

## Tasks / Subtasks

1. Establish the devcontainer baseline (AC 1)
   - Confirm the container builds from a fresh clone and provides Java 21 and Gradle for the existing build.
   - Verify `./gradlew test` runs in-container.
2. Provision Python 3.12 (AC 2) — **decided 2026-08-19; implementation lands on `implementation/story-7-1`**
   - Added the `ghcr.io/devcontainers/features/python:1` feature at version `3.12` to `.devcontainer/devcontainer.json`.
   - Chosen over apt (bookworm has no `python3.12` candidate), a base-image swap, and `uv`, because it matches the existing Java 21 feature pattern and ships `pip`.
   - **Requires a devcontainer rebuild to take effect; not yet verified in a built container.**
   - `.devcontainer/devcontainer-lock.json` regenerated 2026-08-19: the python feature is now pinned by digest (`1.8.0`, sha256 `fbcad695...`).
3. Confirm the graph backend (AC 4) — **decided 2026-08-19**
   - Upstream default (FalkorDB Lite) is used, unlocked by the 3.12 provisioning above. No override, no external service.
   - The install script hard-fails below Python 3.12 so a silent fallback to a different backend cannot happen.
4. Write `scripts/mcp/install-codegraphcontext.sh` (AC 3) — **decided 2026-08-19; implementation lands on `implementation/story-7-1`**
   - Installs pinned `codegraphcontext==0.6.3` (overridable via `CGC_VERSION`), idempotent on re-run.
   - Guards on Python major/minor and on `pip` availability, with actionable errors.
   - **Not yet executed successfully; blocked until the container is rebuilt on 3.12.**
5. Wire the MCP config (AC 5) — **decided 2026-08-19; implementation lands on `implementation/story-7-1`**
   - Stale `serena`/`claude-context` wrapper entries removed.
   - `codegraphcontext` stdio entry added, invoking the upstream entrypoint `codegraphcontext mcp start` directly with no wrapper script.
   - **Not yet verified against a running server.**
6. Validate container-to-host connectivity (AC 6)
   - Confirm the `WigAI` HTTP entry reaches Bitwig on the host via the host alias.
   - Document the strategy and its failure modes.
7. Index the repository and add a verification step (AC 7)
   - Run indexing over `src/`; decide whether indexing runs at container create time or on demand.
   - Add a check that resolves known symbols and fails loudly if the graph is empty or stale.
8. Add the healthcheck script (AC 8)
   - Verify a non-error handshake from `codegraphcontext` inside the container.
9. Handle hygiene and secrets (AC 9) — **partially decided 2026-08-19; implementation lands on `implementation/story-7-1`**
   - Git-ignored the index/graph artifacts (`.codegraphcontext/`, `*.kuzu/`, `falkordb-lite.db`).
   - Still to do: confirm the actual artifact paths the default backend writes once it runs, and document environment variables and local override guidance.
10. Run the token benchmark (AC 10)
    - Measure representative repo tasks with and without the tool; record results in a repo artifact.
11. Write the documentation (AC 11)
    - Create `docs/engineering/devcontainer-mcp-setup.md` covering install, backend, indexing, health checks, and troubleshooting.

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

### Architecture Compliance

This story touches no extension source code. It changes `.devcontainer/`, `scripts/mcp/`, `.vscode/mcp.json`, `.gitignore`, and documentation only. Nothing here may alter WigAI runtime behavior or the MCP tool contracts.

### Library / Framework Requirements

- `codegraphcontext` (upstream, pinned version to be recorded)
- A graph database backend, selected in Task 3
- A Python package installer, selected in Task 2

### File Structure Requirements

Expected touch points:

- `.devcontainer/Dockerfile` and/or `.devcontainer/devcontainer.json`
- `scripts/mcp/install-codegraphcontext.sh` (new)
- `.vscode/mcp.json`
- `.gitignore` (index/graph artifacts)
- `docs/engineering/devcontainer-mcp-setup.md` (new)

### Testing Requirements

No JUnit coverage applies. Verification is:

- The healthcheck script returns a successful handshake for `codegraphcontext`.
- The symbol-resolution check from AC 7 passes against a freshly indexed clone.
- `./scripts/check-story-status.sh` and the existing CI jobs remain green.

### Project Structure Notes

`.gitignore` previously excluded all of `.vscode/`, which made a repo-managed `.vscode/mcp.json` impossible to commit. On 2026-08-19 the rule was changed to ignore `.vscode/*` with a `!.vscode/mcp.json` exception, so the shared MCP config is now trackable while per-developer files such as `settings.json` stay ignored. A plain `!` negation under an excluded directory does not work in git; the directory contents must be excluded instead.

The `.devcontainer/` present in the working tree at the time this story was rescoped is an ECC bootstrap container (`node:20-slim` plus `ecc-universal`), not a deliverable of this story.

### References

- Upstream project: <https://github.com/CodeGraphContext/CodeGraphContext>
- Scope replacement: `_bmad-output/planning-artifacts/sprint-change-proposal-2026-08-19.md`
- Canonical-tool rule: `_bmad-output/planning-artifacts/sprint-change-proposal-2026-02-22.md`
- Epic 7 definition: `_bmad-output/planning-artifacts/epics.md`
- Status authority rules: `docs/engineering/story-status-authority.md`

## Dev Agent Record

### Agent Model Used

- GPT-5 Codex (original create-story workflow)
- Claude Opus 5 (2026-08-19 rescope)

### Debug Log References

- Upstream install/config facts read from the CodeGraphContext repository README on 2026-08-19.
- Container facts verified in-container: `python3 --version` -> 3.11.2; no `pip`, `pip3`, `uv`, or `pipx` on `PATH`.

### Completion Notes List

- Story created from user-requested backlog item in SM chat mode.
- Correct-course decision applied 2026-02-22: canonical approach switched to upstream tools via repo-local install scripts; local wrapper servers classified as superseded.
- Scope replacement applied 2026-08-19: `serena` and `claude-context` removed from Epic 7 entirely and replaced by `codegraphcontext`. Story 7.2, created earlier the same day for `codegraphcontext`, was withdrawn and its acceptance criteria absorbed here.
- No implementation exists against any version of this story, so the rescope unwinds nothing.

### Change Log

- 2026-02-22: Initial implementation-ready story created.
- 2026-02-22: Correct Course approved; story contract updated to upstream install-script approach and status set to `in-progress`.
- 2026-08-19: Status header (`ready-for-dev`) and tracker (`backlog`) reconciled to `in-progress`, the value the 2026-02-22 correction approved. Both had been missed when that correction was applied.
- 2026-08-19: Open technical decisions settled by the product owner: provision Python 3.12+ and use the upstream default graph backend. AC 2 and AC 4 rewritten from open questions into fixed constraints. The corresponding devcontainer feature, install script, MCP entry, and artifact ignores are carried on `implementation/story-7-1` rather than this planning branch, and remain unverified pending a devcontainer rebuild.
- 2026-08-19: Scope replaced. `serena` and `claude-context` removed; `codegraphcontext` adopted as the single context tool. Story renamed from `7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context` and Story 7.2 withdrawn into this one. Acceptance criteria grew from 8 to 11. Status remains `in-progress`.

### File List

Planning branch (`planning/cycle-2-epic-7`):

- `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext.md` (renamed from `...-serena-claude-context.md`)
- `_bmad-output/planning-artifacts/sprint-change-proposal-2026-08-19.md` (new)

Implementation branch (`implementation/story-7-1`):

- `.devcontainer/devcontainer.json` (updated - python 3.12 feature)
- `scripts/mcp/install-codegraphcontext.sh` (new)
- `.vscode/mcp.json` (updated - codegraphcontext stdio entry)
- `.gitignore` (updated - CodeGraphContext generated artifacts)
