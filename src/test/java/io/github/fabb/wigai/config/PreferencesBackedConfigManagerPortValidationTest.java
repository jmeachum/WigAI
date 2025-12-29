package io.github.fabb.wigai.config;

import com.bitwig.extension.callback.DoubleValueChangedCallback;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import io.github.fabb.wigai.common.AppConstants;
import io.github.fabb.wigai.common.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-safe unit tests for PreferencesBackedConfigManager port validation.
 */
class PreferencesBackedConfigManagerPortValidationTest {

    @Mock
    private Logger mockLogger;

    @Mock
    private ControllerHost mockHost;

    @Mock
    private Preferences mockPreferences;

    @Mock
    private SettableStringValue mockHostSetting;

    @Mock
    private SettableRangedValue mockPortSetting;

    private DoubleValueChangedCallback portObserver;

    private PreferencesBackedConfigManager configManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mockHost.getPreferences()).thenReturn(mockPreferences);
        when(mockPreferences.getStringSetting(
            eq("MCP Host"),
            eq("Network Settings"),
            eq(50),
            eq("localhost")
        )).thenReturn(mockHostSetting);
        when(mockPreferences.getNumberSetting(
            eq("MCP Port"),
            eq("Network Settings"),
            eq(1024.0),
            eq(65535.0),
            eq(1.0),
            eq(""),
            eq((double) AppConstants.DEFAULT_MCP_PORT)
        )).thenReturn(mockPortSetting);

        when(mockHostSetting.get()).thenReturn("localhost");
        when(mockPortSetting.getRaw()).thenReturn((double) AppConstants.DEFAULT_MCP_PORT);

        doAnswer(invocation -> {
            portObserver = invocation.getArgument(0);
            return null;
        }).when(mockPortSetting).addRawValueObserver(any(DoubleValueChangedCallback.class));

        configManager = new PreferencesBackedConfigManager(mockLogger, mockHost);
    }

    @Test
    @DisplayName("Default port is DEFAULT_MCP_PORT")
    void defaultPortIsDefault() {
        assertEquals(AppConstants.DEFAULT_MCP_PORT, configManager.getMcpPort());
    }

    @Test
    @DisplayName("Valid port in range is accepted")
    void validPortAccepted() {
        assertNotNull(portObserver);

        portObserver.valueChanged(8080);

        assertEquals(8080, configManager.getMcpPort());
        verify(mockPortSetting, never()).set(AppConstants.DEFAULT_MCP_PORT);
    }

    @Test
    @DisplayName("Port below 1024 is reverted to default and written back")
    void portBelowRangeReverted() {
        assertNotNull(portObserver);

        portObserver.valueChanged(80);

        assertEquals(AppConstants.DEFAULT_MCP_PORT, configManager.getMcpPort());
        verify(mockPortSetting).set(AppConstants.DEFAULT_MCP_PORT);
        verify(mockLogger).warn(contains("Invalid port"));
    }

    @Test
    @DisplayName("Port above 65535 is reverted to default and written back")
    void portAboveRangeReverted() {
        assertNotNull(portObserver);

        portObserver.valueChanged(70000);

        assertEquals(AppConstants.DEFAULT_MCP_PORT, configManager.getMcpPort());
        verify(mockPortSetting).set(AppConstants.DEFAULT_MCP_PORT);
        verify(mockLogger).warn(contains("Invalid port"));
    }

    @Test
    @DisplayName("Valid port change notifies observers")
    void validPortChangeNotifiesObservers() {
        assertNotNull(portObserver);

        ConfigChangeObserver observer = mock(ConfigChangeObserver.class);
        configManager.addObserver(observer);

        portObserver.valueChanged(61234);

        assertEquals(61234, configManager.getMcpPort());
        verify(observer).onPortChanged(AppConstants.DEFAULT_MCP_PORT, 61234);
    }
}
