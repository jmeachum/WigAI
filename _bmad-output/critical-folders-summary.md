# Critical Folders Summary

## Core Runtime Folders

- `src/main/java/io/github/fabb/wigai/`
  - Primary application root and extension lifecycle classes.

- `src/main/java/io/github/fabb/wigai/server/`
  - Embedded Jetty lifecycle and bind safety controls.

- `src/main/java/io/github/fabb/wigai/mcp/`
  - MCP server bootstrapping and unified error handling.

- `src/main/java/io/github/fabb/wigai/mcp/tool/` (`12` files)
  - Tool contract layer and request schemas for MCP operations.

- `src/main/java/io/github/fabb/wigai/features/` (`3` files)
  - Feature controllers for transport, device parameters, and clip/scene workflows.

- `src/main/java/io/github/fabb/wigai/bitwig/`
  - Bitwig API integration facade and scene banking utilities.

- `src/main/java/io/github/fabb/wigai/config/`
  - Preference-backed configuration and observer notifications.

- `src/main/java/io/github/fabb/wigai/common/` (`12` files)
  - Cross-cutting infrastructure: validation, logging, retry, errors, shared records.

## Build/Test/Operations Folders

- `src/test/java/` (`53` test files)
  - Verification coverage for tools, lifecycle, and behavior.

- `.github/workflows/` (`5` workflow files)
  - CI validation, build/test pipeline, release and policy workflows.

- `src/main/resources/META-INF/services/`
  - Bitwig service loader registration for extension definition discovery.

## Auxiliary (Non-Primary Runtime) Folder

- `bitwig-api-doc-scraper/`
  - Node-based documentation utility, not part of the extension runtime path.
