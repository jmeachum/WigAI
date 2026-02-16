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

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
     * Returns the current idempotency cache instance.
     */
    static IdempotencyCache getIdempotencyCache() {
        return idempotencyCache;
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
        Map<String, Object> loggingParams = extractLoggingParameters(arguments);

        // Idempotency dedupe: use raw (un-truncated) request_id for cache keying (opt-in)
        String rawRequestId = arguments != null
                ? extractRawRequestId(arguments.get("request_id"))
                : null;

        if (rawRequestId != null && isMutatingOperation(operation)) {
            IdempotencyKey dedupeKey = new IdempotencyKey(operation, rawRequestId);
            String payloadFingerprint = computePayloadFingerprint(arguments);
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
                String sanitizedForLog = sanitizeRequestId(rawRequestId);
                logger.info(operationId, operation,
                        "Dedupe payload mismatch | request_id=" + sanitizedForLog
                                + " | rejecting mismatched replay");
                return createErrorResponse(ErrorCode.INVALID_PARAMETER, mismatchMessage, operation);
            }

            if (dedupeResult.cacheHit()) {
                String sanitizedForLog = sanitizeRequestId(rawRequestId);
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
     * Maximum length for request_id in log output (truncation-safe for logging only).
     * 256 chars is generous (standard UUID is 36 chars).
     */
    private static final int MAX_REQUEST_ID_LENGTH = 256;

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
     * Maximum length for request_id accepted for cache keying.
     * IDs exceeding this bound skip dedupe entirely (returned as null from extractRawRequestId)
     * to avoid oversized-key memory/CPU pressure while preserving collision-safe semantics.
     */
    static final int MAX_RAW_REQUEST_ID_LENGTH = 1024;

    /**
     * Known correlation-only keys that are not counted as business arguments.
     */
    private static final java.util.Set<String> CORRELATION_KEYS = java.util.Set.of("request_id");

    /**
     * Computes a collision-resistant SHA-256 digest of non-correlation arguments for payload
     * consistency enforcement. The fingerprint excludes {@code request_id} so that identical
     * business payloads produce the same digest regardless of correlation ID.
     * Arguments are canonicalized (sorted keys, deterministic value serialization) before hashing.
     *
     * @param arguments The raw tool arguments (may be null)
     * @return A hex-encoded SHA-256 digest of the non-correlation arguments, or empty string if null/empty
     */
    static String computePayloadFingerprint(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!CORRELATION_KEYS.contains(entry.getKey())) {
                sorted.put(entry.getKey(), entry.getValue());
            }
        }
        if (sorted.isEmpty()) {
            return "";
        }
        String canonical = canonicalizeValue(sorted);
        return sha256Hex(canonical);
    }

    /**
     * Recursively produces a deterministic canonical string for any value.
     * Uses typed, length-delimited segments so delimiter characters inside keys/values
     * cannot create canonicalization ambiguity.
     */
    private static String canonicalizeValue(Object value) {
        if (value == null) {
            return "n;";
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), e.getValue());
            }
            StringBuilder sb = new StringBuilder("m{");
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                String key = entry.getKey();
                String encodedValue = canonicalizeValue(entry.getValue());
                sb.append("k").append(key.length()).append(":").append(key);
                sb.append("v").append(encodedValue.length()).append(":").append(encodedValue).append(";");
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Collection<?> col) {
            StringBuilder sb = new StringBuilder("l[");
            for (Object item : col) {
                String encodedItem = canonicalizeValue(item);
                sb.append("i").append(encodedItem.length()).append(":").append(encodedItem).append(";");
            }
            sb.append("]");
            return sb.toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("a[");
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                String encodedItem = canonicalizeValue(Array.get(value, i));
                sb.append("i").append(encodedItem.length()).append(":").append(encodedItem).append(";");
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof String str) {
            return "s" + str.length() + ":" + str + ";";
        }
        if (value instanceof Number number) {
            return "d" + normalizeNumber(number) + ";";
        }
        if (value instanceof Boolean bool) {
            return bool ? "b1;" : "b0;";
        }
        String asString = String.valueOf(value);
        String className = value.getClass().getName();
        return "o" + className.length() + ":" + className
            + "v" + asString.length() + ":" + asString + ";";
    }

    private static String normalizeNumber(Number number) {
        if (number instanceof Byte || number instanceof Short
            || number instanceof Integer || number instanceof Long
            || number instanceof java.math.BigInteger) {
            return number.toString();
        }
        if (number instanceof BigDecimal bigDecimal) {
            return normalizeBigDecimal(bigDecimal);
        }
        if (number instanceof Float floatValue) {
            if (Float.isNaN(floatValue)) {
                return "NaN";
            }
            if (Float.isInfinite(floatValue)) {
                return floatValue > 0 ? "Infinity" : "-Infinity";
            }
            return normalizeBigDecimal(new BigDecimal(Float.toString(floatValue)));
        }
        if (number instanceof Double doubleValue) {
            if (Double.isNaN(doubleValue)) {
                return "NaN";
            }
            if (Double.isInfinite(doubleValue)) {
                return doubleValue > 0 ? "Infinity" : "-Infinity";
            }
            return normalizeBigDecimal(new BigDecimal(Double.toString(doubleValue)));
        }
        try {
            return normalizeBigDecimal(new BigDecimal(number.toString()));
        } catch (NumberFormatException ex) {
            return number.toString();
        }
    }

    private static String normalizeBigDecimal(BigDecimal bigDecimal) {
        BigDecimal normalized = bigDecimal.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    /**
     * Computes the SHA-256 hex digest of the given input string.
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in standard Java
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Extracts logging-safe parameters from tool arguments.
     * Includes correlation fields (request_id), argument count, and collection sizes — never actual values.
     *
     * @param arguments The raw tool arguments
     * @return A sanitized map safe for logging, or null if no logging parameters
     */
    static Map<String, Object> extractLoggingParameters(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }

        Map<String, Object> loggingParams = new java.util.LinkedHashMap<>();

        // Extract and sanitize request_id for correlation (AC 2, AC 3)
        Object requestId = arguments.get("request_id");
        String sanitizedRequestId = sanitizeRequestId(requestId);
        if (sanitizedRequestId != null) {
            loggingParams.put("request_id", sanitizedRequestId);
        }

        // Parameter summaries: count and shape only, no values (AC 5)
        int nonCorrelationArgCount = 0;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (CORRELATION_KEYS.contains(entry.getKey())) {
                continue;
            }
            nonCorrelationArgCount++;

            // For collection/map arguments, log count and nested shape — no values (AC 5)
            Object value = entry.getValue();
            if (value instanceof java.util.Collection<?> collection) {
                loggingParams.put(entry.getKey() + "_count", collection.size());
                // For collections containing Maps, log first item's keys for shape visibility
                if (!collection.isEmpty()) {
                    Object firstItem = collection.iterator().next();
                    if (firstItem instanceof Map<?, ?> itemMap) {
                        loggingParams.put(entry.getKey() + "_item_keys",
                            new java.util.TreeSet<>(itemMap.keySet().stream()
                                .map(Object::toString).toList()));
                    }
                }
            } else if (value instanceof Map<?, ?> mapValue) {
                loggingParams.put(entry.getKey() + "_keys",
                    new java.util.TreeSet<>(mapValue.keySet().stream()
                        .map(Object::toString).toList()));
            }
        }
        if (nonCorrelationArgCount > 0) {
            loggingParams.put("arg_count", nonCorrelationArgCount);
        }

        // Return null if no logging-relevant parameters found
        return loggingParams.isEmpty() ? null : loggingParams;
    }

    /**
     * Extracts the raw request_id for cache keying purposes.
     * Performs type, blank, length, and printability checks.
     * IDs exceeding {@link #MAX_RAW_REQUEST_ID_LENGTH} are rejected (return null)
     * to avoid oversized-key memory/CPU pressure.
     * IDs containing control or non-printable characters (ASCII 0-31, 127) are rejected
     * to align key semantics with logging-safe correlation and avoid invisible-key ambiguity.
     *
     * @param requestId The raw request_id value from arguments
     * @return The raw request_id string, or null if invalid/absent/oversized/non-printable
     */
    static String extractRawRequestId(Object requestId) {
        if (requestId == null) {
            return null;
        }
        if (!(requestId instanceof String)) {
            return null;
        }
        String raw = (String) requestId;
        if (raw.isEmpty() || raw.isBlank()) {
            return null;
        }
        if (raw.length() > MAX_RAW_REQUEST_ID_LENGTH) {
            return null;
        }
        // Reject IDs containing non-printable-ASCII characters (strict 32..126 range)
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 32 || c > 126) {
                return null;
            }
        }
        return raw;
    }

    /**
     * Sanitizes request_id to prevent log injection and oversized payloads.
     * <ul>
     *   <li>Must be a String (rejects complex objects)</li>
     *   <li>Truncated to MAX_REQUEST_ID_LENGTH chars</li>
     *   <li>Control characters stripped to prevent log injection</li>
     * </ul>
     *
     * @param requestId The raw request_id value from arguments
     * @return Sanitized request_id string, or null if invalid/absent
     */
    static String sanitizeRequestId(Object requestId) {
        if (requestId == null) {
            return null;
        }

        // Type check: only accept String values
        if (!(requestId instanceof String)) {
            return null;
        }

        String rawId = (String) requestId;
        if (rawId.isEmpty() || rawId.isBlank()) {
            return null;
        }

        // Length limit to prevent oversized payloads
        if (rawId.length() > MAX_REQUEST_ID_LENGTH) {
            rawId = rawId.substring(0, MAX_REQUEST_ID_LENGTH);
        }

        // Strip control characters (ASCII 0-31, 127) to prevent log injection
        StringBuilder sanitized = new StringBuilder(rawId.length());
        for (int i = 0; i < rawId.length(); i++) {
            char c = rawId.charAt(i);
            if (c >= 32 && c != 127) {
                sanitized.append(c);
            }
        }

        String result = sanitized.toString();
        return result.isEmpty() ? null : result;
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
     * Converts a legacy error response to the new standardized format.
     *
     * @param errorMessage The legacy error message
     * @param operation The operation that failed
     * @return A standardized error response
     */
    public static McpSchema.CallToolResult upgradeLegacyErrorResponse(String errorMessage, String operation) {
        ErrorCode errorCode = ErrorCode.UNKNOWN_ERROR;

        // Try to determine error code from message
        if (errorMessage.toLowerCase().contains("not found")) {
            if (errorMessage.toLowerCase().contains("track")) {
                errorCode = ErrorCode.TRACK_NOT_FOUND;
            } else if (errorMessage.toLowerCase().contains("scene")) {
                errorCode = ErrorCode.SCENE_NOT_FOUND;
            } else if (errorMessage.toLowerCase().contains("clip")) {
                errorCode = ErrorCode.CLIP_NOT_FOUND;
            }
        } else if (errorMessage.toLowerCase().contains("invalid") || errorMessage.toLowerCase().contains("must be")) {
            errorCode = ErrorCode.INVALID_PARAMETER;
        } else if (errorMessage.toLowerCase().contains("device") && errorMessage.toLowerCase().contains("selected")) {
            errorCode = ErrorCode.DEVICE_NOT_SELECTED;
        }

        return createErrorResponse(errorCode, errorMessage, operation);
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
