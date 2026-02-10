package io.github.fabb.wigai.features;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.data.ParameterSetting;
import io.github.fabb.wigai.common.data.ParameterSettingResult;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DeviceController class.
 */
// TODO (TEA Review): Split this test class into smaller focused files (<300 lines). See test-review-1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md
public class DeviceControllerTest {

    @Mock
    private BitwigApiFacade mockBitwigApiFacade;

    @Mock
    private Logger mockLogger;

    private DeviceController deviceController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        deviceController = new DeviceController(mockBitwigApiFacade, mockLogger);
    }

    @Test
    void testGetSelectedDeviceParameters_WithDevice() {
        // Arrange
        String deviceName = "Test Device";
        List<ParameterInfo> parameters = Arrays.asList(
                new ParameterInfo(0, "Param 1", 0.5, "50%"),
                new ParameterInfo(1, "Param 2", 0.75, "75%")
        );        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn(deviceName);
        when(mockBitwigApiFacade.getSelectedDeviceParameters()).thenReturn(parameters);

        // Act
        DeviceController.DeviceParametersResult result = deviceController.getSelectedDeviceParameters();

        // Assert
        assertNotNull(result);
        assertEquals(deviceName, result.deviceName());
        assertEquals(parameters, result.parameters());
        assertEquals(2, result.parameters().size());

        // Verify logging
        verify(mockLogger).info("DeviceController: Getting selected device parameters");
        verify(mockLogger).info("DeviceController: Retrieved device '" + deviceName + "' with 2 parameters");
    }

    @Test
    void testGetSelectedDeviceParameters_NoDevice() {
        // Arrange - facade throws DEVICE_NOT_SELECTED when no device is selected (per AC2)
        when(mockBitwigApiFacade.getSelectedDeviceName()).thenThrow(
            new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "getSelectedDeviceName", "No device is currently selected")
        );

        // Act & Assert - controller propagates the exception per AC2
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            deviceController.getSelectedDeviceParameters();
        });

        assertEquals(ErrorCode.DEVICE_NOT_SELECTED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("No device is currently selected"));

        // Verify logging
        verify(mockLogger).info("DeviceController: Getting selected device parameters");
        verify(mockLogger).error(contains("DeviceController: Error getting selected device parameters"));
    }

    @Test
    void testGetSelectedDeviceParameters_ExceptionHandling() {
        // Arrange
        when(mockBitwigApiFacade.getSelectedDeviceName()).thenThrow(new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "getSelectedDeviceName", "No device selected"));

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            deviceController.getSelectedDeviceParameters();
        });

        assertEquals(ErrorCode.DEVICE_NOT_SELECTED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("No device selected"));

        // Verify error logging
        verify(mockLogger).error(contains("DeviceController: Error getting selected device parameters"));
    }

    @Test
    void testGetSelectedDeviceParameters_PartialException() {
        // Arrange - facade getName works but getParameters fails
        String deviceName = "Test Device";
        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn(deviceName);
        when(mockBitwigApiFacade.getSelectedDeviceParameters()).thenThrow(new RuntimeException("Parameter error"));

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            deviceController.getSelectedDeviceParameters();
        });

        assertEquals(ErrorCode.BITWIG_API_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Parameter error"));

        // Verify error logging
        verify(mockLogger).error(contains("DeviceController: Unexpected error getting selected device parameters"));
    }

    @Test
    void testSetSelectedDeviceParameter_Success() {
        // Arrange
        int parameterIndex = 3;
        double value = 0.75;

        // Act
        deviceController.setSelectedDeviceParameter(parameterIndex, value);

        // Assert
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(parameterIndex, value);
    }

    @Test
    void testSetSelectedDeviceParameter_ValidationError() {
        // Arrange
        int parameterIndex = 8; // Invalid index
        double value = 0.5;

        doThrow(new BitwigApiException(ErrorCode.INVALID_PARAMETER_INDEX, "setSelectedDeviceParameter", "Parameter index must be between 0-7, got: 8"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(parameterIndex, value);

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            deviceController.setSelectedDeviceParameter(parameterIndex, value);
        });

        assertEquals(ErrorCode.INVALID_PARAMETER_INDEX, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Parameter index must be between 0-7, got: 8"));
    }

    @Test
    void testSetSelectedDeviceParameter_ValueValidationError() {
        // Arrange
        int parameterIndex = 0;
        double value = 1.5; // Invalid value - per project-context.md INVALID_RANGE is for values outside range

        doThrow(new BitwigApiException(ErrorCode.INVALID_RANGE, "setSelectedDeviceParameter", "value must be between 0.0 and 1.0, got: 1.5"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(parameterIndex, value);

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            deviceController.setSelectedDeviceParameter(parameterIndex, value);
        });

        assertEquals(ErrorCode.INVALID_RANGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("value must be between 0.0 and 1.0, got: 1.5"));
    }

    @Test
    void testSetSelectedDeviceParameter_NoDeviceSelected() {
        // Arrange
        int parameterIndex = 0;
        double value = 0.5;

        doThrow(new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "setSelectedDeviceParameter", "No device is currently selected"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(parameterIndex, value);

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            deviceController.setSelectedDeviceParameter(parameterIndex, value);
        });

        assertEquals(ErrorCode.DEVICE_NOT_SELECTED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("No device is currently selected"));
    }

    @Test
    void testSetSelectedDeviceParameter_BitwigApiError() {
        // Arrange
        int parameterIndex = 0;
        double value = 0.5;

        doThrow(new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "setSelectedDeviceParameter", "Bitwig API internal error"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(parameterIndex, value);

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            deviceController.setSelectedDeviceParameter(parameterIndex, value);
        });

        assertEquals(ErrorCode.BITWIG_API_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Bitwig API internal error"));
    }

    @Test
    void testSetSelectedDeviceParameter_BoundaryValues() {
        // Test valid boundary values

        // Test minimum values
        deviceController.setSelectedDeviceParameter(0, 0.0);
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(0, 0.0);

        // Test maximum values
        deviceController.setSelectedDeviceParameter(7, 1.0);
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(7, 1.0);

        // Test mid-range values
        deviceController.setSelectedDeviceParameter(3, 0.5);
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(3, 0.5);
    }

    // =========================== BATCH PARAMETER SETTING TESTS ===========================

    @Test
    void testSetMultipleSelectedDeviceParameters_AllSuccess() {
        // Arrange
        List<ParameterSetting> parameters = Arrays.asList(
            new ParameterSetting(0, 0.25),
            new ParameterSetting(1, 0.75),
            new ParameterSetting(2, 0.5)
        );

        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn("Test Device");

        // Act
        List<ParameterSettingResult> results = deviceController.setMultipleSelectedDeviceParameters(parameters);

        // Assert
        assertEquals(3, results.size());

        for (int i = 0; i < results.size(); i++) {
            ParameterSettingResult result = results.get(i);
            ParameterSetting param = parameters.get(i);

            assertEquals(param.parameter_index(), result.parameter_index());
            assertEquals("success", result.status());
            assertEquals(param.value(), result.new_value());
            assertNull(result.error_code());
            assertNull(result.message());
        }

        // Verify all parameters were set
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(0, 0.25);
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(1, 0.75);
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(2, 0.5);
    }

    @Test
    void testSetMultipleSelectedDeviceParameters_NoDeviceSelected() {
        // Arrange
        List<ParameterSetting> parameters = Arrays.asList(
            new ParameterSetting(0, 0.25)
        );

        when(mockBitwigApiFacade.getSelectedDeviceName()).thenThrow(new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "getSelectedDeviceName", "No device selected"));

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            deviceController.setMultipleSelectedDeviceParameters(parameters);
        });

        assertEquals(ErrorCode.DEVICE_NOT_SELECTED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("No device selected"));

        // Verify no parameter setting was attempted
        verify(mockBitwigApiFacade, never()).setSelectedDeviceParameter(anyInt(), anyDouble());
    }

    @Test
    void testSetMultipleSelectedDeviceParameters_PartialSuccess() {
        // Arrange
        List<ParameterSetting> parameters = Arrays.asList(
            new ParameterSetting(0, 0.25),    // Success
            new ParameterSetting(8, 0.75),    // Invalid index
            new ParameterSetting(2, 1.5),     // Invalid value
            new ParameterSetting(3, 0.5)      // Success
        );

        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn("Test Device");

        // Mock successful parameter setting for valid parameters
        doNothing().when(mockBitwigApiFacade).setSelectedDeviceParameter(0, 0.25);
        doNothing().when(mockBitwigApiFacade).setSelectedDeviceParameter(3, 0.5);

        // Mock validation errors for invalid parameters
        doThrow(new BitwigApiException(ErrorCode.INVALID_PARAMETER_INDEX, "setSelectedDeviceParameter", "Parameter index must be between 0-7"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(8, 0.75);
        doThrow(new BitwigApiException(ErrorCode.INVALID_RANGE, "setSelectedDeviceParameter", "value must be between 0.0 and 1.0"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(2, 1.5);

        // Act
        List<ParameterSettingResult> results = deviceController.setMultipleSelectedDeviceParameters(parameters);

        // Assert
        assertEquals(4, results.size());

        // Check first parameter (success)
        ParameterSettingResult result0 = results.get(0);
        assertEquals(0, result0.parameter_index());
        assertEquals("success", result0.status());
        assertEquals(0.25, result0.new_value());
        assertNull(result0.error_code());
        assertNull(result0.message());

        // Check second parameter (invalid index)
        ParameterSettingResult result1 = results.get(1);
        assertEquals(8, result1.parameter_index());
        assertEquals("error", result1.status());
        assertNull(result1.new_value());
        assertEquals("INVALID_PARAMETER_INDEX", result1.error_code());
        assertTrue(result1.message().contains("Parameter index must be between 0-7"));

        // Check third parameter (invalid value - INVALID_RANGE per project-context.md)
        ParameterSettingResult result2 = results.get(2);
        assertEquals(2, result2.parameter_index());
        assertEquals("error", result2.status());
        assertNull(result2.new_value());
        assertEquals("INVALID_RANGE", result2.error_code());
        assertTrue(result2.message().contains("value must be between 0.0 and 1.0"));

        // Check fourth parameter (success)
        ParameterSettingResult result3 = results.get(3);
        assertEquals(3, result3.parameter_index());
        assertEquals("success", result3.status());
        assertEquals(0.5, result3.new_value());
        assertNull(result3.error_code());
        assertNull(result3.message());

    }

    @Test
    void testSetMultipleSelectedDeviceParameters_AllValidationErrors() {
        // Arrange
        List<ParameterSetting> parameters = Arrays.asList(
            new ParameterSetting(8, 0.25),    // Invalid index
            new ParameterSetting(1, 1.5)      // Invalid value
        );

        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn("Test Device");

        // Mock validation errors (INVALID_RANGE for value errors per project-context.md)
        doThrow(new BitwigApiException(ErrorCode.INVALID_PARAMETER_INDEX, "setSelectedDeviceParameter", "Parameter index must be between 0-7"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(8, 0.25);
        doThrow(new BitwigApiException(ErrorCode.INVALID_RANGE, "setSelectedDeviceParameter", "value must be between 0.0 and 1.0"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(1, 1.5);

        // Act
        List<ParameterSettingResult> results = deviceController.setMultipleSelectedDeviceParameters(parameters);

        // Assert
        assertEquals(2, results.size());

        for (ParameterSettingResult result : results) {
            assertEquals("error", result.status());
            assertNull(result.new_value());
            assertNotNull(result.error_code());
            assertNotNull(result.message());
        }

    }

    @Test
    void testSetMultipleSelectedDeviceParameters_BitwigErrors() {
        // Arrange
        List<ParameterSetting> parameters = Arrays.asList(
            new ParameterSetting(0, 0.25),    // Success
            new ParameterSetting(1, 0.75)     // Bitwig API error
        );

        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn("Test Device");

        // Mock one success and one Bitwig error
        doNothing().when(mockBitwigApiFacade).setSelectedDeviceParameter(0, 0.25);
        doThrow(new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "setSelectedDeviceParameter", "Bitwig internal error"))
            .when(mockBitwigApiFacade).setSelectedDeviceParameter(1, 0.75);

        // Act
        List<ParameterSettingResult> results = deviceController.setMultipleSelectedDeviceParameters(parameters);

        // Assert
        assertEquals(2, results.size());

        // Check first parameter (success)
        ParameterSettingResult result0 = results.get(0);
        assertEquals(0, result0.parameter_index());
        assertEquals("success", result0.status());
        assertEquals(0.25, result0.new_value());

        // Check second parameter (Bitwig error)
        ParameterSettingResult result1 = results.get(1);
        assertEquals(1, result1.parameter_index());
        assertEquals("error", result1.status());
        assertNull(result1.new_value());
        assertEquals("BITWIG_API_ERROR", result1.error_code());
        assertTrue(result1.message().contains("Bitwig internal error"));
    }

    @Test
    void testSetMultipleSelectedDeviceParameters_EmptyList() {
        // Arrange
        List<ParameterSetting> parameters = Collections.emptyList();

        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn("Test Device");

        // Act
        List<ParameterSettingResult> results = deviceController.setMultipleSelectedDeviceParameters(parameters);

        // Assert
        assertTrue(results.isEmpty());

        // Verify no parameter setting was attempted
        verify(mockBitwigApiFacade, never()).setSelectedDeviceParameter(anyInt(), anyDouble());
    }

    @Test
    void testSetMultipleSelectedDeviceParameters_SingleParameter() {
        // Arrange
        List<ParameterSetting> parameters = Arrays.asList(
            new ParameterSetting(4, 0.8)
        );

        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn("Test Device");

        // Act
        List<ParameterSettingResult> results = deviceController.setMultipleSelectedDeviceParameters(parameters);

        // Assert
        assertEquals(1, results.size());

        ParameterSettingResult result = results.get(0);
        assertEquals(4, result.parameter_index());
        assertEquals("success", result.status());
        assertEquals(0.8, result.new_value());

        // Verify parameter was set
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(4, 0.8);
    }

    @Test
    void testSetMultipleSelectedDeviceParameters_BoundaryValues() {
        // Arrange - test all valid boundary parameter indices and values
        List<ParameterSetting> parameters = Arrays.asList(
            new ParameterSetting(0, 0.0),     // Min index, min value
            new ParameterSetting(7, 1.0)      // Max index, max value
        );

        when(mockBitwigApiFacade.getSelectedDeviceName()).thenReturn("Test Device");

        // Act
        List<ParameterSettingResult> results = deviceController.setMultipleSelectedDeviceParameters(parameters);

        // Assert
        assertEquals(2, results.size());

        for (ParameterSettingResult result : results) {
            assertEquals("success", result.status());
            assertNull(result.error_code());
        }

        // Verify both parameters were set
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(0, 0.0);
        verify(mockBitwigApiFacade).setSelectedDeviceParameter(7, 1.0);
    }
}
