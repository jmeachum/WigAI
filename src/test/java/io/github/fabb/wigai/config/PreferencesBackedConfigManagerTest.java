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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-safe unit tests for PreferencesBackedConfigManager.
 * Tests host validation, port validation, and preference writeback behavior.
 */
class PreferencesBackedConfigManagerTest {

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

    @Nested
    @DisplayName("Host Validation Tests")
    class HostValidationTests {

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

    @Nested
    @DisplayName("Port Validation Tests")
    class PortValidationTests {

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

    @Nested
    @DisplayName("Init-time Sanitization Tests")
    class InitTimeSanitizationTests {

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
}
