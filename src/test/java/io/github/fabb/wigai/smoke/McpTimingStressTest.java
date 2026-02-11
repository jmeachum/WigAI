package io.github.fabb.wigai.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static io.github.fabb.wigai.smoke.McpSmokeHarnessTestSupport.BASELINE_TOOLS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Story 1.5 AC 5 — timing-stress harness extension.
 * Verifies that tools do not hang and that failures are surfaced as bounded, actionable errors.
 * Uses fake clients; no live Bitwig instance required.
 */
class McpTimingStressTest {

    @DisplayName("1.5-TS-000a [P1] Given MCP init failure, when timing-stress runs, then fail fast")
    @Test
    void init_failure_fails_fast() {
        McpClient client = new FakeMcpClient(BASELINE_TOOLS) {
            @Override
            public void initialize() {
                throw new RuntimeException("init failed");
            }
        };

        TimingRun run = runTimingStress(client, Duration.ofSeconds(5));

        assertEquals(1, run.exitCode, "Should fail fast when MCP initialization fails");
        assertTrue(run.stderr.contains("MCP initialization failed"));
        assertTrue(run.stderr.contains("init failed"));
    }

    @DisplayName("1.5-TS-000b [P1] Given tools/list failure, when timing-stress runs, then fail fast")
    @Test
    void tools_list_failure_fails_fast() {
        McpClient client = new FakeMcpClient(BASELINE_TOOLS) {
            @Override
            public String listToolsRaw() {
                throw new RuntimeException("tools/list failed");
            }
        };

        TimingRun run = runTimingStress(client, Duration.ofSeconds(5));

        assertEquals(1, run.exitCode, "Should fail fast when tools/list fails");
        assertTrue(run.stderr.contains("tools/list failed"));
    }

    @DisplayName("1.5-TS-001 [P1] Given all tools respond within deadline, when timing-stress runs, then pass")
    @Test
    void all_tools_within_deadline_passes() {
        McpClient client = new FakeMcpClient(BASELINE_TOOLS);

        TimingRun run = runTimingStress(client, Duration.ofSeconds(5));

        assertEquals(0, run.exitCode, "Should pass when all tools respond within deadline");
        assertTrue(run.stdout.contains("TIMING-STRESS PASS"));
        assertTrue(run.stdout.contains("Passed: " + BASELINE_TOOLS.size()));
        assertTrue(run.stdout.contains("Failed: 0"));
    }

    @DisplayName("1.5-TS-002 [P1] Given a tool exceeds deadline, when timing-stress runs, then fail with bounded error")
    @Test
    void tool_exceeding_deadline_fails() {
        // Use a very short deadline so the slow tool exceeds it
        McpClient client = new FakeMcpClient(BASELINE_TOOLS) {
            @Override
            public String callTool(String toolName, Map<String, Object> arguments) {
                if ("status".equals(toolName)) {
                    try {
                        Thread.sleep(50); // Will exceed the 10ms deadline
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return super.callTool(toolName, arguments);
            }
        };

        TimingRun run = runTimingStress(client, Duration.ofMillis(10));

        assertEquals(1, run.exitCode, "Should fail when a tool exceeds the deadline");
        assertTrue(run.stderr.contains("exceeded deadline"), "Should report deadline exceeded");
        assertTrue(run.stderr.contains("status"), "Should name the slow tool");
    }

    @DisplayName("1.5-TS-003 [P1] Given a tool returns invalid envelope, when timing-stress runs, then fail")
    @Test
    void tool_returning_invalid_envelope_fails() {
        McpClient client = new FakeMcpClient(BASELINE_TOOLS) {
            @Override
            public String callTool(String toolName, Map<String, Object> arguments) {
                if ("status".equals(toolName)) {
                    // Raw exception text — not a valid JSON envelope
                    return "NullPointerException at BitwigApiFacade.java:42";
                }
                return super.callTool(toolName, arguments);
            }
        };

        TimingRun run = runTimingStress(client, Duration.ofSeconds(5));

        assertEquals(1, run.exitCode, "Should fail when a tool returns invalid envelope");
        assertTrue(run.stderr.contains("invalid envelope"), "Should report invalid envelope");
        assertTrue(run.stderr.contains("status"), "Should name the failing tool");
    }

    @DisplayName("1.5-TS-004 [P1] Given a tool returns typed error within deadline, when timing-stress runs, then pass")
    @Test
    void typed_error_within_deadline_is_actionable_and_passes() {
        McpClient client = new FakeMcpClient(BASELINE_TOOLS) {
            @Override
            public String callTool(String toolName, Map<String, Object> arguments) {
                if ("get_selected_device_parameters".equals(toolName)) {
                    return """
                        {"status":"error","error":{"code":"DEVICE_NOT_SELECTED","message":"No device selected","operation":"get_selected_device_parameters"}}
                        """.trim();
                }
                return super.callTool(toolName, arguments);
            }
        };

        TimingRun run = runTimingStress(client, Duration.ofSeconds(5));

        assertEquals(0, run.exitCode, "Typed errors are bounded and actionable — should pass");
        assertTrue(run.stdout.contains("DEVICE_NOT_SELECTED"), "Should report the error code");
    }

    @DisplayName("1.5-TS-005 [P1] Given a tool throws exception, when timing-stress runs, then fail with timing info")
    @Test
    void tool_throwing_exception_reports_failure_with_timing() {
        McpClient client = new FakeMcpClient(BASELINE_TOOLS) {
            @Override
            public String callTool(String toolName, Map<String, Object> arguments) {
                if ("list_tracks".equals(toolName)) {
                    throw new RuntimeException("Connection refused");
                }
                return super.callTool(toolName, arguments);
            }
        };

        TimingRun run = runTimingStress(client, Duration.ofSeconds(5));

        assertEquals(1, run.exitCode, "Should fail when a tool throws an exception");
        assertTrue(run.stderr.contains("list_tracks"), "Should name the failing tool");
        assertTrue(run.stderr.contains("Connection refused"), "Should include exception message");
        assertTrue(run.stderr.contains("ms"), "Should include timing information");
    }

    @DisplayName("1.5-TS-006 [P1] Given timing-stress output, when inspected, then per-tool timing is reported")
    @Test
    void per_tool_timing_is_reported_in_output() {
        McpClient client = new FakeMcpClient(BASELINE_TOOLS);

        TimingRun run = runTimingStress(client, Duration.ofSeconds(5));

        assertEquals(0, run.exitCode);
        // Each tool should have timing in parentheses
        for (String tool : BASELINE_TOOLS) {
            assertTrue(run.stdout.contains(tool), "Should mention tool: " + tool);
        }
        // Summary should show counts
        assertTrue(run.stdout.contains("Passed:"), "Should show pass count");
        assertTrue(run.stdout.contains("Failed:"), "Should show fail count");
        assertTrue(run.stdout.contains("Total:"), "Should show total count");
    }

    @DisplayName("1.5-TS-007 [P1] Given default deadline, then it is 5 seconds (bounded by retry total timeout)")
    @Test
    void default_deadline_is_bounded() {
        assertEquals(Duration.ofSeconds(5), McpTimingStressHarness.DEFAULT_DEADLINE,
            "Default deadline should be 5s, well above the 2s retry total timeout");
    }

    private static TimingRun runTimingStress(McpClient client, Duration deadline) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        PrintStream stdout = new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8);
        PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8);

        McpTimingStressHarness harness = new McpTimingStressHarness();
        int exitCode = harness.run(client, deadline, stdout, stderr);

        return new TimingRun(exitCode,
            new String(stdoutBytes.toByteArray(), StandardCharsets.UTF_8),
            new String(stderrBytes.toByteArray(), StandardCharsets.UTF_8));
    }

    private record TimingRun(int exitCode, String stdout, String stderr) {}
}
