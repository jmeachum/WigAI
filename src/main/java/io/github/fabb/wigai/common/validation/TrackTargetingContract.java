package io.github.fabb.wigai.common.validation;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;

import java.util.Locale;
import java.util.Map;

/**
 * Shared parsing and normalization rules for track-targeting selectors.
 */
public final class TrackTargetingContract {

    private TrackTargetingContract() {
    }

    public record TrackTargetSelectors(
        Integer trackIndex,
        String trackName,
        boolean useSelectedTrackFallback
    ) {
    }

    /**
     * Parses track-targeting selectors with optional get_selected support.
     *
     * <p>Rules:
     * - Supports dual selectors (`track_index` + `track_name`) where index remains authoritative.
     * - If no selectors are provided, selected-track fallback is enabled.
     * - When {@code supportsGetSelected} is true, {@code get_selected} may be used only without selectors.
     */
    public static TrackTargetSelectors parse(
        Map<String, Object> arguments,
        String operation,
        boolean supportsGetSelected
    ) {
        Integer trackIndex = parseOptionalTrackIndex(arguments, operation);
        String trackName = parseOptionalTrackName(arguments, operation);

        Boolean getSelected = null;
        if (supportsGetSelected && arguments.containsKey("get_selected")) {
            Object getSelectedValue = arguments.get("get_selected");
            if (!(getSelectedValue instanceof Boolean)) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER_TYPE,
                    operation,
                    "get_selected must be a boolean",
                    Map.of("parameter", "get_selected", "value", getSelectedValue)
                );
            }
            getSelected = (Boolean) getSelectedValue;
        }

        boolean hasSelectors = trackIndex != null || trackName != null;
        if (supportsGetSelected && getSelected != null && hasSelectors) {
            throw new BitwigApiException(
                ErrorCode.INVALID_PARAMETER,
                operation,
                "Do not combine get_selected with track_index or track_name"
            );
        }

        if (!hasSelectors) {
            if (supportsGetSelected && Boolean.FALSE.equals(getSelected)) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "If get_selected is provided, it must be true"
                );
            }
            return new TrackTargetSelectors(null, null, true);
        }

        return new TrackTargetSelectors(trackIndex, trackName, false);
    }

    public static String normalizeTrackName(String trackName) {
        if (trackName == null) {
            return "";
        }
        return trackName.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean namesMatchNormalized(String expectedTrackName, String actualTrackName) {
        return normalizeTrackName(expectedTrackName).equals(normalizeTrackName(actualTrackName));
    }

    private static Integer parseOptionalTrackIndex(Map<String, Object> arguments, String operation) {
        if (!arguments.containsKey("track_index")) {
            return null;
        }

        Object value = arguments.get("track_index");
        if (!(value instanceof Number number)) {
            throw new BitwigApiException(
                ErrorCode.INVALID_PARAMETER_TYPE,
                operation,
                "track_index must be an integer",
                Map.of("parameter", "track_index", "value", value)
            );
        }

        double raw = number.doubleValue();
        if (raw != Math.floor(raw) || Double.isNaN(raw) || Double.isInfinite(raw)) {
            throw new BitwigApiException(
                ErrorCode.INVALID_PARAMETER_INDEX,
                operation,
                "track_index must be an integer, got: " + value,
                Map.of("parameter", "track_index", "value", value)
            );
        }

        if (raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
            throw new BitwigApiException(
                ErrorCode.INVALID_PARAMETER_INDEX,
                operation,
                "track_index value out of integer range: " + value,
                Map.of("parameter", "track_index", "value", value)
            );
        }

        int trackIndex = number.intValue();
        if (trackIndex < 0) {
            throw new BitwigApiException(
                ErrorCode.INVALID_PARAMETER_INDEX,
                operation,
                "track_index must be non-negative, got: " + trackIndex,
                Map.of("parameter", "track_index", "value", trackIndex)
            );
        }

        return trackIndex;
    }

    private static String parseOptionalTrackName(Map<String, Object> arguments, String operation) {
        if (!arguments.containsKey("track_name")) {
            return null;
        }

        Object value = arguments.get("track_name");
        if (!(value instanceof String trackName)) {
            throw new BitwigApiException(
                ErrorCode.INVALID_PARAMETER_TYPE,
                operation,
                "track_name must be a string",
                Map.of("parameter", "track_name", "value", value)
            );
        }
        return ParameterValidator.validateNotEmpty(trackName, "track_name", operation);
    }
}
