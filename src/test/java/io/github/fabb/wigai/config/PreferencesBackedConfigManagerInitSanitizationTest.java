package io.github.fabb.wigai.config;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import io.github.fabb.wigai.common.AppConstants;
import io.github.fabb.wigai.common.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-safe unit tests for init-time sanitization in PreferencesBackedConfigManager.
 */
class PreferencesBackedConfigManagerInitSanitizationTest {

    @Test
    @DisplayName("Invalid persisted host is sanitized and written back on construction")
    void invalidPersistedHostSanitizedOnConstruction() {
        // Create fresh mocks for this test
        Logger logger = mock(Logger.class);
        ControllerHost host = mock(ControllerHost.class);
        Preferences prefs = mock(Preferences.class);
        SettableStringValue hostSetting = mock(SettableStringValue.class);
        SettableRangedValue portSetting = mock(SettableRangedValue.class);

        when(host.getPreferences()).thenReturn(prefs);
        when(prefs.getStringSetting(eq("MCP Host"), eq("Network Settings"), eq(50), eq("localhost")))
            .thenReturn(hostSetting);
        when(prefs.getNumberSetting(eq("MCP Port"), eq("Network Settings"), eq(1024.0), eq(65535.0), eq(1.0), eq(""), eq((double) AppConstants.DEFAULT_MCP_PORT)))
            .thenReturn(portSetting);

        // Simulate invalid persisted host
        when(hostSetting.get()).thenReturn("0.0.0.0");
        when(portSetting.getRaw()).thenReturn((double) AppConstants.DEFAULT_MCP_PORT);

        // Create config manager - should sanitize on construction
        PreferencesBackedConfigManager mgr = new PreferencesBackedConfigManager(logger, host);

        // Verify sanitization occurred
        assertEquals("localhost", mgr.getMcpHost());
        verify(hostSetting).set("localhost");
        verify(logger).info(contains("Sanitized persisted host"));
    }

    @Test
    @DisplayName("Invalid persisted port is sanitized and written back on construction")
    void invalidPersistedPortSanitizedOnConstruction() {
        // Create fresh mocks for this test
        Logger logger = mock(Logger.class);
        ControllerHost host = mock(ControllerHost.class);
        Preferences prefs = mock(Preferences.class);
        SettableStringValue hostSetting = mock(SettableStringValue.class);
        SettableRangedValue portSetting = mock(SettableRangedValue.class);

        when(host.getPreferences()).thenReturn(prefs);
        when(prefs.getStringSetting(eq("MCP Host"), eq("Network Settings"), eq(50), eq("localhost")))
            .thenReturn(hostSetting);
        when(prefs.getNumberSetting(eq("MCP Port"), eq("Network Settings"), eq(1024.0), eq(65535.0), eq(1.0), eq(""), eq((double) AppConstants.DEFAULT_MCP_PORT)))
            .thenReturn(portSetting);

        // Simulate invalid persisted port (below range)
        when(hostSetting.get()).thenReturn("localhost");
        when(portSetting.getRaw()).thenReturn(80.0);

        // Create config manager - should sanitize on construction
        PreferencesBackedConfigManager mgr = new PreferencesBackedConfigManager(logger, host);

        // Verify sanitization occurred
        assertEquals(AppConstants.DEFAULT_MCP_PORT, mgr.getMcpPort());
        verify(portSetting).set(AppConstants.DEFAULT_MCP_PORT);
        verify(logger).info(contains("Sanitized persisted port"));
    }

    @Test
    @DisplayName("Valid persisted values are not written back on construction")
    void validPersistedValuesNotWrittenBack() {
        // Create fresh mocks for this test
        Logger logger = mock(Logger.class);
        ControllerHost host = mock(ControllerHost.class);
        Preferences prefs = mock(Preferences.class);
        SettableStringValue hostSetting = mock(SettableStringValue.class);
        SettableRangedValue portSetting = mock(SettableRangedValue.class);

        when(host.getPreferences()).thenReturn(prefs);
        when(prefs.getStringSetting(eq("MCP Host"), eq("Network Settings"), eq(50), eq("localhost")))
            .thenReturn(hostSetting);
        when(prefs.getNumberSetting(eq("MCP Port"), eq("Network Settings"), eq(1024.0), eq(65535.0), eq(1.0), eq(""), eq((double) AppConstants.DEFAULT_MCP_PORT)))
            .thenReturn(portSetting);

        // Simulate valid persisted values
        when(hostSetting.get()).thenReturn("127.0.0.1");
        when(portSetting.getRaw()).thenReturn(8080.0);

        // Create config manager - should NOT write back
        PreferencesBackedConfigManager mgr = new PreferencesBackedConfigManager(logger, host);

        // Verify no writeback occurred
        assertEquals("127.0.0.1", mgr.getMcpHost());
        assertEquals(8080, mgr.getMcpPort());
        verify(hostSetting, never()).set(any());
        verify(portSetting, never()).set(anyDouble());
    }

    @Test
    @DisplayName("Empty persisted host is sanitized to localhost on construction")
    void emptyPersistedHostSanitizedOnConstruction() {
        // Create fresh mocks for this test
        Logger logger = mock(Logger.class);
        ControllerHost host = mock(ControllerHost.class);
        Preferences prefs = mock(Preferences.class);
        SettableStringValue hostSetting = mock(SettableStringValue.class);
        SettableRangedValue portSetting = mock(SettableRangedValue.class);

        when(host.getPreferences()).thenReturn(prefs);
        when(prefs.getStringSetting(eq("MCP Host"), eq("Network Settings"), eq(50), eq("localhost")))
            .thenReturn(hostSetting);
        when(prefs.getNumberSetting(eq("MCP Port"), eq("Network Settings"), eq(1024.0), eq(65535.0), eq(1.0), eq(""), eq((double) AppConstants.DEFAULT_MCP_PORT)))
            .thenReturn(portSetting);

        // Simulate empty persisted host
        when(hostSetting.get()).thenReturn("");
        when(portSetting.getRaw()).thenReturn((double) AppConstants.DEFAULT_MCP_PORT);

        // Create config manager - should sanitize on construction
        PreferencesBackedConfigManager mgr = new PreferencesBackedConfigManager(logger, host);

        // Verify sanitization occurred
        assertEquals("localhost", mgr.getMcpHost());
        verify(hostSetting).set("localhost");
    }
}
