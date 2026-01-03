package io.github.fabb.wigai.server;

import com.bitwig.extension.controller.api.ControllerHost;
import io.github.fabb.wigai.WigAIExtensionDefinition;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.config.ConfigManager;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.net.BindException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-safe unit tests for JettyServerManager bind failure handling.
 */
// TODO (TEA Review): Split this test class into smaller focused files (<300 lines). See test-review-1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md
class JettyServerManagerBindFailureTest {

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
