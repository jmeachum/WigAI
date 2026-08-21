package io.github.fabb.wigai.mcp;
import io.github.fabb.wigai.WigAIExtensionDefinition;
import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.retry.RetryPolicy;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.DeviceController;
import io.github.fabb.wigai.features.TransportController;
import io.github.fabb.wigai.mcp.idempotency.IdempotencyCache;
import io.github.fabb.wigai.mcp.tool.*;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Error envelope construction, operation naming, and retry integration.
 *
 * <p>Split out of the original 1718-line McpErrorHandlerTest along the same seams
 * as the production extraction.
 */
class McpErrorHandlerTest {

    @BeforeEach
    void resetIdempotencyCache() {
        // Fresh cache per test to prevent cross-test interference
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));
    }

    @Test
    void testMutatingOperationsAllowlist_StaysInSyncWithMutatingRequestIdTools() {
        // Discovery-based: derive the expected set from the authoritative MCP registration
        // path (McpServerManager.allToolSpecifications) to prevent dual-list drift.
        TransportController transportController = mock(TransportController.class);
        ClipSceneController clipSceneController = mock(ClipSceneController.class);
        DeviceController deviceController = mock(DeviceController.class);
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);
        WigAIExtensionDefinition extensionDefinition = mock(WigAIExtensionDefinition.class);
        StructuredLogger sl = mock(StructuredLogger.class);

        // Use the authoritative registration list — same source as McpServerManager.createMcpServlet
        List<McpServerFeatures.SyncToolSpecification> allSpecs =
            McpServerManager.allToolSpecifications(
                extensionDefinition, bitwigApiFacade, transportController,
                clipSceneController, deviceController, sl);

        // Structural JSON-schema inspection: check properties map for request_id key
        Set<String> discoveredRequestIdTools = allSpecs.stream()
            .filter(spec -> {
                var properties = spec.tool().inputSchema().properties();
                return properties != null && properties.containsKey("request_id");
            })
            .map(spec -> spec.tool().name())
            .collect(Collectors.toSet());

        assertEquals(discoveredRequestIdTools, McpErrorHandler.mutatingOperationsForTest(),
            "MUTATING_OPERATIONS must match the set of tools whose schemas include request_id. "
            + "If a new mutating tool is added, include request_id in its schema AND add it to MUTATING_OPERATIONS.");
    }

    @Test
    void testExecuteWithErrorHandling_ErrorOperationEqualsMcpToolName_WhenExceptionOperationDiffers() {
        // Arrange
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-test");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        String mcpToolName = "set_selected_device_parameter";
        String internalOperation = "DeviceController.setParam";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "test-op-override");

        // Act: task throws BitwigApiException with a different operation
        McpSchema.CallToolResult result = McpErrorHandler.executeWithErrorHandling(
            mcpToolName,
            arguments,
            mockLogger,
            () -> { throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, internalOperation, "Bad param"); }
        );

        // Assert: error response uses MCP tool name, not exception's operation
        assertTrue(result.isError());
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(json.contains("\"operation\":\"" + mcpToolName + "\""),
            "error.operation should equal MCP tool name, got: " + json);
        assertFalse(json.contains("\"operation\":\"" + internalOperation + "\""),
            "error.operation should NOT contain internal operation name");
    }

    @Test
    void testExecuteWithErrorHandling_ErrorOperationEqualsMcpToolName_ForGenericException() {
        // Arrange
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-test-2");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        String mcpToolName = "transport_start";

        // Act: task throws a generic exception
        McpSchema.CallToolResult result = McpErrorHandler.executeWithErrorHandling(
            mcpToolName,
            null,
            mockLogger,
            () -> { throw new RuntimeException("Something broke"); }
        );

        // Assert: error response uses MCP tool name
        assertTrue(result.isError());
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(json.contains("\"operation\":\"" + mcpToolName + "\""),
            "error.operation should equal MCP tool name for generic exceptions, got: " + json);
    }

    @Test
    void testExecuteWithErrorHandling_RetryableFailureThenSuccess_ReturnsSuccess() {
        // Arrange
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-retry-1");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        AtomicInteger attempts = new AtomicInteger(0);
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "retry-test-1");

        // Act: first attempt fails with retryable error, second succeeds
        McpSchema.CallToolResult result = McpErrorHandler.executeWithErrorHandling(
            "transport_start",
            arguments,
            mockLogger,
            () -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "transport_start", "transient");
                }
                return Map.of("action", "transport_started");
            }
        );

        // Assert: success response after retry
        assertFalse(result.isError());
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(json.contains("\"status\":\"success\""));
        assertTrue(json.contains("\"action\":\"transport_started\""));
        assertEquals(2, attempts.get());
    }

    @Test
    void testExecuteWithErrorHandling_NonRetryableFailure_FailsFastNoRetry() {
        // Arrange
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-retry-2");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        AtomicInteger attempts = new AtomicInteger(0);
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "retry-test-2");

        // Act: non-retryable error should fail fast
        McpSchema.CallToolResult result = McpErrorHandler.executeWithErrorHandling(
            "launch_clip",
            arguments,
            mockLogger,
            () -> {
                attempts.incrementAndGet();
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "launch_clip", "Track 'X' not found");
            }
        );

        // Assert: error response, only one attempt
        assertTrue(result.isError());
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(json.contains("\"code\":\"TRACK_NOT_FOUND\""));
        assertEquals(1, attempts.get(), "Non-retryable failure should not retry");
    }

    @Test
    void testExecuteWithErrorHandling_RetryExhaustion_ReturnsError() {
        // Arrange
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-retry-3");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "retry-test-3");

        // Act: all attempts fail with retryable error
        McpSchema.CallToolResult result = McpErrorHandler.executeWithErrorHandling(
            "transport_stop",
            arguments,
            mockLogger,
            () -> {
                throw new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "transport_stop", "persistent failure");
            }
        );

        // Assert: error response after retry exhaustion
        assertTrue(result.isError());
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(json.contains("\"code\":\"TRANSPORT_ERROR\""));
        assertTrue(json.contains("\"operation\":\"transport_stop\""));
    }

    @Test
    void testExecuteWithErrorHandling_ExplicitNoRetryPolicy_DoesNotRetry() {
        // Arrange
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-retry-4");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        AtomicInteger attempts = new AtomicInteger(0);
        Map<String, Object> arguments = new HashMap<>();

        // Act: use NONE policy — should not retry even retryable errors
        McpSchema.CallToolResult result = McpErrorHandler.executeWithErrorHandling(
            "transport_start",
            arguments,
            mockLogger,
            RetryPolicy.NONE,
            () -> {
                attempts.incrementAndGet();
                throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "transport_start", "fail");
            }
        );

        // Assert: error response, only one attempt
        assertTrue(result.isError());
        assertEquals(1, attempts.get());
    }

    @Test
    void testExecuteWithErrorHandling_EnvelopeFormatUnchangedAfterRetry() {
        // Regression guard: response envelope must remain status + data|error after retry
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-envelope");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        AtomicInteger attempts = new AtomicInteger(0);

        // Success after retry
        McpSchema.CallToolResult successResult = McpErrorHandler.executeWithErrorHandling(
            "transport_start",
            Map.of("request_id", "env-test"),
            mockLogger,
            () -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "transport_start", "transient");
                }
                return Map.of("action", "started");
            }
        );

        String successJson = ((McpSchema.TextContent) successResult.content().get(0)).text();
        assertTrue(successJson.contains("\"status\":\"success\""), "Must have status field");
        assertTrue(successJson.contains("\"data\""), "Must have data field");
        assertFalse(successJson.contains("\"retry\""), "Retry metadata must NOT leak into response envelope");

        // Error after retry exhaustion
        McpSchema.CallToolResult errorResult = McpErrorHandler.executeWithErrorHandling(
            "transport_stop",
            Map.of("request_id", "env-test-2"),
            mockLogger,
            () -> {
                throw new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "transport_stop", "fail");
            }
        );

        String errorJson = ((McpSchema.TextContent) errorResult.content().get(0)).text();
        assertTrue(errorJson.contains("\"status\":\"error\""), "Must have status field");
        assertTrue(errorJson.contains("\"error\""), "Must have error field");
        assertTrue(errorJson.contains("\"operation\":\"transport_stop\""), "error.operation must equal tool name");
    }

    @Test
    void testExecuteWithErrorHandling_ThreeArgOverload_DoesNotRetryRetryableFailure() {
        // Arrange: the 3-arg overload (no arguments) is used by read-only tools like status, list_tracks.
        // It must NOT retry, even for retryable errors.
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-readonly");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        AtomicInteger attempts = new AtomicInteger(0);

        // Act: 3-arg overload — retryable error should NOT be retried
        McpSchema.CallToolResult result = McpErrorHandler.executeWithErrorHandling(
            "status",
            mockLogger,
            () -> {
                attempts.incrementAndGet();
                throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "status", "transient failure");
            }
        );

        // Assert: error response, only one attempt (no retry for read-only path)
        assertTrue(result.isError());
        assertEquals(1, attempts.get(), "3-arg (read-only) overload must not retry");
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(json.contains("\"code\":\"BITWIG_API_ERROR\""));
        assertTrue(json.contains("\"operation\":\"status\""));
    }
}
