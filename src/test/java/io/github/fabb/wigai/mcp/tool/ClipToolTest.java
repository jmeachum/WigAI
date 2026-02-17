package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.ClipSceneController.ClipLaunchResult;
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
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for ClipTool after migration to unified error handling architecture.
 */
class ClipToolTest {

    @Mock
    private ClipSceneController clipSceneController;
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
    void testLaunchClipSpecification() {
        // Act
        McpServerFeatures.SyncToolSpecification specification = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        // Assert
        assertNotNull(specification);
        assertNotNull(specification.tool());
        assertEquals("launch_clip", specification.tool().name());
        assertEquals("Launch a specific clip in Bitwig by providing track_index or track_name with clip_index",
                     specification.tool().description());
        assertNotNull(specification.tool().inputSchema());
    }

    @Test
    void testSpecificationContainsRequiredFields() {
        // Test that the tool specification includes proper validation
        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        assertNotNull(spec.tool().inputSchema());
        assertTrue(spec.tool().name().contains("clip"));
        assertTrue(spec.tool().description().contains("track"));
    }

    @Test
    void testHandleLaunchClip_Success() throws Exception {
        // Arrange: Mock successful clip launch via controller
        ClipLaunchResult successResult = ClipLaunchResult.success("Clip at Drums[0] launched.");
        when(clipSceneController.launchClip("Drums", 0)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        // Act: Invoke handler through specification
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Controller was called with parsed arguments
        verify(clipSceneController).launchClip("Drums", 0);

        // Assert: Response format is correct
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "clip_launched");
        assertEquals("Drums", dataNode.get("track_name").asText());
        assertEquals(0, dataNode.get("clip_index").asInt());
        assertEquals("Clip at Drums[0] launched.", dataNode.get("message").asText());
        McpResponseTestUtils.assertNotDoubleWrapped(result);
    }

    @Test
    void testHandleLaunchClip_ControllerReturnsError() throws Exception {
        // Arrange: Mock controller returning error result
        ClipLaunchResult errorResult = ClipLaunchResult.error("TRACK_NOT_FOUND", "Track 'Missing' not found");
        when(clipSceneController.launchClip("Missing", 0)).thenReturn(errorResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Missing");
        arguments.put("clip_index", 0);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        // Act: Invoke handler through specification
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Controller was called with parsed arguments
        verify(clipSceneController).launchClip("Missing", 0);

        // Assert: Error response format is correct
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("TRACK_NOT_FOUND", errorNode.get("code").asText());
        assertEquals("Track 'Missing' not found", errorNode.get("message").asText());
        assertEquals("launch_clip", errorNode.get("operation").asText());
    }

    @Test
    void testHandleLaunchClip_DuplicateNameAmbiguityReturnsCandidateGuidance() throws Exception {
        ClipLaunchResult ambiguityResult = ClipLaunchResult.ambiguity(
            "Ambiguous track_name 'Drums'. Provide track_index to confirm target.",
            List.of(
                Map.of("track_index", 1, "track_name", "Drums"),
                Map.of("track_index", 3, "track_name", "Drums")
            )
        );
        when(clipSceneController.launchClip("Drums", 0)).thenReturn(ambiguityResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        verify(clipSceneController).launchClip("Drums", 0);
        assertEquals("INVALID_PARAMETER", errorNode.get("code").asText());
        assertEquals("launch_clip", errorNode.get("operation").asText());
        assertTrue(errorNode.has("details"));
        assertEquals("track_index", errorNode.get("details").get("confirmation_parameter").asText());
        assertEquals(2, errorNode.get("details").get("candidates").size());
        assertEquals(1, errorNode.get("details").get("candidates").get(0).get("track_index").asInt());
    }

    @Test
    void testHandleLaunchClip_WithTrackIndexUsesExplicitConfirmationPath() throws Exception {
        ClipLaunchResult successResult = ClipLaunchResult.success("Clip at Drums[0] launched.", 3);
        when(clipSceneController.launchClip("Drums", 0, 3)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);
        arguments.put("track_index", 3);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "clip_launched");
        verify(clipSceneController).launchClip("Drums", 0, 3);
        verify(clipSceneController, never()).launchClip("Drums", 0);
        assertEquals(3, dataNode.get("track_index").asInt());
    }

    @Test
    void testHandleLaunchClip_WithTrackIndexMismatchReturnsInvalidParameterError() throws Exception {
        ClipLaunchResult mismatchResult = ClipLaunchResult.error(
            "INVALID_PARAMETER",
            "track_index 3 does not match track_name 'Drums'"
        );
        when(clipSceneController.launchClip("Drums", 0, 3)).thenReturn(mismatchResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);
        arguments.put("track_index", 3);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER", errorNode.get("code").asText());
        assertEquals("launch_clip", errorNode.get("operation").asText());
        verify(clipSceneController).launchClip("Drums", 0, 3);
    }

    @Test
    void testHandleLaunchClip_WithTrackIndexOnlyUsesIndexTargetingPath() throws Exception {
        ClipLaunchResult successResult = ClipLaunchResult.success("Clip at Drums[0] launched.", 3, "Drums");
        when(clipSceneController.launchClipWithSelectors(3, null, 0)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_index", 3);
        arguments.put("clip_index", 0);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "clip_launched");
        assertEquals("Drums", dataNode.get("track_name").asText());
        assertEquals(3, dataNode.get("track_index").asInt());
        assertEquals(0, dataNode.get("clip_index").asInt());
        verify(clipSceneController).launchClipWithSelectors(3, null, 0);
        verify(clipSceneController, never()).launchClip(anyString(), anyInt());
        verify(clipSceneController, never()).launchClip(anyString(), anyInt(), anyInt());
    }

    @Test
    void testHandleLaunchClip_WithTrackIndexOnlyAlwaysIncludesTrackNameField() throws Exception {
        ClipLaunchResult successResult = ClipLaunchResult.success("Clip launched.", 3, null);
        when(clipSceneController.launchClipWithSelectors(3, null, 0)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_index", 3);
        arguments.put("clip_index", 0);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "clip_launched");
        assertTrue(dataNode.has("track_name"));
        assertTrue(dataNode.get("track_name").isNull());
        verify(clipSceneController).launchClipWithSelectors(3, null, 0);
    }

    @Test
    void testHandleLaunchClip_WithoutTrackSelectorReturnsMissingRequiredParameter() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("clip_index", 0);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("MISSING_REQUIRED_PARAMETER", errorNode.get("code").asText());
        assertEquals("launch_clip", errorNode.get("operation").asText());
        verifyNoInteractions(clipSceneController);
    }

    @Test
    void testHandleLaunchClip_WithTrackIndexTypeMismatchReturnsInvalidParameterType() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);
        arguments.put("track_index", "not-a-number");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER_TYPE", errorNode.get("code").asText());
        verify(clipSceneController, never()).launchClipWithSelectors(anyInt(), any(), anyInt());
        verify(clipSceneController, never()).launchClip(anyString(), anyInt(), anyInt());
        verify(clipSceneController, never()).launchClip(anyString(), anyInt());
    }

    @Test
    void testHandleLaunchClip_WithFractionalTrackIndexReturnsInvalidParameterIndex() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);
        arguments.put("track_index", 1.5);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER_INDEX", errorNode.get("code").asText());
        verify(clipSceneController, never()).launchClip(anyString(), anyInt(), anyInt());
    }

    @Test
    void testHandleLaunchClip_WithNegativeTrackIndexReturnsInvalidParameterIndex() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);
        arguments.put("track_index", -1);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER_INDEX", errorNode.get("code").asText());
        verify(clipSceneController, never()).launchClip(anyString(), anyInt(), anyInt());
    }

    @Test
    void testHandleLaunchClip_WithOverflowTrackIndexReturnsInvalidParameterIndex() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);
        arguments.put("track_index", 4294967296.0);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER_INDEX", errorNode.get("code").asText());
        verify(clipSceneController, never()).launchClip(anyString(), anyInt(), anyInt());
    }

    @Test
    void testParameterValidationIntegration() {
        // Test that parameter validation is integrated into the tool specification
        McpServerFeatures.SyncToolSpecification specification = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        // Verify tool schema includes validation requirements
        McpSchema.JsonSchema schema = specification.tool().inputSchema();
        assertNotNull(schema);
        // Verify schema is properly configured for validation
        String schemaString = schema.toString();
        assertTrue(schemaString.contains("track_name"));
        assertTrue(schemaString.contains("clip_index"));
        assertTrue(schemaString.contains("required"));
    }

    @Test
    void testLaunchClipSuccessResponseFormat() throws Exception {
        // Arrange: Mock successful clip launch
        ClipLaunchResult successResult = ClipLaunchResult.success("Clip at Drums[0] launched.");
        when(clipSceneController.launchClip("Drums", 0)).thenReturn(successResult);

        // Act: Simulate what the tool does
        Map<String, Object> responseData = Map.of(
            "action", "clip_launched",
            "track_name", "Drums",
            "clip_index", 0,
            "message", "Clip at Drums[0] launched."
        );
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(responseData);

        // Assert: Validate action response format
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "clip_launched");
        assertEquals("Drums", dataNode.get("track_name").asText());
        assertEquals(0, dataNode.get("clip_index").asInt());
        assertEquals("Clip at Drums[0] launched.", dataNode.get("message").asText());
    }

    @Test
    void testLaunchClipErrorResponseFormat() throws Exception {
        // Test error response format for clip operations
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.TRACK_NOT_FOUND,
            "launch_clip",
            "Track 'NonExistent' not found"
        );

        McpSchema.CallToolResult result = McpErrorHandler.createErrorResponse(exception, structuredLogger);

        // Validate error response format
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("TRACK_NOT_FOUND", errorNode.get("code").asText());
        assertEquals("Track 'NonExistent' not found", errorNode.get("message").asText());
        assertEquals("launch_clip", errorNode.get("operation").asText());
    }

    @Test
    void testLaunchClipResponseNotDoubleWrapped() throws Exception {
        // Test that clip launch responses are not double-wrapped
        Map<String, Object> clipData = Map.of(
            "action", "clip_launched",
            "track_name", "Bass",
            "clip_index", 2,
            "message", "Clip launched successfully"
        );
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(clipData);

        // This would have caught the double-wrapping bug
        McpResponseTestUtils.assertNotDoubleWrapped(result);

        // Verify it's properly structured as an action response
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "clip_launched");
        assertEquals("Bass", dataNode.get("track_name").asText());
        assertEquals(2, dataNode.get("clip_index").asInt());
    }

    @Test
    void testClipOperationFailureResponseFormat() throws Exception {
        // Test response format when clip operation fails but no exception is thrown
        ClipLaunchResult failureResult = ClipLaunchResult.error("CLIP_NOT_FOUND", "Clip at index 5 does not exist");

        // Simulate the BitwigApiException that would be thrown in this case
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.CLIP_NOT_FOUND,
            "launch_clip",
            "Clip at index 5 does not exist"
        );

        McpSchema.CallToolResult result = McpErrorHandler.createErrorResponse(exception, structuredLogger);

        // Validate error response format
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("CLIP_NOT_FOUND", errorNode.get("code").asText());
        assertEquals("Clip at index 5 does not exist", errorNode.get("message").asText());
        assertEquals("launch_clip", errorNode.get("operation").asText());
    }

    // === Story 1.4: request_id correlation tests ===

    @Test
    void testLaunchClipWithRequestIdIncludesItInLoggingContext() {
        // AC 2, AC 3: request_id in tool arguments must be included in logging context

        // Arrange
        ClipLaunchResult successResult = ClipLaunchResult.success("Clip at Drums[0] launched.");
        when(clipSceneController.launchClip("Drums", 0)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);
        arguments.put("request_id", "clip-correlation-123");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        // Act
        spec.callHandler().apply(null, mockRequest);

        // Assert: Verify startTimedOperation was called with parameters containing request_id
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("launch_clip"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Parameters map should not be null when request_id is provided");
        assertEquals("clip-correlation-123", capturedParams.get("request_id"),
            "request_id should be included in logging parameters");
    }

    @Test
    void testLaunchClipWithoutRequestIdStillWorks() {
        // AC 3: Backward compatibility - tools work without request_id

        // Arrange
        ClipLaunchResult successResult = ClipLaunchResult.success("Clip at Drums[0] launched.");
        when(clipSceneController.launchClip("Drums", 0)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Drums");
        arguments.put("clip_index", 0);
        // No request_id

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Operation completes successfully
        assertNotNull(result);
        assertFalse(result.isError(), "Operation should succeed without request_id");

        // Verify logging still happened
        verify(structuredLogger).startTimedOperation(any(), eq("launch_clip"), any());
    }

    @Test
    void testLaunchClipFailureIncludesRequestIdAndErrorCodeInLogs() {
        // AC 4: On failure, logs include ErrorCode and request_id

        // Arrange
        ClipLaunchResult errorResult = ClipLaunchResult.error("TRACK_NOT_FOUND", "Track 'Missing' not found");
        when(clipSceneController.launchClip("Missing", 0)).thenReturn(errorResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("track_name", "Missing");
        arguments.put("clip_index", 0);
        arguments.put("request_id", "clip-error-456");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, structuredLogger);

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Result is an error
        assertTrue(result.isError(), "Result should be an error");

        // Assert: Verify startTimedOperation was called with request_id in parameters
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("launch_clip"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Parameters should include request_id even on failure path");
        assertEquals("clip-error-456", capturedParams.get("request_id"),
            "request_id should be in logging parameters for error correlation");

        // Assert: Verify failure was logged with correct ErrorCode
        verify(timedOperation).failure(eq(ErrorCode.TRACK_NOT_FOUND), any());
    }
}
