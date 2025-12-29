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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-safe unit tests for JettyServerManager lifecycle behavior.
 */
class JettyServerManagerLifecycleTest {

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
}
