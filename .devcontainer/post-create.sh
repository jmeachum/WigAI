#!/usr/bin/env bash
set -euo pipefail

# Installs CLI tooling and provisions a Java/Gradle-scoped ECC rules set.
#
# ecc-universal's rules-core module (baseline:rules) has no per-language
# filter -- selecting it always copies the entire rules/ tree (20+ language
# packs). WigAI is pure Java/Gradle, so baseline:rules is excluded from the
# profile install and rules/common + rules/java are copied explicitly
# instead, keeping ~/.claude/rules/ecc scoped to what this repo needs.

npm config set prefix "$HOME/.npm-global"
export PATH="$HOME/.npm-global/bin:$PATH"
grep -qxF 'export PATH="$HOME/.npm-global/bin:$PATH"' "$HOME/.bashrc" 2>/dev/null \
  || echo 'export PATH="$HOME/.npm-global/bin:$PATH"' >> "$HOME/.bashrc"

npm install -g @openai/codex@0.147.0 ecc-universal@2.1.0

ECC_PKG="$HOME/.npm-global/lib/node_modules/ecc-universal"

node "$ECC_PKG/scripts/install-apply.js" \
  --profile minimal \
  --without baseline:rules \
  --with capability:security \
  --with capability:orchestration \
  --with baseline:hooks \
  --skills api-design,contract-first,mcp-server-patterns,hexagonal-architecture,java-coding-standards \
  --target claude

mkdir -p "$HOME/.claude/rules/ecc"
cp -f "$ECC_PKG/rules/README.md" "$HOME/.claude/rules/ecc/"
cp -r "$ECC_PKG/rules/common" "$HOME/.claude/rules/ecc/"
cp -r "$ECC_PKG/rules/java" "$HOME/.claude/rules/ecc/"

./scripts/mcp/install-codegraphcontext.sh
