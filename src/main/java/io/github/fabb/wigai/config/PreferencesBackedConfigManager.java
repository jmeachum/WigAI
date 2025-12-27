package io.github.fabb.wigai.config;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import io.github.fabb.wigai.common.AppConstants;
import io.github.fabb.wigai.common.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Preferences-backed configuration manager for the WigAI extension.
 * Integrates with Bitwig Studio's Controller Preferences panel to provide
 * a user-friendly interface for configuring MCP server settings.
 */
public class PreferencesBackedConfigManager implements ConfigManager {
    private final Logger logger;
    private final ControllerHost host;
    private final List<ConfigChangeObserver> observers = new CopyOnWriteArrayList<>();

    private final SettableStringValue hostSetting;
    private final SettableRangedValue portSetting;

    private String currentHost;
    private int currentPort;

    /**
     * Creates a new PreferencesBackedConfigManager instance.
     *
     * @param logger The logger to use for logging configuration events
     * @param host   The Bitwig ControllerHost for accessing preferences
     */
    public PreferencesBackedConfigManager(Logger logger, ControllerHost host) {
        this.logger = logger;
        this.host = host;

        logger.info("PreferencesBackedConfigManager: Initializing with Bitwig preferences integration");

        // Get preferences instance
        Preferences preferences = host.getPreferences();

        // Create settings in "Network Settings" category
        this.hostSetting = preferences.getStringSetting(
            "MCP Host",
            "Network Settings",
            50,
            "localhost"
        );

        this.portSetting = preferences.getNumberSetting(
            "MCP Port",
            "Network Settings",
            1024.0,
            65535.0,
            1.0,
            "",
            (double) AppConstants.DEFAULT_MCP_PORT
        );

        // Initialize current values from settings with validation
        String persistedHost = hostSetting.get();
        int persistedPort = (int) portSetting.getRaw();

        this.currentHost = validateHost(persistedHost);
        this.currentPort = validatePort(persistedPort);

        // Write back sanitized values if they differ from persisted values.
        // SAFETY: This writeback occurs BEFORE setupChangeListeners(), so no observers
        // are registered yet and no restart notifications will be triggered.
        if (!this.currentHost.equals(persistedHost)) {
            hostSetting.set(this.currentHost);
            logger.info("PreferencesBackedConfigManager: Sanitized persisted host '" + persistedHost + "' to '" + this.currentHost + "'");
        }
        if (this.currentPort != persistedPort) {
            portSetting.set(this.currentPort);
            logger.info("PreferencesBackedConfigManager: Sanitized persisted port " + persistedPort + " to " + this.currentPort);
        }

        // Set up change listeners
        setupChangeListeners();

        logger.info("PreferencesBackedConfigManager: Initialized with host='" + currentHost + "', port=" + currentPort);
    }

    /**
     * Sets up change listeners for preferences settings.
     */
    private void setupChangeListeners() {
        // Host change listener
        hostSetting.addValueObserver(newHost -> {
            if (newHost != null && !newHost.equals(currentHost)) {
                String oldHost = currentHost;
                String validatedHost = validateHost(newHost);
                currentHost = validatedHost;
                // Write back corrected value to preferences if validation changed it
                if (!validatedHost.equals(newHost)) {
                    hostSetting.set(validatedHost);
                }
                // Only notify if there's an actual change after validation (avoid no-op restarts)
                if (!oldHost.equals(validatedHost)) {
                    notifyHostChanged(oldHost, currentHost);
                    logger.info("PreferencesBackedConfigManager: Host changed from '" + oldHost + "' to '" + currentHost + "'");
                }
            }
        });

        // Port change listener
        portSetting.addRawValueObserver(newPort -> {
            int newPortInt = (int) newPort;
            if (newPortInt != currentPort) {
                int oldPort = currentPort;
                int validatedPort = validatePort(newPortInt);
                currentPort = validatedPort;
                // Write back corrected value to preferences if validation changed it
                if (validatedPort != newPortInt) {
                    portSetting.set(validatedPort);
                }
                // Only notify if there's an actual change after validation (avoid no-op restarts)
                if (oldPort != validatedPort) {
                    notifyPortChanged(oldPort, currentPort);
                    logger.info("PreferencesBackedConfigManager: Port changed from " + oldPort + " to " + currentPort);
                }
            }
        });
    }

    /**
     * Validates and sanitizes host input.
     * Enforces loopback-only hosts for MVP (no-auth) security.
     *
     * @param host the host to validate
     * @return validated host (always a loopback address)
     */
    private String validateHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            logger.warn("PreferencesBackedConfigManager: Invalid host '" + host + "', using 'localhost'");
            return "localhost";
        }
        String trimmedHost = host.trim();
        if (!isLoopbackAddress(trimmedHost)) {
            logger.warn("PreferencesBackedConfigManager: Rejected non-loopback host '" + trimmedHost +
                "'. WigAI MVP (no-auth) only allows localhost binding for security. Using 'localhost'.");
            return "localhost";
        }
        return trimmedHost;
    }

    /**
     * Checks if the given host is a loopback address.
     * Allowed values: localhost, 127.0.0.1, ::1
     */
    private boolean isLoopbackAddress(String host) {
        return "localhost".equalsIgnoreCase(host) ||
               "127.0.0.1".equals(host) ||
               "::1".equals(host);
    }

    /**
     * Validates and sanitizes port input.
     */
    private int validatePort(int port) {
        if (port < 1024 || port > 65535) {
            logger.warn("PreferencesBackedConfigManager: Invalid port " + port + ", using default " + AppConstants.DEFAULT_MCP_PORT);
            return AppConstants.DEFAULT_MCP_PORT;
        }
        return port;
    }

    /**
     * Gets the configured MCP server host.
     *
     * @return The MCP server host
     */
    @Override
    public String getMcpHost() {
        return currentHost;
    }

    /**
     * Gets the configured MCP server port.
     *
     * @return The MCP server port
     */
    @Override
    public int getMcpPort() {
        return currentPort;
    }

    /**
     * Sets the MCP server host.
     * This will update the preferences UI and trigger change notifications.
     *
     * @param host The host to use for the MCP server
     */
    @Override
    public void setMcpHost(String host) {
        String validatedHost = validateHost(host);
        hostSetting.set(validatedHost);
        // The change listener will handle the rest
    }

    /**
     * Sets the MCP server port.
     * This will update the preferences UI and trigger change notifications.
     *
     * @param port The port to use for the MCP server
     */
    @Override
    public void setMcpPort(int port) {
        int validatedPort = validatePort(port);
        portSetting.set(validatedPort);
        // The change listener will handle the rest
    }

    /**
     * Adds an observer to be notified when configuration changes.
     *
     * @param observer The observer to add
     */
    @Override
    public void addObserver(ConfigChangeObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            logger.info("PreferencesBackedConfigManager: Added config change observer: " + observer.getClass().getSimpleName());
        }
    }

    /**
     * Notifies all observers that the host has changed.
     */
    private void notifyHostChanged(String oldHost, String newHost) {
        for (ConfigChangeObserver observer : observers) {
            try {
                observer.onHostChanged(oldHost, newHost);
            } catch (Exception e) {
                logger.error("PreferencesBackedConfigManager: Error notifying observer of host change", e);
            }
        }
    }

    /**
     * Notifies all observers that the port has changed.
     */
    private void notifyPortChanged(int oldPort, int newPort) {
        for (ConfigChangeObserver observer : observers) {
            try {
                observer.onPortChanged(oldPort, newPort);
            } catch (Exception e) {
                logger.error("PreferencesBackedConfigManager: Error notifying observer of port change", e);
            }
        }
    }
}
