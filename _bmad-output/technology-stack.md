# Technology Stack

## Part: main

| Category | Technology | Version | Justification |
|---|---|---|---|
| Project Type | Backend Bitwig extension (monolith) | N/A | Single Java codebase with `src/main/java` and Gradle build; extension lifecycle anchored in `WigAIExtension`. |
| Primary Language | Java | 21 (LTS) | `build.gradle.kts` sets `sourceCompatibility` and `targetCompatibility` to `JavaVersion.VERSION_21`. |
| Build System | Gradle (Kotlin DSL) | 8.13 wrapper | `gradle-wrapper.properties` pins `gradle-8.13-bin.zip`; build scripts use `.kts`. |
| Packaging | Shadow plugin + `.bwextension` artifact | Shadow 8.3.0 | `com.gradleup.shadow` creates fat JAR; custom `bwextension` task emits `build/extensions/WigAI.bwextension`. |
| Host Platform API | Bitwig Extension API | 19 | Dependency `com.bitwig:extension-api:19`; `WigAIExtensionDefinition#getRequiredAPIVersion()` returns `19`. |
| MCP Protocol SDK | MCP Java SDK BOM | 0.11.0 | `implementation(platform("io.modelcontextprotocol.sdk:mcp-bom:0.11.0"))` and `implementation("...:mcp")`. |
| HTTP Server | Jetty | 11.0.20 | `JettyServerManager` manages embedded server lifecycle; dependencies include `jetty-server` and `jetty-servlet`. |
| Servlet API | Jakarta Servlet API | 6.0.0 | Explicit dependency `jakarta.servlet:jakarta.servlet-api:6.0.0`; MCP servlet mounted at `/mcp`. |
| API Transport Style | Streamable HTTP / SSE for MCP | SDK-managed | `McpServerManager` uses `HttpServletStreamableServerTransportProvider` and serves MCP over HTTP endpoint. |
| Testing | JUnit Jupiter + MCP test support | JUnit 5.10.0 | `testImplementation` + `testRuntimeOnly` JUnit entries; dedicated `mcp-test` dependency and custom verification tasks. |
| CI Build Runtime | GitHub Actions + Temurin JDK | JDK 21 | `.github/workflows/build-and-test.yml` configures `actions/setup-java@v4` with `java-version: '21'`. |
| Release Automation | Nyx Gradle plugin | 3.1.4 | `settings.gradle.kts` applies `com.mooltiverse.oss.nyx` and configures GitHub publication. |
| Auxiliary Tooling | Node-based API docs scraper | Node module (package version 1.0.0) | `bitwig-api-doc-scraper/package.json` provides `scrape` scripts with `cheerio` and `javadocs-scraper`. |

## Stack Summary

WigAI runs as a Java 21 Bitwig controller extension that embeds a Jetty 11 HTTP server and exposes MCP tools through the MCP Java SDK (BOM 0.11.0). The delivery pipeline is Gradle-based (`bwextension` packaging) with GitHub Actions CI and Nyx-managed semantic releases.
