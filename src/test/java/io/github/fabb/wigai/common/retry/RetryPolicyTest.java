package io.github.fabb.wigai.common.retry;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RetryPolicy: retry classification, backoff calculation, and policy configuration.
 */
class RetryPolicyTest {

    // === Retryable classification tests (AC 2) ===

    @Test
    void testIsRetryable_BitwigApiError_True() {
        var ex = new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "op", "transient failure");
        assertTrue(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_BitwigTimeout_True() {
        var ex = new BitwigApiException(ErrorCode.BITWIG_TIMEOUT, "op", "timeout");
        assertTrue(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_TransportError_True() {
        var ex = new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "op", "transport failed");
        assertTrue(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_BitwigConnectionError_True() {
        var ex = new BitwigApiException(ErrorCode.BITWIG_CONNECTION_ERROR, "op", "connection lost");
        assertTrue(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_DeviceUnavailable_True() {
        var ex = new BitwigApiException(ErrorCode.DEVICE_UNAVAILABLE, "op", "device unavailable");
        assertTrue(RetryPolicy.isRetryable(ex));
    }

    // === Non-retryable classification tests (AC 3) ===

    @Test
    void testIsRetryable_InvalidParameter_False() {
        var ex = new BitwigApiException(ErrorCode.INVALID_PARAMETER, "op", "bad param");
        assertFalse(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_MissingRequiredParameter_False() {
        var ex = new BitwigApiException(ErrorCode.MISSING_REQUIRED_PARAMETER, "op", "missing");
        assertFalse(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_TrackNotFound_False() {
        var ex = new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "op", "track missing");
        assertFalse(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_DeviceNotSelected_False() {
        var ex = new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "op", "no device");
        assertFalse(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_SceneNotFound_False() {
        var ex = new BitwigApiException(ErrorCode.SCENE_NOT_FOUND, "op", "no scene");
        assertFalse(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_ClipNotFound_False() {
        var ex = new BitwigApiException(ErrorCode.CLIP_NOT_FOUND, "op", "no clip");
        assertFalse(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_InvalidRange_False() {
        var ex = new BitwigApiException(ErrorCode.INVALID_RANGE, "op", "out of range");
        assertFalse(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_InternalError_False() {
        var ex = new BitwigApiException(ErrorCode.INTERNAL_ERROR, "op", "bug");
        assertFalse(RetryPolicy.isRetryable(ex));
    }

    @Test
    void testIsRetryable_GenericRuntimeException_False() {
        assertFalse(RetryPolicy.isRetryable(new RuntimeException("generic")));
    }

    @Test
    void testIsRetryable_IllegalArgumentException_False() {
        assertFalse(RetryPolicy.isRetryable(new IllegalArgumentException("bad arg")));
    }

    // === ErrorCode-based classification ===

    @Test
    void testIsRetryable_ErrorCode_BitwigApiError_True() {
        assertTrue(RetryPolicy.isRetryable(ErrorCode.BITWIG_API_ERROR));
    }

    @Test
    void testIsRetryable_ErrorCode_InvalidParameter_False() {
        assertFalse(RetryPolicy.isRetryable(ErrorCode.INVALID_PARAMETER));
    }

    // === Policy configuration tests ===

    @Test
    void testDefaultPolicy_HasExpectedValues() {
        assertEquals(3, RetryPolicy.DEFAULT.getMaxAttempts());
        assertEquals(100L, RetryPolicy.DEFAULT.getInitialBackoffMs());
        assertEquals(2000L, RetryPolicy.DEFAULT.getTotalTimeoutMs());
        assertTrue(RetryPolicy.DEFAULT.isRetryEnabled());
    }

    @Test
    void testNonePolicy_HasSingleAttempt() {
        assertEquals(1, RetryPolicy.NONE.getMaxAttempts());
        assertFalse(RetryPolicy.NONE.isRetryEnabled());
    }

    @Test
    void testConstructor_InvalidMaxAttempts_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(0, 100, 2000));
    }

    @Test
    void testBackoff_ExponentialDoubling() {
        var policy = new RetryPolicy(5, 100, 10000);
        assertEquals(100L, policy.getBackoffMs(0));  // 100 * 2^0
        assertEquals(200L, policy.getBackoffMs(1));  // 100 * 2^1
        assertEquals(400L, policy.getBackoffMs(2));  // 100 * 2^2
        assertEquals(800L, policy.getBackoffMs(3));  // 100 * 2^3
    }

    @Test
    void testBackoff_ZeroInitial_AlwaysZero() {
        var policy = new RetryPolicy(3, 0, 1000);
        assertEquals(0L, policy.getBackoffMs(0));
        assertEquals(0L, policy.getBackoffMs(1));
    }

    @Test
    void testBackoff_AttemptIndexIsCappedAtTen() {
        var policy = new RetryPolicy(3, 100, 1000);
        // Cap is 2^10; higher attempt indexes should not continue growing
        assertEquals(102_400L, policy.getBackoffMs(10));
        assertEquals(102_400L, policy.getBackoffMs(11));
        assertEquals(102_400L, policy.getBackoffMs(100));
    }

    @Test
    void testConstructor_NegativeBackoffAndTimeoutAreClampedToZero() {
        var policy = new RetryPolicy(2, -50, -1000);
        assertEquals(0L, policy.getInitialBackoffMs());
        assertEquals(0L, policy.getTotalTimeoutMs());
        assertEquals(0L, policy.getBackoffMs(0));
    }
}
