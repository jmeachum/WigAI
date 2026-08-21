package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.SceneLaunchResult;
import io.github.fabb.wigai.mcp.McpErrorHandlerTestHooks;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class SceneByNameToolTest {
    @Mock
    private ClipSceneController clipSceneController;
    @Mock
    private StructuredLogger structuredLogger;
    @Mock
    private Logger baseLogger;
    @Mock
    private BitwigApiFacade bitwigApiFacade;
    @Mock
    private StructuredLogger.TimedOperation timedOperation;

    @BeforeEach
    void setUp() {
        McpErrorHandlerTestHooks.resetIdempotencyCache();
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(any(), any(), any())).thenReturn(timedOperation);
    }

    private static McpSchema.CallToolRequest request(Map<String, Object> arguments) {
        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);
        return mockRequest;
    }

    @Test
    void specificationCreation_ExposesExpectedToolMetadata() {
        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("session_launchSceneByName", spec.tool().name());
        assertEquals("Launch a scene in Bitwig by providing its name (case-sensitive)", spec.tool().description());
        assertNotNull(spec.tool().inputSchema());
        assertTrue(spec.tool().inputSchema().properties().containsKey("request_id"),
            "Schema properties must include request_id for mutating tool dedupe support");
        assertFalse(spec.tool().inputSchema().required() != null
                && spec.tool().inputSchema().required().contains("request_id"),
            "request_id must remain optional (not in required list)");
    }

    @Test
    void launchSceneByName_Success_InvokesHandlerAndReturnsActionEnvelope() throws Exception {
        String sceneName = "Verse 1";
        when(clipSceneController.launchSceneByName(sceneName)).thenReturn(SceneLaunchResult.success("Scene 'Verse 1' launched."));
        when(clipSceneController.getBitwigApiFacade()).thenReturn(bitwigApiFacade);
        when(bitwigApiFacade.findSceneByName(sceneName)).thenReturn(0);

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, request(Map.of("scene_name", sceneName)));

        verify(clipSceneController).launchSceneByName(sceneName);
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "scene_launched");
        assertEquals(sceneName, dataNode.get("scene_name").asText());
        assertEquals(0, dataNode.get("launched_scene_index").asInt());
        assertEquals("Scene 'Verse 1' launched.", dataNode.get("message").asText());
        McpResponseTestUtils.assertNotDoubleWrapped(result);
    }

    @Test
    void launchSceneByName_Success_WhenIndexLookupFails_OmitsIndex() throws Exception {
        String sceneName = "Bridge";
        when(clipSceneController.launchSceneByName(sceneName)).thenReturn(SceneLaunchResult.success("Scene 'Bridge' launched."));
        when(clipSceneController.getBitwigApiFacade()).thenReturn(bitwigApiFacade);
        when(bitwigApiFacade.findSceneByName(sceneName)).thenThrow(new RuntimeException("lookup failed"));

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, request(Map.of("scene_name", sceneName)));

        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "scene_launched");
        assertEquals(sceneName, dataNode.get("scene_name").asText());
        assertFalse(dataNode.has("launched_scene_index"), "Index should be omitted on best-effort lookup failure");
        verify(structuredLogger).debug(contains("Failed to find scene index"));
    }

    @Test
    void launchSceneByName_ControllerReturnsError_UsesUnifiedErrorEnvelope() throws Exception {
        when(clipSceneController.launchSceneByName("Missing"))
            .thenReturn(SceneLaunchResult.error("SCENE_NOT_FOUND", "Scene 'Missing' not found"));

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, request(Map.of("scene_name", "Missing")));

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("SCENE_NOT_FOUND", errorNode.get("code").asText());
        assertEquals("Scene 'Missing' not found", errorNode.get("message").asText());
        assertEquals("session_launchSceneByName", errorNode.get("operation").asText());
    }

    @Test
    void launchSceneByName_MissingSceneName_ReturnsMissingRequiredParameterError() throws Exception {
        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, request(Map.of()));

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("MISSING_REQUIRED_PARAMETER", errorNode.get("code").asText());
        assertEquals("session_launchSceneByName", errorNode.get("operation").asText());
    }

    @Test
    void launchSceneByName_BlankSceneName_ReturnsEmptyParameterError() throws Exception {
        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(null, request(Map.of("scene_name", "   ")));

        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("EMPTY_PARAMETER", errorNode.get("code").asText());
        assertEquals("session_launchSceneByName", errorNode.get("operation").asText());
    }

    @Test
    void launchSceneByName_WithRequestId_IncludesItInLoggingContext() {
        when(clipSceneController.launchSceneByName("Verse 1")).thenReturn(SceneLaunchResult.success("ok"));
        when(clipSceneController.getBitwigApiFacade()).thenReturn(bitwigApiFacade);
        when(bitwigApiFacade.findSceneByName("Verse 1")).thenReturn(0);

        Map<String, Object> args = new HashMap<>();
        args.put("scene_name", "Verse 1");
        args.put("request_id", "scene-by-name-req-1");

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);
        spec.callHandler().apply(null, request(args));

        @SuppressWarnings("unchecked")
        var paramsCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("session_launchSceneByName"), paramsCaptor.capture());
        assertEquals("scene-by-name-req-1", paramsCaptor.getValue().get("request_id"));
    }

    @Test
    void launchSceneByName_RepeatedRequestId_DedupesAndAvoidsReExecution() throws Exception {
        String requestId = "scene-by-name-" + UUID.randomUUID();
        when(clipSceneController.launchSceneByName("Verse 1")).thenReturn(SceneLaunchResult.success("Scene launched once"));
        when(clipSceneController.getBitwigApiFacade()).thenReturn(bitwigApiFacade);
        when(bitwigApiFacade.findSceneByName("Verse 1")).thenReturn(2);

        Map<String, Object> args = new HashMap<>();
        args.put("scene_name", "Verse 1");
        args.put("request_id", requestId);
        McpSchema.CallToolRequest request = request(args);

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolResult first = spec.callHandler().apply(null, request);
        McpSchema.CallToolResult second = spec.callHandler().apply(null, request);

        verify(clipSceneController, times(1)).launchSceneByName("Verse 1");
        verify(bitwigApiFacade, times(1)).findSceneByName("Verse 1");
        verify(structuredLogger, times(1)).startTimedOperation(any(), eq("session_launchSceneByName"), any());

        assertSame(first, second, "Dedupe hit must return cached first CallToolResult object");
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(second, "scene_launched");
        assertEquals("Verse 1", dataNode.get("scene_name").asText());
    }

    @Test
    void launchSceneByName_WithoutRequestId_DoesNotDeduplicate() {
        when(clipSceneController.launchSceneByName("Outro")).thenReturn(SceneLaunchResult.success("ok"));
        when(clipSceneController.getBitwigApiFacade()).thenReturn(bitwigApiFacade);
        when(bitwigApiFacade.findSceneByName("Outro")).thenReturn(4);

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger);
        McpSchema.CallToolRequest request = request(Map.of("scene_name", "Outro"));

        spec.callHandler().apply(null, request);
        spec.callHandler().apply(null, request);

        verify(clipSceneController, times(2)).launchSceneByName("Outro");
    }
}
