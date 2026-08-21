package io.github.fabb.wigai.mcp;
import io.github.fabb.wigai.mcp.idempotency.IdempotencyCache;
import io.github.fabb.wigai.mcp.tool.*;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Idempotency cache bootstrap from system properties, including fail-safe
 * handling of invalid values.
 *
 * <p>Split out of the original 1718-line McpErrorHandlerTest along the same seams
 * as the production extraction.
 */
class IdempotencyCacheConfigTest {

    @BeforeEach
    void resetIdempotencyCache() {
        // Fresh cache per test to prevent cross-test interference
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));
    }

    @Test
    void testCreateDefaultCache_UsesSystemPropertyOverrides() {
        String originalTtl = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
        String originalMax = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);

        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, "5000");
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, "50");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();

            // Verify max entries: fill to 50, then add one more — should evict
            for (int i = 0; i < 50; i++) {
                cache.put(
                    new io.github.fabb.wigai.mcp.idempotency.IdempotencyKey("tool", "req-" + i),
                    new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("r")), false)
                );
            }
            assertEquals(50, cache.size());
            cache.put(
                new io.github.fabb.wigai.mcp.idempotency.IdempotencyKey("tool", "req-overflow"),
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("r")), false)
            );
            assertTrue(cache.size() <= 50, "Max entries should be 50 per system property");
        } finally {
            // Restore original system properties
            if (originalTtl != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, originalTtl);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
            }
            if (originalMax != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, originalMax);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
            }
        }
    }

    @Test
    void testCreateDefaultCache_FallsBackToDefaults() {
        String originalTtl = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
        String originalMax = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);

        try {
            System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
            System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();

            // Should work normally with defaults — basic smoke test
            assertNotNull(cache);
            cache.put(
                new io.github.fabb.wigai.mcp.idempotency.IdempotencyKey("tool", "req-1"),
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("r")), false)
            );
            assertEquals(1, cache.size());
        } finally {
            if (originalTtl != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, originalTtl);
            }
            if (originalMax != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, originalMax);
            }
        }
    }

    @Test
    void testCreateDefaultCache_NonNumericTtl_FailsSafeToDefault() {
        String originalTtl = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, "not-a-number");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();
            assertNotNull(cache, "Invalid TTL property must not crash static initialization");
            cache.put(
                new io.github.fabb.wigai.mcp.idempotency.IdempotencyKey("tool", "req-1"),
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("r")), false)
            );
            assertEquals(1, cache.size());
        } finally {
            if (originalTtl != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, originalTtl);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
            }
        }
    }

    @Test
    void testCreateDefaultCache_NonNumericMaxEntries_FailsSafeToDefault() {
        String originalMax = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, "abc");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();
            assertNotNull(cache, "Invalid max entries property must not crash static initialization");
        } finally {
            if (originalMax != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, originalMax);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
            }
        }
    }

    @Test
    void testCreateDefaultCache_NegativeTtl_FailsSafeToDefault() {
        String originalTtl = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, "-500");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();
            assertNotNull(cache, "Negative TTL must fail safe to default, not throw");
        } finally {
            if (originalTtl != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, originalTtl);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
            }
        }
    }

    @Test
    void testCreateDefaultCache_ZeroMaxEntries_FailsSafeToDefault() {
        String originalMax = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, "0");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();
            assertNotNull(cache, "Zero max entries must fail safe to default, not throw");
        } finally {
            if (originalMax != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, originalMax);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
            }
        }
    }
}
