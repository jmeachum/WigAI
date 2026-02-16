package io.github.fabb.wigai.smoke;

import io.github.fabb.wigai.common.AppConstants;

import java.time.Duration;

/**
 * CLI entrypoint for host-backed timing-stress validation.
 *
 * Usage:
 *   java McpTimingStressHarnessMain [--host HOST] [--port PORT] [--endpoint PATH] [--deadline-ms N]
 *
 * Exit codes:
 *   0 = all checks passed
 *   1 = one or more checks failed or connection error
 */
public final class McpTimingStressHarnessMain {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = AppConstants.DEFAULT_MCP_PORT;
    private static final String DEFAULT_ENDPOINT = "/mcp";
    private static final Duration DEFAULT_CLIENT_TIMEOUT = Duration.ofSeconds(30);

    private McpTimingStressHarnessMain() {}

    public static void main(String[] args) {
        TimingCliArgs cliArgs = parseArgs(args);

        McpSmokeHarnessArgs connectionArgs = new McpSmokeHarnessArgs(
                cliArgs.host(),
                cliArgs.port(),
                cliArgs.endpoint(),
                false
        );

        McpClient client = new HttpMcpClient(connectionArgs.resolvedUrl(), DEFAULT_CLIENT_TIMEOUT);
        McpTimingStressHarness harness = new McpTimingStressHarness();

        int exitCode = harness.run(client, cliArgs.deadline(), System.out, System.err);
        System.exit(exitCode);
    }

    static TimingCliArgs parseArgs(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        String endpoint = DEFAULT_ENDPOINT;
        Duration deadline = McpTimingStressHarness.DEFAULT_DEADLINE;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--host", "-h" -> {
                    if (i + 1 < args.length) {
                        host = args[++i];
                    }
                }
                case "--port", "-p" -> {
                    if (i + 1 < args.length) {
                        String portArg = args[++i];
                        try {
                            port = Integer.parseInt(portArg);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid port: " + portArg + " (expected numeric)");
                            port = DEFAULT_PORT;
                        }
                    }
                }
                case "--endpoint", "-e" -> {
                    if (i + 1 < args.length) {
                        endpoint = args[++i];
                    }
                }
                case "--deadline-ms", "-d" -> {
                    if (i + 1 < args.length) {
                        String deadlineArg = args[++i];
                        try {
                            long millis = Long.parseLong(deadlineArg);
                            if (millis > 0) {
                                deadline = Duration.ofMillis(millis);
                            } else {
                                System.err.println("Invalid deadline-ms: " + deadlineArg + " (must be > 0)");
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid deadline-ms: " + deadlineArg + " (expected numeric)");
                        }
                    }
                }
                case "--help" -> {
                    printHelp();
                    System.exit(0);
                }
            }
        }

        return new TimingCliArgs(host, port, endpoint, deadline);
    }

    private static void printHelp() {
        System.out.println("""
            MCP Timing-Stress Harness

            Usage: java McpTimingStressHarnessMain [OPTIONS]

            Options:
              --host, -h HOST         MCP server host (default: localhost)
              --port, -p PORT         MCP server port (default: 61169)
              --endpoint, -e PATH     MCP endpoint path (default: /mcp)
              --deadline-ms, -d N     Per-tool deadline in milliseconds (default: 5000)
              --help                  Show this help message

            Exit Codes:
              0   All checks passed
              1   One or more checks failed
            """);
    }

    record TimingCliArgs(String host, int port, String endpoint, Duration deadline) {}
}
