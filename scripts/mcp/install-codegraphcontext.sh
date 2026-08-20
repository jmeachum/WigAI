#!/usr/bin/env bash
#
# Installs upstream codegraphcontext into the devcontainer.
#
# Canonical tool, upstream source only — this script installs the published
# upstream package. It does NOT implement a replacement or wrapper MCP server.
# See _bmad-output/planning-artifacts/sprint-change-proposal-2026-02-22.md.
#
# Upstream: https://github.com/CodeGraphContext/CodeGraphContext
#
# Backend: the upstream default (FalkorDB Lite on Unix with Python 3.12+) is used
# deliberately. The devcontainer provisions Python 3.12 via the python feature so
# that default applies with no external database service to run.
#
# Idempotent: re-running with the pin already installed is a no-op.
#
# Usage: ./scripts/mcp/install-codegraphcontext.sh

set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Pinned upstream version. Bump deliberately, not incidentally.
CGC_VERSION="${CGC_VERSION:-0.6.3}"
MIN_PYTHON_MINOR=12

echo "== CodeGraphContext install =="
echo "Pinned version: ${CGC_VERSION}"

# Verify dependencies
if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 not found. The devcontainer python feature should provide it."
  exit 1
fi

if ! command -v redis-server >/dev/null 2>&1; then
  echo "ERROR: redis-server not found. It is required for FalkorDB Lite backend."
  echo "Install via: sudo apt-get install redis-server"
  exit 1
fi

PY_VERSION="$(python3 -c 'import sys; print("%d.%d" % sys.version_info[:2])')"
PY_MINOR="$(python3 -c 'import sys; print(sys.version_info[1])')"
PY_MAJOR="$(python3 -c 'import sys; print(sys.version_info[0])')"
echo "Python: ${PY_VERSION}"

if [[ "${PY_MAJOR}" -ne 3 || "${PY_MINOR}" -lt "${MIN_PYTHON_MINOR}" ]]; then
  echo "ERROR: Python 3.${MIN_PYTHON_MINOR}+ is required."
  echo "  The upstream default FalkorDB Lite backend needs 3.12+ on Unix; on an older"
  echo "  interpreter codegraphcontext falls back to a different backend and the"
  echo "  documented setup no longer matches this container."
  echo "  Rebuild the devcontainer so the python feature (version 3.12) applies."
  exit 1
fi

if ! python3 -m pip --version >/dev/null 2>&1; then
  echo "ERROR: pip is not available for this interpreter."
  echo "  Rebuild the devcontainer; the python feature installs pip alongside Python."
  exit 1
fi

INSTALLED="$(python3 -m pip show codegraphcontext 2>/dev/null | awk '/^Version:/ { print $2 }' || true)"
if [[ "${INSTALLED}" == "${CGC_VERSION}" ]]; then
  echo "Already at ${CGC_VERSION}; nothing to do."
else
  [[ -n "${INSTALLED}" ]] && echo "Upgrading from ${INSTALLED} to ${CGC_VERSION}"
  python3 -m pip install --no-input "codegraphcontext==${CGC_VERSION}"
fi

if ! command -v codegraphcontext >/dev/null 2>&1; then
  echo ""
  echo "WARNING: 'codegraphcontext' is installed but not on PATH."
  echo "  Upstream ships a fix for this case:"
  echo "    curl -sSL https://raw.githubusercontent.com/CodeGraphContext/CodeGraphContext/main/scripts/post_install_fix.sh | bash"
  echo "  Review that script before running it."
  exit 1
fi

echo ""
echo "Installed: $(python3 -m pip show codegraphcontext | awk '/^Version:/ { print $2 }')"
echo "Entrypoint: $(command -v codegraphcontext)"

# Repository to index. The graph database (~/.codegraphcontext) lives on the
# devcontainer's persistent home volume (see .devcontainer/devcontainer.json
# "mounts"), so re-running this on every rebuild is cheap: codegraphcontext
# detects an already-complete index and skips the full rebuild instead of
# starting over. Project-level indexing defaults (ignore rules, file size
# limits, etc.) come from ./mcp.json — see
# docs/engineering/devcontainer-mcp-setup.md.
#
# `cgc watch --sync-on-start` performs the initial index itself (or a resync
# if already indexed) before it starts watching, so it is the only indexing
# command run here — a separate `cgc index` call would race it for the same
# database lock once the watcher is running.
INDEX_PATH="src"

echo ""
echo "== Indexing ${INDEX_PATH} and watching for live updates =="
CGC_LOG_DIR="${HOME}/.codegraphcontext/logs"
WATCH_PID_FILE="${CGC_LOG_DIR}/wigai-src-watch.pid"
WATCH_LOG_FILE="${CGC_LOG_DIR}/wigai-src-watch.log"
mkdir -p "${CGC_LOG_DIR}"

# The PID file itself survives container rebuilds (it's on the persistent
# home volume) but the process it names does not, and PIDs get reused by
# unrelated processes in the new container. A loose substring check on
# /proc/<pid>/cmdline is not enough — this repo's own paths and scripts
# mention "codegraphcontext" constantly, so an unrelated process (even this
# script's own shell) can false-positive. Require an exact "codegraphcontext"
# argv token plus an exact "watch" argv token.
watch_already_running() {
  local pid="$1"
  [[ -n "${pid}" ]] || return 1
  kill -0 "${pid}" 2>/dev/null || return 1
  local args
  args="$(tr '\0' '\n' < "/proc/${pid}/cmdline" 2>/dev/null)" || return 1
  grep -qE '(^|/)codegraphcontext$' <<<"${args}" && grep -qx 'watch' <<<"${args}"
}

if watch_already_running "$(cat "${WATCH_PID_FILE}" 2>/dev/null)"; then
  echo "Watcher already running (pid $(cat "${WATCH_PID_FILE}")); leaving it in place."
else
  # --poll: inotify is unreliable on Docker Desktop bind mounts (Mac/Windows).
  # --sync-on-start: reconcile any drift since the last index/watch run.
  nohup codegraphcontext watch "${INDEX_PATH}" --poll --sync-on-start \
    >>"${WATCH_LOG_FILE}" 2>&1 &
  disown
  echo $! > "${WATCH_PID_FILE}"
  echo "Started watcher (pid $(cat "${WATCH_PID_FILE}")). Logs: ${WATCH_LOG_FILE}"
fi

echo ""
echo "== Registering MCP server with Codex CLI =="
if command -v codex >/dev/null 2>&1; then
  if codex mcp get codegraphcontext >/dev/null 2>&1; then
    echo "Already registered with Codex (~/.codex/config.toml)."
  else
    codex mcp add codegraphcontext -- codegraphcontext mcp start
  fi
else
  echo "codex not found on PATH; skipping Codex MCP registration."
fi

echo ""
echo "MCP server config:"
echo "  Claude Code : .mcp.json          (auto-detected on open; approve when prompted)"
echo "  VS Code     : .vscode/mcp.json"
echo "  Codex CLI   : ~/.codex/config.toml (registered above, per-user)"
echo "  cgc defaults: mcp.json           (project env/ignore rules, read by every cgc command)"
echo ""
echo "See docs/engineering/devcontainer-mcp-setup.md for indexing/watch details,"
echo "including how to force a full re-index or restart the watcher."
