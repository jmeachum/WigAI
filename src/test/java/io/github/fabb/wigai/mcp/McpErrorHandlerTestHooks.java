package io.github.fabb.wigai.mcp;

import io.github.fabb.wigai.mcp.idempotency.IdempotencyCache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only hooks for McpErrorHandler shared-state control.
 */
public final class McpErrorHandlerTestHooks {

    private McpErrorHandlerTestHooks() {
        // Utility class
    }

    /**
     * Resets shared idempotency cache to a deterministic fresh instance for test isolation.
     */
    public static void resetIdempotencyCache() {
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));
    }
}
