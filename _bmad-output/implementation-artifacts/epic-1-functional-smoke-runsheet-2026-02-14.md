# Epic 1 Functional Smoke Run Sheet

Date: 2026-02-14  
Scope: Epic 1 stories `1.1` through `1.7`

## Objective

Execute a repeatable verification flow that proves Epic 1 behavior is still working before Epic 2 kickoff.

## Preconditions

1. Bitwig Studio is running with WigAI enabled.
2. MCP endpoint is reachable (default `http://localhost:61169/mcp`).
3. Java 21 is installed.
4. Repo root is current working directory.

## Evidence Folder

Create an evidence folder for this run:

```bash
mkdir -p _bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14
```

## Step-by-Step Execution

### Step 1: Baseline CI-safe Epic 1 Regression Pack

Run targeted tests that cover Epic 1 contracts:

```bash
./gradlew test \
  --tests "io.github.fabb.wigai.config.PreferencesBackedConfigManagerAtddTest" \
  --tests "io.github.fabb.wigai.server.JettyServerManagerUrlFormattingTest" \
  --tests "io.github.fabb.wigai.mcp.tool.BaselineToolEnvelopeAtddTest" \
  --tests "io.github.fabb.wigai.common.logging.StructuredLoggerTest" \
  --tests "io.github.fabb.wigai.common.retry.RetryPolicyTest" \
  --tests "io.github.fabb.wigai.common.retry.RetryExecutorTest" \
  --tests "io.github.fabb.wigai.contract.ErrorContractComplianceTest" \
  --tests "io.github.fabb.wigai.mcp.McpErrorHandlerTest" \
  | tee _bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/01-epic1-targeted-tests.log
```

Pass criteria:
1. Build completes successfully.
2. No test failures in the selected classes.

### Step 2: Safe-Mode Host Smoke (Read-only)

```bash
./gradlew mcpSmokeTest \
  | tee _bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/02-safe-smoke.log
```

Pass criteria:
1. Exit code `0`.
2. `tools/list` succeeds and baseline read-only tools are present.
3. Envelope parsing passes for read-only calls.

### Step 3: Mutation-Mode Host Smoke

```bash
WIGAI_SMOKE_TEST_MUTATIONS=true ./gradlew mcpSmokeTest \
  | tee _bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/03-mutation-smoke.log
```

Pass criteria:
1. Exit code `0`.
2. `transport_start` and `transport_stop` both succeed with valid envelopes.
3. `status.transport.playing` is `true` after start and `false` after stop.

### Step 4: Optional Host Timing Stress Check

```bash
./gradlew mcpTimingStressTest \
  | tee _bmad-output/implementation-artifacts/tests/epic-1-smoke-2026-02-14/04-timing-stress.log
```

Pass criteria:
1. Test passes with no timeout regressions.

### Step 5: Epic 1 Coverage Review

Use this mapping to confirm all Epic 1 story outcomes are represented:

| Story | Capability | Primary Evidence |
|---|---|---|
| 1.1 | Repeatable smoke harness and checklist | `02-safe-smoke.log`, `03-mutation-smoke.log` |
| 1.2 | Localhost defaults + loopback guardrails | `01-epic1-targeted-tests.log` (`PreferencesBackedConfigManagerAtddTest`, `JettyServerManagerUrlFormattingTest`) |
| 1.3 | Standard response envelope contract | `01-epic1-targeted-tests.log` (`BaselineToolEnvelopeAtddTest`) + smoke logs |
| 1.4 | `request_id` correlation logging | `01-epic1-targeted-tests.log` (`StructuredLoggerTest`, `McpErrorHandlerTest`) |
| 1.5 | Non-blocking + bounded retry behavior | `01-epic1-targeted-tests.log` (`RetryPolicyTest`, `RetryExecutorTest`) |
| 1.6 | Canonical index error semantics | `01-epic1-targeted-tests.log` (`ErrorContractComplianceTest`) |
| 1.7 | Mutating-tool idempotency/dedupe | `01-epic1-targeted-tests.log` (`McpErrorHandlerTest`) |

### Step 6: Record Gate Evidence

Update `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`:
1. Set `G3` to `done` only when Steps 2 and 3 pass.
2. Attach log file paths in `Evidence / Notes`.
3. Keep `G1..G6` statuses synchronized with sprint planning status.

## Failure Handling

If any step fails:
1. Keep gate `G3` as `pending` or `in-progress`.
2. Capture failing command output in the evidence folder.
3. Open remediation work before marking Epic 2 kickoff ready.

## References

- `docs/engineering/mcp-smoke-test-runbook.md`
- `_bmad-output/implementation-artifacts/epic-1-retro-2026-02-13.md`
- `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`
