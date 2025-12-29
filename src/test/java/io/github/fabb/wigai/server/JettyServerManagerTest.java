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

import org.eclipse.jetty.server.Server;

import java.lang.reflect.Field;
import java.net.BindException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

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
    @DisplayName("getBindHost (defense-in-depth loopback enforcement)")
    class GetBindHostTests {

        @Test
        @DisplayName("returns 127.0.0.1 for localhost (deterministic binding)")
        void returnsNumericLoopbackForLocalhost() {
            assertEquals("127.0.0.1", serverManager.getBindHost("localhost"));
        }

        @Test
        @DisplayName("returns 127.0.0.1 for LOCALHOST (case-insensitive)")
        void returnsNumericLoopbackForUppercaseLocalhost() {
            assertEquals("127.0.0.1", serverManager.getBindHost("LOCALHOST"));
        }

        @Test
        @DisplayName("returns 127.0.0.1 for LocalHost (mixed case)")
        void returnsNumericLoopbackForMixedCaseLocalhost() {
            assertEquals("127.0.0.1", serverManager.getBindHost("LocalHost"));
        }

        @Test
        @DisplayName("returns 127.0.0.1 unchanged")
        void returnsIpv4LoopbackUnchanged() {
            assertEquals("127.0.0.1", serverManager.getBindHost("127.0.0.1"));
        }

        @Test
        @DisplayName("returns ::1 unchanged")
        void returnsIpv6LoopbackUnchanged() {
            assertEquals("::1", serverManager.getBindHost("::1"));
        }

        @Test
        @DisplayName("returns 127.0.0.1 for null (with warning)")
        void returnsLoopbackForNullWithWarning() {
            assertEquals("127.0.0.1", serverManager.getBindHost(null));
            verify(logger).warn(contains("Null host configured"));
        }

        @Test
        @DisplayName("throws for non-loopback host 0.0.0.0")
        void throwsForWildcardAddress() {
            try {
                serverManager.getBindHost("0.0.0.0");
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("non-loopback"));
                assertTrue(e.getMessage().contains("0.0.0.0"));
            }
        }

        @Test
        @DisplayName("throws for non-loopback IP address")
        void throwsForNonLoopbackIp() {
            try {
                serverManager.getBindHost("192.168.1.100");
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("non-loopback"));
                verify(logger).error(contains("Refusing to bind"));
            }
        }

        @Test
        @DisplayName("throws for public hostname")
        void throwsForPublicHostname() {
            try {
                serverManager.getBindHost("example.com");
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("non-loopback"));
            }
        }

        @Test
        @DisplayName("trims whitespace from host")
        void trimsWhitespaceFromHost() {
            assertEquals("127.0.0.1", serverManager.getBindHost("  localhost  "));
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

        @Test
        @DisplayName("does NOT bracket arbitrary string with colons (not IPv6)")
        void doesNotBracketArbitraryStringWithColons() {
            // "host:with:colons" contains colons but is not a valid IPv6 literal
            assertEquals("host:with:colons", serverManager.formatHostForUrl("host:with:colons"));
        }

        @Test
        @DisplayName("does NOT bracket port-like string with single colon")
        void doesNotBracketPortLikeString() {
            // "localhost:8080" is not an IPv6 address
            assertEquals("localhost:8080", serverManager.formatHostForUrl("localhost:8080"));
        }

        @Test
        @DisplayName("wraps IPv4-mapped IPv6 address in brackets")
        void wrapsIpv4MappedIpv6InBrackets() {
            // ::ffff:192.168.1.1 is a valid IPv4-mapped IPv6 address
            assertEquals("[::ffff:192.168.1.1]", serverManager.formatHostForUrl("::ffff:192.168.1.1"));
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
    @DisplayName("advertised connection URL (AC1)")
    class AdvertisedConnectionUrlTests {

        @Test
        @DisplayName("builds correct URL for localhost with default port")
        void buildsCorrectUrlForLocalhost() {
            // The URL construction logic in notifyServerStarted uses formatHostForUrl + getMcpHost/Port
            // Test the URL format by verifying the components
            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(61169);

            String host = serverManager.formatHostForUrl(configManager.getMcpHost());
            int port = configManager.getMcpPort();
            String endpointPath = "/mcp";

            String expectedUrl = String.format("http://%s:%d%s", host, port, endpointPath);
            assertEquals("http://localhost:61169/mcp", expectedUrl);
        }

        @Test
        @DisplayName("builds correct URL for 127.0.0.1")
        void buildsCorrectUrlForIpv4Loopback() {
            when(configManager.getMcpHost()).thenReturn("127.0.0.1");
            when(configManager.getMcpPort()).thenReturn(61169);

            String host = serverManager.formatHostForUrl(configManager.getMcpHost());
            int port = configManager.getMcpPort();
            String endpointPath = "/mcp";

            String expectedUrl = String.format("http://%s:%d%s", host, port, endpointPath);
            assertEquals("http://127.0.0.1:61169/mcp", expectedUrl);
        }

        @Test
        @DisplayName("builds correct URL for IPv6 loopback ::1 (bracketed)")
        void buildsCorrectUrlForIpv6Loopback() {
            when(configManager.getMcpHost()).thenReturn("::1");
            when(configManager.getMcpPort()).thenReturn(61169);

            String host = serverManager.formatHostForUrl(configManager.getMcpHost());
            int port = configManager.getMcpPort();
            String endpointPath = "/mcp";

            String expectedUrl = String.format("http://%s:%d%s", host, port, endpointPath);
            assertEquals("http://[::1]:61169/mcp", expectedUrl);
        }

        @Test
        @DisplayName("builds correct URL for custom port")
        void buildsCorrectUrlForCustomPort() {
            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(8080);

            String host = serverManager.formatHostForUrl(configManager.getMcpHost());
            int port = configManager.getMcpPort();
            String endpointPath = "/mcp";

            String expectedUrl = String.format("http://%s:%d%s", host, port, endpointPath);
            assertEquals("http://localhost:8080/mcp", expectedUrl);
        }

        @Test
        @DisplayName("URL matches format expected by AC1: http://{loopback}:{port}/mcp")
        void urlMatchesAc1Format() {
            // AC1 specifies: advertises the configured loopback host in logs/notification
            // (e.g., http://localhost:61169/mcp or http://[::1]:61169/mcp)

            // Test localhost variant
            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(61169);
            String url1 = String.format("http://%s:%d/mcp",
                serverManager.formatHostForUrl("localhost"), 61169);
            assertTrue(url1.matches("http://localhost:\\d+/mcp"));

            // Test IPv6 variant
            String url2 = String.format("http://%s:%d/mcp",
                serverManager.formatHostForUrl("::1"), 61169);
            assertTrue(url2.matches("http://\\[::1\\]:\\d+/mcp"));

            // Test IPv4 variant
            String url3 = String.format("http://%s:%d/mcp",
                serverManager.formatHostForUrl("127.0.0.1"), 61169);
            assertTrue(url3.matches("http://127\\.0\\.0\\.1:\\d+/mcp"));
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

    @Nested
    @DisplayName("startServer stale state cleanup")
    class StartServerStaleStateTests {

        @Test
        @DisplayName("cleans up stale server state when server exists but not running")
        void cleansUpStaleStateWhenServerExistsButNotRunning() throws Exception {
            // Arrange: Create a mock server that simulates stale state (exists but not running)
            Server mockStaleServer = mock(Server.class);
            when(mockStaleServer.isRunning()).thenReturn(false);

            // Make configManager throw to fail BEFORE Jetty.start() (CI-safety: no actual port binding)
            when(configManager.getMcpHost()).thenThrow(new RuntimeException("Test: fail fast before Jetty start"));

            // Inject the mock server via reflection to simulate stale state
            Field jettyServerField = JettyServerManager.class.getDeclaredField("jettyServer");
            jettyServerField.setAccessible(true);
            jettyServerField.set(serverManager, mockStaleServer);

            // Act: Attempt to start server - cleanup happens BEFORE getMcpHost() is called
            try {
                serverManager.startServer(null, null);
                fail("Expected exception from getMcpHost()");
            } catch (RuntimeException e) {
                assertEquals("Test: fail fast before Jetty start", e.getMessage());
            }

            // Assert: Verify stale state cleanup occurred before the failure
            verify(mockStaleServer).isRunning(); // Checked running state
            verify(mockStaleServer).destroy();   // Destroyed the stale server
            verify(logger).info("Cleaning up stale server state before starting");
        }

        @Test
        @DisplayName("does not log stale state cleanup on fresh start")
        void doesNotLogStaleStateCleanupOnFreshStart() throws Exception {
            // Arrange: Fresh start with no prior server state
            assertNull(getJettyServerField());

            // Make configManager throw to fail BEFORE Jetty.start() (CI-safety: no actual port binding)
            when(configManager.getMcpHost()).thenThrow(new RuntimeException("Test: fail fast before Jetty start"));

            // Act: Attempt to start (will fail due to getMcpHost() throwing)
            try {
                serverManager.startServer(null, null);
                fail("Expected exception from getMcpHost()");
            } catch (RuntimeException e) {
                assertEquals("Test: fail fast before Jetty start", e.getMessage());
            }

            // Assert: No stale state cleanup message logged
            verify(logger, never()).info("Cleaning up stale server state before starting");
        }

        @Test
        @DisplayName("skips cleanup when server is already running")
        void skipsCleanupWhenServerAlreadyRunning() throws Exception {
            // Arrange: Mock a running server
            Server mockRunningServer = mock(Server.class);
            when(mockRunningServer.isRunning()).thenReturn(true);

            Field jettyServerField = JettyServerManager.class.getDeclaredField("jettyServer");
            jettyServerField.setAccessible(true);
            jettyServerField.set(serverManager, mockRunningServer);

            // Act: Attempt to start server
            boolean result = serverManager.startServer(null, null);

            // Assert: Server was already running, no cleanup or new start attempted
            assertFalse(result);
            verify(mockRunningServer, never()).destroy();
            verify(logger).info("WigAI Server is already running");
            verify(logger, never()).info("Cleaning up stale server state before starting");
        }

        private Server getJettyServerField() throws Exception {
            Field jettyServerField = JettyServerManager.class.getDeclaredField("jettyServer");
            jettyServerField.setAccessible(true);
            return (Server) jettyServerField.get(serverManager);
        }
    }

    @Nested
    @DisplayName("startServer bind failure behavioral flow (AC5)")
    class StartServerBindFailureFlowTests {

        /**
         * Tests the behavioral flow of startServer when bind failures occur.
         *
         * Due to Jetty's complex lifecycle management (ServerConnector requires a real Server
         * for bean registration), we cannot easily mock the Server class directly. Instead,
         * we verify the integration through:
         * 1. Direct tests of containsBindException() - 9 tests verifying detection logic
         * 2. Direct tests of notifyBindFailure() - 3 tests verifying notification behavior
         * 3. Tests of cleanupFailedServer() state management
         * 4. Integration verification through code inspection
         */

        @Test
        @DisplayName("cleanupFailedServer clears state when called after failure")
        void cleanupFailedServerClearsState() throws Exception {
            // Arrange: Set up some server state via reflection
            Server mockServer = mock(Server.class);
            Field jettyServerField = JettyServerManager.class.getDeclaredField("jettyServer");
            jettyServerField.setAccessible(true);
            jettyServerField.set(serverManager, mockServer);

            // Act: Call cleanupFailedServer
            serverManager.cleanupFailedServer();

            // Assert: State should be cleared
            assertNull(jettyServerField.get(serverManager));
            verify(mockServer).destroy();
        }

        @Test
        @DisplayName("cleanupFailedServer handles null server gracefully")
        void cleanupFailedServerHandlesNullServer() {
            // Act: Call cleanupFailedServer when no server exists
            serverManager.cleanupFailedServer();

            // Assert: Should not throw, logger should not log errors
            verify(logger, never()).error(anyString());
        }

        @Test
        @DisplayName("bind failure detection correctly identifies BindException in exception chain")
        void bindFailureDetectionWorksWithExceptionChain() {
            // Direct BindException
            BindException directBind = new BindException("Address already in use");
            assertTrue(serverManager.containsBindException(directBind));

            // BindException in cause chain
            RuntimeException wrapped = new RuntimeException("Server failed", directBind);
            assertTrue(serverManager.containsBindException(wrapped));

            // BindException in suppressed (Jetty MultiException pattern)
            Exception multi = new Exception("Multiple failures");
            multi.addSuppressed(new BindException("Port in use"));
            assertTrue(serverManager.containsBindException(multi));

            // Non-BindException should not trigger notification
            assertFalse(serverManager.containsBindException(new IllegalStateException("Other error")));
        }

        @Test
        @DisplayName("notifyBindFailure produces correct user feedback for AC5")
        void notifyBindFailureProducesCorrectFeedback() {
            // Act: Call notifyBindFailure with specific port
            serverManager.notifyBindFailure(61169);

            // Assert: Verify logger.error is called with port and remediation advice
            verify(logger).error(contains("61169"));
            verify(logger).error(contains("already in use"));
            verify(logger).error(contains("choose another port"));

            // Assert: Verify popup notification is shown to user
            verify(host).showPopupNotification(contains("61169"));
            verify(host).showPopupNotification(contains("Bitwig Preferences"));
        }

        @Test
        @DisplayName("startServer calls notifyBindFailure when direct BindException is thrown")
        void startServerCallsNotifyBindFailureOnDirectBindException() throws Exception {
            // Arrange: Use a spy on a real Server so ServerConnector can register beans
            Server realServer = new Server();
            Server spyServer = spy(realServer);
            doThrow(new BindException("Address already in use")).when(spyServer).start();

            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(61169);

            JettyServerManager testableManager = new JettyServerManager(logger, configManager, extensionDefinition, host) {
                @Override
                protected Server createServer() {
                    return spyServer;
                }
            };

            // Act & Assert: startServer should throw BindException and call notifyBindFailure
            try {
                testableManager.startServer(null, null);
                fail("Expected BindException to be thrown");
            } catch (BindException e) {
                assertEquals("Address already in use", e.getMessage());
            }

            // Verify notifyBindFailure was called (evidence: logger.error with port)
            verify(logger).error(contains("61169"));
            verify(logger).error(contains("already in use"));
            verify(host).showPopupNotification(contains("61169"));
        }

        @Test
        @DisplayName("startServer calls notifyBindFailure when BindException is wrapped in MultiException")
        void startServerCallsNotifyBindFailureOnWrappedBindException() throws Exception {
            // Arrange: Use a spy to simulate Jetty MultiException behavior (BindException in suppressed)
            Server realServer = new Server();
            Server spyServer = spy(realServer);
            Exception multiException = new Exception("Multiple failures");
            multiException.addSuppressed(new BindException("Port in use"));
            doThrow(multiException).when(spyServer).start();

            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(8080);

            JettyServerManager testableManager = new JettyServerManager(logger, configManager, extensionDefinition, host) {
                @Override
                protected Server createServer() {
                    return spyServer;
                }
            };

            // Act & Assert: startServer should throw and call notifyBindFailure
            try {
                testableManager.startServer(null, null);
                fail("Expected exception to be thrown");
            } catch (Exception e) {
                assertEquals("Multiple failures", e.getMessage());
            }

            // Verify notifyBindFailure was called for wrapped BindException
            verify(logger).error(contains("8080"));
            verify(host).showPopupNotification(contains("8080"));
        }

        @Test
        @DisplayName("startServer calls cleanupFailedServer on bind failure")
        void startServerCallsCleanupOnBindFailure() throws Exception {
            // Arrange: Use a spy on a real Server
            Server realServer = new Server();
            Server spyServer = spy(realServer);
            doThrow(new BindException("Address already in use")).when(spyServer).start();

            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(61169);

            JettyServerManager testableManager = new JettyServerManager(logger, configManager, extensionDefinition, host) {
                @Override
                protected Server createServer() {
                    return spyServer;
                }
            };

            // Act: startServer should fail
            try {
                testableManager.startServer(null, null);
            } catch (BindException expected) {
                // Expected
            }

            // Assert: Cleanup was called - verify server.destroy() was invoked
            verify(spyServer).destroy();
        }

        @Test
        @DisplayName("startServer does NOT call notifyBindFailure for non-bind exceptions")
        void startServerDoesNotCallNotifyBindFailureForOtherExceptions() throws Exception {
            // Arrange: Use a spy for non-BindException failure
            Server realServer = new Server();
            Server spyServer = spy(realServer);
            doThrow(new IllegalStateException("Some other error")).when(spyServer).start();

            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(61169);

            JettyServerManager testableManager = new JettyServerManager(logger, configManager, extensionDefinition, host) {
                @Override
                protected Server createServer() {
                    return spyServer;
                }
            };

            // Act: startServer should fail
            try {
                testableManager.startServer(null, null);
                fail("Expected IllegalStateException to be thrown");
            } catch (IllegalStateException e) {
                assertEquals("Some other error", e.getMessage());
            }

            // Assert: notifyBindFailure was NOT called (no "already in use" message)
            verify(logger, never()).error(contains("already in use"));
            verify(host, never()).showPopupNotification(contains("already in use"));
        }
    }
}
