# Minimal, non-root devcontainer base image for ecc-bootstrap.
# Node.js is the mandatory baseline (both provisioned CLIs are npm-based),
# regardless of which --stack was selected.
# No privilege-escalation helper is installed or used anywhere in this image
# or in the generated devcontainer.json lifecycle commands.
FROM node:20-slim

# Install git, python3, tmux, and build dependencies.
# python3 is required by ECC: the continuous-learning instinct CLI
# (skills/continuous-learning-v2/scripts/instinct-cli.py) and the
# security-monitor hook. Both are Python-stdlib only, so no pip packages.
# tmux is required by the ECC orchestration module (worktree orchestrator,
# dmux-workflows); without it those surfaces are inert.
# Build dependencies (gcc, python3-dev, etc.) are needed for falkordblite
# (graph database used by codegraphcontext MCP server).
# redis-server is needed as a runtime dependency for FalkorDB Lite backend.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      git \
      python3 \
      python3-dev \
      python3-pip \
      build-essential \
      redis-server \
      tmux \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user.
RUN useradd --create-home --shell /bin/bash "vscode"

USER vscode
WORKDIR /home/vscode
