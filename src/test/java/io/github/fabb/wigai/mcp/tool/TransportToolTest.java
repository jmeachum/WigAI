package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.TransportController;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for TransportTool after migration to unified error handling architecture.
 */
class TransportToolTest {

    @Mock
    private TransportController transportController;
    @Mock
    private StructuredLogger structuredLogger;
    @Mock
    private Logger baseLogger;
    @Mock
    private StructuredLogger.TimedOperation timedOperation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(any(), any(), any())).thenReturn(timedOperation);
    }

    @Test
    void testTransportStartSpecification() {
        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStartSpecification(transportController, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("transport_start", spec.tool().name());
        assertTrue(spec.tool().description().contains("Start"));
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testTransportStopSpecification() {
        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStopSpecification(transportController, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("transport_stop", spec.tool().name());
        assertTrue(spec.tool().description().contains("Stop"));
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testTransportStartSuccessResponseFormat() throws Exception {
        // Arrange: Mock successful transport start
        when(transportController.startTransport()).thenReturn("Bitwig transport started.");

        // Act: Simulate what the tool does - returns raw data that executeWithErrorHandling wraps
        Map<String, Object> responseData = Map.of(
            "action", "transport_started",
            "message", "Bitwig transport started."
        );
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(responseData);

        // Assert: Validate action response format
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "transport_started");
        assertEquals("Bitwig transport started.", dataNode.get("message").asText());
    }

    @Test
    void testTransportStopSuccessResponseFormat() throws Exception {
        // Arrange: Mock successful transport stop
        when(transportController.stopTransport()).thenReturn("Bitwig transport stopped.");

        // Act: Simulate what the tool does
        Map<String, Object> responseData = Map.of(
            "action", "transport_stopped",
            "message", "Bitwig transport stopped."
        );
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(responseData);

        // Assert: Validate action response format
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "transport_stopped");
        assertEquals("Bitwig transport stopped.", dataNode.get("message").asText());
    }

    @Test
    void testTransportErrorResponseFormat() throws Exception {
        // Test error response format for transport operations
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.TRANSPORT_ERROR,
            "transport_start",
            "Transport is not available"
        );

        McpSchema.CallToolResult result = McpErrorHandler.createErrorResponse(exception, structuredLogger);

        // Validate error response format
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("TRANSPORT_ERROR", errorNode.get("code").asText());
        assertEquals("Transport is not available", errorNode.get("message").asText());
        assertEquals("transport_start", errorNode.get("operation").asText());
    }

    @Test
    void testTransportResponseNotDoubleWrapped() throws Exception {
        // Test that transport responses are not double-wrapped
        Map<String, Object> actionData = Map.of(
            "action", "transport_started",
            "message", "Transport started"
        );
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(actionData);

        // This would have caught the double-wrapping bug
        McpResponseTestUtils.assertNotDoubleWrapped(result);

        // Verify it's properly structured as an action response
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "transport_started");
        assertEquals("Transport started", dataNode.get("message").asText());
    }

    // === Story 1.4: request_id correlation tests ===

    @Test
    void testTransportStartWithRequestIdIncludesItInLoggingContext() {
        // RED PHASE: This test should FAIL until we implement request_id propagation
        // AC 2, AC 3: request_id in tool arguments must be included in logging context

        // Arrange
        when(transportController.startTransport()).thenReturn("Bitwig transport started.");

        // Create arguments with request_id
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "test-correlation-123");

        // Create mock request with arguments
        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        // Get the specification and extract handler
        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStartSpecification(transportController, structuredLogger);

        // Act: Invoke the handler
        spec.callHandler().apply(null, mockRequest);

        // Assert: Verify startTimedOperation was called with parameters containing request_id
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("transport_start"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Parameters map should not be null when request_id is provided");
        assertEquals("test-correlation-123", capturedParams.get("request_id"),
            "request_id should be included in logging parameters");
    }

    @Test
    void testTransportStartWithoutRequestIdStillWorks() {
        // AC 3: Backward compatibility - tools work without request_id

        // Arrange
        when(transportController.startTransport()).thenReturn("Bitwig transport started.");

        // Create empty arguments (no request_id)
        Map<String, Object> arguments = new HashMap<>();

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStartSpecification(transportController, structuredLogger);

        // Act: Invoke the handler - should not throw
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Operation completes successfully
        assertNotNull(result);
        assertFalse(result.isError(), "Operation should succeed without request_id");

        // Verify logging still happened
        verify(structuredLogger).startTimedOperation(any(), eq("transport_start"), any());
    }

    @Test
    void testTransportStartFailureIncludesRequestIdAndErrorCodeInLogs() {
        // RED PHASE: AC 4 - On failure, logs include ErrorCode and request_id

        // Arrange: Mock transport to throw an error
        when(transportController.startTransport()).thenThrow(
            new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "transport_start", "Transport unavailable")
        );

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "error-correlation-456");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStartSpecification(transportController, structuredLogger);

        // Act: Invoke the handler (will fail)
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Result is an error
        assertTrue(result.isError(), "Result should be an error");

        // Assert: Verify startTimedOperation was called with request_id in parameters
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("transport_start"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Parameters should include request_id even on failure path");
        assertEquals("error-correlation-456", capturedParams.get("request_id"),
            "request_id should be in logging parameters for error correlation");

        // Assert: Verify failure was logged with correct ErrorCode
        verify(timedOperation).failure(eq(ErrorCode.TRANSPORT_ERROR), any());
    }

    @Test
    void testTransportStopWithRequestIdIncludesItInLoggingContext() {
        // AC 3: transport_stop also accepts request_id

        when(transportController.stopTransport()).thenReturn("Bitwig transport stopped.");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "stop-correlation-789");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStopSpecification(transportController, structuredLogger);

        // Act
        spec.callHandler().apply(null, mockRequest);

        // Assert
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("transport_stop"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams);
        assertEquals("stop-correlation-789", capturedParams.get("request_id"));
    }
}
