package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.TransportController;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("atdd_red")
class TransportToolRequestIdLoggingAtddRedTest {

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
    @DisplayName("1.4-ATDD-001 [P1] Given request_id for transport_start, when executed, then request_id is included in structured logging parameters")
    void transportStartIncludesRequestIdInStructuredLoggingParameters() {
        when(transportController.startTransport()).thenReturn("Bitwig transport started.");

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStartSpecification(transportController, structuredLogger);
        spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("transport_start", Map.of(
                "request_id", "req-123",
                "notes", List.of("this", "should", "not", "be", "logged")
            ))
        );

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(eq("op-123"), eq("transport_start"), paramsCaptor.capture());
        Map<String, Object> params = paramsCaptor.getValue();

        assertNotNull(params, "Expected McpErrorHandler to pass a sanitized parameters map to StructuredLogger");
        assertEquals("req-123", params.get("request_id"));
        assertFalse(params.containsKey("notes"), "Expected log hygiene: large payload fields should not be logged by default");
    }

    @Test
    @DisplayName("1.4-ATDD-002 [P1] Given request_id for transport_start failure, when error returned, then logs include ErrorCode and request_id context")
    void transportStartFailureIncludesErrorCodeAndRequestIdContext() {
        when(transportController.startTransport()).thenThrow(new BitwigApiException(
            ErrorCode.TRANSPORT_ERROR,
            "transport_start",
            "Transport is not available"
        ));

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStartSpecification(transportController, structuredLogger);
        spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("transport_start", Map.of("request_id", "req-err-1"))
        );

        verify(timedOperation).failure(eq(ErrorCode.TRANSPORT_ERROR), eq("Transport is not available"));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(eq("op-123"), eq("transport_start"), paramsCaptor.capture());
        Map<String, Object> params = paramsCaptor.getValue();

        assertNotNull(params, "Expected McpErrorHandler to pass a sanitized parameters map to StructuredLogger");
        assertEquals("req-err-1", params.get("request_id"));
    }

    private McpSchema.CallToolRequest buildRequest(String name, Map<String, Object> arguments) {
        return McpSchema.CallToolRequest.builder()
            .name(name)
            .arguments(arguments)
            .build();
    }
}

