package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.DeviceController;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for GetDeviceDetailsTool using unified error handling architecture.
 */
// TODO (TEA Review): Split this test class into smaller focused files (<300 lines). See test-review-1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md
class GetDeviceDetailsToolTest {

    @Mock
    private DeviceController deviceController;
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
    void testGetDeviceDetailsSpecification() {
        McpServerFeatures.SyncToolSpecification spec = GetDeviceDetailsTool.getDeviceDetailsSpecification(deviceController, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("get_device_details", spec.tool().name());
        assertTrue(spec.tool().description().contains("device"));
        assertTrue(spec.tool().description().contains("remote controls"));
        assertTrue(spec.tool().description().contains("pages"));
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testParameterValidation_ValidSelectedDeviceMode() throws Exception {
        mockDeviceDetailsSuccess();
        Map<String, Object> args = Map.of("get_for_selected_device", true);

        McpSchema.CallToolResult result = invokeToolHandler(args);

        assertFalse(result.isError());
    }

    @Test
    void testParameterValidation_ValidIdentifierMode() throws Exception {
        mockDeviceDetailsSuccess();
        Map<String, Object> args = Map.of(
            "track_index", 0,
            "device_index", 1
        );

        McpSchema.CallToolResult result = invokeToolHandler(args);

        assertFalse(result.isError());
    }

    @Test
    void testParameterValidation_ValidIdentifierModeWithNames() throws Exception {
        mockDeviceDetailsSuccess();
        Map<String, Object> args = Map.of(
            "track_name", "Bass Track",
            "device_name", "EQ Eight"
        );

        McpSchema.CallToolResult result = invokeToolHandler(args);

        assertFalse(result.isError());
    }

    @Test
    void testParameterValidation_DefaultSelectedMode() throws Exception {
        mockDeviceDetailsSuccess();
        Map<String, Object> args = Map.of();

        McpSchema.CallToolResult result = invokeToolHandler(args);

        assertFalse(result.isError());
    }

    @Test
    void testParameterValidation_BothTrackIdentifiers() throws Exception {
        mockDeviceDetailsSuccess();
        HashMap<String, Object> args = new HashMap<>();
        args.put("track_index", 0);
        args.put("track_name", "Bass Track");
        args.put("device_index", 1);

        McpSchema.CallToolResult result = invokeToolHandler(args);

        assertFalse(result.isError());
    }

    @Test
    void testParameterValidation_BothDeviceIdentifiers() throws Exception {
        HashMap<String, Object> args = new HashMap<>();
        args.put("track_index", 0);
        args.put("device_index", 1);
        args.put("device_name", "EQ Eight");

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER", error.get("code").asText());
        assertTrue(error.get("message").asText().contains("Exactly one of device_index or device_name"));
    }

    @Test
    void testParameterValidation_SelectedModeWithIdentifiers() throws Exception {
        Map<String, Object> args = Map.of(
            "get_for_selected_device", true,
            "track_index", 0
        );

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER", error.get("code").asText());
        assertTrue(error.get("message").asText().contains("Cannot provide get_for_selected_device=true together with other identifier"));
    }

    @Test
    void testParameterValidation_FalseModeWithoutIdentifiers() throws Exception {
        Map<String, Object> args = Map.of(
            "get_for_selected_device", false
        );

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER", error.get("code").asText());
        assertTrue(error.get("message").asText().contains("Must provide identifier parameters when get_for_selected_device=false"));
    }

    @Test
    void testParameterValidation_NegativeTrackIndex() throws Exception {
        HashMap<String, Object> args = new HashMap<>();
        args.put("track_index", -1);
        args.put("device_index", 0);

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER_INDEX", error.get("code").asText());
        assertTrue(error.get("message").asText().contains("track_index must be non-negative"));
    }

    @Test
    void testParameterValidation_NegativeDeviceIndex() throws Exception {
        HashMap<String, Object> args = new HashMap<>();
        args.put("track_index", 0);
        args.put("device_index", -1);

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER_INDEX", error.get("code").asText());
        assertTrue(error.get("message").asText().contains("device_index must be non-negative"));
    }

    @Test
    void testParameterValidation_EmptyTrackName() throws Exception {
        HashMap<String, Object> args = new HashMap<>();
        args.put("track_name", "");
        args.put("device_index", 0);

        McpSchema.CallToolResult result = invokeToolHandler(args);

        assertTrue(result.isError());
    }

    @Test
    void testParameterValidation_EmptyDeviceName() throws Exception {
        HashMap<String, Object> args = new HashMap<>();
        args.put("track_index", 0);
        args.put("device_name", "");

        McpSchema.CallToolResult result = invokeToolHandler(args);

        assertTrue(result.isError());
    }

    @Test
    void testParameterValidation_IncompleteIdentifiers_MissingDevice() throws Exception {
        Map<String, Object> args = Map.of(
            "track_index", 0
        );

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER", error.get("code").asText());
        assertTrue(error.get("message").asText().contains("Exactly one of device_index or device_name"));
    }

    @Test
    void testParameterValidation_IncompleteIdentifiers_MissingTrack() throws Exception {
        mockDeviceDetailsSuccess();
        Map<String, Object> args = Map.of(
            "device_index", 0
        );

        McpSchema.CallToolResult result = invokeToolHandler(args);

        assertFalse(result.isError());
    }

    @Test
    void testDeviceDetailsResponseFormat() throws Exception {
        // Create mock device details result
        List<ParameterInfo> remoteControls = new ArrayList<>();
        remoteControls.add(new ParameterInfo(0, "Threshold", 0.5, "-6.0 dB"));
        remoteControls.add(new ParameterInfo(1, "Ratio", 0.3, "3:1"));
        // Only include existing controls - no need to fill slots since ParameterInfo only represents existing parameters

        DeviceController.DeviceDetailsResult mockResult = new DeviceController.DeviceDetailsResult(
            0, "Drums", 1, "Compressor", "AudioFX", false, true,
            remoteControls
        );

        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any())).thenReturn(mockResult);

        // Test response format
        Map<String, Object> responseData = mockResult.toMap();

        // Validate response structure
        assertNotNull(responseData);
        assertEquals(0, responseData.get("track_index"));
        assertEquals("Drums", responseData.get("track_name"));
        assertEquals(1, responseData.get("index"));
        assertEquals("Compressor", responseData.get("name"));
        assertEquals("AudioFX", responseData.get("type"));
        assertEquals(false, responseData.get("is_bypassed"));
        assertEquals(true, responseData.get("is_selected"));

        // Validate remote controls array - only existing parameters
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> controlsArray = (List<Map<String, Object>>) responseData.get("remote_controls");
        assertNotNull(controlsArray);
        assertEquals(2, controlsArray.size()); // Only existing parameters

        // Check first control (exists)
        Map<String, Object> firstControl = controlsArray.get(0);
        assertEquals(0, firstControl.get("index"));
        assertEquals(true, firstControl.get("exists"));
        assertEquals("Threshold", firstControl.get("name"));
        assertEquals(0.5, firstControl.get("value"));
        assertEquals(null, firstControl.get("raw_value"));
        assertEquals("-6.0 dB", firstControl.get("display_value"));

        // Check second control (exists)
        Map<String, Object> secondControl = controlsArray.get(1);
        assertEquals(1, secondControl.get("index"));
        assertEquals(true, secondControl.get("exists"));
        assertEquals("Ratio", secondControl.get("name"));
        assertEquals(0.3, secondControl.get("value"));
        assertEquals(null, secondControl.get("raw_value"));
        assertEquals("3:1", secondControl.get("display_value"));
    }

    @Test
    void testDeviceNotFoundError() throws Exception {
        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any()))
            .thenThrow(new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, "get_device_details", "Device not found"));

        HashMap<String, Object> args = new HashMap<>();
        args.put("track_index", 0);
        args.put("device_index", 5);

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("DEVICE_NOT_FOUND", error.get("code").asText());
    }

    @Test
    void testTrackNotFoundError() throws Exception {
        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any()))
            .thenThrow(new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "get_device_details", "Track not found"));

        HashMap<String, Object> args = new HashMap<>();
        args.put("track_index", 99);
        args.put("device_index", 0);

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("TRACK_NOT_FOUND", error.get("code").asText());
    }

    @Test
    void testDeviceNotSelectedError() throws Exception {
        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any()))
            .thenThrow(new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "get_device_details", "No device selected"));

        Map<String, Object> args = Map.of("get_for_selected_device", true);

        McpSchema.CallToolResult result = invokeToolHandler(args);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("DEVICE_NOT_SELECTED", error.get("code").asText());
    }

    /**
     * Invokes the real tool handler through the specification, exercising the actual
     * parseArguments path instead of a mirrored reimplementation.
     */
    private McpSchema.CallToolResult invokeToolHandler(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec =
            GetDeviceDetailsTool.getDeviceDetailsSpecification(deviceController, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("get_device_details")
            .arguments(arguments)
            .build();
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), request);
    }

    /**
     * Sets up the deviceController mock to return a valid result for any input,
     * used by tests that verify validation passes (non-error paths).
     */
    private void mockDeviceDetailsSuccess() throws Exception {
        List<ParameterInfo> controls = List.of(new ParameterInfo(0, "Param", 0.5, "50%"));
        DeviceController.DeviceDetailsResult result = new DeviceController.DeviceDetailsResult(
            0, "Track", 0, "Device", "AudioFX", false, true, controls
        );
        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any())).thenReturn(result);
    }
}
