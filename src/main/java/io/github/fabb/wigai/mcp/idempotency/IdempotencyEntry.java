package io.github.fabb.wigai.mcp.idempotency;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * A cached idempotency entry storing the first execution result, its creation timestamp,
 * and a fingerprint of the request payload for consistency enforcement.
 *
 * @param result              The CallToolResult from the first execution (success or error)
 * @param createdAt           The timestamp (epoch millis) when this entry was created
 * @param payloadFingerprint  Hash of non-correlation arguments for payload consistency checks
 */
public record IdempotencyEntry(McpSchema.CallToolResult result, long createdAt, String payloadFingerprint) {

    /**
     * Returns true if this entry has expired relative to the given current time and TTL.
     *
     * @param nowMillis    Current time in epoch millis
     * @param ttlMillis    TTL duration in millis
     * @return true if expired
     */
    public boolean isExpired(long nowMillis, long ttlMillis) {
        return (nowMillis - createdAt) >= ttlMillis;
    }
}
