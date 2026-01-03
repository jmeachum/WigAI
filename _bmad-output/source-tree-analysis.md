# Source Tree Analysis — WigAI

## Repository shape
WigAI is a **single-part backend** that lives inside `src/main/java/io/github/fabb/wigai`. The extension entry point (`WigAIExtension`) boots the Jetty server (`JettyServerManager`), registers MCP tools via `McpServerManager`, and binds everything to Bitwig through the Bitwig API facade. The repository combines Runtime code, BMAD tooling, documentation, and helper scripts in the same tree, so the following sections describe each major area and its role.

## Annotated source tree
```
project-root/                          # Gradle/Java/Bitwig extension workspace
├── _bmad/                             # BMAD workbench: workflows, config, agent prompts, data
│   ├── bmm/workflows/                 # Workflow definitions (document-project, workflow-status, diagrams)
│   ├── data/                          # Shared data (documentation standards, skill metadata)
│   ├── config.yaml                    # Configuration backing agents (names, languages, output locations)
│   └── ...                            # Supports workflow-driven doc generation
├── docs/                              # Documentation site (analysis, reference, engineering, guides)
│   ├── reference/bitwig-api/v19/      # ~200 auto-scraped API pages used by MCP docs and discovery
│   ├── engineering/                   # Process docs (git workflow, MCP smoke-test runbook, CI checklists)
│   ├── sprint-artifacts/              # Historical epics, stories, status, validation reports
│   └── ...                            # QA checklists, traceability matrices, test guides, etc.
├── _bmad-output/                      # Generated outputs (workflow state, API/data docs, scans)
│   ├── project-scan-report.json       # Running workflow state (Steps 1‑9 tracked here)
│   ├── api-contracts-core.md          # MCP tool catalog (Step 4)
│   ├── data-models-core.md            # Parameter/track/clip payload shapes (Step 4)
│   └── source-tree-analysis.md        # (Current analysis) keeps track of directory metadata
├── src/                               # Java codebase (Bitwig extension + supporting modules)
│   ├── main/
│   │   ├── java/io/github/fabb/wigai/  # Core packages
│   │   │   ├── WigAIExtension.java     # Extension lifecycle + server bootstrap (entry point)
│   │   │   ├── WigAIExtensionDefinition.java
│   │   │   ├── bitwig/                 # BitwigApiFacade + SceneBankFacade wrappers
│   │   │   ├── common/                 # Logging, error handling, validation, shared data records
│   │   │   ├── config/                 # Preferences-backed ConfigManager/observers
│   │   │   ├── features/               # Transport, Device, Clip/Scene business logic used by MCP tools
│   │   │   ├── mcp/                    # MCP server manager + tool utilities (error handler, servlets)
│   │   │   └── server/                 # JettyServerManager and helpers enforcing loopback binding
│   │   └── resources/                  # Extension resource files (currently empty)
│   └── test/                          # JUnit and MCP smoke-test harness sources (tagged suites)
├── scripts/                           # Support scripts (build helpers, scraping, ad-hoc tooling)
├── build.gradle.kts                   # Gradle build logic (dependencies, Shadow plugin, custom tasks)
├── settings.gradle.kts                # Root settings registering the project
├── gradlew, gradlew.bat, gradle/       # Gradle wrapper and supporting files
├── build/                             # Gradle output directory (ignored; regenerated per build)
├── bitwig-api-doc-scraper/            # Tool that regenerates the Bitwig API reference snapshot
└── README/CONTRIBUTING/CHANGELOG etc  # High-level project guides, contribution instructions, release notes
```

## Critical folders summary
- `_bmad/`: houses the BMAD workflow machinery that guides documentation generation, configuration, and agent behavior. Treat it as the planning/automation brain.
- `src/main/java/io/github/fabb/wigai`: The single backend part. Entry points live here (`WigAIExtension`, `JettyServerManager`, `McpServerManager`), plus controllers and facades that translate MCP requests into Bitwig API calls.
- `docs/`: Primary documentation hub. Existing docs (architecture, PRD, checklists, API reference, sprint artifacts) provide context for future writers; new outputs should link into this folder as the canonical surface.
- `_bmad-output/`: Workflow-generated artifacts (current scan state, API/data docs) and now this source-tree analysis document. This folder is the working output area before documents are reconciled with `docs/`.
- `scripts/` & `bitwig-api-doc-scraper/`: Utilities that support the build/maintenance workflow (doc scraping, custom tooling). Keep them in sync with Gradle plugins if new Glue code is added.
- `build.gradle.kts` + Gradle wrapper: Central build + packaging logic; defines Shadow JAR, `.bwextension` task, custom verification steps (ATDD, smoke test).

## Entry points and integration
- `WigAIExtension.init()` boots the `JettyServerManager` and `McpServerManager`, linking the extension lifecycle to the embedded Jetty + MCP servlet combo.
- `JettyServerManager` enforces loopback hosts (`localhost`, `127.0.0.1`, `::1`), registers MCP servlets, and reports bind failures to the user interface.
- `McpServerManager` wires controllers (`TransportController`, `DeviceController`, `ClipSceneController`), registers the tool specs (transport, clip/scene, device/track), and exposes them via SSE.
- `BitwigApiFacade` is the Bitwig API abstraction layer; all MCP tools call into features that sit on top of the facade, ensuring Bitwig API usage remains localized and testable.

By tracing the tree above, you can see how the build config, server layer, MCP tools, docs, and BMAD workflows coexist in a single monorepo. Critical directories correspond to the backend requirements (source code, API routes/controllers, config patterns, docs, and automation). Future writers can use this map to find the next files to document or extend.
