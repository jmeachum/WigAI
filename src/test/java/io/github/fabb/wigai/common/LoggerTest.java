package io.github.fabb.wigai.common;

import com.bitwig.extension.controller.api.ControllerHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class LoggerTest {

    private ControllerHost host;
    private Logger logger;

    @BeforeEach
    void setUp() {
        host = mock(ControllerHost.class);
        logger = new Logger(host);
    }

    @Test
    void info_uses_iso_timestamp_then_level_prefix() {
        logger.info("hello world");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(host).println(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.matches("^\\[[0-9]{4}-[0-9]{2}-[0-9]{2}T[^\\]]+Z\\] \\[INFO\\] hello world$"),
                "Expected ISO timestamp then INFO level. Actual: " + message);
    }

    @Test
    void warn_uses_iso_timestamp_then_level_prefix() {
        logger.warn("watch out");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(host).println(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.matches("^\\[[0-9]{4}-[0-9]{2}-[0-9]{2}T[^\\]]+Z\\] \\[WARN\\] watch out$"),
                "Expected ISO timestamp then WARN level. Actual: " + message);
    }

    @Test
    void error_with_exception_formats_main_and_stack_lines_consistently() {
        RuntimeException ex = new RuntimeException("boom");

        logger.error("Something failed", ex);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(host, atLeast(2)).println(messageCaptor.capture());

        for (String line : messageCaptor.getAllValues()) {
            assertTrue(line.matches("^\\[[0-9]{4}-[0-9]{2}-[0-9]{2}T[^\\]]+Z\\] \\[ERROR\\] .+$"),
                    "Every line should include ISO timestamp and ERROR level. Actual: " + line);
        }
    }
}
