package io.github.fabb.wigai.config;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import io.github.fabb.wigai.common.AppConstants;
import io.github.fabb.wigai.common.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Preferences-backed configuration manager for the WigAI extension.
 * Integrates with Bitwig Studio's Controller Preferences panel to provide
 * a user-friendly interface for configuring MCP server settings.
 */
public class PreferencesBackedConfigManager implements ConfigManager {
    private final Logger logger;
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
        // Note: Null updates are ignored as a defensive pattern. Bitwig string preferences
        // shouldn't send null values, but if they do, we keep the current valid host.
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
     * Canonicalizes accepted hosts to ensure consistent casing and stable URLs.
     *
     * @param host the host to validate
     * @return validated host (always a canonical loopback address)
     */
    private String validateHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            logger.warn("PreferencesBackedConfigManager: Invalid host '" + host + "', using 'localhost'");
            return "localhost";
        }
        String trimmedHost = host.trim();
        String canonicalHost = canonicalizeLoopback(trimmedHost);
        if (canonicalHost == null) {
            logger.warn("PreferencesBackedConfigManager: Rejected non-loopback host '" + trimmedHost +
                "'. WigAI MVP (no-auth) only allows localhost binding for security. Using 'localhost'.");
            return "localhost";
        }
        return canonicalHost;
    }

    /**
     * Canonicalizes a loopback address to its standard form.
     * Returns null if the host is not a recognized loopback address.
     *
     * <p>For "localhost", performs DNS resolution verification to ensure it only
     * resolves to loopback addresses (127.0.0.1, ::1). If localhost resolves to
     * any non-loopback address (misconfigured DNS), falls back to 127.0.0.1 for safety.
     *
     * @param host the host to canonicalize
     * @return canonical form ("localhost", "127.0.0.1", or "::1"), or null if not loopback
     */
    private String canonicalizeLoopback(String host) {
        if ("localhost".equalsIgnoreCase(host)) {
            // Verify localhost resolves only to loopback addresses
            if (verifyLocalhostResolvesToLoopback()) {
                return "localhost"; // Normalize casing (e.g., "LOCALHOST" -> "localhost")
            } else {
                // DNS misconfigured - localhost resolves to non-loopback; use explicit numeric loopback
                logger.warn("PreferencesBackedConfigManager: 'localhost' resolves to non-loopback address. " +
                    "Using '127.0.0.1' for safety. Check your system's hosts file or DNS configuration.");
                return "127.0.0.1";
            }
        }
        if ("127.0.0.1".equals(host)) {
            return "127.0.0.1";
        }
        if ("::1".equals(host)) {
            return "::1";
        }
        return null; // Not a loopback address
    }

    /**
     * Verifies that "localhost" resolves only to loopback addresses.
     *
     * <p>Checks all resolved addresses for localhost. If any address is not a loopback,
     * returns false. If resolution fails entirely, returns true conservatively
     * (DNS might be temporarily unavailable but localhost usually works).
     *
     * @return true if localhost resolves only to loopback addresses or if resolution fails
     */
    private boolean verifyLocalhostResolvesToLoopback() {
        try {
            InetAddress[] addresses = InetAddress.getAllByName("localhost");
            for (InetAddress addr : addresses) {
                if (!addr.isLoopbackAddress()) {
                    logger.warn("PreferencesBackedConfigManager: 'localhost' resolved to non-loopback: " +
                        addr.getHostAddress());
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            // Resolution failed - conservatively allow localhost (it usually works even if DNS is slow)
            logger.info("PreferencesBackedConfigManager: Could not resolve 'localhost', allowing it anyway");
            return true;
        }
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
