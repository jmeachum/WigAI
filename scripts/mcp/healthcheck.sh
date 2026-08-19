#!/usr/bin/env bash
#
# Health check for MCP servers inside the devcontainer.
#
# Verifies that:
# - codegraphcontext is installed and runnable
# - codegraphcontext MCP server can start
# - WigAI host endpoint is reachable from container (if running on host)
#
# Exit code 0 on success, non-zero on any failure.
#
# Usage: ./scripts/mcp/healthcheck.sh

set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "=== MCP Healthcheck ==="
echo ""

# Check 1: codegraphcontext is installed
echo "Checking codegraphcontext installation..."
if ! command -v codegraphcontext >/dev/null 2>&1; then
  echo "✗ FAILED: codegraphcontext not found in PATH"
  echo "  Run: ./scripts/mcp/install-codegraphcontext.sh"
  exit 1
fi
echo "✓ Installed: $(command -v codegraphcontext)"

# Check 2: codegraphcontext MCP server can start and respond
echo ""
echo "Checking codegraphcontext MCP server startup..."
timeout 5s codegraphcontext mcp start &>/dev/null &
PID=$!
sleep 1

# Check if process is still running
if kill -0 "$PID" 2>/dev/null; then
  # Process is running, kill it and wait for it to finish
  kill "$PID" 2>/dev/null || true
  wait "$PID" 2>/dev/null || true
  echo "✓ MCP server started successfully"
else
  # Process exited, check if it was successful
  wait_status=0
  wait "$PID" 2>/dev/null || wait_status=$?
  if [[ $wait_status -eq 0 ]]; then
    echo "✓ MCP server started successfully"
  else
    echo "✗ FAILED: codegraphcontext MCP server failed to start (exit code $wait_status)"
    exit 1
  fi
fi

# Check 3: Verify WigAI host connectivity (informational, does not fail healthcheck)
echo ""
echo "Checking WigAI host endpoint connectivity..."
wigai_url="${WIGAI_MCP_URL:-http://host.docker.internal:61169/mcp}"
if timeout 2s curl -s -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  "${wigai_url}" >/dev/null 2>&1; then
  echo "✓ WigAI endpoint reachable: ${wigai_url}"
else
  echo "ℹ WigAI endpoint not currently reachable"
  echo "  Expected URL: ${wigai_url}"
  echo "  (This is normal if WigAI is not running on the host)"
fi

echo ""
echo "=== Healthcheck Complete (All critical checks passed) ==="
exit 0
