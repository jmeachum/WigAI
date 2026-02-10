package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.data.ParameterSettingResult;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.DeviceController;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for DeviceParamTool after migration to unified error handling architecture.
 */
class DeviceParamToolTest {

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
    void testGetSelectedDeviceParametersSpecification() {
        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.getSelectedDeviceParametersSpecification(deviceController, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("get_selected_device_parameters", spec.tool().name());
        assertTrue(spec.tool().description().contains("device"));
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testSetSelectedDeviceParameterSpecification() {
        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("set_selected_device_parameter", spec.tool().name());
        assertTrue(spec.tool().description().contains("parameter"));
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testSetMultipleDeviceParametersSpecification() {
        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("set_selected_device_parameters", spec.tool().name());
        assertTrue(spec.tool().description().contains("multiple"));
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testGetDeviceParametersHandler_Success() throws Exception {
        // Arrange: Mock device controller response
        DeviceController.DeviceParametersResult mockResult = new DeviceController.DeviceParametersResult(
            "Test Device",
            List.of(
                new ParameterInfo(0, "Param 1", 0.5, "50%"),
                new ParameterInfo(1, "Param 2", 0.8, "80%")
            )
        );
        when(deviceController.getSelectedDeviceParameters()).thenReturn(mockResult);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(new HashMap<>());

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.getSelectedDeviceParametersSpecification(deviceController, structuredLogger);

        // Act: Invoke handler through specification
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Controller was called
        verify(deviceController).getSelectedDeviceParameters();

        // Assert: Response format is correct
        JsonNode dataNode = McpResponseTestUtils.validateObjectResponse(result);
        assertEquals("Test Device", dataNode.get("device_name").asText());

        JsonNode parametersNode = dataNode.get("parameters");
        assertTrue(parametersNode.isArray());
        assertEquals(2, parametersNode.size());

        JsonNode firstParam = parametersNode.get(0);
        assertEquals(0, firstParam.get("index").asInt());
        assertEquals("Param 1", firstParam.get("name").asText());
        assertEquals(0.5, firstParam.get("value").asDouble(), 0.001);
        assertEquals("50%", firstParam.get("display_value").asText());
        McpResponseTestUtils.assertNotDoubleWrapped(result);
    }

    @Test
    void testGetDeviceParametersHandler_DeviceNotSelected() throws Exception {
        // Arrange: Mock controller throwing DEVICE_NOT_SELECTED
        when(deviceController.getSelectedDeviceParameters()).thenThrow(
            new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "get_selected_device_parameters", "No device is currently selected")
        );

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(new HashMap<>());

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.getSelectedDeviceParametersSpecification(deviceController, structuredLogger);

        // Act: Invoke handler through specification
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Error response format is correct
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("DEVICE_NOT_SELECTED", errorNode.get("code").asText());
        assertEquals("No device is currently selected", errorNode.get("message").asText());
        assertEquals("get_selected_device_parameters", errorNode.get("operation").asText());
    }

    @Test
    void testSetDeviceParameterHandler_Success() throws Exception {
        // Arrange: Mock successful parameter setting
        doNothing().when(deviceController).setSelectedDeviceParameter(3, 0.75);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 3);
        arguments.put("value", 0.75);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, structuredLogger);

        // Act: Invoke handler through specification
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Controller was called with parsed arguments
        verify(deviceController).setSelectedDeviceParameter(3, 0.75);

        // Assert: Response format is correct
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "parameter_set");
        assertEquals(3, dataNode.get("parameter_index").asInt());
        assertEquals(0.75, dataNode.get("new_value").asDouble(), 0.001);
        McpResponseTestUtils.assertNotDoubleWrapped(result);
    }

    @Test
    void testSetMultipleParametersHandler_Success() throws Exception {
        // Arrange: Mock batch parameter setting results
        List<ParameterSettingResult> mockResults = List.of(
            new ParameterSettingResult(0, "success", 0.5, null, "Parameter set successfully"),
            new ParameterSettingResult(1, "error", null, "INVALID_PARAMETER_INDEX", "Parameter index out of range")
        );
        when(deviceController.setMultipleSelectedDeviceParameters(any())).thenReturn(mockResults);

        List<Map<String, Object>> parametersList = new ArrayList<>();
        Map<String, Object> param0 = new HashMap<>();
        param0.put("parameter_index", 0);
        param0.put("value", 0.5);
        parametersList.add(param0);
        Map<String, Object> param1 = new HashMap<>();
        param1.put("parameter_index", 1);
        param1.put("value", 0.9);
        parametersList.add(param1);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", parametersList);

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, structuredLogger);

        // Act: Invoke handler through specification
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Controller was called
        verify(deviceController).setMultipleSelectedDeviceParameters(any());

        // Assert: Response format is correct
        JsonNode dataNode = McpResponseTestUtils.validateActionResponse(result, "multiple_parameters_set");
        assertTrue(dataNode.has("results"));

        JsonNode resultsNode = dataNode.get("results");
        assertTrue(resultsNode.isArray());
        assertEquals(2, resultsNode.size());

        assertEquals("success", resultsNode.get(0).get("status").asText());
        assertEquals("error", resultsNode.get(1).get("status").asText());
        assertEquals("INVALID_PARAMETER_INDEX", resultsNode.get(1).get("error_code").asText());
        McpResponseTestUtils.assertNotDoubleWrapped(result);
    }

    @Test
    void testDeviceParameterErrorResponseFormat() throws Exception {
        // Test error response format for device parameter operations
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.DEVICE_NOT_SELECTED,
            "get_selected_device_parameters",
            "No device is currently selected"
        );

        McpSchema.CallToolResult result = McpErrorHandler.createErrorResponse(exception, structuredLogger);

        // Validate error response format
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("DEVICE_NOT_SELECTED", errorNode.get("code").asText());
        assertEquals("No device is currently selected", errorNode.get("message").asText());
        assertEquals("get_selected_device_parameters", errorNode.get("operation").asText());
    }

    @Test
    void testDeviceParameterResponseNotDoubleWrapped() throws Exception {
        // Test that device parameter responses are not double-wrapped
        Map<String, Object> deviceData = Map.of(
            "device_name", "Test Device",
            "parameters", List.of()
        );
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(deviceData);

        // This would have caught the double-wrapping bug
        McpResponseTestUtils.assertNotDoubleWrapped(result);

        // Verify it's properly structured as an object response
        JsonNode dataNode = McpResponseTestUtils.validateObjectResponse(result);
        assertEquals("Test Device", dataNode.get("device_name").asText());
        assertTrue(dataNode.get("parameters").isArray());
    }

    // === Story 1.4: request_id correlation tests ===

    @Test
    void testSetDeviceParameterWithRequestIdIncludesItInLoggingContext() {
        // AC 2, AC 3: request_id in tool arguments must be included in logging context

        // Arrange
        doNothing().when(deviceController).setSelectedDeviceParameter(0, 0.5);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 0);
        arguments.put("value", 0.5);
        arguments.put("request_id", "device-param-correlation-123");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, structuredLogger);

        // Act
        spec.callHandler().apply(null, mockRequest);

        // Assert: Verify startTimedOperation was called with parameters containing request_id
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("set_selected_device_parameter"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Parameters map should not be null when request_id is provided");
        assertEquals("device-param-correlation-123", capturedParams.get("request_id"),
            "request_id should be included in logging parameters");
    }

    @Test
    void testSetDeviceParameterWithoutRequestIdStillWorks() {
        // AC 3: Backward compatibility - tools work without request_id

        // Arrange
        doNothing().when(deviceController).setSelectedDeviceParameter(0, 0.5);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 0);
        arguments.put("value", 0.5);
        // No request_id

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, structuredLogger);

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Operation completes successfully
        assertNotNull(result);
        assertFalse(result.isError(), "Operation should succeed without request_id");

        // Verify logging still happened
        verify(structuredLogger).startTimedOperation(any(), eq("set_selected_device_parameter"), any());
    }

    @Test
    void testSetMultipleDeviceParametersWithRequestIdIncludesItInLoggingContext() {
        // AC 2, AC 3: request_id in tool arguments must be included in logging context

        // Arrange
        List<ParameterSettingResult> mockResults = List.of(
            new ParameterSettingResult(0, "success", 0.5, null, "Parameter set successfully")
        );
        when(deviceController.setMultipleSelectedDeviceParameters(any())).thenReturn(mockResults);

        List<Map<String, Object>> parametersList = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("parameter_index", 0);
        param.put("value", 0.5);
        parametersList.add(param);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", parametersList);
        arguments.put("request_id", "batch-param-correlation-456");

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, structuredLogger);

        // Act
        spec.callHandler().apply(null, mockRequest);

        // Assert: Verify startTimedOperation was called with parameters containing request_id
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(structuredLogger).startTimedOperation(any(), eq("set_selected_device_parameters"), paramsCaptor.capture());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Parameters map should not be null when request_id is provided");
        assertEquals("batch-param-correlation-456", capturedParams.get("request_id"),
            "request_id should be included in logging parameters");
    }

    @Test
    void testSetMultipleDeviceParametersWithoutRequestIdStillWorks() {
        // AC 3: Backward compatibility - tools work without request_id

        // Arrange
        List<ParameterSettingResult> mockResults = List.of(
            new ParameterSettingResult(0, "success", 0.5, null, "Parameter set successfully")
        );
        when(deviceController.setMultipleSelectedDeviceParameters(any())).thenReturn(mockResults);

        List<Map<String, Object>> parametersList = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("parameter_index", 0);
        param.put("value", 0.5);
        parametersList.add(param);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", parametersList);
        // No request_id

        McpSchema.CallToolRequest mockRequest = mock(McpSchema.CallToolRequest.class);
        when(mockRequest.arguments()).thenReturn(arguments);

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, structuredLogger);

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(null, mockRequest);

        // Assert: Operation completes successfully
        assertNotNull(result);
        assertFalse(result.isError(), "Operation should succeed without request_id");

        // Verify logging still happened
        verify(structuredLogger).startTimedOperation(any(), eq("set_selected_device_parameters"), any());
    }
}
