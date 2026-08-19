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

## MCP Context Tooling

### codegraphcontext

**Purpose:** Provides repository-wide code understanding through the MCP protocol, enabling Claude to rapidly understand codebase structure, dependencies, and patterns without consuming excessive prompt tokens.

**Upstream:** https://github.com/CodeGraphContext/CodeGraphContext

**Version Pinning:** See `scripts/mcp/install-codegraphcontext.sh` for pinned versions (currently 0.6.3).

**Backend:** codegraphcontext uses FalkorDB Lite (a lightweight graph database) as its default backend, which requires Redis server. The devcontainer includes `redis-server` as a system dependency to support this.

**MCP Configuration:** `.vscode/mcp.json`
```json
{
  "codegraphcontext": {
    "type": "stdio",
    "command": "codegraphcontext",
    "args": ["mcp", "start"]
  }
}
```

**Usage in Claude:**
- Claude Code (IDE) and claude.ai automatically detect and use configured MCP servers
- codegraphcontext provides `search_codebase`, `get_file_context`, and related operations
- Reduces token usage by allowing targeted code retrieval instead of pasting entire files

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
- [ ] `.vscode/mcp.json` exists and is valid JSON
- [ ] Claude Code shows "MCP" indicator in status bar (when MCP servers are active)

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
- **MCP configuration:** `.vscode/mcp.json`
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
