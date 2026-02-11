package io.github.fabb.wigai.common.retry;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;

import java.util.Set;

/**
 * Bounded retry policy for baseline mutating tool operations.
 * Classifies exceptions as retryable (transient host-state/timing) vs non-retryable (validation/state),
 * and defines bounded retry parameters: max attempts, backoff delays, and total timeout.
 */
public class RetryPolicy {

    /**
     * Default retry policy for baseline mutating tools.
     * 3 max attempts, 100ms initial backoff doubling each attempt, 2000ms total timeout.
     */
    public static final RetryPolicy DEFAULT = new RetryPolicy(3, 100L, 2000L);

    /**
     * No-retry policy for read-only or non-retryable operations.
     */
    public static final RetryPolicy NONE = new RetryPolicy(1, 0L, 0L);

    /**
     * ErrorCodes that represent transient host-state/timing/availability failures
     * and are safe to retry. All other ErrorCodes fail fast.
     */
    private static final Set<ErrorCode> RETRYABLE_ERROR_CODES = Set.of(
        ErrorCode.BITWIG_API_ERROR,
        ErrorCode.BITWIG_TIMEOUT,
        ErrorCode.BITWIG_CONNECTION_ERROR,
        ErrorCode.TRANSPORT_ERROR,
        ErrorCode.DEVICE_UNAVAILABLE
    );

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final long totalTimeoutMs;

    /**
     * Creates a retry policy with the specified bounds.
     *
     * @param maxAttempts Maximum number of attempts (must be >= 1)
     * @param initialBackoffMs Initial backoff delay in milliseconds before first retry (doubles each attempt)
     * @param totalTimeoutMs Maximum total time for all attempts combined in milliseconds (0 = no timeout)
     */
    public RetryPolicy(int maxAttempts, long initialBackoffMs, long totalTimeoutMs) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = Math.max(0, initialBackoffMs);
        this.totalTimeoutMs = Math.max(0, totalTimeoutMs);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public long getTotalTimeoutMs() {
        return totalTimeoutMs;
    }

    /**
     * Calculates the backoff delay for a given attempt number (0-indexed).
     * Uses exponential backoff: initialBackoffMs * 2^attemptIndex.
     *
     * @param attemptIndex The zero-based attempt index (0 = first retry, 1 = second retry, etc.)
     * @return The backoff delay in milliseconds
     */
    public long getBackoffMs(int attemptIndex) {
        if (initialBackoffMs == 0) {
            return 0;
        }
        return initialBackoffMs * (1L << Math.min(attemptIndex, 10));
    }

    /**
     * Determines if a given exception is retryable based on its error classification.
     * Only transient host-state/timing/availability failures are retryable.
     * Validation errors, state errors (NOT_FOUND, NOT_SELECTED), and system errors fail fast.
     *
     * @param exception The exception to classify
     * @return true if the exception represents a transient failure that should be retried
     */
    public static boolean isRetryable(Exception exception) {
        if (exception instanceof BitwigApiException bitwigEx) {
            return RETRYABLE_ERROR_CODES.contains(bitwigEx.getErrorCode());
        }
        // Generic RuntimeExceptions could be transient Bitwig issues; don't retry
        // IllegalArgumentException and similar validation exceptions are never retryable
        return false;
    }

    /**
     * Determines if a given ErrorCode is retryable.
     *
     * @param errorCode The error code to classify
     * @return true if the error code represents a transient failure
     */
    public static boolean isRetryable(ErrorCode errorCode) {
        return RETRYABLE_ERROR_CODES.contains(errorCode);
    }

    /**
     * Returns whether this policy actually enables retries (maxAttempts > 1).
     */
    public boolean isRetryEnabled() {
        return maxAttempts > 1;
    }
}
