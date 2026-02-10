# MCP Host Functional Test Matrix (Outside CI)

Last updated: 2026-02-10

## Purpose

Define a repeatable test matrix for WigAI's MCP server when running inside Bitwig.
This matrix complements CI unit/contract tests and is intended for local or lab execution, not GitHub Actions.

## Current Test Split

- CI path: `./gradlew test` (GitHub Actions runs this only).
- Outside-CI smoke path: `./gradlew mcpSmokeTest` (safe mode) and `WIGAI_SMOKE_TEST_MUTATIONS=true ./gradlew mcpSmokeTest` (mutation mode).
- No active `@Tag("host_required")` tests are currently present in `src/test`.

## Protocol-Level Coverage Matrix

| Area | Current Coverage | Gap | Recommended Host-Functional Coverage |
|---|---|---|---|
| `initialize` + `notifications/initialized` | Covered by `HttpMcpClient.initialize()` in smoke harness | No negative-path checks | Add tests for malformed initialize payload, unsupported protocol version, and missing follow-up notification behavior |
| Streamable HTTP session (`mcp-session-id`) | Covered in harness happy path | No explicit invalid/missing session checks | Add tests that assert server behavior for missing, stale, and cross-session IDs |
| `tools/list` discovery | Covered (raw JSON printed + baseline assertions) | No schema regression guard by fixture snapshot | Add fixture-based snapshot comparison for expected tool set and required `inputSchema` keys |
| JSON-RPC vs tool envelope errors | Covered for common harness paths | Limited protocol error-path breadth | Add explicit checks for `method not found`, parse errors, and bad params across representative tools |
| Transport/security HTTP headers | Not covered | Security regressions could go unnoticed | Add checks for `Origin` validation behavior and local-only exposure expectations |

## Tool Coverage Matrix

Legend:
- `Smoke`: covered today by `mcpSmokeTest`
- `Host`: recommended dedicated host-functional test (Bitwig fixture backed)

| Tool | Type | Smoke Coverage Today | Recommended Host Coverage (Outside CI) | Fixture / Preconditions | Priority |
|---|---|---|---|---|---|
| `status` | Read-only | Yes (safe mode call + envelope validation) | Verify fields reflect real DAW state changes before/after transport actions | Any project | P0 |
| `list_tracks` | Read-only | Yes | Assert deterministic order, names, and count against known fixture project | Fixture project with known tracks | P0 |
| `get_track_details` | Read-only | Yes | Validate all selectors (`track_index`, `track_name`, default selected) and selector conflict/error semantics | Fixture with selected track | P0 |
| `list_devices_on_track` | Read-only | Yes | Validate track selector variants and returned device metadata stability | Fixture with track/device inventory | P1 |
| `get_device_details` | Read-only | Yes | Validate track/device selector combinations and expected typed errors | Fixture with multiple devices | P1 |
| `list_scenes` | Read-only | Yes | Validate scene order, names, color formatting, and count | Fixture with named/colorized scenes | P0 |
| `get_clips_in_scene` | Read-only | Yes (including expected missing-param handling) | Validate scene by index/name returns expected clip states and indexes | Fixture with clips in multiple scenes | P0 |
| `get_selected_device_parameters` | Read-only | Yes (including expected `DEVICE_NOT_SELECTED`) | Validate returned parameter list and value/display consistency when a device is selected | Fixture with selected controllable device | P0 |
| `transport_start` | Mutating | Yes (mutation mode) | Assert transport state transition observable via `status.transport` | Any project | P0 |
| `transport_stop` | Mutating | Yes (mutation mode) | Assert transport stop transition and idempotent repeated stop behavior | Any project | P0 |
| `set_selected_device_parameter` | Mutating | Partial (optional round-trip path) | Validate read-after-write with tolerance, bounds errors, and unchanged unrelated params | Fixture with selected device and stable params | P0 |
| `set_selected_device_parameters` | Mutating | No | Validate mixed batch behavior (`success`/`error`) and partial-failure contract | Fixture with selected device | P1 |
| `session_launchSceneByIndex` | Mutating | No | Validate launched scene index and resulting playback/activation effects | Fixture with multiple scenes | P1 |
| `session_launchSceneByName` | Mutating | No | Validate exact name matching and expected not-found behavior | Fixture with unique scene names | P1 |
| `launch_clip` | Mutating | No | Validate clip launch on target track/index and typed errors for invalid targets | Fixture with populated clip slots | P1 |

## Recommended Fixture Set

- `fixture-empty`: empty project for baseline no-selection behavior.
- `fixture-arrangement-basic`: known tracks/scenes/clips for deterministic read checks.
- `fixture-device-focused`: selected device with stable parameters for mutation/read-after-write checks.

## Recommended Non-CI Task Layout

- Keep existing `mcpSmokeTest` as fast readiness gate.
- Add `hostFunctionalTest` task (excluded from default `test`) for fixture-backed scenario tests.
- Add optional `hostBurnInTest` for repeated runs to detect flaky behavior.

## Best-Practice Checks to Incorporate

- Lifecycle conformance: validate initialize flow and version negotiation behavior.
- Session conformance: validate strict handling of `mcp-session-id` lifecycle.
- Tool contract conformance: verify `tools/list` shape and `tools/call` error semantics.
- Security checks for local HTTP server deployments: origin validation, localhost binding, and auth expectations when remotely exposed.
- Run official MCP conformance suites where available for SDK/server maturity.

## References

- MCP lifecycle specification: <https://modelcontextprotocol.io/specification/2025-11-05/basic/lifecycle>
- MCP transport specification (Streamable HTTP, session, security notes): <https://modelcontextprotocol.io/specification/2025-11-05/basic/transports>
- MCP tools specification: <https://modelcontextprotocol.io/specification/2025-11-05/server/tools>
- MCP Inspector docs: <https://modelcontextprotocol.io/docs/tools/inspector>
- MCP Java SDK repository: <https://github.com/modelcontextprotocol/java-sdk>
- MCP Conformance project: <https://github.com/modelcontextprotocol/conformance>
- MCP SDK validation/tiering update (conformance mention): <https://modelcontextprotocol.io/blog/mcp-ecosystem-updates>
