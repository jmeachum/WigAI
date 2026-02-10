package io.github.fabb.wigai.mcp;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
