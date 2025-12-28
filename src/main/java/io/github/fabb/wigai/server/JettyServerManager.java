package io.github.fabb.wigai.server;

import com.bitwig.extension.controller.api.ControllerHost;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.config.ConfigManager;
import io.github.fabb.wigai.WigAIExtensionDefinition;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.BindException;

/**
 * Manages the Jetty server lifecycle and configuration.
 * Handles starting, stopping, and restarting the Jetty server with proper error handling.
 */
public class JettyServerManager {
    private final Logger logger;
    private final ConfigManager configManager;
    private final WigAIExtensionDefinition extensionDefinition;
    private final ControllerHost host;

    // Jetty server management
    private Server jettyServer;
    private ServletContextHandler contextHandler;
    private String currentEndpointPath;

    /**
     * Creates a new JettyServerManager instance.
     *
     * @param logger The logger instance
     * @param configManager The configuration manager
     * @param extensionDefinition The extension definition for version info
     * @param host The Bitwig ControllerHost for popup notifications
     */
    public JettyServerManager(Logger logger, ConfigManager configManager, WigAIExtensionDefinition extensionDefinition, ControllerHost host) {
        this.logger = logger;
        this.configManager = configManager;
        this.extensionDefinition = extensionDefinition;
        this.host = host;
    }

    /**
     * Starts the Jetty server with the current configuration and registers the provided servlet.
     *
     * @param mcpServlet The MCP servlet to register, or null to start without servlet
     * @param endpointPath The endpoint path for the servlet, or null if no servlet provided
     * @throws Exception if the server fails to start
     */
    public void startServer(ServletHolder mcpServlet, String endpointPath) throws Exception {
        if (jettyServer != null && jettyServer.isRunning()) {
            logger.info("WigAI Server is already running");
            return;
        }

        // Create and configure Jetty server
        jettyServer = new Server();
        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setHost(configManager.getMcpHost());
        connector.setPort(configManager.getMcpPort());
        jettyServer.addConnector(connector);

        // Create servlet context handler
        contextHandler = new ServletContextHandler();
        contextHandler.setContextPath("/");
        jettyServer.setHandler(contextHandler);

        // Register servlet if provided
        if (mcpServlet != null && endpointPath != null) {
            contextHandler.addServlet(mcpServlet, endpointPath);
            this.currentEndpointPath = endpointPath;
        }

        // Start the Jetty server
        try {
            jettyServer.start();
        } catch (BindException e) {
            cleanupFailedServer();
            notifyBindFailure(configManager.getMcpPort());
            throw e;
        } catch (Exception e) {
            cleanupFailedServer();
            // Check if root cause is BindException (including suppressed exceptions for Jetty MultiException)
            if (containsBindException(e)) {
                notifyBindFailure(configManager.getMcpPort());
            }
            throw e;
        }

        notifyServerStarted();
    }

    /**
     * Cleans up server state after a failed start attempt.
     * Stops any partially initialized server and resets references to avoid leaks.
     */
    private void cleanupFailedServer() {
        if (jettyServer != null) {
            try {
                jettyServer.stop();
                jettyServer.destroy();
            } catch (Exception e) {
                logger.error("Error during server cleanup after failed start", e);
            }
            jettyServer = null;
            contextHandler = null;
        }
    }

    /**
     * Recursively checks if the exception or any of its causes/suppressed exceptions is a BindException.
     * Handles Jetty MultiException which stores failures in suppressed exceptions.
     *
     * <p><b>Visibility Note:</b> Package-private visibility is intentional to allow unit testing
     * of bind failure detection logic without starting actual servers. Tests verify this method
     * directly rather than through integration tests requiring port binding.
     *
     * @param e the throwable to inspect (may be null)
     * @return true if a BindException is found anywhere in the exception tree
     */
    boolean containsBindException(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof BindException) {
            return true;
        }
        // Check cause chain
        if (containsBindException(e.getCause())) {
            return true;
        }
        // Check suppressed exceptions (for Jetty MultiException behavior)
        for (Throwable suppressed : e.getSuppressed()) {
            if (containsBindException(suppressed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notifies the user that the port could not be bound.
     *
     * <p><b>Visibility Note:</b> Package-private visibility is intentional to allow unit testing
     * of bind failure notification behavior without starting actual servers.
     *
     * @param port the port that could not be bound
     */
    void notifyBindFailure(int port) {
        String message = String.format(
            "WigAI: Port %d is already in use. Please choose another port in Bitwig Preferences → WigAI → Network Settings.",
            port);
        logger.error(message);

        try {
            host.showPopupNotification(message);
        } catch (Exception e) {
            logger.error("WigAI Extension: Error showing bind failure notification", e);
        }
    }

    /**
     * Stops the Jetty server and all servlets.
     */
    public void stopServer() {
        if (jettyServer == null || !jettyServer.isRunning()) {
            logger.info("WigAI Server is not running");
            return;
        }

        try {
            logger.info("Stopping Jetty server");
            jettyServer.stop();
            notifyServerStopped();
        } catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            String fullStackTrace = stringWriter.toString();

            logger.error("Error stopping WigAI Server\n" + fullStackTrace);
        }
    }

    /**
     * Gracefully restarts the server with new configuration and registers the provided servlet.
     *
     * <p>Uses Jetty's synchronous stop() which properly closes connectors and releases ports.
     * No artificial delay is needed since stop() waits for the server to fully shut down.
     *
     * @param mcpServlet The MCP servlet to register, or null to restart without servlet
     * @param endpointPath The endpoint path for the servlet, or null if no servlet provided
     * @throws Exception if the server fails to restart
     */
    public void restartServer(ServletHolder mcpServlet, String endpointPath) throws Exception {
        logger.info("WigAI Extension: Beginning graceful server restart");

        // Stop the current server if running
        // Jetty's stop() is synchronous and waits for connectors to close properly
        if (jettyServer != null && jettyServer.isRunning()) {
            logger.info("WigAI Extension: Stopping current server for restart");
            stopServer();
        }

        // Start the server with new configuration
        logger.info("WigAI Extension: Starting server with updated configuration");
        startServer(mcpServlet, endpointPath);

        logger.info("WigAI Extension: Server restart completed successfully");
    }

    /**
     * Checks if the Jetty server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return jettyServer != null && jettyServer.isRunning();
    }

    /**
     * Gets the current ServletContextHandler.
     *
     * @return The current ServletContextHandler, or null if server is not running
     */
    public ServletContextHandler getContextHandler() {
        return contextHandler;
    }

    /**
     * Notifies that the server started successfully.
     */
    private void notifyServerStarted() {
        String endpointPath = currentEndpointPath != null ? currentEndpointPath : "";
        String connectionUrl = String.format("http://%s:%d%s",
            configManager.getMcpHost(), configManager.getMcpPort(), endpointPath);
        String message = String.format("WigAI MCP Server v%s started. Connect AI agents to: %s",
            extensionDefinition.getVersion(), connectionUrl);
        logger.info(message);

        try {
            host.showPopupNotification(message);
        } catch (Exception e) {
            logger.error("WigAI Extension: Error showing startup notification", e);
        }
    }

    /**
     * Notifies that the server stopped.
     */
    private void notifyServerStopped() {
        String message = String.format("WigAI MCP Server v%s stopped", extensionDefinition.getVersion());
        logger.info(message);

        try {
            host.showPopupNotification(message);
        } catch (Exception e) {
            logger.error("WigAI Extension: Error showing stop notification", e);
        }
    }
}
