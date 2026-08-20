---
name: codegraphcontext
description: Query the pre-indexed WigAI code graph (Java `src/`) via the codegraphcontext MCP server for symbol lookup, call-graph analysis, dead-code detection, and complexity hotspots — instead of grep/Explore for code already indexed under `src/`.
metadata:
  origin: project
---

# CodeGraphContext (WigAI)

Repository-wide code understanding for `./src` (the Java source tree) through the `codegraphcontext` MCP server, so symbol/dependency questions return targeted graph results instead of requiring grep or whole-file reads.

**Upstream:** https://github.com/CodeGraphContext/CodeGraphContext
**Full setup reference:** [docs/engineering/devcontainer-mcp-setup.md](../../../docs/engineering/devcontainer-mcp-setup.md)

## Status & connection

- Registered with Claude Code via `.mcp.json` (repo root) — approved once per session/host. If a tool call errors as unavailable, check `claude mcp list` for "Pending approval": approve by running `claude` in a terminal (or the `/mcp` command / MCP panel if the host surfaces one) and confirming the server. `mcp.json` (no leading dot) is a **different** file — codegraphcontext's own project-default config (env vars, ignore rules, tool allowlist), not something Claude Code reads.
- Indexed path: `./src` (`src/main` + `src/test`), kept current by a background watcher. Check it's alive: `kill -0 "$(cat ~/.codegraphcontext/logs/wigai-src-watch.pid)" && echo running`.
- Full diagnostics: `./scripts/mcp/healthcheck.sh`.

## When to use

- "Where is X defined", "who calls X", "what does X depend on" for Java code under `src/` — use these tools first; the graph is pre-indexed so this is cheaper than grep or the Explore agent.
- Before refactoring or extending a hotspot — check complexity and dead code before editing.
- **Not** for `docs/`, build output, or anything outside `src/` — those paths aren't indexed (see `IGNORE_DIRS` in `mcp.json`). Use grep/Explore for those.

## Tool reference

Verified against the live server (v0.6.3) on 2026-08-20 — this is the actual tool list, which is longer than what `docs/engineering/devcontainer-mcp-setup.md` currently documents.

### Core (use these for day-to-day WigAI work)

| Tool | Use for |
|---|---|
| `find_code` | Symbol/code search — "find the `Track` interface" |
| `analyze_code_relationships` | Callers/callees, dependency edges for a symbol |
| `find_dead_code` | Unreferenced functions/classes |
| `calculate_cyclomatic_complexity` | Complexity score for one function/file |
| `find_most_complex_functions` | Repo-wide complexity hotspot ranking |
| `get_repository_stats` | Summary stats (node/edge counts, etc.) for an indexed repo |
| `execute_cypher_query` | Raw graph query — escape hatch for anything not covered by a named tool |
| `visualize_graph_query` | Render a subgraph for a query |
| `list_indexed_repositories` / `delete_repository` | Inventory / remove indexed repos |
| `list_graphs` | List available FalkorDB graphs (relevant once more than one repo is indexed) |

### Indexing & live-sync management

| Tool | Use for |
|---|---|
| `add_code_to_graph` / `add_package_to_graph` | Index an additional path/package not already covered |
| `check_job_status` / `list_jobs` | Poll an async indexing job |
| `watch_directory` / `unwatch_directory` / `list_watched_paths` | Manage live-sync scope — `src/` is already watched by the installed watcher when it's running; don't duplicate it, and see the DB-lock note above before starting a watch from inside a tool call |

### Extended — available but not yet used in this project

These showed up in the live tool list but don't have an established use case here yet (WigAI is a single-repo Bitwig extension, not Spring-based, and isn't using the bundle registry). Reach for them if the need arises rather than by default:

| Tool | Purpose |
|---|---|
| `find_java_spring_beans` / `find_java_spring_endpoints` | Spring bean/endpoint discovery — not applicable, WigAI doesn't use Spring |
| `analyze_architectural_evolution` | Track how the architecture/graph has changed over time |
| `simulate_architectural_change` / `simulate_metrics` | What-if impact analysis before making a structural change |
| `generate_report` | Produces a CGC_REPORT.md (complexity hotspots, coupling) — same as the `codegraphcontext report` CLI command |
| `discover_codegraph_contexts` / `switch_context` | Multi-repo/multi-context switching |
| `load_bundle` / `search_registry_bundles` | codegraphcontext's package/bundle registry |
| `find_datasource_nodes` | Locate database/config datasource definitions in the graph |

## Workflow

1. **Symbol lookup** ("where is X", "find the X class/interface") → `find_code` first; fall back to grep only if the graph misses it (e.g. it's outside `src/`).
2. **Impact analysis** before editing → `analyze_code_relationships` on the symbol.
3. **Before touching a legacy/complex area** → `calculate_cyclomatic_complexity` or `find_most_complex_functions` to scope the change.
4. **Cleanup work** → `find_dead_code` before deleting anything by hand.
5. **Anything else** (custom graph shape, aggregate queries) → `execute_cypher_query`.

## If the MCP is unavailable

Fall back to grep / the Explore agent — this skill is a token-efficiency optimization over indexed `src/`, not the only way to search the repo. Don't block work on it; note that the check was skipped if relevant.
