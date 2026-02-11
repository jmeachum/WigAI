package io.github.fabb.wigai.smoke;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Timing-stress harness extension for Story 1.5 AC 5.
 *
 * Validates that MCP tools complete within a bounded deadline and that failures
 * are surfaced as actionable errors (proper JSON envelope) rather than hangs or
 * Bitwig instability. Intended for manual or scripted host-required validation.
 *
 * Exit codes: 0 = all tools completed within deadline, 1 = timeout or envelope failure.
 */
public final class McpTimingStressHarness {

    /**
     * Default per-tool deadline. Tools backed by bounded retry (2000ms total timeout)
     * should complete well within this window even with retries.
     */
    static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(5);

    /**
     * Runs timing-stress validation on all baseline tools.
     * Each tool call is timed and must complete within the deadline.
     * Envelope format is validated to confirm errors are actionable (not raw exceptions).
     *
     * @param client   The MCP client to use
     * @param deadline Maximum allowed duration per tool call
     * @param out      Stdout for progress/results
     * @param err      Stderr for failures
     * @return 0 if all tools pass, 1 if any tool exceeds deadline or returns invalid envelope
     */
    public int run(McpClient client, Duration deadline, PrintStream out, PrintStream err) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        out.println("=== MCP Timing-Stress Harness ===");
        out.println("Per-tool deadline: " + deadline.toMillis() + "ms");
        out.println();

        // Initialize session
        try {
            client.initialize();
        } catch (Exception e) {
            err.println("FAIL: MCP initialization failed: " + e.getMessage());
            return 1;
        }

        // Discover tools
        List<String> tools;
        try {
            String rawJson = client.listToolsRaw();
            McpSmokeHarness harness = new McpSmokeHarness();
            tools = harness.parseToolNamesFromRaw(rawJson);
        } catch (Exception e) {
            err.println("FAIL: tools/list failed: " + e.getMessage());
            return 1;
        }

        out.println("Discovered tools: " + tools);
        out.println();

        int passed = 0;
        int failed = 0;
        long deadlineMs = deadline.toMillis();

        for (String tool : tools) {
            long startNs = System.nanoTime();
            String response;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> client.callTool(tool, Map.of()));
            try {
                response = future.get(deadlineMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                err.println("FAIL: " + tool + " timed out after " + elapsedMs + "ms (deadline: " + deadlineMs + "ms)");
                failed++;
                continue;
            } catch (Exception e) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                err.println("FAIL: " + tool + " threw exception after " + elapsedMs + "ms: " + cause.getMessage());
                failed++;
                continue;
            }
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            // Validate envelope format (must be actionable, not raw exception text)
            McpSmokeHarness envelopeParser = new McpSmokeHarness();
            McpSmokeHarness.EnvelopeResult envelope = envelopeParser.parseEnvelope(response);

            if (!envelope.isValidEnvelope()) {
                err.println("FAIL: " + tool + " returned invalid envelope after " + elapsedMs + "ms: " + envelope.errorMessage());
                failed++;
                continue;
            }

            // Both success and typed errors are acceptable — they're bounded and actionable
            String status = envelope.isError()
                ? "error [" + envelope.errorCode() + "]"
                : "success";
            out.println("OK: " + tool + " → " + status + " (" + elapsedMs + "ms)");
            passed++;
        }

        out.println();
        out.println("--- Timing-Stress Summary ---");
        out.println("Passed: " + passed + " | Failed: " + failed + " | Total: " + tools.size());

        if (failed > 0) {
            err.println("FAIL: " + failed + " tool(s) exceeded deadline or returned invalid envelope");
            return 1;
        }

        out.println("=== TIMING-STRESS PASS ===");
        return 0;
    }
}
