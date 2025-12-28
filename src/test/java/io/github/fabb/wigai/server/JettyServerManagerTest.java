package io.github.fabb.wigai.server;

import com.bitwig.extension.controller.api.ControllerHost;
import io.github.fabb.wigai.WigAIExtensionDefinition;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.BindException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

/**
 * CI-safe unit tests for JettyServerManager.
 * Tests bind failure detection logic without starting actual servers.
 */
class JettyServerManagerTest {

    @Mock
    private Logger logger;

    @Mock
    private ConfigManager configManager;

    @Mock
    private WigAIExtensionDefinition extensionDefinition;

    @Mock
    private ControllerHost host;

    private JettyServerManager serverManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        serverManager = new JettyServerManager(logger, configManager, extensionDefinition, host);
    }

    @Nested
    @DisplayName("containsBindException")
    class ContainsBindExceptionTests {

        @Test
        @DisplayName("returns false for null")
        void returnsFalseForNull() {
            assertFalse(serverManager.containsBindException(null));
        }

        @Test
        @DisplayName("returns true for direct BindException")
        void returnsTrueForDirectBindException() {
            BindException bindException = new BindException("Address already in use");
            assertTrue(serverManager.containsBindException(bindException));
        }

        @Test
        @DisplayName("returns true for BindException in cause chain")
        void returnsTrueForBindExceptionInCauseChain() {
            BindException bindException = new BindException("Address already in use");
            RuntimeException wrapper = new RuntimeException("Server failed to start", bindException);

            assertTrue(serverManager.containsBindException(wrapper));
        }

        @Test
        @DisplayName("returns true for BindException nested deeply in cause chain")
        void returnsTrueForDeeplyNestedBindException() {
            BindException bindException = new BindException("Address already in use");
            Exception level1 = new Exception("Level 1", bindException);
            Exception level2 = new Exception("Level 2", level1);
            Exception level3 = new Exception("Level 3", level2);

            assertTrue(serverManager.containsBindException(level3));
        }

        @Test
        @DisplayName("returns true for BindException in suppressed exceptions (MultiException case)")
        void returnsTrueForBindExceptionInSuppressed() {
            BindException bindException = new BindException("Address already in use");
            Exception multiException = new Exception("Multiple failures");
            multiException.addSuppressed(bindException);

            assertTrue(serverManager.containsBindException(multiException));
        }

        @Test
        @DisplayName("returns true for BindException in nested suppressed exception")
        void returnsTrueForNestedSuppressedBindException() {
            BindException bindException = new BindException("Address already in use");
            Exception innerException = new Exception("Inner", bindException);
            Exception multiException = new Exception("Multiple failures");
            multiException.addSuppressed(innerException);

            assertTrue(serverManager.containsBindException(multiException));
        }

        @Test
        @DisplayName("returns false for non-BindException")
        void returnsFalseForNonBindException() {
            RuntimeException runtimeException = new RuntimeException("Some other error");
            assertFalse(serverManager.containsBindException(runtimeException));
        }

        @Test
        @DisplayName("returns false for exception with non-BindException cause")
        void returnsFalseForNonBindExceptionCause() {
            IllegalArgumentException cause = new IllegalArgumentException("Invalid argument");
            RuntimeException wrapper = new RuntimeException("Wrapper", cause);

            assertFalse(serverManager.containsBindException(wrapper));
        }

        @Test
        @DisplayName("returns false for exception with non-BindException suppressed")
        void returnsFalseForNonBindExceptionSuppressed() {
            Exception multiException = new Exception("Multiple failures");
            multiException.addSuppressed(new IllegalStateException("State error"));
            multiException.addSuppressed(new NullPointerException("NPE"));

            assertFalse(serverManager.containsBindException(multiException));
        }
    }

    @Nested
    @DisplayName("formatHostForUrl")
    class FormatHostForUrlTests {

        @Test
        @DisplayName("returns localhost for null host")
        void returnsLocalhostForNull() {
            assertEquals("localhost", serverManager.formatHostForUrl(null));
        }

        @Test
        @DisplayName("returns localhost unchanged")
        void returnsLocalhostUnchanged() {
            assertEquals("localhost", serverManager.formatHostForUrl("localhost"));
        }

        @Test
        @DisplayName("returns 127.0.0.1 unchanged (IPv4)")
        void returnsIpv4Unchanged() {
            assertEquals("127.0.0.1", serverManager.formatHostForUrl("127.0.0.1"));
        }

        @Test
        @DisplayName("wraps IPv6 loopback ::1 in brackets")
        void wrapsIpv6LoopbackInBrackets() {
            assertEquals("[::1]", serverManager.formatHostForUrl("::1"));
        }

        @Test
        @DisplayName("wraps full IPv6 address in brackets")
        void wrapsFullIpv6InBrackets() {
            assertEquals("[2001:db8::1]", serverManager.formatHostForUrl("2001:db8::1"));
        }

        @Test
        @DisplayName("wraps IPv6 localhost form in brackets")
        void wrapsIpv6LocalhostInBrackets() {
            assertEquals("[0:0:0:0:0:0:0:1]", serverManager.formatHostForUrl("0:0:0:0:0:0:0:1"));
        }
    }

    @Nested
    @DisplayName("notifyBindFailure")
    class NotifyBindFailureTests {

        @Test
        @DisplayName("logs error with port number and remediation advice")
        void logsErrorWithPortAndRemediation() {
            serverManager.notifyBindFailure(61169);

            verify(logger).error(contains("61169"));
            verify(logger).error(contains("already in use"));
            verify(logger).error(contains("choose another port"));
        }

        @Test
        @DisplayName("shows popup notification with actionable message")
        void showsPopupNotification() {
            serverManager.notifyBindFailure(8080);

            verify(host).showPopupNotification(contains("8080"));
            verify(host).showPopupNotification(contains("already in use"));
        }

        @Test
        @DisplayName("message includes Bitwig Preferences path for user guidance")
        void messageIncludesPreferencesPath() {
            serverManager.notifyBindFailure(61169);

            verify(host).showPopupNotification(contains("Bitwig Preferences"));
            verify(host).showPopupNotification(contains("Network Settings"));
        }
    }

    @Nested
    @DisplayName("stopServer")
    class StopServerTests {

        @Test
        @DisplayName("returns false when server is not running")
        void returnsFalseWhenServerNotRunning() {
            // Server was never started, so stopServer should return false
            boolean result = serverManager.stopServer();

            assertFalse(result);
            verify(logger).info("WigAI Server is not running");
        }
    }

    @Nested
    @DisplayName("isRunning")
    class IsRunningTests {

        @Test
        @DisplayName("returns false when server was never started")
        void returnsFalseWhenNeverStarted() {
            assertFalse(serverManager.isRunning());
        }
    }
}
