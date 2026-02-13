package io.github.fabb.wigai.mcp.idempotency;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IdempotencyCache: hit, miss, TTL expiry, capacity eviction.
 */
class IdempotencyCacheTest {

    private static McpSchema.CallToolResult dummyResult(String text) {
        McpSchema.TextContent content = new McpSchema.TextContent(text);
        return new McpSchema.CallToolResult(List.of(content), false);
    }

    // === Cache miss tests ===

    @Test
    void get_CacheMiss_ReturnsNull() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-1");
        assertNull(cache.get(key), "Cache miss should return null");
    }

    // === Cache hit tests ===

    @Test
    void get_AfterPut_ReturnsCachedResult() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-1");
        McpSchema.CallToolResult result = dummyResult("{\"status\":\"success\"}");

        cache.put(key, result);
        McpSchema.CallToolResult cached = cache.get(key);

        assertNotNull(cached, "Cache hit should return result");
        assertSame(result, cached, "Should return exact same result object");
    }

    @Test
    void get_DifferentToolSameRequestId_NoCrossContamination() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        McpSchema.CallToolResult result1 = dummyResult("result-1");
        McpSchema.CallToolResult result2 = dummyResult("result-2");

        cache.put(new IdempotencyKey("transport_start", "req-1"), result1);
        cache.put(new IdempotencyKey("transport_stop", "req-1"), result2);

        assertSame(result1, cache.get(new IdempotencyKey("transport_start", "req-1")));
        assertSame(result2, cache.get(new IdempotencyKey("transport_stop", "req-1")));
    }

    @Test
    void get_SameToolDifferentRequestId_NoCrossContamination() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        McpSchema.CallToolResult result1 = dummyResult("result-1");
        McpSchema.CallToolResult result2 = dummyResult("result-2");

        cache.put(new IdempotencyKey("transport_start", "req-1"), result1);
        cache.put(new IdempotencyKey("transport_start", "req-2"), result2);

        assertSame(result1, cache.get(new IdempotencyKey("transport_start", "req-1")));
        assertSame(result2, cache.get(new IdempotencyKey("transport_start", "req-2")));
    }

    // === TTL expiry tests ===

    @Test
    void get_AfterTtlExpiry_ReturnsNull() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-1");
        cache.put(key, dummyResult("cached"));

        // Advance clock past TTL
        clock.set(1000L + 60_000L);
        assertNull(cache.get(key), "Expired entry should return null");
    }

    @Test
    void get_JustBeforeTtlExpiry_ReturnsResult() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-1");
        McpSchema.CallToolResult result = dummyResult("cached");
        cache.put(key, result);

        // Advance clock to just before TTL
        clock.set(1000L + 59_999L);
        assertSame(result, cache.get(key), "Entry should still be valid just before TTL");
    }

    @Test
    void get_AfterTtlExpiry_AllowsReExecution() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-1");
        cache.put(key, dummyResult("first-result"));

        // Expire
        clock.set(1000L + 60_000L);
        assertNull(cache.get(key));

        // Re-insert with new result
        McpSchema.CallToolResult newResult = dummyResult("second-result");
        cache.put(key, newResult);
        assertSame(newResult, cache.get(key), "Should return new result after re-execution");
    }

    // === Capacity eviction tests ===

    @Test
    void put_AtCapacity_EvictsExpiredFirst() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(100L, 3, clock::get);

        // Fill to capacity
        cache.put(new IdempotencyKey("tool", "req-1"), dummyResult("r1"));
        clock.set(1050L);
        cache.put(new IdempotencyKey("tool", "req-2"), dummyResult("r2"));
        clock.set(1080L);
        cache.put(new IdempotencyKey("tool", "req-3"), dummyResult("r3"));

        assertEquals(3, cache.size());

        // Advance so req-1 is expired (created at 1000, TTL=100, now=1100)
        clock.set(1100L);

        // Adding new entry should evict expired req-1
        cache.put(new IdempotencyKey("tool", "req-4"), dummyResult("r4"));

        assertNull(cache.get(new IdempotencyKey("tool", "req-1")), "Expired entry should be evicted");
        assertNotNull(cache.get(new IdempotencyKey("tool", "req-4")), "New entry should be stored");
    }

    @Test
    void put_AtCapacity_NoExpired_EvictsOldest() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 3, clock::get);

        // Fill to capacity with no expiry (TTL=60s, clock only advances slightly)
        cache.put(new IdempotencyKey("tool", "req-1"), dummyResult("r1"));
        clock.set(1001L);
        cache.put(new IdempotencyKey("tool", "req-2"), dummyResult("r2"));
        clock.set(1002L);
        cache.put(new IdempotencyKey("tool", "req-3"), dummyResult("r3"));

        assertEquals(3, cache.size());

        // Add new entry — oldest (req-1) should be evicted
        clock.set(1003L);
        cache.put(new IdempotencyKey("tool", "req-4"), dummyResult("r4"));

        assertNull(cache.get(new IdempotencyKey("tool", "req-1")), "Oldest entry should be evicted");
        assertNotNull(cache.get(new IdempotencyKey("tool", "req-2")));
        assertNotNull(cache.get(new IdempotencyKey("tool", "req-3")));
        assertNotNull(cache.get(new IdempotencyKey("tool", "req-4")));
    }

    @Test
    void put_AtCapacity_TiedCreatedAt_EvictsDeterministicallyByKey() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 3, clock::get);

        // Intentionally keep all createdAt equal so eviction must use deterministic tie-break.
        IdempotencyKey keyB = new IdempotencyKey("tool", "req-b");
        IdempotencyKey keyA = new IdempotencyKey("tool", "req-a");
        IdempotencyKey keyC = new IdempotencyKey("tool", "req-c");

        cache.put(keyB, dummyResult("rb"));
        cache.put(keyA, dummyResult("ra"));
        cache.put(keyC, dummyResult("rc"));

        // At capacity; inserting one more should evict exactly one tied "oldest".
        cache.put(new IdempotencyKey("tool", "req-d"), dummyResult("rd"));

        // Deterministic rule: when createdAt ties, smallest key is evicted first.
        assertNull(cache.get(keyA), "Tie-break eviction must be deterministic and key-ordered");
        assertNotNull(cache.get(keyB));
        assertNotNull(cache.get(keyC));
    }

    @Test
    void put_BeyondCapacity_RemainsWithinMaxEntries() {
        AtomicLong clock = new AtomicLong(0L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 5, clock::get);

        for (int i = 0; i < 20; i++) {
            clock.set(i);
            cache.put(new IdempotencyKey("tool", "req-" + i), dummyResult("r" + i));
        }

        assertTrue(cache.size() <= 5, "Cache size should never exceed maxEntries, got: " + cache.size());
    }

    @Test
    void put_AtCapacity_ReplacingExistingKey_DoesNotEvictUnrelatedEntry() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 3, clock::get);

        IdempotencyKey key1 = new IdempotencyKey("tool", "req-1");
        IdempotencyKey key2 = new IdempotencyKey("tool", "req-2");
        IdempotencyKey key3 = new IdempotencyKey("tool", "req-3");

        cache.put(key1, dummyResult("r1")); // oldest
        clock.set(1001L);
        cache.put(key2, dummyResult("r2"));
        clock.set(1002L);
        cache.put(key3, dummyResult("r3"));
        assertEquals(3, cache.size());

        // Replace an existing (non-oldest) key at capacity.
        // This must not evict key1 just because capacity check ran before overwrite.
        clock.set(1003L);
        McpSchema.CallToolResult replacement = dummyResult("r3-updated");
        cache.put(key3, replacement);

        assertEquals(3, cache.size(), "Overwrite should not reduce cache cardinality");
        assertNotNull(cache.get(key1), "Oldest unrelated key must remain present");
        assertNotNull(cache.get(key2));
        assertSame(replacement, cache.get(key3), "Replacement value must be stored");
    }

    // === IdempotencyKey validation tests ===

    @Test
    void idempotencyKey_NullToolName_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey(null, "req-1"));
    }

    @Test
    void idempotencyKey_EmptyToolName_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("", "req-1"));
    }

    @Test
    void idempotencyKey_NullRequestId_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("tool", null));
    }

    @Test
    void idempotencyKey_EmptyRequestId_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("tool", ""));
    }

    @Test
    void idempotencyKey_Equality() {
        IdempotencyKey key1 = new IdempotencyKey("transport_start", "req-1");
        IdempotencyKey key2 = new IdempotencyKey("transport_start", "req-1");
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void idempotencyKey_Inequality_DifferentTool() {
        IdempotencyKey key1 = new IdempotencyKey("transport_start", "req-1");
        IdempotencyKey key2 = new IdempotencyKey("transport_stop", "req-1");
        assertNotEquals(key1, key2);
    }

    @Test
    void idempotencyKey_Inequality_DifferentRequestId() {
        IdempotencyKey key1 = new IdempotencyKey("transport_start", "req-1");
        IdempotencyKey key2 = new IdempotencyKey("transport_start", "req-2");
        assertNotEquals(key1, key2);
    }

    // === IdempotencyEntry expiry tests ===

    @Test
    void idempotencyEntry_IsExpired_AtExactTtl_ReturnsTrue() {
        IdempotencyEntry entry = new IdempotencyEntry(dummyResult("r"), 1000L, "");
        assertTrue(entry.isExpired(1000L + 60_000L, 60_000L));
    }

    @Test
    void idempotencyEntry_IsExpired_BeforeTtl_ReturnsFalse() {
        IdempotencyEntry entry = new IdempotencyEntry(dummyResult("r"), 1000L, "");
        assertFalse(entry.isExpired(1000L + 59_999L, 60_000L));
    }

    // === Default constructor tests ===

    @Test
    void defaultConstructor_CreatesWithDefaults() {
        IdempotencyCache cache = new IdempotencyCache();
        // Verify it doesn't throw and basic operations work
        IdempotencyKey key = new IdempotencyKey("tool", "req");
        assertNull(cache.get(key));
        cache.put(key, dummyResult("r"));
        assertNotNull(cache.get(key));
    }

    // === Constructor validation tests ===

    @Test
    void constructor_ZeroTtl_Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new IdempotencyCache(0, 100, System::currentTimeMillis));
    }

    @Test
    void constructor_NegativeTtl_Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new IdempotencyCache(-1, 100, System::currentTimeMillis));
    }

    @Test
    void constructor_ZeroMaxEntries_Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new IdempotencyCache(60_000L, 0, System::currentTimeMillis));
    }

    @Test
    void constructor_NullClock_Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new IdempotencyCache(60_000L, 100, null));
    }

    // === getOrCompute tests ===

    @Test
    void getOrCompute_CacheMiss_ExecutesComputationAndStores() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-1");
        AtomicInteger executions = new AtomicInteger(0);

        IdempotencyCache.DedupeResult result = cache.getOrCompute(key, () -> {
            executions.incrementAndGet();
            return dummyResult("{\"status\":\"success\"}");
        });

        assertNotNull(result.result());
        assertFalse(result.cacheHit(), "First call should be a cache miss");
        assertEquals(1, executions.get());
        // Verify it was stored
        assertNotNull(cache.get(key));
    }

    @Test
    void getOrCompute_CacheHit_ReturnsWithoutComputation() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-1");
        McpSchema.CallToolResult original = dummyResult("{\"status\":\"success\"}");
        cache.put(key, original);

        AtomicInteger executions = new AtomicInteger(0);

        IdempotencyCache.DedupeResult result = cache.getOrCompute(key, () -> {
            executions.incrementAndGet();
            return dummyResult("should not reach");
        });

        assertTrue(result.cacheHit(), "Second call should be a cache hit");
        assertSame(original, result.result());
        assertEquals(0, executions.get(), "Computation must not run on cache hit");
    }

    @Test
    void getOrCompute_ExpiredEntry_RecomputesAndStores() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(100L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-1");
        cache.put(key, dummyResult("first"));

        // Expire
        clock.set(1100L);

        IdempotencyCache.DedupeResult result = cache.getOrCompute(key, () -> dummyResult("second"));

        assertFalse(result.cacheHit(), "Expired entry should be a miss");
        assertEquals("second", ((McpSchema.TextContent) result.result().content().get(0)).text());
    }

    @Test
    void getOrCompute_ConcurrentSameKey_ExecutesOnlyOnce() throws Exception {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("transport_start", "req-concurrent");
        AtomicInteger executions = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            // Launch 4 threads all trying to compute the same key simultaneously
            Future<IdempotencyCache.DedupeResult>[] futures = new Future[4];
            for (int i = 0; i < 4; i++) {
                futures[i] = executor.submit(() -> {
                    startLatch.await();
                    return cache.getOrCompute(key, () -> {
                        executions.incrementAndGet();
                        return dummyResult("computed");
                    });
                });
            }

            // Release all threads at once
            startLatch.countDown();

            // Collect results
            for (Future<IdempotencyCache.DedupeResult> f : futures) {
                assertNotNull(f.get().result());
            }

            assertEquals(1, executions.get(),
                    "Concurrent getOrCompute for same key must execute computation exactly once");
        } finally {
            executor.shutdown();
        }
    }

    // === Payload fingerprint tests ===

    @Test
    void getOrCompute_SameFingerprint_ReturnsCachedResult() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("set_selected_device_parameter", "req-fp1");
        AtomicInteger executions = new AtomicInteger(0);
        String fingerprint = "abc123";

        IdempotencyCache.DedupeResult first = cache.getOrCompute(key, fingerprint, () -> {
            executions.incrementAndGet();
            return dummyResult("success");
        });

        IdempotencyCache.DedupeResult second = cache.getOrCompute(key, fingerprint, () -> {
            executions.incrementAndGet();
            return dummyResult("should_not_reach");
        });

        assertEquals(1, executions.get());
        assertTrue(second.cacheHit());
        assertFalse(second.payloadMismatch());
        assertSame(first.result(), second.result());
    }

    @Test
    void getOrCompute_DifferentFingerprint_ReportsPayloadMismatch() {
        AtomicLong clock = new AtomicLong(1000L);
        IdempotencyCache cache = new IdempotencyCache(60_000L, 100, clock::get);

        IdempotencyKey key = new IdempotencyKey("set_selected_device_parameter", "req-fp2");
        AtomicInteger executions = new AtomicInteger(0);

        cache.getOrCompute(key, "abc123", () -> {
            executions.incrementAndGet();
            return dummyResult("success");
        });

        IdempotencyCache.DedupeResult second = cache.getOrCompute(key, "xyz789", () -> {
            executions.incrementAndGet();
            return dummyResult("should_not_reach");
        });

        assertEquals(1, executions.get(), "Mismatched fingerprint must not re-execute");
        assertTrue(second.cacheHit());
        assertTrue(second.payloadMismatch(), "Mismatched fingerprint must report payload mismatch");
        assertNull(second.result(), "Payload mismatch must return null result");
    }

    @Test
    void idempotencyEntry_PayloadFingerprint_IsStored() {
        McpSchema.CallToolResult result = dummyResult("r");
        IdempotencyEntry entry = new IdempotencyEntry(result, 1000L, "abc123");
        assertEquals("abc123", entry.payloadFingerprint());
    }

    // === Strict capacity enforcement under concurrent inserts ===

    @Test
    void put_ConcurrentInserts_NeverExceedsMaxEntries() throws Exception {
        AtomicLong clock = new AtomicLong(0L);
        int maxEntries = 10;
        IdempotencyCache cache = new IdempotencyCache(60_000L, maxEntries, clock::get);

        int threadCount = 8;
        int insertsPerThread = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            Future<?>[] futures = new Future[threadCount];
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures[t] = executor.submit(() -> {
                    startLatch.await();
                    for (int i = 0; i < insertsPerThread; i++) {
                        clock.incrementAndGet();
                        cache.put(
                            new IdempotencyKey("tool", "t" + threadId + "-req-" + i),
                            dummyResult("r")
                        );
                    }
                    return null;
                });
            }

            // Release all threads at once
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get();
            }

            assertTrue(cache.size() <= maxEntries,
                    "Cache must never exceed maxEntries even under concurrent inserts, got: " + cache.size());
        } finally {
            executor.shutdown();
        }
    }
}
