package io.github.fabb.wigai.common;

import com.bitwig.extension.controller.api.ControllerHost;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Simple logger implementation for the WigAI extension.
 * Uses Bitwig's ControllerHost.println for logging.
 */
public class Logger {
    private final ControllerHost host;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Creates a new Logger instance.
     *
     * @param host The Bitwig ControllerHost to use for logging
     */
    public Logger(ControllerHost host) {
        if (host == null) {
            throw new IllegalArgumentException("ControllerHost cannot be null");
        }
        this.host = host;
    }

    /**
     * Log an informational message.
     *
     * @param message The message to log
     */
    public void info(String message) {
        log("INFO", message);
    }

    /**
     * Log a warning message.
     *
     * @param message The message to log
     */
    public void warn(String message) {
        log("WARN", message);
    }

    /**
     * Log an error message.
     *
     * @param message The message to log
     */
    public void error(String message) {
        log("ERROR", message);
    }

    /**
     * Log a debug message.
     *
     * @param message The message to log
     */
    public void debug(String message) {
        log("DEBUG", message);
    }

    /**
     * Log an exception with an error message.
     *
     * @param message The error message
     * @param e       The exception to log
     */
    public void error(String message, Throwable e) {
        log("ERROR", message + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        // Print stack trace in a Bitwig-console friendly format
        for (StackTraceElement element : e.getStackTrace()) {
            log("ERROR", "at " + element.toString());
        }
    }

    private void log(String level, String message) {
        host.println("[" + getCurrentTimestamp() + "] [" + level + "] " + message);
    }

    private String getCurrentTimestamp() {
        return Instant.now().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER);
    }
}
