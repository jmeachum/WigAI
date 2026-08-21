package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.controller.api.Transport;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.error.WigAIErrorHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transport control and playback status reporting.
 *
 * <p>Wraps the shared {@link Transport} already created and marked-interested by
 * {@link BitwigApiFacade}, and owns the beat/time position formatting used by status
 * responses.
 */
class TransportFacade {

    private static final String DEFAULT_BEAT_POSITION = "1.1.1:0";
    private static final String DEFAULT_TIME_STRING = "0:00.000";
    private static final int TICKS_PER_SIXTEENTH = 240;
    private static final int BEATS_PER_MEASURE = 4;
    private static final int SIXTEENTHS_PER_BEAT = 4;

    private final Transport transport;
    private final Logger logger;

    TransportFacade(Transport transport, Logger logger) {
        this.transport = transport;
        this.logger = logger;
    }

    /**
     * Starts the transport playback.
     */
    public void startTransport() {
        logger.info("BitwigApiFacade: Starting transport playback");
        transport.play();
    }

    /**
     * Stops the transport playback.
     */
    public void stopTransport() {
        logger.info("BitwigApiFacade: Stopping transport playback");
        transport.stop();
    }

    /**
     * Gets the current transport status information.
     *
     * @return A map containing transport status data
     * @throws BitwigApiException if transport status cannot be retrieved due to API error
     */
    public java.util.Map<String, Object> getTransportStatus() throws BitwigApiException {
        final String operation = "getTransportStatus";
        logger.info("BitwigApiFacade: Getting transport status");
        java.util.Map<String, Object> transportMap = new java.util.LinkedHashMap<>();

        try {
            transportMap.put("playing", transport.isPlaying().get());
            transportMap.put("recording", transport.isArrangerRecordEnabled().get());
            transportMap.put("loop_active", transport.isArrangerLoopEnabled().get());
            transportMap.put("metronome_active", transport.isMetronomeEnabled().get());
            transportMap.put("current_tempo", transport.tempo().getRaw());
            transportMap.put("time_signature", transport.timeSignature().get());

            // Format position as Bitwig-style beat string
            double positionInBeats = transport.getPosition().get();
            String beatStr = formatBitwigBeatPosition(positionInBeats);
            transportMap.put("current_beat_str", beatStr);

            // Get time string using playPositionInSeconds
            double positionInSeconds = transport.playPositionInSeconds().get();
            String timeStr = formatTimeString(positionInSeconds);
            transportMap.put("current_time_str", timeStr);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Unable to get complete transport status: " + e.getMessage());
            throw new BitwigApiException(
                ErrorCode.BITWIG_API_ERROR,
                operation,
                "Failed to retrieve transport status: " + e.getMessage()
            );
        }

        return transportMap;
    }

    /**
     * Formats seconds into a time string in the format MM:SS.mmm or HH:MM:SS.mmm
     * @param seconds The time in seconds
     * @return Formatted time string with milliseconds
     */
    private String formatTimeString(double seconds) {
        try {
            int totalSeconds = (int) Math.floor(seconds);
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int secs = totalSeconds % 60;

            // Calculate milliseconds from the fractional part
            int milliseconds = (int) Math.round((seconds - Math.floor(seconds)) * 1000);

            // Handle edge case where rounding gives us 1000ms
            if (milliseconds >= 1000) {
                milliseconds = 0;
                secs += 1;
                if (secs >= 60) {
                    secs = 0;
                    minutes += 1;
                    if (minutes >= 60) {
                        minutes = 0;
                        hours += 1;
                    }
                }
            }

            if (hours > 0) {
                return String.format("%d:%02d:%02d.%03d", hours, minutes, secs, milliseconds);
            } else {
                return String.format("%d:%02d.%03d", minutes, secs, milliseconds);
            }
        } catch (Exception e) {
            return DEFAULT_TIME_STRING;
        }
    }

    /**
     * Formats a position in beats to Bitwig-style format: measures.beats.sixteenths:ticks
     * Example: 1.1.1:0 = measure 1, beat 1, sixteenth 1, tick 0
     */
    private String formatBitwigBeatPosition(double positionInBeats) {
        try {
            // Assume 4/4 time signature for calculation
            int beatsPerMeasure = BEATS_PER_MEASURE;
            int sixteenthsPerBeat = SIXTEENTHS_PER_BEAT;
            int ticksPerSixteenth = TICKS_PER_SIXTEENTH; // Common MIDI resolution

            // Convert beats to total ticks
            int totalTicks = (int) Math.round(positionInBeats * sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate measures (1-based)
            int measures = (totalTicks / (beatsPerMeasure * sixteenthsPerBeat * ticksPerSixteenth)) + 1;
            int remainingTicks = totalTicks % (beatsPerMeasure * sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate beats within measure (1-based)
            int beats = (remainingTicks / (sixteenthsPerBeat * ticksPerSixteenth)) + 1;
            remainingTicks = remainingTicks % (sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate sixteenths within beat (1-based)
            int sixteenths = (remainingTicks / ticksPerSixteenth) + 1;
            int ticks = remainingTicks % ticksPerSixteenth;

            return String.format("%d.%d.%d:%d", measures, beats, sixteenths, ticks);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error formatting beat position: " + e.getMessage());
            return DEFAULT_BEAT_POSITION;
        }
    }
}
