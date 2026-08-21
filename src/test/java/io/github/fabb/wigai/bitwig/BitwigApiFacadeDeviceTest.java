package io.github.fabb.wigai.bitwig;
import com.bitwig.extension.controller.api.*;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Device parameter control and selected-device reporting.
 *
 * <p>Mock harness lives in {@link BitwigApiFacadeTestSupport}.
 */
class BitwigApiFacadeDeviceTest extends BitwigApiFacadeTestSupport {

    @Test
    void testSetSelectedDeviceParameter_Success() {
        // Arrange
        int parameterIndex = 3;
        double value = 0.75;

        // Mock device exists
        com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockExists.get()).thenReturn(true);
        when(mockCursorDevice.exists()).thenReturn(mockExists);

        // Mock parameter value setter
        com.bitwig.extension.controller.api.SettableRangedValue mockValueSetter = mock(com.bitwig.extension.controller.api.SettableRangedValue.class);
        when(mockRemoteControl.value()).thenReturn(mockValueSetter);
        when(mockParameterBank.getParameter(parameterIndex)).thenReturn(mockRemoteControl);

        // Act
        bitwigApiFacade.setSelectedDeviceParameter(parameterIndex, value);

        // Assert
        verify(mockValueSetter).set(value);
        verify(mockLogger).info("BitwigApiFacade: Setting parameter " + parameterIndex + " to " + value);
        verify(mockLogger).info("BitwigApiFacade: Successfully set parameter " + parameterIndex + " to " + value);
    }

    @Test
    void testSetSelectedDeviceParameter_InvalidParameterIndex() {
        // Mock device exists for this test
        com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockExists.get()).thenReturn(true);
        when(mockCursorDevice.exists()).thenReturn(mockExists);

        // Test invalid parameter index (too high)
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            bitwigApiFacade.setSelectedDeviceParameter(8, 0.5);
        });

        assertEquals(ErrorCode.INVALID_PARAMETER_INDEX, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("parameter_index must be between 0 and 7, got: 8"));

        // Test invalid parameter index (negative)
        BitwigApiException exception2 = assertThrows(BitwigApiException.class, () -> {
            bitwigApiFacade.setSelectedDeviceParameter(-1, 0.5);
        });

        assertEquals(ErrorCode.INVALID_PARAMETER_INDEX, exception2.getErrorCode());
        assertTrue(exception2.getMessage().contains("parameter_index must be between 0 and 7, got: -1"));
    }

    @Test
    void testSetSelectedDeviceParameter_InvalidValue() {
        // Mock device exists for this test
        com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockExists.get()).thenReturn(true);
        when(mockCursorDevice.exists()).thenReturn(mockExists);

        // Test value too high
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            bitwigApiFacade.setSelectedDeviceParameter(0, 1.5);
        });

        assertEquals(ErrorCode.INVALID_RANGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("value must be between 0.0 and 1.0, got: 1.5"));

        // Test negative value
        BitwigApiException exception2 = assertThrows(BitwigApiException.class, () -> {
            bitwigApiFacade.setSelectedDeviceParameter(0, -0.1);
        });

        assertEquals(ErrorCode.INVALID_RANGE, exception2.getErrorCode());
        assertTrue(exception2.getMessage().contains("value must be between 0.0 and 1.0, got: -0.1"));
    }

    @Test
    void testSetSelectedDeviceParameter_NoDeviceSelected() {
        // Arrange
        com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockExists.get()).thenReturn(false);
        when(mockCursorDevice.exists()).thenReturn(mockExists);

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            bitwigApiFacade.setSelectedDeviceParameter(0, 0.5);
        });

        assertEquals(ErrorCode.DEVICE_NOT_SELECTED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("No device is currently selected"));
    }

    @Test
    void testSetSelectedDeviceParameter_BitwigApiError() {
        // Arrange
        int parameterIndex = 0;
        double value = 0.5;

        // Mock device exists
        com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockExists.get()).thenReturn(true);
        when(mockCursorDevice.exists()).thenReturn(mockExists);

        // Mock parameter access that throws exception
        when(mockParameterBank.getParameter(parameterIndex)).thenThrow(new RuntimeException("Bitwig API error"));

        // Act & Assert
        BitwigApiException exception = assertThrows(BitwigApiException.class, () -> {
            bitwigApiFacade.setSelectedDeviceParameter(parameterIndex, value);
        });

        // Current implementation returns OPERATION_FAILED for all RuntimeException
        assertEquals(ErrorCode.OPERATION_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Bitwig API error"));
        assertEquals("Bitwig API error", exception.getCause().getMessage());
    }

    @Test
    void testSetSelectedDeviceParameter_BoundaryValues() {
        // Arrange
        com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockExists.get()).thenReturn(true);
        when(mockCursorDevice.exists()).thenReturn(mockExists);

        com.bitwig.extension.controller.api.SettableRangedValue mockValueSetter = mock(com.bitwig.extension.controller.api.SettableRangedValue.class);
        when(mockRemoteControl.value()).thenReturn(mockValueSetter);
        when(mockParameterBank.getParameter(anyInt())).thenReturn(mockRemoteControl);

        // Test minimum boundary values
        bitwigApiFacade.setSelectedDeviceParameter(0, 0.0);
        verify(mockValueSetter).set(0.0);

        // Test maximum boundary values
        bitwigApiFacade.setSelectedDeviceParameter(7, 1.0);
        verify(mockValueSetter).set(1.0);

        // Verify parameter bank access for boundary indices
        verify(mockParameterBank, times(2)).getParameter(0);
        verify(mockParameterBank, times(2)).getParameter(7);
    }

    @Test
    void testGetSelectedDeviceInfo_WithDeviceSelected() {
        // Arrange
        // Mock device exists
        com.bitwig.extension.controller.api.BooleanValue mockDeviceExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockDeviceExists.get()).thenReturn(true);
        when(mockCursorDevice.exists()).thenReturn(mockDeviceExists);

        // Mock device name
        com.bitwig.extension.controller.api.SettableStringValue mockDeviceName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
        when(mockDeviceName.get()).thenReturn("Test Device");
        when(mockCursorDevice.name()).thenReturn(mockDeviceName);

        // Mock device enabled status
        com.bitwig.extension.controller.api.SettableBooleanValue mockDeviceEnabled = mock(com.bitwig.extension.controller.api.SettableBooleanValue.class);
        when(mockDeviceEnabled.get()).thenReturn(true);
        when(mockCursorDevice.isEnabled()).thenReturn(mockDeviceEnabled);

        // Mock cursor track name and position (project-absolute index)
        com.bitwig.extension.controller.api.SettableStringValue mockTrackName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
        when(mockTrackName.get()).thenReturn("Test Track");
        when(mockCursorTrack.name()).thenReturn(mockTrackName);

        // Mock cursorTrack.position() for project-absolute track index
        // Use non-zero value to verify we're actually reading from position(), not defaulting
        com.bitwig.extension.controller.api.IntegerValue mockCursorTrackPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(mockCursorTrackPosition.get()).thenReturn(5);
        when(mockCursorTrack.position()).thenReturn(mockCursorTrackPosition);

        // Mock device parameters
        for (int i = 0; i < 8; i++) {
            RemoteControl mockParam = mock(RemoteControl.class);

            com.bitwig.extension.controller.api.SettableStringValue mockParamName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
            com.bitwig.extension.controller.api.SettableRangedValue mockParamValue = mock(com.bitwig.extension.controller.api.SettableRangedValue.class);
            com.bitwig.extension.controller.api.SettableStringValue mockParamDisplay = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
            com.bitwig.extension.controller.api.BooleanValue mockParamExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);

            if (i == 0) {
                when(mockParamName.get()).thenReturn("Cutoff");
                when(mockParamValue.get()).thenReturn(0.7);
                when(mockParamDisplay.get()).thenReturn("70%");
                when(mockParamExists.get()).thenReturn(true);
            } else if (i == 2) {
                when(mockParamName.get()).thenReturn("Resonance");
                when(mockParamValue.get()).thenReturn(0.3);
                when(mockParamDisplay.get()).thenReturn("30%");
                when(mockParamExists.get()).thenReturn(true);
            } else {
                when(mockParamName.get()).thenReturn("");  // Empty name for unused parameters
                when(mockParamValue.get()).thenReturn(0.0);
                when(mockParamDisplay.get()).thenReturn("0%");
                when(mockParamExists.get()).thenReturn(false);
            }

            when(mockParam.name()).thenReturn(mockParamName);
            when(mockParam.value()).thenReturn(mockParamValue);
            when(mockParam.displayedValue()).thenReturn(mockParamDisplay);
            when(mockParam.exists()).thenReturn(mockParamExists);
            when(mockParameterBank.getParameter(i)).thenReturn(mockParam);
        }

        // Act
        java.util.Map<String, Object> result = bitwigApiFacade.getSelectedDeviceInfo();

        // Assert
        assertNotNull(result);
        assertEquals("Test Track", result.get("track_name"));
        assertEquals(5, result.get("track_index"));  // Project-absolute index from cursorTrack.position()
        assertEquals(0, result.get("index"));  // Device index in chain (always 0 per Bitwig API limitation)
        assertEquals("Test Device", result.get("name"));
        assertEquals(false, result.get("bypassed"));  // Device is enabled, so not bypassed

        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> parameters = (java.util.List<java.util.Map<String, Object>>) result.get("parameters");
        assertEquals(2, parameters.size());  // Only 2 parameters with non-empty names

        java.util.Map<String, Object> param0 = parameters.get(0);
        assertEquals(0, param0.get("index"));
        assertEquals("Cutoff", param0.get("name"));
        assertEquals(0.7, param0.get("value"));
        assertEquals("70%", param0.get("display_value"));

        java.util.Map<String, Object> param2 = parameters.get(1);
        assertEquals(2, param2.get("index"));
        assertEquals("Resonance", param2.get("name"));
        assertEquals(0.3, param2.get("value"));
        assertEquals("30%", param2.get("display_value"));

        verify(mockLogger).info("BitwigApiFacade: Getting selected device information");
        verify(mockLogger).info("BitwigApiFacade: Retrieved selected device info: Test Device");
    }

    @Test
    void testGetSelectedDeviceInfo_NoDeviceSelected() {
        // Arrange
        com.bitwig.extension.controller.api.BooleanValue mockDeviceExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockDeviceExists.get()).thenReturn(false);
        when(mockCursorDevice.exists()).thenReturn(mockDeviceExists);

        // Act
        java.util.Map<String, Object> result = bitwigApiFacade.getSelectedDeviceInfo();

        // Assert
        assertNull(result);
        verify(mockLogger).info("BitwigApiFacade: Getting selected device information");
        verify(mockLogger).info("BitwigApiFacade: No device selected");
    }

    @Test
    void testGetSelectedDeviceInfo_WithBypassedDevice() {
        // Arrange
        // Mock device exists
        com.bitwig.extension.controller.api.BooleanValue mockDeviceExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockDeviceExists.get()).thenReturn(true);
        when(mockCursorDevice.exists()).thenReturn(mockDeviceExists);

        // Mock device properties
        com.bitwig.extension.controller.api.SettableStringValue mockDeviceName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
        when(mockDeviceName.get()).thenReturn("Test Device");
        when(mockCursorDevice.name()).thenReturn(mockDeviceName);

        com.bitwig.extension.controller.api.SettableBooleanValue mockDeviceEnabled = mock(com.bitwig.extension.controller.api.SettableBooleanValue.class);
        when(mockDeviceEnabled.get()).thenReturn(false);  // Device is bypassed
        when(mockCursorDevice.isEnabled()).thenReturn(mockDeviceEnabled);

        // Mock cursor track name and position (uses project-absolute index)
        com.bitwig.extension.controller.api.SettableStringValue mockTrackName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
        when(mockTrackName.get()).thenReturn("Test Track");
        when(mockCursorTrack.name()).thenReturn(mockTrackName);

        com.bitwig.extension.controller.api.SettableIntegerValue mockTrackPosition = mock(com.bitwig.extension.controller.api.SettableIntegerValue.class);
        when(mockTrackPosition.get()).thenReturn(5);  // Project-absolute track position
        when(mockCursorTrack.position()).thenReturn(mockTrackPosition);

        // Mock empty device parameters
        for (int i = 0; i < 8; i++) {
            RemoteControl mockParam = mock(RemoteControl.class);

            com.bitwig.extension.controller.api.SettableStringValue mockParamName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
            when(mockParamName.get()).thenReturn("");  // Empty names
            // Ensure parameters are treated as non-existent to avoid NPEs in exists().get()
            com.bitwig.extension.controller.api.BooleanValue mockParamExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
            when(mockParamExists.get()).thenReturn(false);
            when(mockParam.name()).thenReturn(mockParamName);
            when(mockParam.exists()).thenReturn(mockParamExists);
            when(mockParameterBank.getParameter(i)).thenReturn(mockParam);
        }

        // Act
        java.util.Map<String, Object> result = bitwigApiFacade.getSelectedDeviceInfo();

        // Assert
        assertNotNull(result);
        assertEquals("Test Track", result.get("track_name"));
        assertEquals(5, result.get("track_index"));  // Project-absolute position
        assertEquals(0, result.get("index"));
        assertEquals("Test Device", result.get("name"));
        assertEquals(true, result.get("bypassed"));  // Device is disabled, so bypassed

        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> parameters = (java.util.List<java.util.Map<String, Object>>) result.get("parameters");
        assertEquals(0, parameters.size());  // No parameters with non-empty names
    }
}
