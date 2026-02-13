package io.github.fabb.wigai.mcp.idempotency;

/**
 * Composite key for idempotency deduplication: (tool_name, request_id).
 * Two keys are equal iff both components match exactly.
 */
public record IdempotencyKey(String toolName, String requestId) {

    public IdempotencyKey {
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("toolName must not be null or empty");
        }
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("requestId must not be null or empty");
        }
    }
}
