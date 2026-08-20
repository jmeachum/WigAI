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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for McpErrorHandler, focusing on request_id sanitization.
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

    // === request_id sanitization tests (Story 1.4 AI-Review follow-up) ===

    @Test
    void testSanitizeRequestId_ValidString_ReturnsUnchanged() {
        String result = RequestContextExtractor.sanitizeRequestId("test-correlation-123");
        assertEquals("test-correlation-123", result);
    }

    @Test
    void testSanitizeRequestId_Null_ReturnsNull() {
        String result = RequestContextExtractor.sanitizeRequestId(null);
        assertNull(result);
    }

    @Test
    void testSanitizeRequestId_EmptyString_ReturnsNull() {
        String result = RequestContextExtractor.sanitizeRequestId("");
        assertNull(result);
    }

    @Test
    void testSanitizeRequestId_WhitespaceOnly_ReturnsNull() {
        assertNull(RequestContextExtractor.sanitizeRequestId("   "), "Spaces-only should return null");
        assertNull(RequestContextExtractor.sanitizeRequestId("\t\t"), "Tabs-only should return null");
        assertNull(RequestContextExtractor.sanitizeRequestId("  \t  "), "Mixed whitespace should return null");
    }

    @Test
    void testSanitizeRequestId_NonStringType_ReturnsNull() {
        // Integer
        assertNull(RequestContextExtractor.sanitizeRequestId(12345));
        // Boolean
        assertNull(RequestContextExtractor.sanitizeRequestId(true));
        // Object
        assertNull(RequestContextExtractor.sanitizeRequestId(new Object()));
        // Array/List
        assertNull(RequestContextExtractor.sanitizeRequestId(java.util.List.of("a", "b")));
    }

    @Test
    void testSanitizeRequestId_OversizedString_Truncated() {
        // Create a string longer than 256 chars
        String longId = "x".repeat(300);
        String result = RequestContextExtractor.sanitizeRequestId(longId);

        assertNotNull(result);
        assertEquals(256, result.length(), "Should truncate to 256 chars");
        assertEquals("x".repeat(256), result);
    }

    @Test
    void testSanitizeRequestId_ControlCharacters_Stripped() {
        // Newline, tab, carriage return, null byte
        String withControlChars = "test\n\t\r\0id";
        String result = RequestContextExtractor.sanitizeRequestId(withControlChars);

        assertEquals("testid", result, "Control characters should be stripped");
    }

    @Test
    void testSanitizeRequestId_OnlyControlCharacters_ReturnsNull() {
        String onlyControl = "\n\t\r\0";
        String result = RequestContextExtractor.sanitizeRequestId(onlyControl);

        assertNull(result, "String with only control chars should return null");
    }

    @Test
    void testSanitizeRequestId_DeleteCharacter_Stripped() {
        // ASCII 127 (DEL) should be stripped
        String withDel = "test" + (char) 127 + "id";
        String result = RequestContextExtractor.sanitizeRequestId(withDel);

        assertEquals("testid", result, "DEL character (127) should be stripped");
    }

    @Test
    void testSanitizeRequestId_UuidFormat_Unchanged() {
        // Standard UUID format should pass through unchanged
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String result = RequestContextExtractor.sanitizeRequestId(uuid);

        assertEquals(uuid, result);
    }

    @Test
    void testSanitizeRequestId_SpecialCharacters_Preserved() {
        // Printable special characters should be preserved
        String withSpecial = "req-123_test.abc@example";
        String result = RequestContextExtractor.sanitizeRequestId(withSpecial);

        assertEquals(withSpecial, result);
    }

    @Test
    void testSanitizeRequestId_ExactlyMaxLength_NotTruncated() {
        String exactMax = "x".repeat(256);
        String result = RequestContextExtractor.sanitizeRequestId(exactMax);

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

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

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

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals("test-456", result.get("request_id"));
        assertEquals(1, result.get("arg_count"), "arg_count should count non-correlation args");
        assertEquals(2, result.get("parameters_count"), "Should include count of list items");
    }

    @Test
    void testExtractLoggingParameters_OnlyRequestIdNoArgCount() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "test-789");

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals("test-789", result.get("request_id"));
        assertNull(result.get("arg_count"), "No non-correlation args means no arg_count");
    }

    @Test
    void testExtractLoggingParameters_NoRequestIdStillHasArgCount() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 5);
        arguments.put("value", 0.3);

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

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

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

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

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

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

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

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

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

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

    // === Idempotency dedupe integration tests (Story 1.7) ===

    private StructuredLogger createMockLogger() {
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-dedupe");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);
        return mockLogger;
    }

    @Test
    void testDedupe_SameToolSameRequestId_ReturnsFirstResultWithoutReExecution() {
        // AC 1: same (tool_name, request_id) returns cached result without re-executing
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "dedupe-test-1");

        // First call — executes normally
        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "transport_started");
            }
        );

        // Second call — same tool + request_id, should NOT re-execute
        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Task should execute only once; second call is dedupe hit");
        assertSame(first, second, "Dedupe hit should return exact same result object");
        assertFalse(first.isError());
        String json = ((McpSchema.TextContent) first.content().get(0)).text();
        assertTrue(json.contains("\"status\":\"success\""));
    }

    @Test
    void testDedupe_SameRequestIdDifferentTools_NoCrossContamination() {
        // AC 1: (tool_name, request_id) key prevents cross-tool collision
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "shared-req-id");

        // Call transport_start
        McpSchema.CallToolResult startResult = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        // Call transport_stop with same request_id — different tool, should execute
        McpSchema.CallToolResult stopResult = McpErrorHandler.executeWithErrorHandling(
            "transport_stop", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "stopped");
            }
        );

        assertEquals(2, executions.get(), "Different tools with same request_id should both execute");
        assertNotSame(startResult, stopResult);
    }

    @Test
    void testDedupe_NoRequestId_NeverDedupes() {
        // AC 3: no request_id means no dedupe (backward compatible)
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> argsNoReqId = new HashMap<>();
        argsNoReqId.put("parameter_index", 0);

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", argsNoReqId, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", argsNoReqId, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(), "Without request_id, every call should execute");
    }

    @Test
    void testDedupe_NullArguments_NeverDedupes() {
        // AC 3: null arguments means no dedupe
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", null, mockLogger, RetryPolicy.DEFAULT,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", null, mockLogger, RetryPolicy.DEFAULT,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(), "Null arguments should never dedupe");
    }

    @Test
    void testDedupe_InvalidRequestId_NeverDedupes() {
        // AC 3: invalid request_id (non-string, empty) means no dedupe
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> argsInvalid = new HashMap<>();
        argsInvalid.put("request_id", 12345); // non-string

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", argsInvalid, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", argsInvalid, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(), "Non-string request_id should never dedupe");
    }

    @Test
    void testDedupe_CachedErrorResult_ReturnsSameError() {
        // AC 1: cached result includes errors — first failure is returned on dedupe hit
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "error-dedupe-test");

        // First call fails
        McpSchema.CallToolResult firstError = McpErrorHandler.executeWithErrorHandling(
            "launch_clip", args, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                throw new BitwigApiException(ErrorCode.CLIP_NOT_FOUND, "launch_clip", "Clip not found");
            }
        );

        // Second call with same key — should return cached error, not re-execute
        McpSchema.CallToolResult secondError = McpErrorHandler.executeWithErrorHandling(
            "launch_clip", args, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Error result should also be cached for dedupe");
        assertSame(firstError, secondError);
        assertTrue(firstError.isError());
        String json = ((McpSchema.TextContent) firstError.content().get(0)).text();
        assertTrue(json.contains("\"code\":\"CLIP_NOT_FOUND\""));
    }

    @Test
    void testDedupe_TtlExpiry_AllowsReExecution() {
        // AC 2: after TTL expiry, re-execution occurs
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "ttl-test");

        // First call
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "first");
            }
        );

        // Advance clock past TTL
        clock.set(1000L + 60_000L);

        // Second call — TTL expired, should re-execute
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "second");
            }
        );

        assertEquals(2, executions.get(), "After TTL expiry, request should re-execute");
    }

    @Test
    void testDedupe_ReadOnlyPath_NeverDedupes() {
        // AC 3: 3-arg overload (read-only) should never dedupe
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        // Even though we use same operation name, 3-arg path has no arguments
        McpErrorHandler.executeWithErrorHandling(
            "status", mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("transport", "playing");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "status", mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("transport", "playing");
            }
        );

        assertEquals(2, executions.get(), "Read-only path should never dedupe");
    }

    @Test
    void testDedupe_NonMutatingOperationWithRequestId_DoesNotDedupesOnSharedPath() {
        // Review follow-up: shared execution path must enforce mutating-only dedupe.
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);
        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "status-req-1");

        McpErrorHandler.executeWithErrorHandling(
            "status", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("transport", "playing");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "status", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("transport", "playing");
            }
        );

        assertEquals(2, executions.get(),
            "Non-mutating operations must not dedupe even when request_id is provided");
    }

    @Test
    void testExecuteWithValidation_MutatingOperationWithRequestId_Dedupes() {
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger validations = new AtomicInteger(0);
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "validation-dedupe");
        args.put("parameter_index", 7);

        McpSchema.CallToolResult first = McpErrorHandler.executeWithValidation(
            "set_selected_device_parameter",
            args,
            mockLogger,
            (arguments, operation) -> {
                validations.incrementAndGet();
                return ((Number) arguments.get("parameter_index")).intValue();
            },
            validatedIndex -> {
                executions.incrementAndGet();
                return Map.of("action", "parameter_set", "parameter_index", validatedIndex);
            }
        );

        McpSchema.CallToolResult second = McpErrorHandler.executeWithValidation(
            "set_selected_device_parameter",
            args,
            mockLogger,
            (arguments, operation) -> {
                validations.incrementAndGet();
                return ((Number) arguments.get("parameter_index")).intValue();
            },
            validatedIndex -> {
                executions.incrementAndGet();
                return Map.of("action", "parameter_set", "parameter_index", validatedIndex);
            }
        );

        assertEquals(1, validations.get(), "Validation should run once when dedupe returns cached result");
        assertEquals(1, executions.get(), "Mutating executeWithValidation path should dedupe by request_id");
        assertSame(first, second, "Dedupe hit should return cached first result");
    }

    @Test
    void testExecuteWithValidation_NonMutatingOperationWithRequestId_DoesNotDedupe() {
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger validations = new AtomicInteger(0);
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "validation-non-mutating");
        args.put("track_index", 1);

        McpErrorHandler.executeWithValidation(
            "list_tracks",
            args,
            mockLogger,
            (arguments, operation) -> {
                validations.incrementAndGet();
                return ((Number) arguments.get("track_index")).intValue();
            },
            validated -> {
                executions.incrementAndGet();
                return Map.of("track_index", validated);
            }
        );

        McpErrorHandler.executeWithValidation(
            "list_tracks",
            args,
            mockLogger,
            (arguments, operation) -> {
                validations.incrementAndGet();
                return ((Number) arguments.get("track_index")).intValue();
            },
            validated -> {
                executions.incrementAndGet();
                return Map.of("track_index", validated);
            }
        );

        assertEquals(2, validations.get(), "Non-mutating executeWithValidation should validate each call");
        assertEquals(2, executions.get(), "Non-mutating executeWithValidation should not dedupe");
    }

    @Test
    void testDedupe_RetryThenDedupe_FirstSuccessfulResultCached() {
        // Coherence: retry succeeds on 2nd attempt, then dedupe returns that success
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "retry-then-dedupe");

        // First call — retries once, then succeeds
        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                int attempt = executions.incrementAndGet();
                if (attempt == 1) {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "transport_start", "transient");
                }
                return Map.of("action", "transport_started");
            }
        );

        // Second call — dedupe hit, no execution
        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(2, executions.get(), "First call retried once (2 attempts); second call deduped (0 attempts)");
        assertSame(first, second, "Dedupe should return the first successful result");
        assertFalse(first.isError());
    }

    @Test
    void testDedupe_EnvelopeFormatPreserved() {
        // Regression: dedupe hit must return identical envelope format
        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "envelope-test");

        // Success case
        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> Map.of("action", "transport_started", "message", "OK")
        );

        McpSchema.CallToolResult deduped = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> { throw new RuntimeException("should not reach"); }
        );

        String firstJson = ((McpSchema.TextContent) first.content().get(0)).text();
        String dedupedJson = ((McpSchema.TextContent) deduped.content().get(0)).text();

        assertEquals(firstJson, dedupedJson, "Dedupe hit must return identical JSON envelope");
        assertTrue(firstJson.contains("\"status\":\"success\""));
        assertTrue(firstJson.contains("\"data\""));
        assertFalse(firstJson.contains("\"dedupe\""), "No dedupe metadata should leak into response envelope");
    }

    @Test
    void testDedupe_OperationNamePreserved() {
        // Regression: error.operation must still equal MCP tool name after dedupe
        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "op-name-test");

        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args, mockLogger, RetryPolicy.NONE,
            () -> {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "internal_op", "No device");
            }
        );

        McpSchema.CallToolResult deduped = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args, mockLogger, RetryPolicy.NONE,
            () -> { throw new RuntimeException("should not reach"); }
        );

        String json = ((McpSchema.TextContent) first.content().get(0)).text();
        assertTrue(json.contains("\"operation\":\"set_selected_device_parameter\""),
            "error.operation should equal MCP tool name, got: " + json);
        assertSame(first, deduped);
    }

    // === Review follow-up: extractRawRequestId tests ===

    @Test
    void testExtractRawRequestId_ValidString_ReturnsUnchanged() {
        assertEquals("test-123", RequestContextExtractor.extractRawRequestId("test-123"));
    }

    @Test
    void testExtractRawRequestId_Null_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId(null));
    }

    @Test
    void testExtractRawRequestId_NonString_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId(12345));
    }

    @Test
    void testExtractRawRequestId_Blank_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId("   "));
    }

    @Test
    void testExtractRawRequestId_LongString_NoTruncation() {
        // extractRawRequestId must preserve full string for cache keying (no truncation)
        String longId = "x".repeat(500);
        String result = RequestContextExtractor.extractRawRequestId(longId);
        assertEquals(500, result.length(), "Raw request_id must not be truncated for cache keying");
        assertEquals(longId, result);
    }

    @Test
    void testExtractRawRequestId_ExactlyMaxLength_Accepted() {
        String exactMax = "x".repeat(RequestContextExtractor.MAX_RAW_REQUEST_ID_LENGTH);
        String result = RequestContextExtractor.extractRawRequestId(exactMax);
        assertNotNull(result, "request_id at exactly max length must be accepted");
        assertEquals(RequestContextExtractor.MAX_RAW_REQUEST_ID_LENGTH, result.length());
    }

    @Test
    void testExtractRawRequestId_ExceedsMaxLength_ReturnsNull() {
        String oversized = "x".repeat(RequestContextExtractor.MAX_RAW_REQUEST_ID_LENGTH + 1);
        String result = RequestContextExtractor.extractRawRequestId(oversized);
        assertNull(result, "Oversized request_id must be rejected (skip dedupe) to avoid memory pressure");
    }

    // === Review follow-up round 8: reject control/non-printable request_id for dedupe keying ===

    @Test
    void testExtractRawRequestId_ControlCharacters_ReturnsNull() {
        // request_id with embedded control chars must be rejected for dedupe keying
        assertNull(RequestContextExtractor.extractRawRequestId("test\nid"),
            "Newline in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("test\tid"),
            "Tab in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("test\0id"),
            "Null byte in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("test\rid"),
            "Carriage return in request_id must cause rejection");
    }

    @Test
    void testExtractRawRequestId_DeleteCharacter_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId("test" + (char) 127 + "id"),
            "DEL character (127) in request_id must cause rejection");
    }

    @Test
    void testExtractRawRequestId_OnlyControlCharacters_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId("\n\t\r\0"),
            "All-control request_id must be rejected");
    }

    @Test
    void testExtractRawRequestId_PrintableCharactersOnly_Accepted() {
        // Standard UUIDs, alphanumeric, hyphens, dots, underscores
        assertEquals("550e8400-e29b-41d4-a716-446655440000",
            RequestContextExtractor.extractRawRequestId("550e8400-e29b-41d4-a716-446655440000"));
        assertEquals("req-123_test.abc@example",
            RequestContextExtractor.extractRawRequestId("req-123_test.abc@example"));
    }

    @Test
    void testExtractRawRequestId_NonAsciiCharacters_ReturnsNull() {
        // Characters above ASCII 126 must be rejected for strict printable-ASCII keying
        assertNull(RequestContextExtractor.extractRawRequestId("test\u0080id"),
            "Extended ASCII (128) in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("café"),
            "Non-ASCII accented characters in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("test\u00FFid"),
            "Latin-1 Supplement (255) in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("\u4E2D\u6587"),
            "CJK characters in request_id must cause rejection");
    }

    @Test
    void testExtractRawRequestId_BoundaryPrintableAscii_Accepted() {
        // Space (32) and tilde (126) are the boundaries of printable ASCII
        assertEquals(" test ", RequestContextExtractor.extractRawRequestId(" test "),
            "Space (ASCII 32) must be accepted");
        assertEquals("~test~", RequestContextExtractor.extractRawRequestId("~test~"),
            "Tilde (ASCII 126) must be accepted");
        assertEquals("test!@#$%^&*(){}|", RequestContextExtractor.extractRawRequestId("test!@#$%^&*(){}|"),
            "All printable ASCII symbols must be accepted");
    }

    @Test
    void testDedupe_ControlCharRequestId_SkipsDedupe() {
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "test\ninjection");

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(),
            "Control-char request_id must skip dedupe — both calls execute");
    }

    @Test
    void testDedupe_OversizedRequestId_SkipsDedupe() {
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        String oversized = "x".repeat(RequestContextExtractor.MAX_RAW_REQUEST_ID_LENGTH + 1);
        Map<String, Object> args = new HashMap<>();
        args.put("request_id", oversized);

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(), "Oversized request_id must skip dedupe — both calls execute");
    }

    // === Review follow-up: long request_id keying — no truncation-based collision ===

    @Test
    void testDedupe_LongRequestIds_DifferAfterTruncationPoint_NoCacheCollision() {
        // Two request_ids that share the first 256 chars but differ after must NOT collide
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        String prefix = "a".repeat(256);
        String id1 = prefix + "-suffix-ONE";
        String id2 = prefix + "-suffix-TWO";

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", id1);
        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", id2);

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args1, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "first");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args2, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "second");
            }
        );

        assertEquals(2, executions.get(),
                "Different long request_ids must not collide even if they share the first 256 chars");
    }

    // === Review follow-up: dedupe-hit log includes outcome ===

    @Test
    void testDedupe_HitLogIncludesOutcome_Success() {
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "log-outcome-success");

        // First call — success
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> Map.of("action", "started")
        );

        // Second call — dedupe hit, should log outcome=success
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> { throw new RuntimeException("should not reach"); }
        );

        verify(mockLogger).info(any(), eq("transport_start"),
                contains("outcome=success"));
    }

    @Test
    void testDedupe_HitLogIncludesOutcome_Error() {
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "log-outcome-error");

        // First call — error
        McpErrorHandler.executeWithErrorHandling(
            "launch_clip", args, mockLogger, RetryPolicy.NONE,
            () -> { throw new BitwigApiException(ErrorCode.CLIP_NOT_FOUND, "launch_clip", "Not found"); }
        );

        // Second call — dedupe hit, should log outcome=error
        McpErrorHandler.executeWithErrorHandling(
            "launch_clip", args, mockLogger, RetryPolicy.NONE,
            () -> { throw new RuntimeException("should not reach"); }
        );

        verify(mockLogger).info(any(), eq("launch_clip"),
                contains("outcome=error"));
    }

    @Test
    void testDedupe_MissDoesNotLogDedupeHit() {
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "no-hit-log");

        // First call — cache miss, should NOT log "Dedupe hit"
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> Map.of("action", "started")
        );

        verify(mockLogger, never()).info(any(), any(), contains("Dedupe hit"));
    }

    // === Review follow-up: runtime-configurable TTL and max entries ===

    @Test
    void testCreateDefaultCache_UsesSystemPropertyOverrides() {
        String originalTtl = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
        String originalMax = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);

        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, "5000");
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, "50");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();

            // Verify max entries: fill to 50, then add one more — should evict
            for (int i = 0; i < 50; i++) {
                cache.put(
                    new io.github.fabb.wigai.mcp.idempotency.IdempotencyKey("tool", "req-" + i),
                    new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("r")), false)
                );
            }
            assertEquals(50, cache.size());
            cache.put(
                new io.github.fabb.wigai.mcp.idempotency.IdempotencyKey("tool", "req-overflow"),
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("r")), false)
            );
            assertTrue(cache.size() <= 50, "Max entries should be 50 per system property");
        } finally {
            // Restore original system properties
            if (originalTtl != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, originalTtl);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
            }
            if (originalMax != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, originalMax);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
            }
        }
    }

    @Test
    void testCreateDefaultCache_FallsBackToDefaults() {
        String originalTtl = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
        String originalMax = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);

        try {
            System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
            System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();

            // Should work normally with defaults — basic smoke test
            assertNotNull(cache);
            cache.put(
                new io.github.fabb.wigai.mcp.idempotency.IdempotencyKey("tool", "req-1"),
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("r")), false)
            );
            assertEquals(1, cache.size());
        } finally {
            if (originalTtl != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, originalTtl);
            }
            if (originalMax != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, originalMax);
            }
        }
    }

    // === Review follow-up round 8: payload consistency on dedupe ===

    @Test
    void testDedupe_SamePayload_ReturnsCachedResult() {
        // Same request_id + same payload = normal dedupe hit
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "payload-same");
        args1.put("parameter_index", 3);
        args1.put("value", 0.5);

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "payload-same");
        args2.put("parameter_index", 3);
        args2.put("value", 0.5);

        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args1, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "parameter_set");
            }
        );

        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args2, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Identical payload must dedupe normally");
        assertSame(first, second);
    }

    @Test
    void testDedupe_DifferentPayload_RejectsWithError() {
        // Same request_id but different payload = conflict error, not stale cache hit
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "payload-conflict");
        args1.put("parameter_index", 3);
        args1.put("value", 0.5);

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "payload-conflict");
        args2.put("parameter_index", 7);
        args2.put("value", 0.9);

        McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args1, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "parameter_set");
            }
        );

        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args2, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Mismatched payload must not re-execute");
        assertTrue(second.isError(), "Mismatched payload replay must return an error");
        String json = ((McpSchema.TextContent) second.content().get(0)).text();
        assertTrue(json.contains("\"code\":\"INVALID_PARAMETER\""),
            "Error code should be INVALID_PARAMETER for payload mismatch, got: " + json);
        assertTrue(json.contains("request_id"),
            "Error message should mention request_id, got: " + json);
    }

    @Test
    void testDedupe_RequestIdOnlyPayload_DedupesSameEmptyPayload() {
        // request_id is the only argument — both calls have identical (empty) payload fingerprint
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "empty-payload-1");

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "empty-payload-1");

        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args1, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args2, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Same empty payload should dedupe");
        assertSame(first, second);
    }

    @Test
    void testComputePayloadFingerprint_ExcludesRequestId() {
        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "test-123");
        args.put("parameter_index", 3);
        args.put("value", 0.5);

        Map<String, Object> argsDifferentReqId = new HashMap<>();
        argsDifferentReqId.put("request_id", "different-id");
        argsDifferentReqId.put("parameter_index", 3);
        argsDifferentReqId.put("value", 0.5);

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(args),
            PayloadFingerprint.computePayloadFingerprint(argsDifferentReqId),
            "Fingerprint must exclude request_id — same business args must produce same fingerprint");
    }

    @Test
    void testComputePayloadFingerprint_DifferentArgs_DifferentFingerprint() {
        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "test-123");
        args1.put("parameter_index", 3);

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "test-123");
        args2.put("parameter_index", 7);

        assertNotEquals(
            PayloadFingerprint.computePayloadFingerprint(args1),
            PayloadFingerprint.computePayloadFingerprint(args2),
            "Different business args must produce different fingerprints");
    }

    @Test
    void testComputePayloadFingerprint_NullArgs_ReturnsEmptyString() {
        assertEquals("", PayloadFingerprint.computePayloadFingerprint(null));
    }

    @Test
    void testComputePayloadFingerprint_EmptyArgs_ReturnsEmptyString() {
        assertEquals("", PayloadFingerprint.computePayloadFingerprint(new HashMap<>()));
    }

    @Test
    void testComputePayloadFingerprint_OnlyRequestId_ReturnsEmptyString() {
        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "test-123");
        assertEquals("", PayloadFingerprint.computePayloadFingerprint(args),
            "Args with only request_id should fingerprint as empty (no business payload)");
    }

    @Test
    void testComputePayloadFingerprint_ReturnsSha256HexDigest() {
        Map<String, Object> args = new HashMap<>();
        args.put("parameter_index", 3);
        args.put("value", 0.5);

        String fingerprint = PayloadFingerprint.computePayloadFingerprint(args);

        assertNotNull(fingerprint);
        assertFalse(fingerprint.isEmpty(), "Non-empty payload must produce a non-empty fingerprint");
        assertEquals(64, fingerprint.length(), "SHA-256 hex digest must be 64 characters");
        assertTrue(fingerprint.matches("[0-9a-f]{64}"), "Must be valid hex-encoded SHA-256 digest");
    }

    @Test
    void testComputePayloadFingerprint_Deterministic() {
        Map<String, Object> args1 = new HashMap<>();
        args1.put("value", 0.5);
        args1.put("parameter_index", 3);

        Map<String, Object> args2 = new HashMap<>();
        args2.put("parameter_index", 3);
        args2.put("value", 0.5);

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(args1),
            PayloadFingerprint.computePayloadFingerprint(args2),
            "Same args in different insertion order must produce identical fingerprint");
    }

    @Test
    void testComputePayloadFingerprint_CollisionCandidatePayloads_DifferentFingerprint() {
        // Prior canonicalization could collide for these two distinct payloads:
        // {'[': ':":'} and {'[:"': ':'}
        Map<String, Object> args1 = Map.of("[", ":\":");
        Map<String, Object> args2 = Map.of("[:\"", ":");

        assertNotEquals(
            PayloadFingerprint.computePayloadFingerprint(args1),
            PayloadFingerprint.computePayloadFingerprint(args2),
            "Distinct delimiter-heavy payloads must never collide under canonicalization");
    }

    @Test
    void testComputePayloadFingerprint_NormalizesEquivalentNumbers() {
        Map<String, Object> args1 = new HashMap<>();
        args1.put("parameter_index", 1);
        args1.put("payload", Map.of(
            "value", 1,
            "grid", List.of(1, 1.50, 2)
        ));

        Map<String, Object> args2 = new HashMap<>();
        args2.put("parameter_index", 1.0);
        args2.put("payload", Map.of(
            "value", 1.0,
            "grid", List.of(1.0, 1.5, 2.0)
        ));

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(args1),
            PayloadFingerprint.computePayloadFingerprint(args2),
            "Semantically equivalent numeric payloads must produce identical fingerprints");
    }

    @Test
    void testComputePayloadFingerprint_FloatAndDoubleParity() {
        // 0.1f and 0.1d must produce identical fingerprints — both represent "0.1"
        Map<String, Object> argsFloat = new HashMap<>();
        argsFloat.put("value", 0.1f);

        Map<String, Object> argsDouble = new HashMap<>();
        argsDouble.put("value", 0.1d);

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(argsFloat),
            PayloadFingerprint.computePayloadFingerprint(argsDouble),
            "Float 0.1f and Double 0.1d must produce identical fingerprints via canonical textual form");
    }

    @Test
    void testComputePayloadFingerprint_FloatAndDoubleParityNested() {
        // Nested collections with Float vs Double must also match
        Map<String, Object> argsFloat = new HashMap<>();
        argsFloat.put("grid", List.of(0.1f, 0.25f, 1.0f));

        Map<String, Object> argsDouble = new HashMap<>();
        argsDouble.put("grid", List.of(0.1d, 0.25d, 1.0d));

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(argsFloat),
            PayloadFingerprint.computePayloadFingerprint(argsDouble),
            "Float and Double lists with same values must produce identical fingerprints");
    }

    @Test
    void testDedupe_PayloadMismatch_RoutedThroughTimedOperationTelemetry() {
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-telemetry");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "telemetry-mismatch");
        args1.put("parameter_index", 3);

        McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args1, mockLogger, RetryPolicy.NONE,
            () -> Map.of("action", "set")
        );

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "telemetry-mismatch");
        args2.put("parameter_index", 7);

        McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args2, mockLogger, RetryPolicy.NONE,
            () -> Map.of("action", "should_not_reach")
        );

        // Verify timed operation failure was recorded for the mismatch path
        verify(mockTimedOp).failure(eq(ErrorCode.INVALID_PARAMETER),
                contains("different payload"));
    }

    // === Review follow-up round 2: hardened bootstrap (invalid system properties) ===

    @Test
    void testCreateDefaultCache_NonNumericTtl_FailsSafeToDefault() {
        String originalTtl = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, "not-a-number");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();
            assertNotNull(cache, "Invalid TTL property must not crash static initialization");
            cache.put(
                new io.github.fabb.wigai.mcp.idempotency.IdempotencyKey("tool", "req-1"),
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("r")), false)
            );
            assertEquals(1, cache.size());
        } finally {
            if (originalTtl != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, originalTtl);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
            }
        }
    }

    @Test
    void testCreateDefaultCache_NonNumericMaxEntries_FailsSafeToDefault() {
        String originalMax = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, "abc");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();
            assertNotNull(cache, "Invalid max entries property must not crash static initialization");
        } finally {
            if (originalMax != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, originalMax);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
            }
        }
    }

    @Test
    void testCreateDefaultCache_NegativeTtl_FailsSafeToDefault() {
        String originalTtl = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, "-500");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();
            assertNotNull(cache, "Negative TTL must fail safe to default, not throw");
        } finally {
            if (originalTtl != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS, originalTtl);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_TTL_MILLIS);
            }
        }
    }

    @Test
    void testCreateDefaultCache_ZeroMaxEntries_FailsSafeToDefault() {
        String originalMax = System.getProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
        try {
            System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, "0");

            IdempotencyCache cache = McpErrorHandler.createDefaultCache();
            assertNotNull(cache, "Zero max entries must fail safe to default, not throw");
        } finally {
            if (originalMax != null) {
                System.setProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES, originalMax);
            } else {
                System.clearProperty(McpErrorHandler.PROP_IDEMPOTENCY_MAX_ENTRIES);
            }
        }
    }
}
