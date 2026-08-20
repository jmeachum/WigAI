# WigAI - Bitwig Studio Extension for AI Control

WigAI is a Model Context Protocol (MCP) server implemented as a Bitwig Studio extension. It provides an interface for external AI assistants to control Bitwig Studio through simple text-based commands.

## Overview

The extension enables external AI agents (e.g., IDE-based copilots, standalone AI assistants) to interact with and control Bitwig Studio using the Model Context Protocol. This allows for hands-free control via text commands interpreted by an AI assistant.

## Key Features

-   MCP server implementation
-   Transport control (start/stop playback)
-   Device parameter control for selected devices
-   Clip and scene launching

## Requirements

-   Bitwig Studio (5.2.7 or later)
-   Java 21 LTS
-   External AI agent supporting Model Context Protocol

## Building

To build the extension:

```bash
./gradlew build
```

This will generate a `.bwextension` file in the `build/extensions` directory.

## Installation

1. Copy the `WigAI.bwextension` file from `build/extensions` to your Bitwig Studio extensions directory.
2. Launch Bitwig Studio
3. Open Bitwig preferences, navigate to Extensions, and activate WigAI

## Usage

Once the extension is activated in Bitwig Studio, the MCP server will be available at `http://localhost:61169/mcp`. External AI agents can connect to this endpoint to send commands and receive responses.

## Development Container Setup

For reproducible development, WigAI includes a devcontainer configuration with:

- **Java 21** and Gradle
- **Python 3.12** for supporting tools
- **codegraphcontext** MCP server for repository context and code understanding
- **Host MCP connectivity** to Bitwig Studio running on the host machine
- **GitHub CLI (`gh`)** for opening pull requests from inside the container

### Quick Start

1. **Open in devcontainer:**
   ```bash
   git clone https://github.com/WigAI/WigAI.git
   cd WigAI
   # VS Code will prompt to "Reopen in Container" — click to proceed
   ```

2. **Verify setup:**
   ```bash
   ./scripts/mcp/healthcheck.sh
   ```

3. **Build and test:**
   ```bash
   ./gradlew build
   ./gradlew test
   ```

### MCP Context Tooling

The devcontainer includes **codegraphcontext**, an MCP server that provides repository-wide code understanding. `./src` is indexed and kept in sync automatically by a background watcher started on container create/rebuild. When using Claude Code or Codex CLI inside the devcontainer:

- Both automatically retrieve targeted code context without token-heavy file pasting
- Significantly reduces prompt token usage for code understanding and refactoring tasks
- Configured in `.mcp.json` (Claude Code), `.vscode/mcp.json` (VS Code), and `~/.codex/config.toml` (Codex CLI, registered automatically by the install script)

**Host Connectivity:** If Bitwig is running on the host machine, MCP requests from the container use `host.docker.internal:61169` to reach the WigAI endpoint.

For detailed setup, troubleshooting, and customization, see [Devcontainer and MCP Tooling Setup Guide](docs/engineering/devcontainer-mcp-setup.md).

## Development

This project is developed using the [BMAD v2 method](https://github.com/bmadcode/BMAD-METHOD) with AI Agents. The files in folders `.bmad`, `.claude` and `docs` are used for this development method.

**Contributing**: See [CONTRIBUTING.md](CONTRIBUTING.md) for workflow, branch naming, and PR guidelines.

**Documentation**: Comprehensive project documentation is available in [docs/reference/](docs/reference/)

## Releases

This project uses [Nyx](https://github.com/mooltiverse/nyx) for semantic versioning and automated releases. The release process follows [Conventional Commits](https://www.conventionalcommits.org/) specification:

- `feat:` - new features (minor version bump)
- `fix:` - bug fixes (patch version bump)
- `feat!:` or `BREAKING CHANGE:` - breaking changes (major version bump)
- `chore:`, `docs:`, `style:`, `refactor:`, `test:` - no version bump

Releases are automatically created on pushes to the `main` branch when commits follow the conventional commit format.

## License

[MIT License](LICENSE)

## Author

fabb
