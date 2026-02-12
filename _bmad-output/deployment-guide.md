# Deployment Guide

## Deployment Model

WigAI deploys as a Bitwig extension artifact (`WigAI.bwextension`) and runs in-process within Bitwig Studio.

## Build Artifacts

- Main artifact: `build/extensions/WigAI.bwextension`
- Build task: `./gradlew bwextension` (or `./gradlew build`)

## Installation Procedure

1. Build the extension artifact
2. Copy `WigAI.bwextension` to Bitwig's extension directory
3. Launch Bitwig and enable WigAI extension
4. Confirm MCP endpoint availability (`/mcp` on configured loopback host/port)

## Runtime Network Configuration

- Default host: `localhost`
- Default port: `61169`
- Allowed bind hosts: loopback only (`localhost`, `127.0.0.1`, `::1`)

## CI/CD Pipeline

- PR validation uses reusable build/test workflow
- Release workflow publishes on `main` via Nyx
- Branch policy checks gate promotion paths

## Release Flow

1. Merge validated cycle/hotfix into `main`
2. GitHub Actions runs release workflow
3. Nyx computes semantic version and publishes GitHub release assets
