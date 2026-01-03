package io.github.fabb.wigai.common.logging;

import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StructuredLogger, particularly TimedOperation request_id correlation.
 * Story 1.4: Ensures request_id from parameters is included in completion/failure logs.
 */
class StructuredLoggerTest {

    @Mock
    private Logger baseLogger;

    private StructuredLogger structuredLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        structuredLogger = new StructuredLogger(baseLogger, "TestComponent");
    }

    @Test
    void testTimedOperationSuccessIncludesRequestIdInLog() {
        // Story 1.4 AC 2: request_id must be included in ALL logs for that invocation
        // This includes the completion log, not just the start log

        // Arrange: Start an operation with request_id in parameters
        Map<String, Object> parameters = Map.of("request_id", "test-correlation-123");
        StructuredLogger.TimedOperation timedOp = structuredLogger.startTimedOperation("op-1", "transport_start", parameters);

        // Reset mock to clear the start log call
        reset(baseLogger);

        // Act: Complete the operation successfully
        timedOp.success("operation completed");

        // Assert: Verify the completion log includes request_id
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(baseLogger).info(messageCaptor.capture());

        String logMessage = messageCaptor.getValue();
        assertTrue(logMessage.contains("request_id=test-correlation-123") || logMessage.contains("request_id\":\"test-correlation-123"),
            "Completion log should include request_id. Actual message: " + logMessage);
    }

    @Test
    void testTimedOperationFailureIncludesRequestIdInLog() {
        // Story 1.4 AC 4: On failure, logs include request_id if present

        // Arrange: Start an operation with request_id in parameters
        Map<String, Object> parameters = Map.of("request_id", "error-correlation-456");
        StructuredLogger.TimedOperation timedOp = structuredLogger.startTimedOperation("op-2", "transport_start", parameters);

        // Reset mock to clear the start log call
        reset(baseLogger);

        // Act: Complete the operation with failure
        timedOp.failure(ErrorCode.TRANSPORT_ERROR, "Transport unavailable");

        // Assert: Verify the failure log includes request_id
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(baseLogger).error(messageCaptor.capture());

        String logMessage = messageCaptor.getValue();
        assertTrue(logMessage.contains("request_id=error-correlation-456") || logMessage.contains("request_id\":\"error-correlation-456"),
            "Failure log should include request_id. Actual message: " + logMessage);
    }

    @Test
    void testTimedOperationSuccessWorksWithoutRequestId() {
        // Story 1.4: Backward compatibility - operations work without request_id

        // Arrange: Start an operation without parameters
        StructuredLogger.TimedOperation timedOp = structuredLogger.startTimedOperation("op-3", "transport_start", null);

        // Reset mock to clear the start log call
        reset(baseLogger);

        // Act: Complete the operation successfully - should not throw
        timedOp.success("operation completed");

        // Assert: Log was called without error
        verify(baseLogger).info(any(String.class));
    }

    @Test
    void testTimedOperationFailureWorksWithoutRequestId() {
        // Story 1.4: Backward compatibility - operations work without request_id

        // Arrange: Start an operation without parameters
        StructuredLogger.TimedOperation timedOp = structuredLogger.startTimedOperation("op-4", "transport_start", null);

        // Reset mock to clear the start log call
        reset(baseLogger);

        // Act: Complete the operation with failure - should not throw
        timedOp.failure(ErrorCode.TRANSPORT_ERROR, "Transport unavailable");

        // Assert: Log was called without error
        verify(baseLogger).error(any(String.class));
    }
}
