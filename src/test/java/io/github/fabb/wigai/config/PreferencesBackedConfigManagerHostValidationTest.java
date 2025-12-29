package io.github.fabb.wigai.config;

import com.bitwig.extension.callback.StringValueChangedCallback;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-safe unit tests for PreferencesBackedConfigManager host validation.
 */
class PreferencesBackedConfigManagerHostValidationTest {

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

        configManager = new PreferencesBackedConfigManager(mockLogger, mockHost);
    }

    @Test
    @DisplayName("Default host is localhost")
    void defaultHostIsLocalhost() {
        assertEquals("localhost", configManager.getMcpHost());
    }

    @Test
    @DisplayName("Empty host is sanitized to localhost and written back")
    void emptyHostIsSanitizedAndWrittenBack() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("");

        assertEquals("localhost", configManager.getMcpHost());
        verify(mockHostSetting).set("localhost");
        verify(mockLogger).warn(contains("Invalid host"));
    }

    @Test
    @DisplayName("Whitespace host is sanitized to localhost and written back")
    void whitespaceHostIsSanitizedAndWrittenBack() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("   ");

        assertEquals("localhost", configManager.getMcpHost());
        verify(mockHostSetting).set("localhost");
        verify(mockLogger).warn(contains("Invalid host"));
    }

    @Test
    @DisplayName("Non-loopback host 0.0.0.0 is rejected and reverted")
    void nonLoopbackAllInterfacesRejected() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("0.0.0.0");

        assertEquals("localhost", configManager.getMcpHost());
        verify(mockHostSetting).set("localhost");
        verify(mockLogger).warn(contains("non-loopback"));
    }

    @Test
    @DisplayName("Non-loopback private IP is rejected and reverted")
    void nonLoopbackPrivateIpRejected() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("192.168.1.100");

        assertEquals("localhost", configManager.getMcpHost());
        verify(mockHostSetting).set("localhost");
        verify(mockLogger).warn(contains("non-loopback"));
    }

    @Test
    @DisplayName("Non-loopback public hostname is rejected and reverted")
    void nonLoopbackPublicHostnameRejected() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("example.com");

        assertEquals("localhost", configManager.getMcpHost());
        verify(mockHostSetting).set("localhost");
        verify(mockLogger).warn(contains("non-loopback"));
    }

    @Test
    @DisplayName("Loopback 127.0.0.1 is allowed")
    void loopback127Allowed() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("127.0.0.1");

        assertEquals("127.0.0.1", configManager.getMcpHost());
        verify(mockHostSetting, never()).set("localhost");
    }

    @Test
    @DisplayName("Loopback ::1 (IPv6) is allowed")
    void loopbackIpv6Allowed() {
        assertNotNull(hostObserver);

        hostObserver.valueChanged("::1");

        assertEquals("::1", configManager.getMcpHost());
        verify(mockHostSetting, never()).set("localhost");
    }

    @Test
    @DisplayName("Loopback localhost is allowed")
    void loopbackLocalhostAllowed() {
        // Change to something else first
        hostObserver.valueChanged("127.0.0.1");

        // Then change to localhost
        hostObserver.valueChanged("localhost");

        assertEquals("localhost", configManager.getMcpHost());
    }

    @Test
    @DisplayName("Null host update is ignored (defensive pattern)")
    void nullHostUpdateIsIgnored() {
        // First change to a non-default value
        hostObserver.valueChanged("127.0.0.1");
        assertEquals("127.0.0.1", configManager.getMcpHost());

        // Simulate null update (shouldn't happen with Bitwig, but defensive)
        hostObserver.valueChanged(null);

        // Host should remain unchanged
        assertEquals("127.0.0.1", configManager.getMcpHost());
        // No writeback should occur for null
        verify(mockHostSetting, never()).set(null);
    }
}
