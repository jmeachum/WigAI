package io.github.fabb.wigai.server;

import com.bitwig.extension.controller.api.ControllerHost;
import io.github.fabb.wigai.WigAIExtensionDefinition;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.config.ConfigManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CI-safe unit tests for JettyServerManager host normalization and URL formatting.
 */
class JettyServerManagerUrlFormattingTest {

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
        @DisplayName("trims whitespace from localhost")
        void trimsWhitespaceFromLocalhost() {
            assertEquals("127.0.0.1", serverManager.getBindHost("  localhost  "));
        }

        @Test
        @DisplayName("trims whitespace from IPv4 loopback")
        void trimsWhitespaceFromIpv4Loopback() {
            assertEquals("127.0.0.1", serverManager.getBindHost("  127.0.0.1  "));
        }

        @Test
        @DisplayName("trims whitespace from IPv6 loopback")
        void trimsWhitespaceFromIpv6Loopback() {
            assertEquals("::1", serverManager.getBindHost("  ::1  "));
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
    @DisplayName("advertised connection URL (AC1)")
    class AdvertisedConnectionUrlTests {

        @Test
        @DisplayName("advertised URL uses actual bind host (127.0.0.1) when localhost is configured")
        void advertisedUrlUsesBindHostForLocalhost() {
            // notifyServerStarted now uses getBindHost() to ensure advertised URL matches actual binding.
            // When localhost is configured, we bind to 127.0.0.1 (deterministic), so URL should show 127.0.0.1.
            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(61169);

            // Simulate the URL construction in notifyServerStarted:
            // actualBindHost = getBindHost(configManager.getMcpHost());
            // formattedHost = formatHostForUrl(actualBindHost);
            String actualBindHost = serverManager.getBindHost(configManager.getMcpHost());
            String formattedHost = serverManager.formatHostForUrl(actualBindHost);
            int port = configManager.getMcpPort();
            String endpointPath = "/mcp";

            String advertisedUrl = String.format("http://%s:%d%s", formattedHost, port, endpointPath);
            // Expect 127.0.0.1 (not localhost) to avoid IPv6/IPv4 mismatch
            assertEquals("http://127.0.0.1:61169/mcp", advertisedUrl);
        }

        @Test
        @DisplayName("advertised URL matches bind host for 127.0.0.1")
        void advertisedUrlMatchesBindHostForIpv4Loopback() {
            when(configManager.getMcpHost()).thenReturn("127.0.0.1");
            when(configManager.getMcpPort()).thenReturn(61169);

            String actualBindHost = serverManager.getBindHost(configManager.getMcpHost());
            String formattedHost = serverManager.formatHostForUrl(actualBindHost);
            int port = configManager.getMcpPort();
            String endpointPath = "/mcp";

            String advertisedUrl = String.format("http://%s:%d%s", formattedHost, port, endpointPath);
            assertEquals("http://127.0.0.1:61169/mcp", advertisedUrl);
        }

        @Test
        @DisplayName("advertised URL uses IPv6 brackets for ::1")
        void advertisedUrlUsesIpv6BracketsForIpv6Loopback() {
            when(configManager.getMcpHost()).thenReturn("::1");
            when(configManager.getMcpPort()).thenReturn(61169);

            String actualBindHost = serverManager.getBindHost(configManager.getMcpHost());
            String formattedHost = serverManager.formatHostForUrl(actualBindHost);
            int port = configManager.getMcpPort();
            String endpointPath = "/mcp";

            String advertisedUrl = String.format("http://%s:%d%s", formattedHost, port, endpointPath);
            assertEquals("http://[::1]:61169/mcp", advertisedUrl);
        }

        @Test
        @DisplayName("advertised URL uses custom port with bind host")
        void advertisedUrlUsesCustomPortWithBindHost() {
            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(8080);

            String actualBindHost = serverManager.getBindHost(configManager.getMcpHost());
            String formattedHost = serverManager.formatHostForUrl(actualBindHost);
            int port = configManager.getMcpPort();
            String endpointPath = "/mcp";

            String advertisedUrl = String.format("http://%s:%d%s", formattedHost, port, endpointPath);
            // Expect 127.0.0.1 (not localhost) because getBindHost normalizes localhost
            assertEquals("http://127.0.0.1:8080/mcp", advertisedUrl);
        }

        @Test
        @DisplayName("advertised URL always matches actual bind address (defense-in-depth)")
        void advertisedUrlAlwaysMatchesBindAddress() {
            // This test verifies the fix for IPv6/IPv4 mismatch issue:
            // The advertised URL should always use the actual bind address (from getBindHost),
            // not the configured host, to prevent unreachable URLs on systems where
            // localhost resolves to a different address family than what we bind to.

            // localhost -> binds to 127.0.0.1, advertises 127.0.0.1
            assertEquals("127.0.0.1", serverManager.getBindHost("localhost"));
            assertEquals("127.0.0.1", serverManager.formatHostForUrl(serverManager.getBindHost("localhost")));

            // LOCALHOST (uppercase) -> binds to 127.0.0.1, advertises 127.0.0.1
            assertEquals("127.0.0.1", serverManager.getBindHost("LOCALHOST"));
            assertEquals("127.0.0.1", serverManager.formatHostForUrl(serverManager.getBindHost("LOCALHOST")));

            // 127.0.0.1 -> binds to 127.0.0.1, advertises 127.0.0.1
            assertEquals("127.0.0.1", serverManager.getBindHost("127.0.0.1"));
            assertEquals("127.0.0.1", serverManager.formatHostForUrl(serverManager.getBindHost("127.0.0.1")));

            // ::1 -> binds to ::1, advertises [::1] (bracketed for URL)
            assertEquals("::1", serverManager.getBindHost("::1"));
            assertEquals("[::1]", serverManager.formatHostForUrl(serverManager.getBindHost("::1")));
        }
    }

    @Nested
    @DisplayName("notifyServerStarted logging/notification (AC1)")
    class NotifyServerStartedTests {

        @Test
        @DisplayName("logs and shows popup with correct URL when server starts with localhost")
        void logsCorrectUrlForLocalhost() throws Exception {
            // Arrange: Mock config to return localhost:61169
            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(61169);
            when(extensionDefinition.getVersion()).thenReturn("0.4.0");

            // Use spy on real Server to allow bean registration while controlling start() behavior
            Server realServer = new Server();
            Server spyServer = spy(realServer);
            doNothing().when(spyServer).start(); // Prevent actual server start

            JettyServerManager testableManager = new JettyServerManager(logger, configManager, extensionDefinition, host) {
                @Override
                protected Server createServer() {
                    return spyServer;
                }
            };

            // Act: Start server with servlet + endpoint path so URL includes /mcp
            ServletHolder mockServlet = mock(ServletHolder.class);
            testableManager.startServer(mockServlet, "/mcp");

            // Assert: notifyServerStarted logs/shows URL with actual bind host (127.0.0.1, not localhost)
            // This verifies the fix for IPv6/IPv4 mismatch issue
            verify(logger).info(contains("http://127.0.0.1:61169/mcp"));
            verify(host).showPopupNotification(contains("http://127.0.0.1:61169/mcp"));
        }

        @Test
        @DisplayName("logs and shows popup with bracketed IPv6 for ::1")
        void logsCorrectUrlForIpv6() throws Exception {
            // Arrange: Mock config to return ::1:61169
            when(configManager.getMcpHost()).thenReturn("::1");
            when(configManager.getMcpPort()).thenReturn(61169);
            when(extensionDefinition.getVersion()).thenReturn("0.4.0");

            Server realServer = new Server();
            Server spyServer = spy(realServer);
            doNothing().when(spyServer).start();

            JettyServerManager testableManager = new JettyServerManager(logger, configManager, extensionDefinition, host) {
                @Override
                protected Server createServer() {
                    return spyServer;
                }
            };

            // Act: Start server with servlet + endpoint path
            ServletHolder mockServlet = mock(ServletHolder.class);
            testableManager.startServer(mockServlet, "/mcp");

            // Assert: IPv6 address is bracketed in URL
            verify(logger).info(contains("http://[::1]:61169/mcp"));
            verify(host).showPopupNotification(contains("http://[::1]:61169/mcp"));
        }

        @Test
        @DisplayName("logs and shows popup with custom port")
        void logsCorrectUrlForCustomPort() throws Exception {
            // Arrange: Mock config to return localhost:8080
            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(8080);
            when(extensionDefinition.getVersion()).thenReturn("0.4.0");

            Server realServer = new Server();
            Server spyServer = spy(realServer);
            doNothing().when(spyServer).start();

            JettyServerManager testableManager = new JettyServerManager(logger, configManager, extensionDefinition, host) {
                @Override
                protected Server createServer() {
                    return spyServer;
                }
            };

            // Act: Start server with servlet + endpoint path
            ServletHolder mockServlet = mock(ServletHolder.class);
            testableManager.startServer(mockServlet, "/mcp");

            // Assert: Custom port is used in URL, with actual bind host (127.0.0.1)
            verify(logger).info(contains("http://127.0.0.1:8080/mcp"));
            verify(host).showPopupNotification(contains("http://127.0.0.1:8080/mcp"));
        }

        @Test
        @DisplayName("includes version number in startup notification")
        void includesVersionInNotification() throws Exception {
            when(configManager.getMcpHost()).thenReturn("localhost");
            when(configManager.getMcpPort()).thenReturn(61169);
            when(extensionDefinition.getVersion()).thenReturn("0.5.0-beta");

            Server realServer = new Server();
            Server spyServer = spy(realServer);
            doNothing().when(spyServer).start();

            JettyServerManager testableManager = new JettyServerManager(logger, configManager, extensionDefinition, host) {
                @Override
                protected Server createServer() {
                    return spyServer;
                }
            };

            // Start with servlet + endpoint path
            ServletHolder mockServlet = mock(ServletHolder.class);
            testableManager.startServer(mockServlet, "/mcp");

            verify(logger).info(contains("v0.5.0-beta"));
            verify(host).showPopupNotification(contains("v0.5.0-beta"));
        }
    }
}
