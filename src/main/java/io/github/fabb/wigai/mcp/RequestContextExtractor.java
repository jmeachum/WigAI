package io.github.fabb.wigai.mcp;

import java.util.Map;

/**
 * Extraction and sanitization of request correlation context from tool arguments.
 *
 * <p>Produces logging-safe parameter maps (shapes and sizes, never values) and enforces
 * the request_id bounds used for log output and idempotency cache keying. Pure functions
 * with no external state.
 */
final class RequestContextExtractor {

    private RequestContextExtractor() {} // Prevent instantiation

    /**
     * Maximum length for request_id in log output (truncation-safe for logging only).
     * 256 chars is generous (standard UUID is 36 chars).
     */
    private static final int MAX_REQUEST_ID_LENGTH = 256;

    /**
     * Known correlation-only keys that are not counted as business arguments.
     */
    static final java.util.Set<String> CORRELATION_KEYS = java.util.Set.of("request_id");

    /**
     * Maximum length for request_id accepted for cache keying.
     * IDs exceeding this bound skip dedupe entirely (returned as null from extractRawRequestId)
     * to avoid oversized-key memory/CPU pressure while preserving collision-safe semantics.
     */
    static final int MAX_RAW_REQUEST_ID_LENGTH = 1024;

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
}
