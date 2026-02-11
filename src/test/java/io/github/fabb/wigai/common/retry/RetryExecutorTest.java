package io.github.fabb.wigai.common.retry;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RetryExecutor: retry-then-success, exhaustion, non-retryable fail-fast, and logging.
 */
class RetryExecutorTest {

    private StructuredLogger mockLogger;

    @BeforeEach
    void setUp() {
        mockLogger = mock(StructuredLogger.class);
    }

    // === Retryable failure then success (AC 2) ===

    @Test
    void testRetryableFailureThenSuccess_RetriesAndSucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        RetryExecutor.RetryResult<String> result = RetryExecutor.executeWithRetry(
            "test_op", policy, mockLogger, "op-1", null,
            () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 2) {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "transient failure");
                }
                return "success";
            }
        );

        assertEquals("success", result.value());
        assertEquals(2, result.totalAttempts());
        assertEquals(1, result.retryCount());
    }

    @Test
    void testRetryableFailureThenSuccess_OnThirdAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        RetryExecutor.RetryResult<String> result = RetryExecutor.executeWithRetry(
            "test_op", policy, mockLogger, "op-2", null,
            () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "test_op", "transient");
                }
                return "recovered";
            }
        );

        assertEquals("recovered", result.value());
        assertEquals(3, result.totalAttempts());
        assertEquals(2, result.retryCount());
    }

    // === Retryable failure exhausting max attempts (AC 2, bounded failure) ===

    @Test
    void testRetryExhaustion_AllAttemptsFail_ThrowsLastException() {
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        BitwigApiException thrown = assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-3", null,
                () -> {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "persistent failure");
                }
            )
        );

        assertEquals(ErrorCode.BITWIG_API_ERROR, thrown.getErrorCode());
        assertEquals("persistent failure", thrown.getMessage());
    }

    @Test
    void testRetryExhaustion_LogsExhaustedMessage() {
        RetryPolicy policy = new RetryPolicy(2, 10, 5000);

        assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-4", null,
                () -> {
                    throw new BitwigApiException(ErrorCode.BITWIG_TIMEOUT, "test_op", "timeout");
                }
            )
        );

        // Verify retry exhaustion was logged
        verify(mockLogger, atLeastOnce()).error(eq("op-4"), eq("test_op"), contains("Retry exhausted"));
    }

    // === Non-retryable failure path (AC 3, zero retries) ===

    @Test
    void testNonRetryableFailure_FailsFastNoRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-5", null,
                () -> {
                    attempts.incrementAndGet();
                    throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "test_op", "bad param");
                }
            )
        );

        assertEquals(1, attempts.get(), "Non-retryable failure should not retry");
    }

    @Test
    void testNonRetryableFailure_TrackNotFound_NoRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-6", null,
                () -> {
                    attempts.incrementAndGet();
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "test_op", "not found");
                }
            )
        );

        assertEquals(1, attempts.get());
    }

    @Test
    void testNonRetryableFailure_DeviceNotSelected_NoRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-7", null,
                () -> {
                    attempts.incrementAndGet();
                    throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "test_op", "no device");
                }
            )
        );

        assertEquals(1, attempts.get());
    }

    @Test
    void testNonRetryableFailure_GenericException_NoRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        assertThrows(RuntimeException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-8", null,
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalArgumentException("validation error");
                }
            )
        );

        assertEquals(1, attempts.get());
    }

    // === Success on first attempt (no retry needed) ===

    @Test
    void testSuccessOnFirstAttempt_NoRetryLogging() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        RetryExecutor.RetryResult<String> result = RetryExecutor.executeWithRetry(
            "test_op", policy, mockLogger, "op-9", null,
            () -> "immediate success"
        );

        assertEquals("immediate success", result.value());
        assertEquals(1, result.totalAttempts());
        assertEquals(0, result.retryCount());

        // No retry logging should occur on first-attempt success
        verify(mockLogger, never()).warn(any(), any(), any());
        verify(mockLogger, never()).error(any(), any(), any());
    }

    // === Retry logging with correlation metadata (AC 4) ===

    @Test
    void testRetryLogging_IncludesAttemptAndMaxAttempts() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);
        Map<String, Object> params = Map.of("request_id", "corr-123");

        RetryExecutor.executeWithRetry(
            "test_op", policy, mockLogger, "op-10", params,
            () -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "transient");
                }
                return "ok";
            }
        );

        // Verify retry attempt was logged with correlation
        verify(mockLogger).warn(eq("op-10"), eq("test_op"), contains("Retry attempt 1/3"));
        verify(mockLogger).warn(eq("op-10"), eq("test_op"), contains("request_id=corr-123"));
        // Verify retry success was logged
        verify(mockLogger).info(eq("op-10"), eq("test_op"), contains("succeeded after retry"));
    }

    @Test
    void testRetryLogging_ExhaustedIncludesTotalDuration() {
        RetryPolicy policy = new RetryPolicy(2, 10, 5000);

        assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-11", null,
                () -> {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "fail");
                }
            )
        );

        verify(mockLogger).error(eq("op-11"), eq("test_op"), contains("total_duration="));
        verify(mockLogger).error(eq("op-11"), eq("test_op"), contains("final_error_code=BITWIG_API_ERROR"));
    }

    // === No-retry policy (RetryPolicy.NONE) ===

    @Test
    void testNonePolicy_NoRetryEvenForRetryableFailure() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", RetryPolicy.NONE, mockLogger, "op-12", null,
                () -> {
                    attempts.incrementAndGet();
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "fail");
                }
            )
        );

        assertEquals(1, attempts.get(), "NONE policy should only attempt once");
    }

    // === Bounded total timeout ===

    @Test
    void testTotalTimeout_StopsRetryingWhenExceeded() {
        // Very short timeout to force early termination
        RetryPolicy policy = new RetryPolicy(10, 50, 1);
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-13", null,
                () -> {
                    attempts.incrementAndGet();
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "fail");
                }
            )
        );

        // Should have stopped well before 10 attempts due to timeout
        assertTrue(attempts.get() < 10, "Should stop before maxAttempts due to timeout, got: " + attempts.get());
    }

    @Test
    void testTotalTimeout_LogsTimeoutExceededReason() {
        // Force timeout before second attempt by capping remaining backoff to 100ms
        RetryPolicy policy = new RetryPolicy(10, 200, 100);

        assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-13b", null,
                () -> {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "still failing");
                }
            )
        );

        verify(mockLogger).error(eq("op-13b"), eq("test_op"), contains("total timeout exceeded (100ms)"));
    }

    @Test
    void testTotalTimeoutExceeded_ThrowsBitwigTimeoutNotStaleException() {
        // Backoff of 200ms with total timeout of 150ms — timeout fires before second attempt
        RetryPolicy policy = new RetryPolicy(10, 200, 150);

        BitwigApiException thrown = assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-timeout-code", null,
                () -> {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "transient");
                }
            )
        );

        // Must throw BITWIG_TIMEOUT, not the stale BITWIG_API_ERROR
        assertEquals(ErrorCode.BITWIG_TIMEOUT, thrown.getErrorCode(),
            "Should throw BITWIG_TIMEOUT when total timeout exceeded, not rethrow stale exception");
        assertTrue(thrown.getMessage().contains("total timeout exceeded"),
            "Message should indicate total timeout was exceeded");
    }

    @Test
    void testInterruptedDuringBackoff_ThrowsBitwigTimeoutNotStaleException() {
        // Long backoff so we can interrupt during sleep
        RetryPolicy policy = new RetryPolicy(3, 5000, 10000);
        AtomicInteger attempts = new AtomicInteger(0);
        Thread testThread = Thread.currentThread();

        // Schedule interrupt to arrive during backoff sleep
        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            testThread.interrupt();
        });
        interrupter.start();

        BitwigApiException thrown = assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-backoff-int", null,
                () -> {
                    attempts.incrementAndGet();
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "transient");
                }
            )
        );

        assertEquals(ErrorCode.BITWIG_TIMEOUT, thrown.getErrorCode(),
            "Should throw BITWIG_TIMEOUT when interrupted during backoff, not stale exception");
        assertTrue(thrown.getMessage().contains("retry backoff interrupted"));
        assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag should be preserved");
        Thread.interrupted(); // Clear for test isolation
    }

    @Test
    void testInterruptedDuringExecution_PreservesInterruptFlagAndThrows() {
        RetryPolicy policy = new RetryPolicy(3, 50, 5000);

        // Pre-interrupt the calling thread; future.get() will throw InterruptedException
        Thread.currentThread().interrupt();

        assertThrows(InterruptedException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-13c", null,
                () -> {
                    Thread.sleep(200); // Task blocks on ForkJoin thread while calling thread is interrupted
                    return "unreachable";
                }
            )
        );

        assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag should remain set");
        Thread.interrupted(); // Clear for test isolation
    }

    // === Hard timeout enforcement (AI-Review: task.execute() blocks) ===

    @Test
    void testHardTimeout_TaskBlocksIndefinitely_EventuallyThrows() {
        // totalTimeoutMs = 300; task blocks forever; hard timeout should trigger
        RetryPolicy policy = new RetryPolicy(3, 50, 300);
        AtomicInteger attempts = new AtomicInteger(0);

        long startMs = System.currentTimeMillis();
        BitwigApiException thrown = assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, mockLogger, "op-14", null,
                () -> {
                    attempts.incrementAndGet();
                    Thread.sleep(10_000); // Simulate a hung operation
                    return "should not reach";
                }
            )
        );
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertEquals(ErrorCode.BITWIG_TIMEOUT, thrown.getErrorCode());
        assertTrue(thrown.getMessage().contains("timeout"));
        assertTrue(attempts.get() >= 1, "Should have attempted at least once");
        // Must complete well before the 10s sleep — bounded by totalTimeoutMs
        assertTrue(elapsedMs < 2000, "Should complete within 2s, not wait for hung task. Actual: " + elapsedMs + "ms");
    }

    // === Null logger handling ===

    @Test
    void testNullLogger_DoesNotThrow() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(3, 10, 5000);

        RetryExecutor.RetryResult<String> result = RetryExecutor.executeWithRetry(
            "test_op", policy, null, null, null,
            () -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "fail");
                }
                return "ok";
            }
        );

        assertEquals("ok", result.value());
    }

    @Test
    void testRetryExhaustion_NullLogger_ThrowsLastExceptionWithoutNpe() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(2, 0, 1000);

        BitwigApiException thrown = assertThrows(BitwigApiException.class, () ->
            RetryExecutor.executeWithRetry(
                "test_op", policy, null, null, null,
                () -> {
                    attempts.incrementAndGet();
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "still failing");
                }
            )
        );

        assertEquals("still failing", thrown.getMessage());
        assertEquals(2, attempts.get(), "Should exhaust configured attempts even when logger is null");
    }

    // === Response envelope format unchanged (regression guard) ===

    @Test
    void testRetryResult_CarriesCorrectMetadata() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = new RetryPolicy(5, 10, 5000);

        RetryExecutor.RetryResult<Map<String, String>> result = RetryExecutor.executeWithRetry(
            "test_op", policy, null, null, null,
            () -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "test_op", "fail");
                }
                return Map.of("action", "done");
            }
        );

        assertEquals(Map.of("action", "done"), result.value());
        assertEquals(3, result.totalAttempts());
        assertEquals(2, result.retryCount());
    }
}
