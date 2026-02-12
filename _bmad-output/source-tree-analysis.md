# Source Tree Analysis

## Annotated Project Tree (main)

```text
WigAI/
├── build.gradle.kts                         # Gradle build definition (Java 21, dependencies, packaging tasks)
├── settings.gradle.kts                      # Root project + Nyx release configuration
├── gradle/                                  # Wrapper infrastructure (Gradle 8.13)
├── src/
│   ├── main/
│   │   ├── java/io/github/fabb/wigai/
│   │   │   ├── WigAIExtensionDefinition.java # Bitwig extension definition + API version contract (service entry target)
│   │   │   ├── WigAIExtension.java           # Runtime lifecycle entrypoint (init/exit, server startup/restart)
│   │   │   ├── server/                       # Embedded Jetty lifecycle + bind enforcement
│   │   │   │   └── JettyServerManager.java   # Start/stop/restart + loopback-only host safeguards
│   │   │   ├── mcp/                          # MCP server bootstrap + standardized error handling
│   │   │   │   ├── McpServerManager.java     # Registers all MCP tools on /mcp servlet
│   │   │   │   ├── McpErrorHandler.java      # Uniform success/error envelopes + retry integration
│   │   │   │   └── tool/                     # MCP tool contracts (15 tools)
│   │   │   ├── features/                     # Feature-level controllers (transport/device/clip-scene)
│   │   │   ├── bitwig/                       # Bitwig API facade/adapter layer
│   │   │   │   └── BitwigApiFacade.java      # Core host integration boundary
│   │   │   ├── config/                       # Runtime host/port config + observer notifications
│   │   │   └── common/                       # Cross-cutting utilities (error, retry, logging, validation, data records)
│   │   └── resources/
│   │       └── META-INF/services/com.bitwig.extension.ExtensionDefinition
│   │                                            # Service registration used by Bitwig to load extension definition
│   └── test/java/                              # JUnit + MCP behavior/validation tests
├── docs/                                       # Project and generated reference documentation
├── .github/workflows/                          # CI/CD workflows (build/test/PR/release)
├── scripts/                                    # Local helper scripts
└── bitwig-api-doc-scraper/                     # Auxiliary Node-based Bitwig API doc scraper (non-runtime part)
```

## Entry Points and Execution Flow

1. Bitwig loads `WigAIExtensionDefinition` via service descriptor.
2. `WigAIExtension#init()` initializes config, server managers, and MCP tool stack.
3. `McpServerManager` builds MCP tool server and servlet.
4. `JettyServerManager` binds and exposes endpoint at `/mcp`.

## Key File Locations

- **Protocol/API tools:** `src/main/java/io/github/fabb/wigai/mcp/tool/`
- **Domain host integration:** `src/main/java/io/github/fabb/wigai/bitwig/BitwigApiFacade.java`
- **Operational safeguards:** `src/main/java/io/github/fabb/wigai/server/JettyServerManager.java`
- **Config + observer model:** `src/main/java/io/github/fabb/wigai/config/`
- **Cross-cutting reliability:** `src/main/java/io/github/fabb/wigai/common/`
