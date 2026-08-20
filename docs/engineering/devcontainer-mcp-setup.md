# Devcontainer and MCP Tooling Setup Guide

## Overview

WigAI uses a reproducible devcontainer configuration that includes:
- **Java 21** for Gradle-based building
- **Python 3.12** for supporting tools and utilities
- **codegraphcontext MCP server** for repository context and code understanding
- **Host MCP endpoint** (Bitwig) for music production tool integration

This guide covers setup, verification, troubleshooting, and local customization.

## Quick Start

1. **Clone and open in devcontainer:**
   ```bash
   git clone https://github.com/WigAI/WigAI.git
   cd WigAI
   # Open in VS Code Dev Container (VS Code will prompt automatically)
   ```

2. **Verify setup on container open:**
   The devcontainer will automatically:
   - Install Java 21 and Gradle
   - Install Python 3.12
   - Set up Node.js tooling (npm, ecc, skills)
   - Install codegraphcontext MCP server

3. **Run healthcheck (inside container):**
   ```bash
   ./scripts/mcp/healthcheck.sh
   ```

`postCreateCommand` also indexes `./src`, starts a background file watcher that
keeps the graph in sync, and registers the MCP server with Codex CLI — see
[Repository Indexing & Live Updates](#repository-indexing--live-updates) below.

## MCP Context Tooling

### codegraphcontext

**Purpose:** Provides repository-wide code understanding through the MCP protocol, enabling Claude to rapidly understand codebase structure, dependencies, and patterns without consuming excessive prompt tokens.

**Upstream:** https://github.com/CodeGraphContext/CodeGraphContext

**Version Pinning:** See `scripts/mcp/install-codegraphcontext.sh` for pinned versions (currently 0.6.3).

**Backend:** codegraphcontext uses FalkorDB Lite (a lightweight graph database) as its default backend, which requires Redis server. The devcontainer includes `redis-server` as a system dependency to support this.

**Indexed path:** `./src` (the Java source tree — `src/main` and `src/test`). Kept
in sync automatically by a background watcher; see
[Repository Indexing & Live Updates](#repository-indexing--live-updates).

**MCP tools available:** `add_code_to_graph`, `add_package_to_graph`, `check_job_status`,
`list_jobs`, `find_code`, `analyze_code_relationships`, `watch_directory`, `unwatch_directory`,
`list_watched_paths`, `find_dead_code`, `execute_cypher_query`, `calculate_cyclomatic_complexity`,
`find_most_complex_functions`, `list_indexed_repositories`, `delete_repository`,
`visualize_graph_query`. This is the `tools.alwaysAllow` list in `mcp.json` (see
[MCP Configuration Files](#mcp-configuration-files) below) — trim it there if a
project ever wants to restrict what the server exposes.

The server's actual tool set (v0.6.3, verified live 2026-08-20) is larger than the
`alwaysAllow` list above: it also exposes `get_repository_stats`, `list_graphs`,
`find_java_spring_beans`, `find_java_spring_endpoints`, `analyze_architectural_evolution`,
`simulate_architectural_change`, `simulate_metrics`, `generate_report`,
`discover_codegraph_contexts`, `switch_context`, `load_bundle`,
`search_registry_bundles`, and `find_datasource_nodes`. These aren't in `alwaysAllow`
purely because the list predates them, not because they're intentionally restricted —
none are Spring/bundle-registry relevant to this project today, so day-to-day use
sticks to the tools already listed above. See
[.claude/skills/codegraphcontext/SKILL.md](../../.claude/skills/codegraphcontext/SKILL.md)
for the full categorized tool reference and usage workflow.

**Usage in Claude/Codex:**
- Claude Code (CLI and IDE) and Codex CLI both auto-detect the server once configured (see below)
- Ask Claude/Codex to use it directly: "use find_code to locate the Track interface",
  "use analyze_code_relationships on BitwigApiFacade"
- Because the graph is pre-indexed and kept current by the watcher, these calls return
  targeted results without Claude/Codex needing to grep or read whole files first

### MCP Configuration Files

There are four MCP-related config files/locations in play, each serving a different client:

| File | Committed? | Consumed by | Notes |
|------|------------|-------------|-------|
| `mcp.json` (repo root) | Yes | `codegraphcontext` itself | codegraphcontext's own project-default config. Every `cgc`/`codegraphcontext` command run from the repo root reads the `env` block under `mcpServers.CodeGraphContext` (exact key, case-sensitive) as its **lowest-priority** config source — below runtime env vars and `~/.codegraphcontext/.env`. This is where `IGNORE_DIRS`, `MAX_FILE_SIZE_MB`, `ENABLE_AUTO_WATCH`, etc. live for this repo. Also controls the MCP server's `tools.alwaysAllow`/`disabledTools`. |
| `.mcp.json` (repo root) | Yes | Claude Code (CLI + IDE) | Claude Code's own project-scoped MCP config format. Auto-detected on open; Claude prompts to approve the server the first time. Distinct file from `mcp.json` above — the leading dot matters. |
| `.vscode/mcp.json` | Yes | VS Code's built-in MCP integration | Also declares the `WigAI` (Bitwig) host endpoint. |
| `~/.codex/config.toml` | No (per-user, outside the repo) | Codex CLI | Global to the user, not per-project — Codex has no project-scoped MCP config. `scripts/mcp/install-codegraphcontext.sh` registers it idempotently via `codex mcp add`. Persists across container rebuilds because `/home/vscode` is a named volume mount. |

If you ever regenerate `mcp.json` by running `cgc mcp setup` interactively, keep the
`mcpServers.CodeGraphContext` key name and re-point `command` at plain `codegraphcontext`
(not the absolute interpreter path the wizard writes) so it keeps working if the Python
feature's install path ever changes.

## Repository Indexing & Live Updates

`scripts/mcp/install-codegraphcontext.sh` runs on every `postCreateCommand` (container
create *and* rebuild) and, after installing the pinned `codegraphcontext` version:

1. Starts `codegraphcontext watch src --poll --sync-on-start` as a detached background
   process. This single command does the initial index if `./src` isn't indexed yet,
   reconciles any drift if it already is, and then keeps watching for file changes —
   there's no separate blocking "index" step, since running `cgc index` and `cgc watch`
   at the same time against the same embedded database causes a lock error (see
   Troubleshooting below).
2. Registers `codegraphcontext` with Codex CLI via `codex mcp add` (skipped if already
   registered, and skipped entirely if `codex` isn't on `PATH`).

The graph database lives under `~/.codegraphcontext`, which is on the devcontainer's
persistent `/home/vscode` volume mount — it survives container rebuilds, so re-running
the install script only does incremental work (sync, not a full re-index).

**Why `./src` and not the whole repo:** `./src` is the Java source tree; indexing it
directly (rather than the repo root) keeps the graph focused on application code and
avoids depending on `IGNORE_DIRS` to exclude `_bmad/`, `docs/`, build output, etc.

**Watcher status and logs:**
```bash
cat ~/.codegraphcontext/logs/wigai-src-watch.pid   # PID of the background watcher
tail -f ~/.codegraphcontext/logs/wigai-src-watch.log
kill -0 "$(cat ~/.codegraphcontext/logs/wigai-src-watch.pid)" && echo running
```

**Stop the watcher:**
```bash
kill "$(cat ~/.codegraphcontext/logs/wigai-src-watch.pid)"
```

**Force a full re-index** (e.g., after bumping `CGC_VERSION` or suspecting graph drift
the watcher's sync didn't catch): stop the watcher first, then re-index, then restart
the watcher — running `index --force` while the watcher still holds the database causes
a lock error.
```bash
kill "$(cat ~/.codegraphcontext/logs/wigai-src-watch.pid)"
codegraphcontext index src --force
./scripts/mcp/install-codegraphcontext.sh   # restarts the watcher, idempotent otherwise
```

**Other useful commands:**
```bash
codegraphcontext doctor          # diagnostics: DB connectivity, config validity, deps
codegraphcontext stats           # indexing statistics
codegraphcontext list            # indexed repositories
codegraphcontext report          # CGC_REPORT.md: complexity hotspots, cross-module coupling
```

## Container-to-Host Networking

### Bitwig MCP Endpoint (WigAI HTTP endpoint)

When running WigAI on the host and using Claude Code inside the devcontainer:

**Default Strategy:** `host.docker.internal:61169`

This works in Docker Desktop (Mac/Windows) and is configured in `.vscode/mcp.json`:
```json
{
  "WigAI": {
    "type": "http",
    "url": "http://host.docker.internal:61169/mcp"
  }
}
```

**If using Docker on Linux:**
- `host.docker.internal` may not resolve
- **Workaround 1:** Update Docker daemon config to enable host-gateway
  ```bash
  # In /etc/docker/daemon.json, add or update:
  {
    "host-gateway": "172.17.0.1"
  }
  # Then restart Docker: sudo systemctl restart docker
  ```
- **Workaround 2:** Use the host's IP address instead
  ```bash
  export WIGAI_HOST_IP=$(hostname -I | awk '{print $1}')
  # Update .vscode/mcp.json to use $WIGAI_HOST_IP:61169
  ```

**Testing connectivity:**
```bash
# Inside container - send a JSON-RPC call to test MCP endpoint
curl -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  http://host.docker.internal:61169/mcp
# Should return a JSON-RPC response if WigAI is running on host
```

## Local Customization

### Environment Variables

The devcontainer uses standard environment variables for configuration:

| Variable | Purpose | Default |
|----------|---------|---------|
| `WIGAI_MCP_URL` | Custom WigAI MCP endpoint URL | `http://host.docker.internal:61169/mcp` |
| `CGC_VERSION` | Pinned codegraphcontext version | `0.6.3` |
| `JAVA_HOME` | Java installation path | Auto-detected via sdkman |

### Local .env File (Secrets Management)

**Never commit secrets or credentials.** Instead:

1. Create `.env.local` in the workspace root (ignored by git):
   ```bash
   export WIGAI_API_KEY="your-api-key-here"
   export BITWIG_PORT=61169
   ```

2. Load before working:
   ```bash
   source .env.local
   ```

3. Or add to `.devcontainer/devcontainer.json` remoteEnv (user-local only):
   ```json
   "remoteEnv": {
     "WIGAI_API_KEY": "${localEnv:WIGAI_API_KEY}"
   }
   ```

## Troubleshooting

### Issue: Devcontainer fails to build

**Symptoms:** Build error mentioning missing Java, Python, or node modules.

**Solutions:**
1. Rebuild container from scratch:
   ```bash
   # In VS Code: Dev Containers: Rebuild Container
   # Or: devcontainer up --workspace-folder=. --build-in-docker
   ```

2. Check Dockerfile and devcontainer.json:
   ```bash
   cat .devcontainer/devcontainer.json | jq '.features'
   ```

### Issue: codegraphcontext installation fails with falkordblite error

**Symptoms:** Build error mentioning `falkordblite` or `redis.submodule` during postCreateCommand.

**Root Cause:** FalkorDB Lite (graph database used by codegraphcontext) requires Redis server and build dependencies (gcc, python3-dev). These must be installed in the devcontainer before codegraphcontext can build.

**Solutions:**
1. Rebuild container with latest Dockerfile (should include `redis-server` and build-essential):
   ```bash
   # In VS Code: Dev Containers: Rebuild Container
   ```

2. If rebuild doesn't help, manually install dependencies and retry:
   ```bash
   sudo apt-get update && sudo apt-get install -y redis-server build-essential python3-dev
   ./scripts/mcp/install-codegraphcontext.sh
   ```

3. Verify redis-server is available:
   ```bash
   command -v redis-server
   redis-server --version
   ```

### Issue: "Could not set lock on file" / "Database Connection Error" from codegraphcontext

**Symptoms:** Running `codegraphcontext index` (or any `cgc` command) fails with something like
`Database Connection Error: IO exception: Could not set lock on file : .../db/kuzudb`.

**Root Cause:** The embedded graph database only allows one writer at a time. The background
watcher (`wigai-src-watch.pid`) already holds it, so a concurrent `index` call collides.

**Solution:** Stop the watcher before running `index` directly, then restart it — see
[Force a full re-index](#repository-indexing--live-updates) above.
```bash
kill "$(cat ~/.codegraphcontext/logs/wigai-src-watch.pid)"
codegraphcontext index src   # or any other cgc command that needs the DB
./scripts/mcp/install-codegraphcontext.sh
```

### Issue: codegraphcontext not found after container opens

**Symptoms:** `command not found: codegraphcontext` or healthcheck fails.

**Solutions:**
1. Verify install completed:
   ```bash
   python3 -m pip show codegraphcontext
   ```

2. Check PATH:
   ```bash
   echo $PATH | grep -o '.npm-global/bin'
   ```

3. Manually install (if dependencies are available):
   ```bash
   ./scripts/mcp/install-codegraphcontext.sh
   ```

4. If install script fails with redis-server error, see above section.

### Issue: WigAI endpoint not reachable from container

**Symptoms:** WigAI healthcheck reports endpoint not reachable, or MCP client cannot reach Bitwig.

**Solutions:**
1. Verify WigAI is running on host:
   ```bash
   # On host
   lsof -i :61169
   # Should show java process listening
   ```

2. Test from host first:
   ```bash
   curl -X POST -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
     http://localhost:61169/mcp
   # Should return a JSON-RPC response with tools list
   ```

3. If running on Linux, use workarounds from [Bitwig MCP Endpoint](#bitwig-mcp-endpoint-wigai-http-endpoint) section.

4. Check firewall:
   ```bash
   # Mac
   sudo lsof -i :61169
   # Linux
   sudo netstat -tlnp | grep 61169
   ```

### Issue: MCP servers not showing in Claude Code

**Symptoms:** `.vscode/mcp.json` exists but Claude doesn't use the servers.

**Solutions:**
1. Reload VS Code window:
   ```
   Cmd+Shift+P (Mac) / Ctrl+Shift+P (Linux/Windows)
   "Developer: Reload Window"
   ```

2. Check MCP configuration:
   ```bash
   cat .vscode/mcp.json | jq '.'
   ```

3. Verify command paths resolve:
   ```bash
   which codegraphcontext
   # Should output /usr/local/bin/codegraphcontext or similar
   ```

4. Check Claude Code logs (VS Code: View → Output → Claude)

## Verification Checklist

Run this after opening devcontainer or after any setup changes:

- [ ] `./gradlew --version` returns Java version (should be 21)
- [ ] `python3 --version` returns 3.12+
- [ ] `npm --version` works
- [ ] `ecc --version` works
- [ ] `./scripts/mcp/healthcheck.sh` passes
- [ ] `.vscode/mcp.json` and `.mcp.json` exist and are valid JSON
- [ ] Claude Code shows "MCP" indicator in status bar (when MCP servers are active)
- [ ] `cat ~/.codegraphcontext/logs/wigai-src-watch.pid` names a running process (watcher is up)
- [ ] `codex mcp get codegraphcontext` succeeds (Codex CLI registration is in place)

## Development Workflow

### Building and Testing

Inside the devcontainer:

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Specific test class
./gradlew test --tests "com.example.YourTest"

# With logging
./gradlew build -i
```

### Using MCP Context in Claude Code

When working in Claude Code inside the devcontainer:

1. Ask Claude to understand the codebase:
   - "Analyze the architecture of the src/main/java/com/bitwig directory"
   - "Find all usages of the Track interface"
   - "Explain the MCP protocol implementation"

2. Claude automatically uses codegraphcontext to retrieve code without pasting entire files.

3. If you notice token usage is high, verify MCP is active:
   - Check VS Code status bar for "MCP" indicator
   - Run `./scripts/mcp/healthcheck.sh` inside container
   - Reload window if necessary

## References

- **Devcontainer spec:** `.devcontainer/devcontainer.json`
- **MCP configuration:** `mcp.json` (codegraphcontext project defaults), `.mcp.json` (Claude Code), `.vscode/mcp.json` (VS Code) — see [MCP Configuration Files](#mcp-configuration-files)
- **Install scripts:** `scripts/mcp/`
- **Codegraphcontext docs:** https://github.com/CodeGraphContext/CodeGraphContext
- **Dev Container spec:** https://containers.dev

## Getting Help

If you encounter issues not covered here:

1. Check `.devcontainer/` files and `.vscode/mcp.json` for configuration
2. Run `./scripts/mcp/healthcheck.sh` to diagnose
3. Review devcontainer logs: VS Code → View → Output → Dev Containers
4. Consult upstream tool documentation (links above)
5. Open an issue with:
   - Container OS (Mac/Linux/Windows)
   - Error message
   - Output of `./scripts/mcp/healthcheck.sh`
   - `.vscode/mcp.json` configuration (sanitize secrets)
