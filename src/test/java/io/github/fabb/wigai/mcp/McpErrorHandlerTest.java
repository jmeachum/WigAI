package io.github.fabb.wigai.mcp;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.retry.RetryPolicy;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for McpErrorHandler, focusing on request_id sanitization.
 */
class McpErrorHandlerTest {

    // === request_id sanitization tests (Story 1.4 AI-Review follow-up) ===

    @Test
    void testSanitizeRequestId_ValidString_ReturnsUnchanged() {
        String result = McpErrorHandler.sanitizeRequestId("test-correlation-123");
        assertEquals("test-correlation-123", result);
    }

    @Test
    void testSanitizeRequestId_Null_ReturnsNull() {
        String result = McpErrorHandler.sanitizeRequestId(null);
        assertNull(result);
    }

    @Test
    void testSanitizeRequestId_EmptyString_ReturnsNull() {
        String result = McpErrorHandler.sanitizeRequestId("");
        assertNull(result);
    }

    @Test
    void testSanitizeRequestId_WhitespaceOnly_ReturnsNull() {
        assertNull(McpErrorHandler.sanitizeRequestId("   "), "Spaces-only should return null");
        assertNull(McpErrorHandler.sanitizeRequestId("\t\t"), "Tabs-only should return null");
        assertNull(McpErrorHandler.sanitizeRequestId("  \t  "), "Mixed whitespace should return null");
    }

    @Test
    void testSanitizeRequestId_NonStringType_ReturnsNull() {
        // Integer
        assertNull(McpErrorHandler.sanitizeRequestId(12345));
        // Boolean
        assertNull(McpErrorHandler.sanitizeRequestId(true));
        // Object
        assertNull(McpErrorHandler.sanitizeRequestId(new Object()));
        // Array/List
        assertNull(McpErrorHandler.sanitizeRequestId(java.util.List.of("a", "b")));
    }

    @Test
    void testSanitizeRequestId_OversizedString_Truncated() {
        // Create a string longer than 256 chars
        String longId = "x".repeat(300);
        String result = McpErrorHandler.sanitizeRequestId(longId);

        assertNotNull(result);
        assertEquals(256, result.length(), "Should truncate to 256 chars");
        assertEquals("x".repeat(256), result);
    }

    @Test
    void testSanitizeRequestId_ControlCharacters_Stripped() {
        // Newline, tab, carriage return, null byte
        String withControlChars = "test\n\t\r\0id";
        String result = McpErrorHandler.sanitizeRequestId(withControlChars);

        assertEquals("testid", result, "Control characters should be stripped");
    }

    @Test
    void testSanitizeRequestId_OnlyControlCharacters_ReturnsNull() {
        String onlyControl = "\n\t\r\0";
        String result = McpErrorHandler.sanitizeRequestId(onlyControl);

        assertNull(result, "String with only control chars should return null");
    }

    @Test
    void testSanitizeRequestId_DeleteCharacter_Stripped() {
        // ASCII 127 (DEL) should be stripped
        String withDel = "test" + (char) 127 + "id";
        String result = McpErrorHandler.sanitizeRequestId(withDel);

        assertEquals("testid", result, "DEL character (127) should be stripped");
    }

    @Test
    void testSanitizeRequestId_UuidFormat_Unchanged() {
        // Standard UUID format should pass through unchanged
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String result = McpErrorHandler.sanitizeRequestId(uuid);

        assertEquals(uuid, result);
    }

    @Test
    void testSanitizeRequestId_SpecialCharacters_Preserved() {
        // Printable special characters should be preserved
        String withSpecial = "req-123_test.abc@example";
        String result = McpErrorHandler.sanitizeRequestId(withSpecial);

        assertEquals(withSpecial, result);
    }

    @Test
    void testSanitizeRequestId_ExactlyMaxLength_NotTruncated() {
        String exactMax = "x".repeat(256);
        String result = McpErrorHandler.sanitizeRequestId(exactMax);

        assertEquals(256, result.length());
        assertEquals(exactMax, result);
    }

    // === Parameter summary tests (Story 1.4 AI-Review: AC 5 counts/shape) ===

    @Test
    void testExtractLoggingParameters_IncludesArgCountForNonCorrelationArgs() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 3);
        arguments.put("value", 0.5);
        arguments.put("request_id", "test-123");

        Map<String, Object> result = McpErrorHandler.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals("test-123", result.get("request_id"));
        assertEquals(2, result.get("arg_count"), "arg_count should count non-correlation args");
    }

    @Test
    void testExtractLoggingParameters_IncludesCollectionCountForListArgs() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", List.of(
            Map.of("parameter_index", 0, "value", 0.5),
            Map.of("parameter_index", 1, "value", 0.7)
        ));
        arguments.put("request_id", "test-456");

        Map<String, Object> result = McpErrorHandler.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals("test-456", result.get("request_id"));
        assertEquals(1, result.get("arg_count"), "arg_count should count non-correlation args");
        assertEquals(2, result.get("parameters_count"), "Should include count of list items");
    }

    @Test
    void testExtractLoggingParameters_OnlyRequestIdNoArgCount() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "test-789");

        Map<String, Object> result = McpErrorHandler.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals("test-789", result.get("request_id"));
        assertNull(result.get("arg_count"), "No non-correlation args means no arg_count");
    }

    @Test
    void testExtractLoggingParameters_NoRequestIdStillHasArgCount() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 5);
        arguments.put("value", 0.3);

        Map<String, Object> result = McpErrorHandler.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertNull(result.get("request_id"));
        assertEquals(2, result.get("arg_count"));
    }

    @Test
    void testExtractLoggingParameters_DoesNotLeakArgumentValues() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 3);
        arguments.put("value", 0.5);
        arguments.put("secret_data", "should-not-appear");

        Map<String, Object> result = McpErrorHandler.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertNull(result.get("parameter_index"), "Actual argument values must not be in logging params");
        assertNull(result.get("value"), "Actual argument values must not be in logging params");
        assertNull(result.get("secret_data"), "Actual argument values must not be in logging params");
    }

    @Test
    void testExtractLoggingParameters_ListOfMaps_IncludesItemKeys() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", List.of(
            Map.of("parameter_index", 0, "value", 0.5),
            Map.of("parameter_index", 1, "value", 0.7)
        ));

        Map<String, Object> result = McpErrorHandler.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals(2, result.get("parameters_count"));
        @SuppressWarnings("unchecked")
        java.util.Set<String> itemKeys = (java.util.Set<String>) result.get("parameters_item_keys");
        assertNotNull(itemKeys, "Should include item keys for List<Map> arguments");
        assertTrue(itemKeys.contains("parameter_index"));
        assertTrue(itemKeys.contains("value"));
    }

    @Test
    void testExtractLoggingParameters_MapArgument_IncludesKeys() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("config", Map.of("host", "localhost", "port", 8080));

        Map<String, Object> result = McpErrorHandler.extractLoggingParameters(arguments);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        java.util.Set<String> configKeys = (java.util.Set<String>) result.get("config_keys");
        assertNotNull(configKeys, "Should include keys for Map arguments");
        assertTrue(configKeys.contains("host"));
        assertTrue(configKeys.contains("port"));
    }

    @Test
    void testExtractLoggingParameters_EmptyCollection_NoItemKeys() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", List.of());

        Map<String, Object> result = McpErrorHandler.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals(0, result.get("parameters_count"));
        assertNull(result.get("parameters_item_keys"), "Empty collection should not have item keys");
    }

    // === error.operation override test (Story 1.4 AI-Review) ===

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

    // === Retry integration tests (Story 1.5) ===

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

    // === Read-only / no-arguments overload does NOT retry (Story 1.5 AI-Review) ===

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
