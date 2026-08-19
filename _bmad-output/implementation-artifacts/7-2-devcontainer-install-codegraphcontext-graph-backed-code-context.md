# Story 7.2: devcontainer-install-codegraphcontext-graph-backed-code-context

Status: backlog

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI contributor,
I want upstream `codegraphcontext` installed and indexed inside the devcontainer as a repo-local MCP server,
so that AI assistants can answer structural questions about a 100+ file Java codebase from a code graph instead of re-reading source into the prompt.

**Depends on:** Story 7.1 — the devcontainer, the install-script pattern, and the `.vscode/mcp.json` wiring must exist first.

**Upstream source:** <https://github.com/CodeGraphContext/CodeGraphContext>

## Acceptance Criteria

1. **Given** the devcontainer image provides Python without a package installer
   **When** container initialization runs
   **Then** a Python package installer is provisioned deterministically and the chosen approach (system `pip`, `pipx`, or an isolated virtualenv under the workspace) is documented, without weakening the image's non-root, no-privilege-escalation posture.

2. **Given** the devcontainer is running
   **When** the repo-local install script executes
   **Then** upstream `codegraphcontext` is installed from its canonical source at a pinned version recorded in repo-managed configuration, and no repo-local replacement or wrapper server is introduced.

3. **Given** `codegraphcontext` supports several graph database backends and the container currently runs Python 3.11
   **When** the backend is selected
   **Then** a backend that works fully in-container is chosen and documented, with an explicit rationale for why it was picked over the alternatives, and any external-service backend is either avoided or its connection settings documented as optional developer configuration.

4. **Given** repo-local MCP configuration is used
   **When** an MCP-capable client reads workspace MCP config
   **Then** `.vscode/mcp.json` contains a `codegraphcontext` stdio entry executing the upstream-installed entrypoint (`codegraphcontext mcp start`), alongside the existing `WigAI`, `serena`, and `claude-context` entries.

5. **Given** the tool is installed and configured
   **When** the repository is indexed inside the container
   **Then** indexing completes over the Java sources in `src/` and the result is queryable, verified by a check that resolves known symbols (for example `WigAIExtension`, `BitwigApiFacade`, `McpServerManager`) and their relationships.

6. **Given** the MCP server entry is configured
   **When** the devcontainer healthcheck script runs
   **Then** `codegraphcontext` starts successfully and returns a non-error handshake/metadata response, in the same manner as the other MCP servers.

7. **Given** indexing state and graph data are generated artifacts
   **When** the implementation is complete
   **Then** those artifacts are git-ignored, `~/.codegraphcontext/.env` is never committed, and any environment variables the chosen backend requires (for example `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`) are documented with local-override guidance and no committed secrets.

8. **Given** Story 7.1 establishes a token-usage benchmark
   **When** the benchmark is re-run with `codegraphcontext` available
   **Then** its contribution to input-token reduction on representative structural-navigation tasks is recorded in the same repo artifact, with explicit analysis if it does not help.

9. **Given** this setup is intended for team reuse
   **When** documentation is reviewed
   **Then** in-repo guidance covers install-script execution, backend choice, the indexing step and when to re-index, health checks, and troubleshooting.

## Tasks / Subtasks

1. Provision a Python package installer in the devcontainer (AC 1)
   - Decide between system `pip`, `pipx`, and a workspace virtualenv; record the rationale.
   - Update `.devcontainer/Dockerfile` or the lifecycle commands accordingly.
   - Confirm the image keeps its non-root user and installs no privilege-escalation helper.
2. Select and document the graph backend (AC 3)
   - Evaluate the in-container options against Python 3.11 (upstream's default FalkorDB Lite path documents a 3.12+ requirement on Unix).
   - Decide whether to raise the container Python version or select an alternative backend; record which and why.
3. Write `scripts/mcp/install-codegraphcontext.sh` (AC 2)
   - Install the pinned upstream version; make the script idempotent and safe to re-run.
   - Record the pin alongside the `serena` and `claude-context` pins from Story 7.1.
4. Wire the MCP entry (AC 4)
   - Add the `codegraphcontext` stdio server entry to `.vscode/mcp.json`.
   - Verify the entry invokes the upstream entrypoint directly, with no wrapper script.
5. Index the repository and add a verification step (AC 5)
   - Run indexing over `src/`; decide whether indexing runs at container create time or on demand.
   - Add a check that resolves known symbols and fails loudly if the graph is empty or stale.
6. Extend the healthcheck script (AC 6)
   - Add `codegraphcontext` to whatever Story 7.1 established for handshake verification.
7. Handle hygiene and secrets (AC 7)
   - Git-ignore the index/graph artifacts and any local env file.
   - Document required environment variables and local override guidance.
8. Extend the token benchmark (AC 8)
   - Re-run Story 7.1's benchmark with the tool available; record the delta on structural-navigation tasks.
9. Update documentation (AC 9)
   - Extend the devcontainer MCP setup doc created by Story 7.1.

## Dev Notes

### Developer Context Section

WigAI is a 101-file Java codebase under `io.github.fabb.wigai` with a layered structure (`server` -> `mcp` -> `features` -> `bitwig`, over a shared `common`). Structural questions — who calls `BitwigApiFacade`, which tools touch `TrackTargetingContract`, what breaks if an error code changes — currently cost a lot of prompt tokens because the answer requires reading many files. A code graph answers those directly, which is the point of this story.

Upstream reports support for 23 languages including Java and Kotlin, which covers both `src/` and the Gradle Kotlin DSL build scripts.

### Technical Requirements

- Upstream install only. The canonical-tool rule from the 2026-02-22 course correction applies here identically: no repo-local replacement servers, no wrappers. See `_bmad-output/planning-artifacts/sprint-change-proposal-2026-02-22.md`.
- Container currently runs Python 3.11.2 with no `pip`, `pip3`, `uv`, or `pipx` on `PATH`. The Dockerfile installs Python deliberately as a stdlib-only dependency for ECC tooling, so adding an installer is a real change to that image's contract and should be made explicitly rather than incidentally.
- Upstream documents Python 3.10–3.14 support, `pip install codegraphcontext` as the primary install path, and `codegraphcontext mcp start` as the stdio MCP entrypoint.
- Upstream's default backend selection (FalkorDB Lite on Unix) is documented as requiring Python 3.12+; alternatives include KuzuDB, Neo4j, LadybugDB, and Nornic DB. This is the main open technical decision in the story.

### Architecture Compliance

This story touches no extension source code. It changes `.devcontainer/`, `scripts/mcp/`, `.vscode/mcp.json`, `.gitignore`, and documentation only. Nothing here may alter WigAI runtime behavior or the MCP tool contracts.

### Library / Framework Requirements

- `codegraphcontext` (upstream, pinned version to be recorded)
- A graph database backend, selected in Task 2
- A Python package installer, selected in Task 1

### File Structure Requirements

Expected touch points:

- `.devcontainer/Dockerfile` and/or `.devcontainer/devcontainer.json`
- `scripts/mcp/install-codegraphcontext.sh` (new)
- `.vscode/mcp.json` (now repo-trackable; see Project Structure Notes)
- `.gitignore` (index/graph artifacts)
- `docs/engineering/devcontainer-mcp-setup.md` (created by Story 7.1, extended here)

### Testing Requirements

No JUnit coverage applies. Verification is:

- The healthcheck script returns a successful handshake for `codegraphcontext`.
- The symbol-resolution check from AC 5 passes against a freshly indexed clone.
- `./scripts/check-story-status.sh` and the existing CI jobs remain green.

### Project Structure Notes

`.gitignore` previously excluded all of `.vscode/`, which made Story 7.1's AC3 unsatisfiable — a repo-managed `.vscode/mcp.json` could never be committed. On 2026-08-19 the rule was changed to ignore `.vscode/*` with a `!.vscode/mcp.json` exception, so the shared MCP config is now trackable while per-developer files such as `settings.json` stay ignored. A plain `!` negation under an excluded directory does not work in git; the directory contents must be excluded instead.

The `.devcontainer/` present in the working tree at the time this story was written is an ECC bootstrap container (`node:20-slim` plus `ecc-universal`), not a Story 7.1 deliverable. Story 7.1's devcontainer work is still unimplemented.

### References

- Upstream project: <https://github.com/CodeGraphContext/CodeGraphContext>
- Story 7.1: `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context.md`
- Canonical-tool rule: `_bmad-output/planning-artifacts/sprint-change-proposal-2026-02-22.md`
- Epic 7 definition: `_bmad-output/planning-artifacts/epics.md`
- Status authority rules: `docs/engineering/story-status-authority.md`

## Dev Agent Record

### Agent Model Used

- Claude Opus 5 (story creation)

### Debug Log References

- Upstream install/config facts read from the CodeGraphContext repository README on 2026-08-19.
- Container facts verified in-container: `python3 --version` -> 3.11.2; no `pip`, `pip3`, `uv`, or `pipx` on `PATH`.

### Completion Notes List

- Story created 2026-08-19 from a direct scope addition: Epic 7 also requires an install for `codegraphcontext`.
- Split into its own story rather than folded into Story 7.1, so 7.1's two-tool contract and slug stay stable and this tool's distinct concerns (Python toolchain, graph backend selection, indexing) get their own acceptance criteria.
- Status initialized as `backlog`; it depends on Story 7.1, which is `in-progress`.

### Change Log

- 2026-08-19: Story created. Epic 7 scope extended to a third upstream context tool.

### File List

- `_bmad-output/implementation-artifacts/7-2-devcontainer-install-codegraphcontext-graph-backed-code-context.md` (new)
