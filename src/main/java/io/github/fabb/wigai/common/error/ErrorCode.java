package io.github.fabb.wigai.common.error;

/**
 * Comprehensive error classification system for WigAI.
 * Provides consistent error codes across all system layers.
 */
public enum ErrorCode {
    // Validation Errors
    INVALID_PARAMETER("INVALID_PARAMETER", "Invalid parameter value provided"),
    INVALID_PARAMETER_INDEX("INVALID_PARAMETER_INDEX", "Parameter index out of valid range"),
    MISSING_REQUIRED_PARAMETER("MISSING_REQUIRED_PARAMETER", "Required parameter is missing"),
    INVALID_PARAMETER_TYPE("INVALID_PARAMETER_TYPE", "Parameter has incorrect type"),
    INVALID_RANGE("INVALID_RANGE", "Parameter value outside valid range"),
    EMPTY_PARAMETER("EMPTY_PARAMETER", "Parameter cannot be empty"),

    // State Errors
    DEVICE_NOT_SELECTED("DEVICE_NOT_SELECTED", "No device is currently selected"),
    DEVICE_NOT_FOUND("DEVICE_NOT_FOUND", "Specified device was not found"),
    TRACK_NOT_FOUND("TRACK_NOT_FOUND", "Specified track was not found"),
    SCENE_NOT_FOUND("SCENE_NOT_FOUND", "Specified scene was not found"),
    CLIP_NOT_FOUND("CLIP_NOT_FOUND", "Specified clip was not found"),
    PROJECT_NOT_LOADED("PROJECT_NOT_LOADED", "No project is currently loaded"),
    ENGINE_NOT_ACTIVE("ENGINE_NOT_ACTIVE", "Audio engine is not active"),

    // Bitwig API Errors
    BITWIG_API_ERROR("BITWIG_API_ERROR", "Bitwig API operation failed"),
    BITWIG_CONNECTION_ERROR("BITWIG_CONNECTION_ERROR", "Failed to connect to Bitwig API"),
    BITWIG_TIMEOUT("BITWIG_TIMEOUT", "Bitwig API operation timed out"),
    DEVICE_UNAVAILABLE("DEVICE_UNAVAILABLE", "Device is not available or responding"),
    TRANSPORT_ERROR("TRANSPORT_ERROR", "Transport operation failed"),

    // System Errors
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal system error occurred"),
    CONFIGURATION_ERROR("CONFIGURATION_ERROR", "System configuration error"),
    RESOURCE_UNAVAILABLE("RESOURCE_UNAVAILABLE", "Required resource is unavailable"),
    OPERATION_FAILED("OPERATION_FAILED", "Operation failed to complete"),
    SERIALIZATION_ERROR("SERIALIZATION_ERROR", "Failed to serialize/deserialize data"),

    // MCP Protocol Errors
    MCP_PROTOCOL_ERROR("MCP_PROTOCOL_ERROR", "MCP protocol error"),
    MCP_PARSING_ERROR("MCP_PARSING_ERROR", "Failed to parse MCP request"),
    MCP_RESPONSE_ERROR("MCP_RESPONSE_ERROR", "Failed to generate MCP response"),

    // Unknown/Fallback
    UNKNOWN_ERROR("UNKNOWN_ERROR", "An unknown error occurred");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * Gets the error code string.
     * @return The error code
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the default error message for this error code.
     * @return The default message
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * Parses a string error code to the corresponding ErrorCode enum value.
     * Performs case-insensitive matching and handles common aliases.
     * @param errorCodeString The string error code to parse
     * @return The matching ErrorCode, or OPERATION_FAILED if not found
     */
    public static ErrorCode fromString(String errorCodeString) {
        if (errorCodeString == null || errorCodeString.trim().isEmpty()) {
            return OPERATION_FAILED;
        }

        String normalized = errorCodeString.trim().toUpperCase();

        // Try direct enum match first
        for (ErrorCode ec : values()) {
            if (ec.getCode().equals(normalized) || ec.name().equals(normalized)) {
                return ec;
            }
        }

        // Handle common aliases and variations
        return switch (normalized) {
            case "CLIP_INDEX_OUT_OF_BOUNDS" -> INVALID_PARAMETER_INDEX;
            case "BITWIG_ERROR" -> BITWIG_API_ERROR;
            default -> OPERATION_FAILED;
        };
    }

    /**
     * Determines the appropriate error code based on an exception.
     * Uses exception type classification.
     * @param exception The exception to analyze
     * @return The most appropriate error code
     */
    public static ErrorCode fromException(Exception exception) {
        if (exception == null) {
            return UNKNOWN_ERROR;
        }

        // Check for structured exceptions first (most reliable)
        if (exception instanceof BitwigApiException) {
            return ((BitwigApiException) exception).getErrorCode();
        }

        // Check exception type
        if (exception instanceof IllegalArgumentException) {
            return INVALID_PARAMETER;
        }

        if (exception instanceof RuntimeException) {
            return OPERATION_FAILED;
        }

        // Fallback to class name patterns
        String className = exception.getClass().getSimpleName().toLowerCase();
        // Check for timeout-related exceptions
        if (className.contains("timeout")) {
            return BITWIG_TIMEOUT;
        }

        return UNKNOWN_ERROR;
    }
}
