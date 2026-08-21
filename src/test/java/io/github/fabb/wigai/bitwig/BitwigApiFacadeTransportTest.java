package io.github.fabb.wigai.bitwig;
import com.bitwig.extension.controller.api.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Transport start/stop behavior.
 *
 * <p>Mock harness lives in {@link BitwigApiFacadeTestSupport}.
 */
class BitwigApiFacadeTransportTest extends BitwigApiFacadeTestSupport {

    @Test
    void testStartTransport() {
        // Call the method
        bitwigApiFacade.startTransport();

        // Verify the transport's play method was called
        verify(mockTransport).play();

        // Verify logging
        verify(mockLogger).info("BitwigApiFacade: Starting transport playback");
    }

    @Test
    void testStopTransport() {
        // Execute the facade method
        bitwigApiFacade.stopTransport();

        // Verify the transport.stop() was called
        verify(mockTransport).stop();

        // Verify logging
        verify(mockLogger).info("BitwigApiFacade: Stopping transport playback");
    }
}
