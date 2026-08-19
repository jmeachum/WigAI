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

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: python3 not found. The devcontainer python feature should provide it."
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
echo ""
echo "Next: index this repository with"
echo "  codegraphcontext index ."
