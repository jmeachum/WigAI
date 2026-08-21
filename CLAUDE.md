# WigAI

Bitwig Studio extension / MCP server. Java 21, Gradle (Kotlin DSL). See
`docs/reference/` for architecture, tech-stack, and API details.

## Code Review

For the `orch-*` pipeline's Review phase, and for any review of `src/**/*.java`,
use the `java-reviewer` agent instead of the generic `code-reviewer`. Still add
`security-reviewer` whenever the diff touches a security trigger (auth,
user input, external calls, secrets), per the usual rule.

`bitwig-api-doc-scraper/` is a small TypeScript utility outside the main Java
codebase — use `typescript-reviewer` for changes scoped to that directory.
