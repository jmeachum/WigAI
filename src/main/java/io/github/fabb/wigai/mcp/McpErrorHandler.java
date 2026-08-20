package io.github.fabb.wigai.mcp;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.error.WigAIErrorHandler;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.retry.RetryExecutor;
import io.github.fabb.wigai.common.retry.RetryPolicy;
import io.github.fabb.wigai.mcp.idempotency.IdempotencyCache;
import io.github.fabb.wigai.mcp.idempotency.IdempotencyKey;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Centralized MCP error handling utility for consistent tool response formatting.
 * Ensures all MCP tools return standardized JSON response format with proper error handling.
 */
public class McpErrorHandler {

    /** System property key for idempotency TTL in milliseconds. */
    static final String PROP_IDEMPOTENCY_TTL_MILLIS = "wigai.idempotency.ttl.millis";

    /** System property key for idempotency max cache entries. */
    static final String PROP_IDEMPOTENCY_MAX_ENTRIES = "wigai.idempotency.max.entries";

    /**
     * Shared idempotency cache for mutating tool deduplication.
     * Reads TTL and max entries from system properties, falling back to defaults.
     */
    private static volatile IdempotencyCache idempotencyCache = createDefaultCache();

    /**
     * Creates an IdempotencyCache using system property overrides when present,
     * falling back to built-in defaults (TTL=60s, maxEntries=1000).
     * Invalid property values (non-numeric, non-positive) fail safe to defaults.
     */
    static IdempotencyCache createDefaultCache() {
        long ttl = parseLongProperty(PROP_IDEMPOTENCY_TTL_MILLIS, IdempotencyCache.DEFAULT_TTL_MILLIS);
        int maxEntries = parseIntProperty(PROP_IDEMPOTENCY_MAX_ENTRIES, IdempotencyCache.DEFAULT_MAX_ENTRIES);
        return new IdempotencyCache(ttl, maxEntries, System::currentTimeMillis);
    }

    /**
     * Parses a long system property, returning the default on missing/invalid values.
     */
    static long parseLongProperty(String key, long defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses an int system property, returning the default on missing/invalid values.
     */
    static int parseIntProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Replaces the idempotency cache instance (for testing).
     */
    static void setIdempotencyCache(IdempotencyCache cache) {
        idempotencyCache = cache;
    }

    /**
     * Creates a standardized MCP success response.
     *
     * @param data The success data to include in the response
     * @return A McpSchema.CallToolResult with success response
     */
    public static McpSchema.CallToolResult createSuccessResponse(Object data) {
        // For MCP tools, return the data directly as per API specification
        // The response format should match the API reference exactly
        Map<String, Object> response = Map.of(
            "status", "success",
            "data", data
        );
        String jsonResponse = WigAIErrorHandler.toJsonString(response);
        McpSchema.TextContent textContent = new McpSchema.TextContent(jsonResponse);
        return new McpSchema.CallToolResult(List.of(textContent), false);
    }

    /**
     * Creates a standardized MCP error response from a BitwigApiException.
     *
     * @param exception The BitwigApiException to convert
     * @param logger The structured logger for error recording
     * @return A McpSchema.CallToolResult with error response
     */
    public static McpSchema.CallToolResult createErrorResponse(BitwigApiException exception, StructuredLogger logger) {
        Map<String, Object> error = new java.util.LinkedHashMap<>();
        error.put("code", exception.getErrorCode().getCode());
        error.put("message", exception.getMessage());
        error.put("operation", exception.getOperation());

        Map<String, Object> details = normalizeErrorDetails(exception.getContext());
        if (!details.isEmpty()) {
            error.put("details", details);
        }

        // For MCP tools, return the error in the API format directly
        Map<String, Object> response = Map.of(
            "status", "error",
            "error", error
        );
        String jsonResponse = WigAIErrorHandler.toJsonString(response);
        McpSchema.TextContent textContent = new McpSchema.TextContent(jsonResponse);
        return new McpSchema.CallToolResult(List.of(textContent), true);
    }

    private static Map<String, Object> normalizeErrorDetails(Object context) {
        if (!(context instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    /**
     * Creates a standardized MCP error response from a generic exception.
     *
     * @param exception The exception to convert
     * @param operation The operation that failed
     * @param logger The structured logger for error recording
     * @return A McpSchema.CallToolResult with error response
     */
    public static McpSchema.CallToolResult createErrorResponse(Exception exception, String operation, StructuredLogger logger) {
        BitwigApiException bitwigException = BitwigApiException.fromException(operation, exception);
        return createErrorResponse(bitwigException, logger);
    }

    /**
     * Creates a standardized MCP error response with custom error details.
     *
     * @param errorCode The error code
     * @param message The error message
     * @param operation The operation that failed
     * @return A McpSchema.CallToolResult with error response
     */
    public static McpSchema.CallToolResult createErrorResponse(ErrorCode errorCode, String message, String operation) {
        Map<String, Object> response = Map.of(
            "status", "error",
            "error", Map.of(
                "code", errorCode.getCode(),
                "message", message,
                "operation", operation
            )
        );
        String jsonResponse = WigAIErrorHandler.toJsonString(response);
        McpSchema.TextContent textContent = new McpSchema.TextContent(jsonResponse);
        return new McpSchema.CallToolResult(List.of(textContent), true);
    }

    /**
     * Executes a tool operation with standardized error handling and response formatting.
     * Uses no-retry policy — intended for read-only / non-mutating tool paths.
     *
     * @param operation The operation name for error context
     * @param logger The structured logger
     * @param task The tool operation to execute
     * @return A McpSchema.CallToolResult with success or error response
     */
    public static McpSchema.CallToolResult executeWithErrorHandling(String operation, StructuredLogger logger, ToolOperation task) {
        return executeWithErrorHandling(operation, null, logger, RetryPolicy.NONE, task);
    }

    /**
     * Executes a tool operation with standardized error handling, response formatting, request_id correlation,
     * and bounded retry for transient failures. Uses the default retry policy for mutating tools.
     *
     * @param operation The operation name for error context
     * @param arguments The tool arguments (may contain request_id for correlation)
     * @param logger The structured logger
     * @param task The tool operation to execute
     * @return A McpSchema.CallToolResult with success or error response
     */
    public static McpSchema.CallToolResult executeWithErrorHandling(
            String operation,
            Map<String, Object> arguments,
            StructuredLogger logger,
            ToolOperation task) {
        return executeWithErrorHandling(operation, arguments, logger, RetryPolicy.DEFAULT, task);
    }

    /**
     * Executes a tool operation with standardized error handling, response formatting, request_id correlation,
     * and bounded retry according to the specified policy.
     * <p>
     * Retry behavior:
     * <ul>
     *   <li>Retryable failures (transient host-state/timing): retried up to maxAttempts with backoff</li>
     *   <li>Non-retryable failures (validation/state errors): fail fast with zero retries</li>
     *   <li>Total runtime bounded by policy totalTimeoutMs</li>
     * </ul>
     *
     * @param operation The operation name for error context
     * @param arguments The tool arguments (may contain request_id for correlation)
     * @param logger The structured logger
     * @param retryPolicy The retry policy to apply
     * @param task The tool operation to execute
     * @return A McpSchema.CallToolResult with success or error response
     */
    public static McpSchema.CallToolResult executeWithErrorHandling(
            String operation,
            Map<String, Object> arguments,
            StructuredLogger logger,
            RetryPolicy retryPolicy,
            ToolOperation task) {

        String operationId = logger.generateOperationId();
        Map<String, Object> loggingParams = RequestContextExtractor.extractLoggingParameters(arguments);

        // Idempotency dedupe: use raw (un-truncated) request_id for cache keying (opt-in)
        String rawRequestId = arguments != null
                ? RequestContextExtractor.extractRawRequestId(arguments.get("request_id"))
                : null;

        if (rawRequestId != null && isMutatingOperation(operation)) {
            IdempotencyKey dedupeKey = new IdempotencyKey(operation, rawRequestId);
            String payloadFingerprint = PayloadFingerprint.computePayloadFingerprint(arguments);
            Supplier<McpSchema.CallToolResult> computation = () ->
                    executeOperation(operation, logger, operationId, loggingParams, retryPolicy, task);

            IdempotencyCache.DedupeResult dedupeResult =
                    idempotencyCache.getOrCompute(dedupeKey, payloadFingerprint, computation);

            if (dedupeResult.payloadMismatch()) {
                String mismatchMessage = "request_id reused with different payload; "
                        + "each request_id must map to a single unique request";
                StructuredLogger.TimedOperation timedOperation =
                        logger.startTimedOperation(operationId, operation, loggingParams);
                timedOperation.failure(ErrorCode.INVALID_PARAMETER, mismatchMessage);
                String sanitizedForLog = RequestContextExtractor.sanitizeRequestId(rawRequestId);
                logger.info(operationId, operation,
                        "Dedupe payload mismatch | request_id=" + sanitizedForLog
                                + " | rejecting mismatched replay");
                return createErrorResponse(ErrorCode.INVALID_PARAMETER, mismatchMessage, operation);
            }

            if (dedupeResult.cacheHit()) {
                String sanitizedForLog = RequestContextExtractor.sanitizeRequestId(rawRequestId);
                String outcome = dedupeResult.result().isError() ? "error" : "success";
                logger.info(operationId, operation,
                        "Dedupe hit | request_id=" + sanitizedForLog
                                + " | outcome=" + outcome
                                + " | returning cached result");
            }
            return dedupeResult.result();
        }

        // Non-dedupe path (no request_id)
        return executeOperation(operation, logger, operationId, loggingParams, retryPolicy, task);
    }

    /**
     * Executes the tool operation with retry, error handling, and timed logging.
     * Extracted to share between dedupe and non-dedupe paths.
     */
    private static McpSchema.CallToolResult executeOperation(
            String operation,
            StructuredLogger logger,
            String operationId,
            Map<String, Object> loggingParams,
            RetryPolicy retryPolicy,
            ToolOperation task) {

        StructuredLogger.TimedOperation timedOperation = logger.startTimedOperation(operationId, operation, loggingParams);

        try {
            RetryExecutor.RetryResult<Object> retryResult = RetryExecutor.executeWithRetry(
                operation, retryPolicy, logger, operationId, loggingParams,
                task::execute
            );
            timedOperation.success(retryResult.value());
            return createSuccessResponse(retryResult.value());
        } catch (BitwigApiException e) {
            timedOperation.failure(e.getErrorCode(), e.getMessage());
            // Always use the provided operation name (MCP tool name), not the exception's internal operation.
            // Preserve exception context for structured error details when available.
            BitwigApiException normalized = new BitwigApiException(
                e.getErrorCode(),
                operation,
                e.getMessage(),
                e.getContext(),
                e
            );
            return createErrorResponse(normalized, logger);
        } catch (Exception e) {
            ErrorCode errorCode = ErrorCode.fromException(e);
            timedOperation.failure(errorCode, e.getMessage());
            return createErrorResponse(e, operation, logger);
        }
    }

    /**
     * Explicit mutating operation allowlist for idempotency dedupe.
     * Shared execution path enforces dedupe only for these operations.
     */
    private static final java.util.Set<String> MUTATING_OPERATIONS = java.util.Set.of(
        "transport_start",
        "transport_stop",
        "launch_clip",
        "session_launchSceneByIndex",
        "session_launchSceneByName",
        "set_selected_device_parameter",
        "set_selected_device_parameters"
    );

    static boolean isMutatingOperation(String operation) {
        return operation != null && MUTATING_OPERATIONS.contains(operation);
    }

    static java.util.Set<String> mutatingOperationsForTest() {
        return MUTATING_OPERATIONS;
    }

    /**
     * Executes a tool operation with parameter validation and standardized error handling.
     *
     * @param operation The operation name for error context
     * @param arguments The tool arguments to validate
     * @param logger The structured logger
     * @param validator The parameter validation function
     * @param task The tool operation to execute with validated parameters
     * @return A McpSchema.CallToolResult with success or error response
     */
    public static <T> McpSchema.CallToolResult executeWithValidation(
            String operation,
            Map<String, Object> arguments,
            StructuredLogger logger,
            ParameterValidator<T> validator,
            ToolOperationWithParams<T> task) {
        RetryPolicy retryPolicy = isMutatingOperation(operation) ? RetryPolicy.DEFAULT : RetryPolicy.NONE;
        return executeWithErrorHandling(
            operation,
            arguments,
            logger,
            retryPolicy,
            () -> {
                T validatedParams = validator.validate(arguments, operation);
                return task.execute(validatedParams);
            }
        );
    }

    /**
     * Functional interface for tool operations that return a result.
     */
    @FunctionalInterface
    public interface ToolOperation {
        Object execute() throws Exception;
    }

    /**
     * Functional interface for tool operations that take validated parameters.
     */
    @FunctionalInterface
    public interface ToolOperationWithParams<T> {
        Object execute(T validatedParams) throws Exception;
    }

    /**
     * Functional interface for parameter validation.
     */
    @FunctionalInterface
    public interface ParameterValidator<T> {
        T validate(Map<String, Object> arguments, String operation) throws BitwigApiException;
    }
}
