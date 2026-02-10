package io.github.fabb.wigai.mcp;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.error.WigAIErrorHandler;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * Centralized MCP error handling utility for consistent tool response formatting.
 * Ensures all MCP tools return standardized JSON response format with proper error handling.
 */
public class McpErrorHandler {

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
        // For MCP tools, return the error in the API format directly
        Map<String, Object> response = Map.of(
            "status", "error",
            "error", Map.of(
                "code", exception.getErrorCode().getCode(),
                "message", exception.getMessage(),
                "operation", exception.getOperation()
            )
        );
        String jsonResponse = WigAIErrorHandler.toJsonString(response);
        McpSchema.TextContent textContent = new McpSchema.TextContent(jsonResponse);
        return new McpSchema.CallToolResult(List.of(textContent), true);
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
     *
     * @param operation The operation name for error context
     * @param logger The structured logger
     * @param task The tool operation to execute
     * @return A McpSchema.CallToolResult with success or error response
     */
    public static McpSchema.CallToolResult executeWithErrorHandling(String operation, StructuredLogger logger, ToolOperation task) {
        return executeWithErrorHandling(operation, null, logger, task);
    }

    /**
     * Executes a tool operation with standardized error handling, response formatting, and request_id correlation.
     * This overload accepts tool arguments to extract request_id for logging correlation.
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

        String operationId = logger.generateOperationId();
        Map<String, Object> loggingParams = extractLoggingParameters(arguments);
        StructuredLogger.TimedOperation timedOperation = logger.startTimedOperation(operationId, operation, loggingParams);

        try {
            Object result = task.execute();
            timedOperation.success(result);
            return createSuccessResponse(result);
        } catch (BitwigApiException e) {
            timedOperation.failure(e.getErrorCode(), e.getMessage());
            // Always use the provided operation name (MCP tool name), not the exception's internal operation
            return createErrorResponse(e.getErrorCode(), e.getMessage(), operation);
        } catch (Exception e) {
            ErrorCode errorCode = ErrorCode.fromException(e);
            timedOperation.failure(errorCode, e.getMessage());
            return createErrorResponse(e, operation, logger);
        }
    }

    /**
     * Maximum length for request_id to prevent oversized log payloads.
     * 256 chars is generous (standard UUID is 36 chars).
     */
    private static final int MAX_REQUEST_ID_LENGTH = 256;

    /**
     * Known correlation-only keys that are not counted as business arguments.
     */
    private static final java.util.Set<String> CORRELATION_KEYS = java.util.Set.of("request_id");

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

        String operationId = logger.generateOperationId();
        Map<String, Object> loggingParams = extractLoggingParameters(arguments);
        StructuredLogger.TimedOperation timedOperation = logger.startTimedOperation(operationId, operation, loggingParams);

        try {
            // Validate parameters
            T validatedParams = validator.validate(arguments, operation);

            // Execute operation with validated parameters
            Object result = task.execute(validatedParams);

            timedOperation.success(result);
            return createSuccessResponse(result);
        } catch (BitwigApiException e) {
            timedOperation.failure(e.getErrorCode(), e.getMessage());
            // Always use the provided operation name (MCP tool name), not the exception's internal operation
            return createErrorResponse(e.getErrorCode(), e.getMessage(), operation);
        } catch (Exception e) {
            ErrorCode errorCode = ErrorCode.fromException(e);
            timedOperation.failure(errorCode, e.getMessage());
            return createErrorResponse(e, operation, logger);
        }
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
