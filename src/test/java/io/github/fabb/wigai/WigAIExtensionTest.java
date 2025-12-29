package io.github.fabb.wigai;

import com.bitwig.extension.controller.api.ControllerHost;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.mcp.McpServerManager;
import io.github.fabb.wigai.server.JettyServerManager;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WigAIExtensionTest {

    @Mock
    private WigAIExtensionDefinition extensionDefinition;

    @Mock
    private ControllerHost host;

    private WigAIExtension extension;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        extension = new WigAIExtension(extensionDefinition, host);

        // Inject mocks to avoid calling init() and to keep tests CI-safe.
        setField(extension, "logger", mock(Logger.class));
        setField(extension, "mcpServerManager", mock(McpServerManager.class));
        setField(extension, "jettyServerManager", mock(JettyServerManager.class));
    }

    @Test
    @DisplayName("onHostChanged triggers Jetty restart via MCP servlet creation")
    void onHostChangedTriggersRestart() throws Exception {
        McpServerManager mcpServerManager = getField(extension, "mcpServerManager");
        JettyServerManager jettyServerManager = getField(extension, "jettyServerManager");
        ServletHolder servletHolder = mock(ServletHolder.class);

        when(mcpServerManager.createMcpServlet("/mcp")).thenReturn(servletHolder);

        extension.onHostChanged("localhost", "127.0.0.1");

        verify(jettyServerManager).restartServer(servletHolder, "/mcp");
    }

    @Test
    @DisplayName("onPortChanged triggers Jetty restart via MCP servlet creation")
    void onPortChangedTriggersRestart() throws Exception {
        McpServerManager mcpServerManager = getField(extension, "mcpServerManager");
        JettyServerManager jettyServerManager = getField(extension, "jettyServerManager");
        ServletHolder servletHolder = mock(ServletHolder.class);

        when(mcpServerManager.createMcpServlet("/mcp")).thenReturn(servletHolder);

        extension.onPortChanged(61169, 8080);

        verify(jettyServerManager).restartServer(servletHolder, "/mcp");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = WigAIExtension.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName) throws Exception {
        Field field = WigAIExtension.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}
