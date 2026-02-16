---
project_name: 'WigAI'
user_name: 'Josh'
date: '2025-12-18T04:56:02Z'
sections_completed: ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'quality_rules', 'workflow_rules', 'anti_patterns']
status: 'complete'
rule_count: 37
optimized_for_llm: true
existing_patterns_found: 10
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

- Language: Java 21
- Build: Gradle (Kotlin DSL)
- Bitwig Extension API: com.bitwig:extension-api:19
- MCP Java SDK: io.modelcontextprotocol.sdk:mcp-bom:0.11.0
- HTTP server: Jetty 11.0.20 (jetty-server, jetty-servlet)
- Servlet API: jakarta.servlet-api:6.0.0
- Testing: JUnit Jupiter 5.10.0 (Mockito used in unit tests)

## Critical Implementation Rules

### Language-Specific Rules (Java)

- Keep packages under `io.github.fabb.wigai.*` (match existing layout).
- Prefer existing patterns: MCP tools call controllers; controllers call `BitwigApiFacade`.
- Do not call Bitwig Extension API directly from MCP tool classes.
- Use `ParameterValidator` for required fields, ranges, and index validation.
- Avoid blocking operations on Bitwig/UI-sensitive paths; keep retries bounded.

### Framework-Specific Rules (MCP / Server)

- MCP tool names MUST be `snake_case` (e.g., `transport_start`, `launch_clip`).
- MCP tool JSON arguments MUST be `snake_case` (use `@JsonProperty("...")` in argument records).
- All tool handlers MUST use `McpErrorHandler.executeWithErrorHandling(...)` (no bespoke envelopes).
- Mutating tools MUST accept optional `request_id` and implement short-lived in-memory dedupe keyed by `(tool_name, request_id)`.
- Default server binding is loopback-only (`localhost` / `127.0.0.1` / `::1`); treat non-loopback configs as unsafe and explicitly intentional.
- Do not log full note payloads unless debug is explicitly enabled.

### Error Code Semantics (Single Source of Truth)

**This section is the canonical reference for error code definitions.** All other artifacts must reference this, not redefine:

| Artifact | Relationship | When Conflicts Arise |
|----------|--------------|---------------------|
| `ErrorCode.java` | Implements these definitions | Update code to match this doc |
| `api-reference.md` | References (links here) | Update docs to match this doc |
| `ErrorContractComplianceTest` | Enforces these definitions | Tests fail = fix implementation |
| Story error scenarios | Uses codes from this list | Story references this doc |
| Code-review checklists | Validates against this list | Reject review items that contradict |

**Canonical Error Codes:**

*Validation Errors (input validation failures):*
- `MISSING_REQUIRED_PARAMETER` — parameter not provided in request
- `EMPTY_PARAMETER` — parameter provided but empty (empty string, empty array)
- `INVALID_PARAMETER` — parameter has wrong type, malformed structure, or ambiguous/conflicting selector inputs that require explicit disambiguation (e.g., duplicate exact-name targets requiring `track_index` confirmation)
- `INVALID_PARAMETER_TYPE` — parameter type mismatch (e.g., string where number expected)
- `INVALID_PARAMETER_INDEX` — index argument outside valid bounds (e.g., parameter_index not in 0-7, track_index negative)
- `INVALID_RANGE` — numeric *value* outside allowed range (e.g., parameter value not in 0.0-1.0)

*State Errors (valid request but system state prevents operation):*
- `DEVICE_NOT_SELECTED` — operation requires a selected device but none is selected
- `DEVICE_NOT_FOUND` — specified device does not exist
- `TRACK_NOT_FOUND` — specified track does not exist
- `SCENE_NOT_FOUND` — specified scene does not exist
- `CLIP_NOT_FOUND` — specified clip does not exist

*Bitwig API Errors (external system failures):*
- `BITWIG_API_ERROR` — Bitwig API call failed (external system error)
- `TRANSPORT_ERROR` — transport operation failed

*System Errors (internal failures):*
- `INTERNAL_ERROR` — unexpected internal failure (code bug, not API issue)
- `OPERATION_FAILED` — catch-all for unclassified failures

**Index vs Range Clarification:**
- `INVALID_PARAMETER_INDEX` — for *index arguments* (track_index, scene_index, parameter_index, clip_index) when negative or exceeding valid bounds
- `INVALID_RANGE` — for *numeric values* (parameter value 0.0-1.0, tempo, etc.) when outside allowed range
- Rule: If the argument *selects an item by position*, use `INVALID_PARAMETER_INDEX`. If it *sets a numeric value*, use `INVALID_RANGE`.

**Response Envelope:**
- `error.operation` — always equals the MCP tool name, not internal method names

**When Adding New Error Codes:** Update this section FIRST, then propagate to code/docs/tests.

### Testing Rules

- Add/extend tests alongside code:
  - Tools: `src/test/java/io/github/fabb/wigai/mcp/tool/*ToolTest.java`
  - Controllers: `src/test/java/io/github/fabb/wigai/features/*ControllerTest.java`
- MCP response format MUST match `McpResponseTestUtils` expectations (no double-wrapping; `status` + `data|error`).
- Prefer unit tests with mocks for Bitwig-facing components; keep “Bitwig host required” behavior out of CI tests.
- Add regression tests for any bugfix affecting response envelopes, validation, or error codes.

### Code Quality & Style Rules

- Keep changes minimal and consistent with existing code patterns (tools/controllers/facade layering).
- Use `snake_case` for MCP JSON fields and tool names; Java identifiers remain standard Java style.
- Do not introduce new response envelope formats; always use the unified MCP error/response helpers.
- Prefer small, focused classes per tool; avoid “god tools” that mix unrelated responsibilities.

### Development Workflow Rules

- `main` is protected; merges to `main` trigger releases.
- Day-to-day work happens on `develop/cycle-*` and is promoted to `main` via PR at cycle completion.
- Use branch naming conventions per `docs/engineering/git-workflow.md` (e.g., `analysis/*`, `planning/*`, `solutioning/*`, `implementation/*`, `docs/*`, `hotfix/*`).
- CI: PR validation runs tests/build for code changes; docs-only changes skip tests but still report status checks.

### Critical Don't-Miss Rules

- Never overwrite existing clips by default; destructive operations require explicit opt-in flags (e.g., `overwrite=true`, `clear_existing=true`).
- Don’t assume Bitwig selection state is reliable for targeting; prefer explicit track/scene/slot addressing.
- Avoid double-creating/double-writing on retries: mutating tools must support `request_id` + dedupe.
- Do not log full note payloads unless debug is explicitly enabled.
- Do not block Bitwig responsiveness; keep retries bounded and errors actionable.

---

## Usage Guidelines

**For AI Agents:**

- Read this file before implementing any code.
- Follow ALL rules exactly as documented.
- When in doubt, prefer the more restrictive option.
- Update this file if new patterns emerge.

**For Humans:**

- Keep this file lean and focused on agent needs.
- Update when technology stack changes.
- Review quarterly for outdated rules.
- Remove rules that become obvious over time.

Last Updated: 2025-12-30
