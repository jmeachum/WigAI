package io.github.fabb.wigai.common.retry;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounded retry executor for baseline mutating tool operations.
 * Wraps operations with retry logic following the configured RetryPolicy,
 * classifying exceptions as retryable or non-retryable and logging retry attempts.
 * <p>
 * Guarantees:
 * <ul>
 *   <li>Max attempts are finite and explicit</li>
 *   <li>Backoff is finite and predictable (exponential with bounded delay)</li>
 *   <li>Total runtime is bounded by totalTimeoutMs to prevent hangs</li>
 *   <li>Non-retryable failures fail fast with zero retries</li>
 * </ul>
 */
public class RetryExecutor {

    /**
     * Functional interface for operations that may be retried.
     */
    @FunctionalInterface
    public interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    /**
     * Executes an operation with bounded retry according to the given policy.
     * Logs retry attempts with correlation metadata when a logger is provided.
     *
     * @param <T>       The return type of the operation
     * @param operation The operation name (for logging)
     * @param policy    The retry policy to follow
     * @param logger    Optional structured logger for retry logging (may be null)
     * @param operationId Optional operation correlation ID (may be null)
     * @param loggingParams Optional logging parameters including request_id (may be null)
     * @param task      The operation to execute
     * @return The result of the operation
     * @throws Exception The last exception if all attempts fail
     */
    public static <T> RetryResult<T> executeWithRetry(
            String operation,
            RetryPolicy policy,
            StructuredLogger logger,
            String operationId,
            Map<String, Object> loggingParams,
            RetryableOperation<T> task) throws Exception {

        if (!policy.isRetryEnabled()) {
            // Single attempt, no retry overhead
            T result = task.execute();
            return new RetryResult<>(result, 1, 0);
        }

        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        int attempt = 0;

        while (attempt < policy.getMaxAttempts()) {
            // Check total timeout before each attempt (except the first)
            if (attempt > 0 && policy.getTotalTimeoutMs() > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= policy.getTotalTimeoutMs()) {
                    logRetryExhausted(logger, operationId, operation, attempt, policy.getMaxAttempts(),
                        System.currentTimeMillis() - startTime, lastException, loggingParams,
                        "total timeout exceeded (" + policy.getTotalTimeoutMs() + "ms)");
                    throw new BitwigApiException(ErrorCode.BITWIG_TIMEOUT, operation,
                        "total timeout exceeded (" + policy.getTotalTimeoutMs() + "ms)");
                }
            }

            try {
                T result;
                if (policy.getTotalTimeoutMs() > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long remainingMs = policy.getTotalTimeoutMs() - elapsed;
                    if (remainingMs <= 0) {
                        throw new BitwigApiException(ErrorCode.BITWIG_TIMEOUT, operation,
                            "total timeout exceeded (" + policy.getTotalTimeoutMs() + "ms)");
                    }
                    result = executeWithTimeout(task, remainingMs, operation);
                } else {
                    result = task.execute();
                }
                if (attempt > 0 && logger != null) {
                    logRetrySuccess(logger, operationId, operation, attempt + 1, policy.getMaxAttempts(),
                        System.currentTimeMillis() - startTime, loggingParams);
                }
                return new RetryResult<>(result, attempt + 1, attempt);
            } catch (Exception e) {
                lastException = e;

                // Non-retryable exceptions fail fast — no retry
                if (!RetryPolicy.isRetryable(e)) {
                    throw e;
                }

                // Last attempt — don't sleep, just throw
                if (attempt + 1 >= policy.getMaxAttempts()) {
                    break;
                }

                // Log the retry attempt
                if (logger != null) {
                    logRetryAttempt(logger, operationId, operation, attempt + 1, policy.getMaxAttempts(),
                        e, loggingParams);
                }

                // Backoff before next attempt (bounded by total timeout)
                long backoffMs = policy.getBackoffMs(attempt);
                if (policy.getTotalTimeoutMs() > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long remaining = policy.getTotalTimeoutMs() - elapsed;
                    backoffMs = Math.min(backoffMs, Math.max(0, remaining));
                }

                if (backoffMs > 0) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BitwigApiException(ErrorCode.BITWIG_TIMEOUT, operation,
                            "retry backoff interrupted");
                    }
                }

                attempt++;
            }
        }

        // All attempts exhausted
        logRetryExhausted(logger, operationId, operation, attempt + 1, policy.getMaxAttempts(),
            System.currentTimeMillis() - startTime, lastException, loggingParams, "max attempts reached");
        throw lastException;
    }

    /**
     * Executes a task with a hard timeout using a CompletableFuture.
     * If the task blocks beyond timeoutMs, cancels it and throws BITWIG_TIMEOUT.
     * Preserves interrupt flag if the calling thread is interrupted.
     */
    private static <T> T executeWithTimeout(RetryableOperation<T> task, long timeoutMs, String operation) throws Exception {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.execute();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BitwigApiException(ErrorCode.BITWIG_TIMEOUT, operation,
                "operation blocked beyond timeout (" + timeoutMs + "ms)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException ce) {
                cause = ce.getCause();
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }

    private static void logRetryAttempt(StructuredLogger logger, String operationId, String operation,
                                         int attempt, int maxAttempts, Exception error,
                                         Map<String, Object> loggingParams) {
        StringBuilder msg = new StringBuilder();
        msg.append("Retry attempt ").append(attempt).append("/").append(maxAttempts);
        msg.append(" for ").append(operation);
        if (error instanceof BitwigApiException bex) {
            msg.append(" | error_code=").append(bex.getErrorCode().getCode());
        }
        msg.append(" | reason=").append(error.getMessage());
        appendParams(msg, loggingParams);
        logger.warn(operationId, operation, msg.toString());
    }

    private static void logRetrySuccess(StructuredLogger logger, String operationId, String operation,
                                         int totalAttempts, int maxAttempts, long totalDurationMs,
                                         Map<String, Object> loggingParams) {
        StringBuilder msg = new StringBuilder();
        msg.append("Operation succeeded after retry");
        msg.append(" | attempts=").append(totalAttempts).append("/").append(maxAttempts);
        msg.append(" | total_duration=").append(totalDurationMs).append("ms");
        appendParams(msg, loggingParams);
        logger.info(operationId, operation, msg.toString());
    }

    private static void logRetryExhausted(StructuredLogger logger, String operationId, String operation,
                                            int totalAttempts, int maxAttempts, long totalDurationMs,
                                            Exception lastError, Map<String, Object> loggingParams,
                                            String reason) {
        if (logger == null) return;
        StringBuilder msg = new StringBuilder();
        msg.append("Retry exhausted for ").append(operation);
        msg.append(" | attempts=").append(totalAttempts).append("/").append(maxAttempts);
        msg.append(" | total_duration=").append(totalDurationMs).append("ms");
        msg.append(" | reason=").append(reason);
        if (lastError instanceof BitwigApiException bex) {
            msg.append(" | final_error_code=").append(bex.getErrorCode().getCode());
        }
        msg.append(" | final_error=").append(lastError != null ? lastError.getMessage() : "unknown");
        appendParams(msg, loggingParams);
        logger.error(operationId, operation, msg.toString());
    }

    private static void appendParams(StringBuilder msg, Map<String, Object> loggingParams) {
        if (loggingParams != null) {
            for (Map.Entry<String, Object> entry : loggingParams.entrySet()) {
                msg.append(" | ").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
    }

    /**
     * Result of a retry-aware execution, including metadata about retry attempts.
     *
     * @param <T> The result type
     */
    public record RetryResult<T>(T value, int totalAttempts, int retryCount) {}
}
