package io.github.fabb.wigai.mcp.idempotency;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Bounded in-memory idempotency cache keyed by (tool_name, request_id).
 * <p>
 * Provides deduplication for mutating MCP tool calls: if a request with the same
 * key arrives within the TTL window, the cached first result is returned without
 * re-executing the operation.
 * <p>
 * Bounded guarantees:
 * <ul>
 *   <li>Entries expire after {@code ttlMillis} and are eligible for eviction</li>
 *   <li>Maximum {@code maxEntries} are stored; when capacity is reached, the oldest
 *       expired entry is evicted first, then the oldest entry overall</li>
 *   <li>Memory usage is bounded regardless of traffic volume</li>
 * </ul>
 */
public class IdempotencyCache {

    /** Default TTL: 60 seconds. */
    public static final long DEFAULT_TTL_MILLIS = 60_000L;

    /** Default maximum cache entries. */
    public static final int DEFAULT_MAX_ENTRIES = 1000;

    /** Number of lock stripes for per-key synchronization. */
    private static final int STRIPE_COUNT = 16;

    private final ConcurrentHashMap<IdempotencyKey, IdempotencyEntry> cache;
    private final long ttlMillis;
    private final int maxEntries;
    private final LongSupplier clock;
    private final Object[] stripes;
    private final ReentrantLock capacityLock = new ReentrantLock();

    /**
     * Result of a dedupe lookup: the tool result, whether it came from the cache,
     * and whether a payload mismatch was detected.
     *
     * @param result           The tool result (null when payloadMismatch is true)
     * @param cacheHit         Whether the result was served from cache
     * @param payloadMismatch  Whether the cached entry had a different payload fingerprint
     */
    public record DedupeResult(McpSchema.CallToolResult result, boolean cacheHit, boolean payloadMismatch) {}

    /**
     * Creates a cache with default TTL (60s) and max entries (1000) using the system clock.
     */
    public IdempotencyCache() {
        this(DEFAULT_TTL_MILLIS, DEFAULT_MAX_ENTRIES, System::currentTimeMillis);
    }

    /**
     * Creates a cache with the specified bounds and clock (for testability).
     *
     * @param ttlMillis  Time-to-live for cache entries in milliseconds
     * @param maxEntries Maximum number of entries before eviction
     * @param clock      Clock supplier for current time in epoch millis
     */
    public IdempotencyCache(long ttlMillis, int maxEntries, LongSupplier clock) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be positive, got: " + ttlMillis);
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive, got: " + maxEntries);
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.clock = clock;
        this.cache = new ConcurrentHashMap<>();
        this.stripes = new Object[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            this.stripes[i] = new Object();
        }
    }

    /**
     * Looks up a cached result for the given key. Returns null on cache miss or expired entry.
     * Expired entries are removed on access (lazy eviction).
     *
     * @param key The idempotency key
     * @return The cached CallToolResult, or null if not found or expired
     */
    public McpSchema.CallToolResult get(IdempotencyKey key) {
        IdempotencyEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(clock.getAsLong(), ttlMillis)) {
            cache.remove(key, entry);
            return null;
        }
        return entry.result();
    }

    /**
     * Stores a result in the cache with a zero payload fingerprint.
     * If the cache is at capacity, evicts the oldest expired entry first,
     * then the oldest entry overall if no expired entries exist.
     *
     * @param key    The idempotency key
     * @param result The CallToolResult to cache
     */
    public void put(IdempotencyKey key, McpSchema.CallToolResult result) {
        put(key, result, "");
    }

    /**
     * Stores a result in the cache with a payload fingerprint for consistency checks.
     * If the cache is at capacity, evicts the oldest expired entry first,
     * then the oldest entry overall if no expired entries exist.
     * <p>
     * Capacity control is globally serialized via {@code capacityLock} so that
     * concurrent inserts cannot bypass eviction and cause temporary over-capacity.
     *
     * @param key                The idempotency key
     * @param result             The CallToolResult to cache
     * @param payloadFingerprint Collision-resistant digest of non-correlation arguments for consistency enforcement
     */
    public void put(IdempotencyKey key, McpSchema.CallToolResult result, String payloadFingerprint) {
        long now = clock.getAsLong();
        capacityLock.lock();
        try {
            boolean isReplacement = cache.containsKey(key);
            if (!isReplacement && cache.size() >= maxEntries) {
                evict(now);
            }
            cache.put(key, new IdempotencyEntry(result, now, payloadFingerprint));
        } finally {
            capacityLock.unlock();
        }
    }

    /**
     * Atomically checks the cache for an existing non-expired entry. On hit, returns the
     * cached result. On miss, executes the computation, stores the result, and returns it.
     * <p>
     * This overload does not enforce payload consistency (fingerprint = 0).
     *
     * @param key         The idempotency key
     * @param computation Supplier that executes the tool operation and returns the result
     * @return A {@link DedupeResult} containing the result and whether it was a cache hit
     */
    public DedupeResult getOrCompute(IdempotencyKey key, Supplier<McpSchema.CallToolResult> computation) {
        return getOrCompute(key, "", computation);
    }

    /**
     * Atomically checks the cache for an existing non-expired entry with payload consistency
     * enforcement. On hit with matching fingerprint, returns the cached result. On hit with
     * mismatched fingerprint, returns a payload-mismatch result (no re-execution). On miss,
     * executes the computation, stores the result with the fingerprint, and returns it.
     * <p>
     * Uses striped locking per key to prevent concurrent duplicate execution for the
     * same {@code (tool_name, request_id)} while allowing unrelated keys to proceed
     * in parallel.
     *
     * @param key                The idempotency key
     * @param payloadFingerprint Collision-resistant digest of non-correlation arguments for consistency checks
     * @param computation        Supplier that executes the tool operation and returns the result
     * @return A {@link DedupeResult} containing the result, cache-hit status, and mismatch flag
     */
    public DedupeResult getOrCompute(IdempotencyKey key, String payloadFingerprint,
                                     Supplier<McpSchema.CallToolResult> computation) {
        // Fast path: check for non-expired entry without locking
        IdempotencyEntry existing = cache.get(key);
        if (existing != null) {
            long now = clock.getAsLong();
            if (!existing.isExpired(now, ttlMillis)) {
                if (!Objects.equals(existing.payloadFingerprint(), payloadFingerprint)) {
                    return new DedupeResult(null, true, true);
                }
                return new DedupeResult(existing.result(), true, false);
            }
        }

        // Slow path: acquire stripe lock, double-check, compute, store
        synchronized (stripeFor(key)) {
            existing = cache.get(key);
            if (existing != null) {
                long now = clock.getAsLong();
                if (!existing.isExpired(now, ttlMillis)) {
                    if (!Objects.equals(existing.payloadFingerprint(), payloadFingerprint)) {
                        return new DedupeResult(null, true, true);
                    }
                    return new DedupeResult(existing.result(), true, false);
                }
                cache.remove(key, existing);
            }

            McpSchema.CallToolResult result = computation.get();
            put(key, result, payloadFingerprint);
            return new DedupeResult(result, false, false);
        }
    }

    /**
     * Returns the current number of entries in the cache (including potentially expired ones).
     */
    public int size() {
        return cache.size();
    }

    /**
     * Returns the lock stripe for the given key, distributing keys across stripes
     * by hash code to allow unrelated keys to proceed concurrently.
     */
    private Object stripeFor(IdempotencyKey key) {
        return stripes[Math.floorMod(key.hashCode(), STRIPE_COUNT)];
    }

    /**
     * Evicts entries to make room. Strategy:
     * 1. Remove all expired entries first.
     * 2. If still at capacity, remove the oldest entry (by createdAt).
     */
    private void evict(long now) {
        // Phase 1: Remove all expired entries
        Iterator<Map.Entry<IdempotencyKey, IdempotencyEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IdempotencyKey, IdempotencyEntry> entry = it.next();
            if (entry.getValue().isExpired(now, ttlMillis)) {
                it.remove();
            }
        }

        // Phase 2: If still at capacity, remove the oldest entry
        if (cache.size() >= maxEntries) {
            IdempotencyKey oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<IdempotencyKey, IdempotencyEntry> entry : cache.entrySet()) {
                long createdAt = entry.getValue().createdAt();
                IdempotencyKey candidateKey = entry.getKey();
                if (createdAt < oldestTime
                        || (createdAt == oldestTime
                        && oldestKey != null
                        && compareKeys(candidateKey, oldestKey) < 0)) {
                    oldestTime = createdAt;
                    oldestKey = candidateKey;
                }
            }
            if (oldestKey != null) {
                cache.remove(oldestKey);
            }
        }
    }

    /**
     * Total ordering for deterministic tie-breaks when createdAt timestamps are equal.
     */
    private static int compareKeys(IdempotencyKey a, IdempotencyKey b) {
        int toolCmp = a.toolName().compareTo(b.toolName());
        if (toolCmp != 0) {
            return toolCmp;
        }
        return a.requestId().compareTo(b.requestId());
    }
}
