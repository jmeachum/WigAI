package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.WigAIExtensionDefinition;
import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.data.ParameterSettingResult;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.DeviceController;
import io.github.fabb.wigai.features.TransportController;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@Tag("atdd")
class BaselineToolEnvelopeAtddTest {

    @Test
    void statusSuccessEnvelopeIncludesRequiredFields() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        WigAIExtensionDefinition extensionDefinition = mock(WigAIExtensionDefinition.class);
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(extensionDefinition.getVersion()).thenReturn("1.2.3");
        when(bitwigApiFacade.getProjectName()).thenReturn("Test Project");
        when(bitwigApiFacade.isAudioEngineActive()).thenReturn(true);
        when(bitwigApiFacade.getTransportStatus()).thenReturn(Map.of("playing", false));
        when(bitwigApiFacade.getProjectParameters()).thenReturn(List.of(new ParameterInfo(0, "Tempo", 0.5, "50%")));
        when(bitwigApiFacade.getSelectedTrackInfo()).thenReturn(Map.of("name", "Track 1"));
        when(bitwigApiFacade.getSelectedDeviceInfo()).thenReturn(Map.of("name", "Device 1"));
        when(bitwigApiFacade.getSelectedClipSlotInfo()).thenReturn(Map.of("slot_index", 0));

        McpServerFeatures.SyncToolSpecification spec = StatusTool.specification(extensionDefinition, bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("status", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertTrue(data.has("wigai_version"));
        assertTrue(data.has("project_name"));
        assertTrue(data.has("audio_engine_active"));
        assertTrue(data.has("transport"));
        assertTrue(data.has("project_parameters"));
        assertTrue(data.has("selected_track"));
        assertTrue(data.has("selected_device"));
        assertTrue(data.has("selected_clip_slot"));
        assertFalse(data.has("partial_failures"));
    }

    @Test
    void statusPartialFailureAddsSummaryFields() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        WigAIExtensionDefinition extensionDefinition = mock(WigAIExtensionDefinition.class);
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(extensionDefinition.getVersion()).thenReturn("1.2.3");
        when(bitwigApiFacade.getProjectName()).thenReturn("Test Project");
        when(bitwigApiFacade.isAudioEngineActive()).thenReturn(true);
        when(bitwigApiFacade.getTransportStatus()).thenReturn(Map.of("playing", false));
        when(bitwigApiFacade.getProjectParameters()).thenReturn(List.of(new ParameterInfo(0, "Tempo", 0.5, "50%")));
        when(bitwigApiFacade.getSelectedTrackInfo()).thenReturn(Map.of("name", "Track 1"));
        when(bitwigApiFacade.getSelectedDeviceInfo()).thenThrow(new RuntimeException("Device unavailable"));
        when(bitwigApiFacade.getSelectedClipSlotInfo()).thenReturn(Map.of("slot_index", 0));

        McpServerFeatures.SyncToolSpecification spec = StatusTool.specification(extensionDefinition, bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("status", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertTrue(data.has("partial_failures"));
        assertTrue(data.get("partial_failures").isArray());
        assertEquals(1, data.get("partial_failures").size());
        assertTrue(data.has("status_note"));
    }

    @Test
    void statusErrorEnvelopeUsesToolName() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        WigAIExtensionDefinition extensionDefinition = mock(WigAIExtensionDefinition.class);
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(extensionDefinition.getVersion()).thenThrow(new RuntimeException("boom"));

        McpServerFeatures.SyncToolSpecification spec = StatusTool.specification(extensionDefinition, bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("status", Map.of())
        );

        assertError(result, "OPERATION_FAILED", "status");
    }

    @Test
    void transportStartSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        TransportController transportController = mock(TransportController.class);

        when(transportController.startTransport()).thenReturn("Transport started.");

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStartSpecification(transportController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("transport_start", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertEquals("transport_started", data.get("action").asText());
        assertEquals("Transport started.", data.get("message").asText());
    }

    @Test
    void transportStartErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        TransportController transportController = mock(TransportController.class);

        when(transportController.startTransport()).thenThrow(
            new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "transport_start", "Transport unavailable")
        );

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStartSpecification(transportController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("transport_start", Map.of())
        );

        assertError(result, "TRANSPORT_ERROR", "transport_start");
    }

    @Test
    void transportStopSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        TransportController transportController = mock(TransportController.class);

        when(transportController.stopTransport()).thenReturn("Transport stopped.");

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStopSpecification(transportController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("transport_stop", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertEquals("transport_stopped", data.get("action").asText());
        assertEquals("Transport stopped.", data.get("message").asText());
    }

    @Test
    void transportStopErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        TransportController transportController = mock(TransportController.class);

        when(transportController.stopTransport()).thenThrow(
            new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "transport_stop", "Transport unavailable")
        );

        McpServerFeatures.SyncToolSpecification spec = TransportTool.transportStopSpecification(transportController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("transport_stop", Map.of())
        );

        assertError(result, "TRANSPORT_ERROR", "transport_stop");
    }

    @Test
    void launchClipSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        when(clipSceneController.launchClip("Track 1", 0))
            .thenReturn(ClipSceneController.ClipLaunchResult.success("Clip launched."));

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("launch_clip", Map.of("track_name", "Track 1", "clip_index", 0))
        );

        JsonNode data = assertSuccess(result);
        assertEquals("clip_launched", data.get("action").asText());
    }

    @Test
    void launchClipErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        when(clipSceneController.launchClip("Track 1", 0))
            .thenReturn(ClipSceneController.ClipLaunchResult.error("TRACK_NOT_FOUND", "Track missing"));

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("launch_clip", Map.of("track_name", "Track 1", "clip_index", 0))
        );

        assertError(result, "TRACK_NOT_FOUND", "launch_clip");
    }

    @Test
    void launchSceneByIndexSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        when(clipSceneController.launchSceneByIndex(1))
            .thenReturn(ClipSceneController.SceneLaunchResult.success("Scene launched."));

        McpServerFeatures.SyncToolSpecification spec = SceneTool.launchSceneByIndexSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("session_launchSceneByIndex", Map.of("scene_index", 1))
        );

        JsonNode data = assertSuccess(result);
        assertEquals("scene_launched", data.get("action").asText());
    }

    @Test
    void launchSceneByIndexErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        when(clipSceneController.launchSceneByIndex(1))
            .thenReturn(ClipSceneController.SceneLaunchResult.error("SCENE_NOT_FOUND", "Scene missing"));

        McpServerFeatures.SyncToolSpecification spec = SceneTool.launchSceneByIndexSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("session_launchSceneByIndex", Map.of("scene_index", 1))
        );

        assertError(result, "SCENE_NOT_FOUND", "session_launchSceneByIndex");
    }

    @Test
    void launchSceneByNameSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(clipSceneController.launchSceneByName("Intro"))
            .thenReturn(ClipSceneController.SceneLaunchResult.success("Scene launched."));
        when(clipSceneController.getBitwigApiFacade()).thenReturn(bitwigApiFacade);
        when(bitwigApiFacade.findSceneByName("Intro")).thenReturn(3);

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("session_launchSceneByName", Map.of("scene_name", "Intro"))
        );

        JsonNode data = assertSuccess(result);
        assertEquals("scene_launched", data.get("action").asText());
    }

    @Test
    void launchSceneByNameErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        when(clipSceneController.launchSceneByName("Intro"))
            .thenReturn(ClipSceneController.SceneLaunchResult.error("SCENE_NOT_FOUND", "Missing"));

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("session_launchSceneByName", Map.of("scene_name", "Intro"))
        );

        assertError(result, "SCENE_NOT_FOUND", "session_launchSceneByName");
    }

    @Test
    void listTracksSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(bitwigApiFacade.getAllTracksInfo(null)).thenReturn(sampleTrackList());

        McpServerFeatures.SyncToolSpecification spec = ListTracksTool.specification(bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("list_tracks", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertEquals(2, data.size());
    }

    @Test
    void listTracksErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        McpServerFeatures.SyncToolSpecification spec = ListTracksTool.specification(bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("list_tracks", Map.of("type", "nope"))
        );

        assertError(result, "INVALID_PARAMETER", "list_tracks");
        org.mockito.Mockito.verify(bitwigApiFacade, never()).getAllTracksInfo(any());
    }

    @Test
    void getTrackDetailsSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(bitwigApiFacade.getSelectedTrackDetails()).thenReturn(Map.of("index", 0, "name", "Track 1"));

        McpServerFeatures.SyncToolSpecification spec = GetTrackDetailsTool.specification(bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_track_details", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertEquals("Track 1", data.get("name").asText());
    }

    @Test
    void getTrackDetailsErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(bitwigApiFacade.getSelectedTrackDetails()).thenReturn(null);

        McpServerFeatures.SyncToolSpecification spec = GetTrackDetailsTool.specification(bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_track_details", Map.of())
        );

        assertError(result, "TRACK_NOT_FOUND", "get_track_details");
    }

    @Test
    void listDevicesOnTrackSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(bitwigApiFacade.getDevicesOnTrack(null, null, true)).thenReturn(sampleDeviceList());

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("list_devices_on_track", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertEquals(1, data.size());
    }

    @Test
    void listDevicesOnTrackErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("list_devices_on_track", Map.of("track_index", 1, "get_selected", true))
        );

        assertError(result, "INVALID_PARAMETER", "list_devices_on_track");
    }

    @Test
    void listScenesSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(bitwigApiFacade.getAllScenesInfo()).thenReturn(sampleSceneList());

        McpServerFeatures.SyncToolSpecification spec = ListScenesTool.specification(bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("list_scenes", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertEquals(2, data.size());
    }

    @Test
    void listScenesErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        BitwigApiFacade bitwigApiFacade = mock(BitwigApiFacade.class);

        when(bitwigApiFacade.getAllScenesInfo()).thenThrow(
            new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "list_scenes", "Scene fetch failed")
        );

        McpServerFeatures.SyncToolSpecification spec = ListScenesTool.specification(bitwigApiFacade, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("list_scenes", Map.of())
        );

        assertError(result, "BITWIG_API_ERROR", "list_scenes");
    }

    @Test
    void getClipsInSceneSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        when(clipSceneController.getClipsInScene(1, null)).thenReturn(sampleClipList());

        McpServerFeatures.SyncToolSpecification spec = GetClipsInSceneTool.getClipsInSceneSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_clips_in_scene", Map.of("scene_index", 1))
        );

        JsonNode data = assertSuccess(result);
        assertEquals(2, data.size());
    }

    @Test
    void getClipsInSceneErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        McpServerFeatures.SyncToolSpecification spec = GetClipsInSceneTool.getClipsInSceneSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_clips_in_scene", Map.of())
        );

        assertError(result, "MISSING_REQUIRED_PARAMETER", "get_clips_in_scene");
    }

    @Test
    void getSelectedDeviceParametersSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        DeviceController.DeviceParametersResult params = new DeviceController.DeviceParametersResult(
            "Device 1",
            List.of(new ParameterInfo(0, "Gain", 0.5, "50%"))
        );
        when(deviceController.getSelectedDeviceParameters()).thenReturn(params);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.getSelectedDeviceParametersSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_selected_device_parameters", Map.of())
        );

        JsonNode data = assertSuccess(result);
        assertEquals("Device 1", data.get("device_name").asText());
    }

    @Test
    void getSelectedDeviceParametersErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        when(deviceController.getSelectedDeviceParameters()).thenThrow(
            new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "get_selected_device_parameters", "No device selected")
        );

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.getSelectedDeviceParametersSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_selected_device_parameters", Map.of())
        );

        assertError(result, "DEVICE_NOT_SELECTED", "get_selected_device_parameters");
    }

    @Test
    void errorOperationAlwaysReflectsMcpToolName() throws Exception {
        // AC2 verification: error.operation must equal the invoked MCP tool name,
        // even when controller throws exception with internal operation name
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        // Controller throws exception with INTERNAL operation name (not MCP tool name)
        when(deviceController.getSelectedDeviceParameters()).thenThrow(
            new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "getSelectedDeviceParameters", "Internal error")
        );

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.getSelectedDeviceParametersSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_selected_device_parameters", Map.of())
        );

        // Verify operation is MCP tool name, not internal name
        assertError(result, "BITWIG_API_ERROR", "get_selected_device_parameters");
    }

    @Test
    void launchClipMissingRequiredParameterError() throws Exception {
        // AC2 coverage: validation error with MISSING_REQUIRED_PARAMETER code
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("launch_clip", Map.of("track_name", "Test"))  // missing clip_index
        );

        assertError(result, "MISSING_REQUIRED_PARAMETER", "launch_clip");
    }

    @Test
    void launchClipEmptyParameterError() throws Exception {
        // AC2 coverage: validation error with EMPTY_PARAMETER code
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("launch_clip", Map.of("track_name", "  ", "clip_index", 0))  // whitespace track_name
        );

        assertError(result, "EMPTY_PARAMETER", "launch_clip");
    }

    @Test
    void launchClipInvalidRangeError() throws Exception {
        // AC2 coverage: validation error with INVALID_RANGE code for clip_index
        StructuredLogger logger = mockStructuredLogger();
        ClipSceneController clipSceneController = mock(ClipSceneController.class);

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(clipSceneController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("launch_clip", Map.of("track_name", "Track 1", "clip_index", -1))  // negative clip_index
        );

        assertError(result, "INVALID_RANGE", "launch_clip");
    }

    @Test
    void setSelectedDeviceParameterInvalidParameterIndexError() throws Exception {
        // AC2 coverage: validation error with INVALID_PARAMETER_INDEX code
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("set_selected_device_parameter", Map.of("parameter_index", 8, "value", 0.5))  // index 8 is out of range (0-7)
        );

        assertError(result, "INVALID_PARAMETER_INDEX", "set_selected_device_parameter");
    }

    @Test
    void getDeviceDetailsDeviceNotFoundError() throws Exception {
        // AC2 coverage: DEVICE_NOT_FOUND error for non-existent device
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        when(deviceController.getDeviceDetails(0, null, 0, null, null)).thenThrow(
            new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, "getDeviceDetails", "Device not found at index 0")
        );

        McpServerFeatures.SyncToolSpecification spec = GetDeviceDetailsTool.getDeviceDetailsSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_device_details", Map.of("track_index", 0, "device_index", 0))
        );

        assertError(result, "DEVICE_NOT_FOUND", "get_device_details");
    }

    @Test
    void setSelectedDeviceParameterSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("set_selected_device_parameter", Map.of("parameter_index", 1, "value", 0.7))
        );

        JsonNode data = assertSuccess(result);
        assertEquals("parameter_set", data.get("action").asText());
    }

    @Test
    void setSelectedDeviceParameterErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("set_selected_device_parameter", Map.of("parameter_index", 1, "value", 2.0))
        );

        assertError(result, "INVALID_RANGE", "set_selected_device_parameter");
    }

    @Test
    void setSelectedDeviceParametersSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        List<ParameterSettingResult> results = List.of(
            new ParameterSettingResult(0, "success", 0.5, null, "ok")
        );
        when(deviceController.setMultipleSelectedDeviceParameters(any())).thenReturn(results);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("set_selected_device_parameters", Map.of(
                "parameters", List.of(Map.of("parameter_index", 0, "value", 0.5))
            ))
        );

        JsonNode data = assertSuccess(result);
        assertEquals("multiple_parameters_set", data.get("action").asText());
    }

    @Test
    void setSelectedDeviceParametersEmptyArrayError() throws Exception {
        // AC2 coverage: empty parameters array returns EMPTY_PARAMETER (not MISSING_REQUIRED_PARAMETER)
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("set_selected_device_parameters", Map.of("parameters", List.of()))
        );

        assertError(result, "EMPTY_PARAMETER", "set_selected_device_parameters");
    }

    @Test
    void getDeviceDetailsSuccessEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        DeviceController.DeviceDetailsResult details = new DeviceController.DeviceDetailsResult(
            0,
            "Track 1",
            0,
            "Device 1",
            "AudioFX",
            false,
            true,
            List.of(new ParameterInfo(0, "Gain", 0.5, "50%"))
        );
        when(deviceController.getDeviceDetails(null, null, null, null, true)).thenReturn(details);

        McpServerFeatures.SyncToolSpecification spec = GetDeviceDetailsTool.getDeviceDetailsSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_device_details", Map.of("get_for_selected_device", true))
        );

        JsonNode data = assertSuccess(result);
        assertEquals("Device 1", data.get("name").asText());
    }

    @Test
    void getDeviceDetailsErrorEnvelope() throws Exception {
        StructuredLogger logger = mockStructuredLogger();
        DeviceController deviceController = mock(DeviceController.class);

        McpServerFeatures.SyncToolSpecification spec = GetDeviceDetailsTool.getDeviceDetailsSpecification(deviceController, logger);
        McpSchema.CallToolResult result = spec.callHandler().apply(
            mock(McpSyncServerExchange.class),
            buildRequest("get_device_details", Map.of("get_for_selected_device", true, "track_index", 0))
        );

        assertError(result, "INVALID_PARAMETER", "get_device_details");
    }

    private StructuredLogger mockStructuredLogger() {
        StructuredLogger logger = mock(StructuredLogger.class);
        Logger baseLogger = mock(Logger.class);
        StructuredLogger.TimedOperation timedOperation = mock(StructuredLogger.TimedOperation.class);
        when(logger.getBaseLogger()).thenReturn(baseLogger);
        when(logger.generateOperationId()).thenReturn("op-123");
        when(logger.startTimedOperation(any(), any(), any())).thenReturn(timedOperation);
        return logger;
    }

    private McpSchema.CallToolRequest buildRequest(String name, Map<String, Object> arguments) {
        return McpSchema.CallToolRequest.builder()
            .name(name)
            .arguments(arguments)
            .build();
    }

    private JsonNode assertSuccess(McpSchema.CallToolResult result) throws Exception {
        JsonNode data = McpResponseTestUtils.validateSuccessResponse(result);
        McpResponseTestUtils.assertNotDoubleWrapped(result);
        return data;
    }

    private void assertError(McpSchema.CallToolResult result, String expectedCode, String expectedOperation) throws Exception {
        McpResponseTestUtils.assertNotDoubleWrapped(result);
        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals(expectedCode, error.get("code").asText());
        assertEquals(expectedOperation, error.get("operation").asText());
    }

    private List<Map<String, Object>> sampleTrackList() {
        List<Map<String, Object>> tracks = new ArrayList<>();
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("index", 0);
        track.put("name", "Track 1");
        track.put("type", "audio");
        tracks.add(track);
        tracks.add(Map.of("index", 1, "name", "Track 2", "type", "instrument"));
        return tracks;
    }

    private List<Map<String, Object>> sampleDeviceList() {
        return List.of(Map.of(
            "index", 0,
            "name", "Device 1",
            "type", "AudioFX",
            "is_bypassed", false,
            "is_selected", true
        ));
    }

    private List<Map<String, Object>> sampleSceneList() {
        return List.of(
            Map.of("index", 0, "name", "Intro", "color", "rgb(255,0,0)"),
            Map.of("index", 1, "name", "Verse", "color", "rgb(0,255,0)")
        );
    }

    private List<Map<String, Object>> sampleClipList() {
        return List.of(
            Map.of("track_index", 0, "track_name", "Track 1", "has_content", true),
            Map.of("track_index", 1, "track_name", "Track 2", "has_content", false)
        );
    }
}
