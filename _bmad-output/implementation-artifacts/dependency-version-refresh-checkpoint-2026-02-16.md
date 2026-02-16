# Dependency/Version Refresh Checkpoint

Date: 2026-02-16
Story: 2-0-dependency-version-refresh-checkpoint-g6-closure
Gate: G6 (Epic 2 Kickoff)

## 1. Dependency Baseline Matrix

| Dependency | Current Pinned | Latest Available | Release Date | Recommended Action | Risk Notes |
|---|---|---|---|---|---|
| MCP Java SDK BOM | `0.11.0` | `0.17.2` | 2025-01-22 | **Defer** | Major version jump (0.11 -> 0.17). API breaking changes likely in transport/session layer. Requires dedicated upgrade story with migration testing against Bitwig runtime. |
| Jetty | `11.0.20` | `11.0.26` (final) | EOL 2025-01-01 | **Defer** | Jetty 11 line is EOL. Final release is 11.0.26. Upgrade to 11.0.26 is low-risk patch bump; migration to Jetty 12.x (latest 12.1.6) requires EE9->EE10 servlet migration and dedicated story. |
| JUnit Jupiter | `5.10.0` | `5.14.3` | 2025-02-15 | **Defer** | Test-only dependency. Upgrade is low-risk but not required for G6 closure. Can be bundled with next dependency refresh. |

### Transitive-Risk Notes

1. **Servlet API version conflict**: Jetty 11 bundles `jetty-jakarta-servlet-api:5.0.2` (Servlet 5.0 / EE9). We also explicitly declare `jakarta.servlet-api:6.0.0` (Servlet 6.0 / EE10). Both jars land on the classpath. Current runtime behavior is stable because WigAI's servlet usage is limited to MCP transport wiring, but upgrading to Jetty 12 (EE10-native) would resolve this dual-jar condition.
2. **SLF4J convergence**: Multiple dependencies declare SLF4J 2.0.x; Gradle resolves to `2.0.17`. No conflict.
3. **Jackson convergence**: MCP SDK and json-schema-validator both pull Jackson 2.18.3. No conflict.
4. **Reactor Core**: MCP SDK pulls `reactor-core:3.7.0`. No known compatibility issue with current stack.
5. **Bitwig Extension API 19**: Proprietary; no transitive dependencies. Version locked to Bitwig Studio release cycle.

## 2. Automated Regression Evidence

- Command: `./gradlew test --rerun`
- Result: **PASS** (696 tests, 0 failures, 100% success rate, 5.850s)
- Log: `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/01-gradle-test.log`

## 3. Host-Required Smoke Evidence

### Safe Smoke
- Command: `./gradlew mcpSmokeTest`
- Result: **PASS** (15 tools discovered, all baseline checks OK, `get_clips_in_scene` typed error MISSING_REQUIRED_PARAMETER as expected)
- Log: `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/02-safe-smoke.log`

### Mutation Smoke
- Command: `WIGAI_SMOKE_TEST_MUTATIONS=true ./gradlew mcpSmokeTest`
- Result: **PASS** (transport_start OK, `playing==true` attempt 2, transport_stop OK, `playing==false` attempt 2, device parameter round-trip OK)
- Log: `_bmad-output/implementation-artifacts/tests/epic-2-g6-checkpoint-2026-02-16/03-mutation-smoke.log`

## 4. Rollback Reference

- Baseline commit SHA: `8c297d5e0a86178b06815685634bab5a62045a1f`
- Branch: `implementation/story-2-0`
- Nearest tag: `0.3.1` (+92 commits)
- Rollback reference: `git reset --hard 8c297d5e0a86178b06815685634bab5a62045a1f` on branch `implementation/story-2-0`

## 5. Gate Closure

- G6 status: **done** (2026-02-16)
- Kickoff checklist updated: yes (`_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`)
- Sprint status synchronized: yes (`_bmad-output/implementation-artifacts/sprint-status.yaml`)
