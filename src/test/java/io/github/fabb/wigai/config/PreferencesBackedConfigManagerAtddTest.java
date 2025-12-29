package io.github.fabb.wigai.config;

import com.bitwig.extension.callback.DoubleValueChangedCallback;
import com.bitwig.extension.callback.StringValueChangedCallback;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import io.github.fabb.wigai.common.AppConstants;
import io.github.fabb.wigai.common.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("atdd")
class PreferencesBackedConfigManagerAtddTest {

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

    private StringValueChangedCallback hostObserver;
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
            hostObserver = invocation.getArgument(0);
            return null;
        }).when(mockHostSetting).addValueObserver(any(StringValueChangedCallback.class));

        doAnswer(invocation -> {
            portObserver = invocation.getArgument(0);
            return null;
        }).when(mockPortSetting).addRawValueObserver(any(DoubleValueChangedCallback.class));

        configManager = new PreferencesBackedConfigManager(mockLogger, mockHost);
    }

    @DisplayName("1.2-ATDD-001 [P1] Given first enable, when prefs load, then defaults are localhost:61169")
    @Test
    void defaults_to_localhost_and_default_port_on_first_load() {
        assertEquals("localhost", configManager.getMcpHost());
        assertEquals(AppConstants.DEFAULT_MCP_PORT, configManager.getMcpPort());
    }

    @DisplayName("1.2-ATDD-002 [P1] Given empty host, when applied, then sanitize to localhost and persist")
    @Test
    void empty_or_whitespace_host_is_sanitized_and_written_back() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("   ");

        assertEquals("localhost", configManager.getMcpHost());
        verify(mockHostSetting).set("localhost");
        verify(mockLogger).warn(contains("Invalid host"));
    }

    @DisplayName("1.2-ATDD-003 [P1] Given non-loopback host, when applied, then revert to localhost and warn")
    @Test
    void non_loopback_host_is_rejected_and_reverted() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("0.0.0.0");

        assertEquals("localhost", configManager.getMcpHost());
        verify(mockHostSetting).set("localhost");
        verify(mockLogger).warn(contains("non-loopback"));
    }

    @DisplayName("1.2-ATDD-004 [P1] Given valid port change, when applied, then observers notified")
    @Test
    void valid_port_change_notifies_observers() {
        assertNotNull(portObserver);

        ConfigChangeObserver observer = mock(ConfigChangeObserver.class);
        configManager.addObserver(observer);

        portObserver.valueChanged(61234);

        assertEquals(61234, configManager.getMcpPort());
        verify(observer).onPortChanged(AppConstants.DEFAULT_MCP_PORT, 61234);
    }

    @DisplayName("1.2-ATDD-004b [P1] Given valid port change, when applied, then restart can be triggered for new port (AC4 full flow)")
    @Test
    void valid_port_change_triggers_restart_with_new_port() {
        // AC4: "WigAI performs a graceful restart and the MCP endpoint is reachable at the configured loopback host and new port"
        // This test verifies the config → observer → restart trigger chain.
        // Actual endpoint reachability requires integration testing with a running server.
        assertNotNull(portObserver);

        // Track whether restart would be triggered with correct port
        final int[] capturedOldPort = new int[1];
        final int[] capturedNewPort = new int[1];
        final boolean[] restartTriggered = new boolean[1];

        ConfigChangeObserver restartingObserver = new ConfigChangeObserver() {
            @Override
            public void onHostChanged(String oldHost, String newHost) { }

            @Override
            public void onPortChanged(int oldPort, int newPort) {
                capturedOldPort[0] = oldPort;
                capturedNewPort[0] = newPort;
                restartTriggered[0] = true;
                // In WigAIExtension, this calls serverManager.restartServer()
            }
        };
        configManager.addObserver(restartingObserver);

        // Change to valid port in range 1024-65535
        int newPort = 8080;
        portObserver.valueChanged(newPort);

        // Verify restart would be triggered with correct port values
        assertEquals(true, restartTriggered[0], "Restart should be triggered");
        assertEquals(AppConstants.DEFAULT_MCP_PORT, capturedOldPort[0], "Old port should be default");
        assertEquals(newPort, capturedNewPort[0], "New port should be the changed value");
        assertEquals(newPort, configManager.getMcpPort(), "Config should return new port for server binding");
    }

    @DisplayName("1.2-ATDD-005 [P1] Given invalid port, when applied, then default and persist")
    @Test
    void invalid_port_reverts_to_default_and_is_written_back() {
        assertNotNull(portObserver);

        portObserver.valueChanged(80);

        assertEquals(AppConstants.DEFAULT_MCP_PORT, configManager.getMcpPort());
        verify(mockPortSetting).set(AppConstants.DEFAULT_MCP_PORT);
        verify(mockLogger).warn(contains("Invalid port"));
    }
}
