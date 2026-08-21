package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.SceneLaunchResult;
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
 * Unit tests for SceneTool focusing on error handling integration.
 */
class SceneToolTest {

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
    void testLaunchSceneByIndexSpecification() {
        // Act
        McpServerFeatures.SyncToolSpecification specification = SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger);

        // Assert
        assertNotNull(specification);
        assertNotNull(specification.tool());
        assertEquals("session_launchSceneByIndex", specification.tool().name());
        assertEquals("Launch a scene in Bitwig by providing its zero-based index", specification.tool().description());
        assertNotNull(specification.tool().inputSchema());
    }

    @Test
    void testHandleLaunchScene_Success() throws Exception {
        // Arrange: Mock successful scene launch via controller
        SceneLaunchResult successResult = SceneLaunchResult.success("Scene 1 launched.");
        when(clipSceneController.launchSceneByIndex(1)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("scene_index", 1);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger);

        // Act: Invoke handler through specification
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Controller was called with parsed arguments
        verify(clipSceneController).launchSceneByIndex(1);

        // Assert: Response format is correct
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "scene_launched");
        assertEquals(1, dataNode.get("scene_index").asInt());
        assertEquals("Scene 1 launched.", dataNode.get("message").asText());
        McpResponseTestUtils.assertNotDoubleWrapped(result);
    }

    @Test
    void testParameterValidationIntegration() {
        // Test that parameter validation is integrated into the tool specification
        McpServerFeatures.SyncToolSpecification specification = SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger);

        // Verify tool schema includes validation requirements
        McpSchema.JsonSchema schema = specification.tool().inputSchema();
        assertNotNull(schema);
        String schemaString = schema.toString();
        assertTrue(schemaString.contains("scene_index"));
        assertTrue(schemaString.contains("required"));
        assertTrue(schemaString.contains("minimum"));
    }

    @Test
    void testHandleLaunchScene_ControllerReturnsError() throws Exception {
        // Arrange: Mock controller returning error result for out-of-bounds index
        SceneLaunchResult errorResult = SceneLaunchResult.error("INVALID_PARAMETER_INDEX", "Scene index 99 is out of bounds for all tracks");
        when(clipSceneController.launchSceneByIndex(99)).thenReturn(errorResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("scene_index", 99);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger);

        // Act: Invoke handler through specification
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Controller was called with parsed arguments
        verify(clipSceneController).launchSceneByIndex(99);

        // Assert: Error response format is correct
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER_INDEX", errorNode.get("code").asText());
        assertEquals("Scene index 99 is out of bounds for all tracks", errorNode.get("message").asText());
        assertEquals("session_launchSceneByIndex", errorNode.get("operation").asText());
    }

    @Test
    void testLaunchSceneByIndexSuccessResponseFormat() throws Exception {
        // Arrange: Mock successful scene launch
        SceneLaunchResult successResult = SceneLaunchResult.success("Scene 1 launched.");
        when(clipSceneController.launchSceneByIndex(1)).thenReturn(successResult);

        // Act: Simulate what the tool does
        Map<String, Object> responseData = Map.of(
            "action", "scene_launched",
            "scene_index", 1,
            "message", "Scene 1 launched."
        );
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(responseData);

        // Assert: Validate action response format
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "scene_launched");
        assertEquals(1, dataNode.get("scene_index").asInt());
        assertEquals("Scene 1 launched.", dataNode.get("message").asText());
    }

    @Test
    void testLaunchSceneErrorResponseFormat() throws Exception {
        // Test error response format for scene operations
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.SCENE_NOT_FOUND,
            "session_launchSceneByIndex",
            "Scene index 99 is out of range"
        );
        
        McpSchema.CallToolResult result = McpErrorHandler.createErrorResponse(exception, structuredLogger);
        
        // Validate error response format
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("SCENE_NOT_FOUND", errorNode.get("code").asText());
        assertEquals("Scene index 99 is out of range", errorNode.get("message").asText());
        assertEquals("session_launchSceneByIndex", errorNode.get("operation").asText());
    }

    @Test
    void testLaunchSceneResponseNotDoubleWrapped() throws Exception {
        // Test that scene launch responses are not double-wrapped
        Map<String, Object> sceneData = Map.of(
            "action", "scene_launched",
            "scene_index", 3,
            "message", "Scene launched successfully"
        );
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(sceneData);
        
        // This would have caught the double-wrapping bug
        McpResponseTestUtils.assertNotDoubleWrapped(result);
        
        // Verify it's properly structured as an action response
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "scene_launched");
        assertEquals(3, dataNode.get("scene_index").asInt());
    }

    // === Story 1.4: request_id correlation tests ===

    @Test
    void testLaunchSceneWithRequestIdIncludesItInLoggingContext() {
        // AC 2, AC 3: request_id in tool arguments must be included in logging context

        // Arrange
        SceneLaunchResult successResult = SceneLaunchResult.success("Scene 0 launched.");
        when(clipSceneController.launchSceneByIndex(0)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("scene_index", 0);
        arguments.put("request_id", "scene-correlation-123");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger);

        // Act
        spec.callHandler().apply(null, mockRequest);

        // Assert: Verify startTimedOperation was called with parameters containing request_id
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("session_launchSceneByIndex"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Parameters map should not be null when request_id is provided");
        assertEquals("scene-correlation-123", capturedParams.get("request_id"),
            "request_id should be included in logging parameters");
    }

    @Test
    void testLaunchSceneWithoutRequestIdStillWorks() {
        // AC 3: Backward compatibility - tools work without request_id

        // Arrange
        SceneLaunchResult successResult = SceneLaunchResult.success("Scene 0 launched.");
        when(clipSceneController.launchSceneByIndex(0)).thenReturn(successResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("scene_index", 0);
        // No request_id

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger);

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Operation completes successfully
        assertNotNull(result);
        assertFalse(result.isError(), "Operation should succeed without request_id");

        // Verify logging still happened
        verify(structuredLogger).startTimedOperation(any(), eq("session_launchSceneByIndex"), any());
    }

    @Test
    void testLaunchSceneFailureIncludesRequestIdAndErrorCodeInLogs() {
        // AC 4: On failure, logs include ErrorCode and request_id

        // Arrange
        SceneLaunchResult errorResult = SceneLaunchResult.error("INVALID_PARAMETER_INDEX", "Scene index 99 is out of bounds for all tracks");
        when(clipSceneController.launchSceneByIndex(99)).thenReturn(errorResult);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("scene_index", 99);
        arguments.put("request_id", "scene-error-456");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger);

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Result is an error
        assertTrue(result.isError(), "Result should be an error");

        // Assert: Verify startTimedOperation was called with request_id in parameters
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("session_launchSceneByIndex"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Parameters should include request_id even on failure path");
        assertEquals("scene-error-456", capturedParams.get("request_id"),
            "request_id should be in logging parameters for error correlation");

        // Assert: Verify failure was logged with correct ErrorCode
        verify(timedOperation).failure(eq(ErrorCode.INVALID_PARAMETER_INDEX), any());
    }
}
