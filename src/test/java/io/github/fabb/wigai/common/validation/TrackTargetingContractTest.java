package io.github.fabb.wigai.common.validation;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackTargetingContractTest {

    @Test
    void parse_AllowsDualSelectorsWithIndexAuthoritative() {
        TrackTargetingContract.TrackTargetSelectors selectors = TrackTargetingContract.parse(
            Map.of("track_index", 2, "track_name", "Drums"),
            "get_track_details",
            true
        );

        assertEquals(2, selectors.trackIndex());
        assertEquals("Drums", selectors.trackName());
        assertFalse(selectors.useSelectedTrackFallback());
    }

    @Test
    void parse_UsesSelectedFallbackWhenSelectorsMissing() {
        TrackTargetingContract.TrackTargetSelectors selectors = TrackTargetingContract.parse(
            Map.of(),
            "list_devices_on_track",
            true
        );

        assertTrue(selectors.useSelectedTrackFallback());
    }

    @Test
    void parse_RejectsGetSelectedFalseWithoutSelectors() {
        BitwigApiException exception = assertThrows(
            BitwigApiException.class,
            () -> TrackTargetingContract.parse(
                Map.of("get_selected", false),
                "get_track_details",
                true
            )
        );

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
    }

    @Test
    void parse_RejectsFractionalTrackIndexAsInvalidParameterIndex() {
        BitwigApiException exception = assertThrows(
            BitwigApiException.class,
            () -> TrackTargetingContract.parse(
                Map.of("track_index", 1.5),
                "get_track_details",
                true
            )
        );

        assertEquals(ErrorCode.INVALID_PARAMETER_INDEX, exception.getErrorCode());
    }

    @Test
    void parse_RejectsTrackIndexOverflowAsInvalidParameterIndex() {
        BitwigApiException exception = assertThrows(
            BitwigApiException.class,
            () -> TrackTargetingContract.parse(
                Map.of("track_index", 4294967296.0),
                "get_track_details",
                true
            )
        );

        assertEquals(ErrorCode.INVALID_PARAMETER_INDEX, exception.getErrorCode());
    }

    @Test
    void parse_RejectsTrackIndexTypeMismatchAsInvalidParameterType() {
        BitwigApiException exception = assertThrows(
            BitwigApiException.class,
            () -> TrackTargetingContract.parse(
                Map.of("track_index", "abc"),
                "get_track_details",
                true
            )
        );

        assertEquals(ErrorCode.INVALID_PARAMETER_TYPE, exception.getErrorCode());
    }

    @Test
    void normalizeTrackName_UsesTrimAndCaseInsensitiveMatching() {
        assertTrue(TrackTargetingContract.namesMatchNormalized("  Drums  ", "drums"));
        assertFalse(TrackTargetingContract.namesMatchNormalized("Drums", "Bass"));
    }
}
