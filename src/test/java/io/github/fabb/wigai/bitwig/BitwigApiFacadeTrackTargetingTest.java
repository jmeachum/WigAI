package io.github.fabb.wigai.bitwig;
import com.bitwig.extension.controller.api.*;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Track resolution: name lookup, selector precedence, and fuzzy candidates.
 *
 * <p>Mock harness lives in {@link BitwigApiFacadeTestSupport}.
 */
class BitwigApiFacadeTrackTargetingTest extends BitwigApiFacadeTestSupport {

    @Test
    void testFindTrackIndexByName_DuplicateExactNamesThrowsAmbiguityError() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(4);

        Track track0 = mock(Track.class);
        Track track1 = mock(Track.class);
        Track track2 = mock(Track.class);
        Track track3 = mock(Track.class);

        when(mockTrackBank.getItemAt(0)).thenReturn(track0);
        when(mockTrackBank.getItemAt(1)).thenReturn(track1);
        when(mockTrackBank.getItemAt(2)).thenReturn(track2);
        when(mockTrackBank.getItemAt(3)).thenReturn(track3);

        com.bitwig.extension.controller.api.BooleanValue existsTrue = boolValue(true);
        when(track0.exists()).thenReturn(existsTrue);
        when(track1.exists()).thenReturn(existsTrue);
        when(track2.exists()).thenReturn(existsTrue);
        when(track3.exists()).thenReturn(existsTrue);

        SettableStringValue bassName = stringValue("Bass");
        SettableStringValue drumsNameA = stringValue("Drums");
        SettableStringValue drumsNameB = stringValue("Drums");
        SettableStringValue leadName = stringValue("Lead");
        when(track0.name()).thenReturn(bassName);
        when(track1.name()).thenReturn(drumsNameA);
        when(track2.name()).thenReturn(drumsNameB);
        when(track3.name()).thenReturn(leadName);

        BitwigApiException exception = assertThrows(BitwigApiException.class, () ->
            bitwigApiFacade.findTrackIndexByName("Drums")
        );

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Ambiguous"));
        assertTrue(exception.getContext() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) exception.getContext();
        assertEquals("track_index", context.get("confirmation_parameter"));
        assertTrue(context.get("candidates") instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) context.get("candidates");
        assertEquals(2, candidates.size());
        assertEquals(List.of("track_index", "track_name"), new java.util.ArrayList<>(candidates.get(0).keySet()));
        assertEquals(1, candidates.get(0).get("track_index"));
        assertEquals("Drums", candidates.get(0).get("track_name"));
        assertEquals(2, candidates.get(1).get("track_index"));
        assertEquals("Drums", candidates.get(1).get("track_name"));
    }

    @Test
    void testFindTrackIndexByName_TrimmedCaseInsensitiveExactMatch() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(2);

        Track track0 = mock(Track.class);
        Track track1 = mock(Track.class);
        when(mockTrackBank.getItemAt(0)).thenReturn(track0);
        when(mockTrackBank.getItemAt(1)).thenReturn(track1);

        com.bitwig.extension.controller.api.BooleanValue existsTrue = boolValue(true);
        when(track0.exists()).thenReturn(existsTrue);
        when(track1.exists()).thenReturn(existsTrue);

        SettableStringValue bassName = stringValue("Bass");
        SettableStringValue drumsName = stringValue("Drums");
        when(track0.name()).thenReturn(bassName);
        when(track1.name()).thenReturn(drumsName);

        int index = bitwigApiFacade.findTrackIndexByName("  drums  ");
        assertEquals(1, index);
    }

    @Test
    void testResolveTrackIndex_DualSelectorsMismatchReturnsInvalidParameter() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(2);
        Track track0 = mock(Track.class);
        Track track1 = mock(Track.class);
        when(mockTrackBank.getItemAt(0)).thenReturn(track0);
        when(mockTrackBank.getItemAt(1)).thenReturn(track1);

        com.bitwig.extension.controller.api.BooleanValue existsTrue = boolValue(true);
        when(track0.exists()).thenReturn(existsTrue);
        when(track1.exists()).thenReturn(existsTrue);
        SettableStringValue bassName = stringValue("Bass");
        SettableStringValue drumsName = stringValue("Drums");
        when(track0.name()).thenReturn(bassName);
        when(track1.name()).thenReturn(drumsName);

        BitwigApiException exception = assertThrows(
            BitwigApiException.class,
            () -> bitwigApiFacade.resolveTrackIndex(0, "Drums", false, "list_devices_on_track")
        );

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("does not match track_name"));
    }

    @Test
    void testResolveTrackIndex_SelectedFallbackWithoutSelectionReturnsGuidance() {
        com.bitwig.extension.controller.api.BooleanValue trackMissing = boolValue(false);
        when(mockCursorTrack.exists()).thenReturn(trackMissing);

        BitwigApiException exception = assertThrows(
            BitwigApiException.class,
            () -> bitwigApiFacade.resolveTrackIndex(null, null, true, "get_track_details")
        );

        assertEquals(ErrorCode.TRACK_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Provide track_index or track_name"));
    }

    @Test
    void testResolveTrack_PrioritizesMatchTypeAndSortsByTrackIndex() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(5);

        Track track0 = mock(Track.class);
        Track track1 = mock(Track.class);
        Track track2 = mock(Track.class);
        Track track3 = mock(Track.class);
        Track track4 = mock(Track.class);

        when(mockTrackBank.getItemAt(0)).thenReturn(track0);
        when(mockTrackBank.getItemAt(1)).thenReturn(track1);
        when(mockTrackBank.getItemAt(2)).thenReturn(track2);
        when(mockTrackBank.getItemAt(3)).thenReturn(track3);
        when(mockTrackBank.getItemAt(4)).thenReturn(track4);

        com.bitwig.extension.controller.api.BooleanValue existsTrue = boolValue(true);
        when(track0.exists()).thenReturn(existsTrue);
        when(track1.exists()).thenReturn(existsTrue);
        when(track2.exists()).thenReturn(existsTrue);
        when(track3.exists()).thenReturn(existsTrue);
        when(track4.exists()).thenReturn(existsTrue);

        SettableStringValue drumBus = stringValue("Drum Bus");
        SettableStringValue myDrumRoom = stringValue("My Drum Room");
        SettableStringValue drumExact = stringValue("  DRUM  ");
        SettableStringValue drumKit = stringValue("Drum Kit");
        SettableStringValue bass = stringValue("Bass");
        when(track0.name()).thenReturn(drumBus);
        when(track1.name()).thenReturn(myDrumRoom);
        when(track2.name()).thenReturn(drumExact);
        when(track3.name()).thenReturn(drumKit);
        when(track4.name()).thenReturn(bass);

        Map<String, Object> result = bitwigApiFacade.resolveTrack("drum", "resolve_track");

        assertEquals(true, result.get("ambiguous"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertEquals(4, candidates.size());

        assertEquals(List.of("track_index", "track_name", "match_type"), new java.util.ArrayList<>(candidates.get(0).keySet()));
        assertEquals(2, candidates.get(0).get("track_index"));
        assertEquals("exact", candidates.get(0).get("match_type"));

        assertEquals(0, candidates.get(1).get("track_index"));
        assertEquals("prefix", candidates.get(1).get("match_type"));

        assertEquals(3, candidates.get(2).get("track_index"));
        assertEquals("prefix", candidates.get(2).get("match_type"));

        assertEquals(1, candidates.get(3).get("track_index"));
        assertEquals("substring", candidates.get(3).get("match_type"));
    }

    @Test
    void testResolveTrack_DuplicateExactNamesRemainAmbiguous() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(4);

        Track track0 = mock(Track.class);
        Track track1 = mock(Track.class);
        Track track2 = mock(Track.class);
        Track track3 = mock(Track.class);

        when(mockTrackBank.getItemAt(0)).thenReturn(track0);
        when(mockTrackBank.getItemAt(1)).thenReturn(track1);
        when(mockTrackBank.getItemAt(2)).thenReturn(track2);
        when(mockTrackBank.getItemAt(3)).thenReturn(track3);

        com.bitwig.extension.controller.api.BooleanValue existsTrue = boolValue(true);
        when(track0.exists()).thenReturn(existsTrue);
        when(track1.exists()).thenReturn(existsTrue);
        when(track2.exists()).thenReturn(existsTrue);
        when(track3.exists()).thenReturn(existsTrue);

        SettableStringValue bass = stringValue("Bass");
        SettableStringValue drumsLower = stringValue("Drums");
        SettableStringValue drumsUpper = stringValue("DRUMS");
        SettableStringValue lead = stringValue("Lead");
        when(track0.name()).thenReturn(bass);
        when(track1.name()).thenReturn(drumsLower);
        when(track2.name()).thenReturn(drumsUpper);
        when(track3.name()).thenReturn(lead);

        Map<String, Object> result = bitwigApiFacade.resolveTrack("  drums  ", "resolve_track");

        assertEquals(true, result.get("ambiguous"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertEquals(2, candidates.size());
        assertEquals(1, candidates.get(0).get("track_index"));
        assertEquals("exact", candidates.get(0).get("match_type"));
        assertEquals(2, candidates.get(1).get("track_index"));
        assertEquals("exact", candidates.get(1).get("match_type"));
    }

    @Test
    void testResolveTrack_PaginatesBeyondVisibleWindow() {
        java.util.concurrent.atomic.AtomicInteger page = new java.util.concurrent.atomic.AtomicInteger(0);

        when(mockTrackBank.getSizeOfBank()).thenReturn(1);

        com.bitwig.extension.controller.api.BooleanValue slotExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(slotExists.get()).thenReturn(true);
        when(mockTrack.exists()).thenReturn(slotExists);

        SettableStringValue slotName = mock(SettableStringValue.class);
        when(mockTrack.name()).thenReturn(slotName);
        when(slotName.get()).thenAnswer(inv -> page.get() == 0 ? "Bass" : "Snare");

        com.bitwig.extension.controller.api.IntegerValue slotPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(slotPosition.get()).thenAnswer(inv -> page.get());
        when(mockTrack.position()).thenReturn(slotPosition);

        when(mockTrackBankCanScrollForwards.get()).thenAnswer(inv -> page.get() == 0);
        when(mockTrackBankCanScrollBackwards.get()).thenAnswer(inv -> page.get() > 0);
        doAnswer(inv -> { page.incrementAndGet(); return null; }).when(mockTrackBank).scrollPageForwards();
        doAnswer(inv -> { page.decrementAndGet(); return null; }).when(mockTrackBank).scrollPageBackwards();

        bitwigApiFacade = new BitwigApiFacade(mockHost, mockLogger);

        Map<String, Object> result = bitwigApiFacade.resolveTrack("snare", "resolve_track");

        assertEquals(false, result.get("ambiguous"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertEquals(1, candidates.size());
        assertEquals(1, candidates.get(0).get("track_index"));
        assertEquals("Snare", candidates.get(0).get("track_name"));
        assertEquals("exact", candidates.get(0).get("match_type"));
    }

    @Test
    void testResolveTrack_SingleCandidateSetsAmbiguousFalse() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(3);

        Track track0 = mock(Track.class);
        Track track1 = mock(Track.class);
        Track track2 = mock(Track.class);

        when(mockTrackBank.getItemAt(0)).thenReturn(track0);
        when(mockTrackBank.getItemAt(1)).thenReturn(track1);
        when(mockTrackBank.getItemAt(2)).thenReturn(track2);

        com.bitwig.extension.controller.api.BooleanValue existsTrue = boolValue(true);
        when(track0.exists()).thenReturn(existsTrue);
        when(track1.exists()).thenReturn(existsTrue);
        when(track2.exists()).thenReturn(existsTrue);

        SettableStringValue bass = stringValue("Bass");
        SettableStringValue lead = stringValue("Lead");
        SettableStringValue snare = stringValue("Snare");
        when(track0.name()).thenReturn(bass);
        when(track1.name()).thenReturn(lead);
        when(track2.name()).thenReturn(snare);

        Map<String, Object> result = bitwigApiFacade.resolveTrack("snare", "resolve_track");

        assertEquals(false, result.get("ambiguous"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertEquals(1, candidates.size());
        assertEquals(2, candidates.get(0).get("track_index"));
        assertEquals("Snare", candidates.get(0).get("track_name"));
        assertEquals("exact", candidates.get(0).get("match_type"));
    }

    @Test
    void testResolveTrack_NoMatchReturnsTrackNotFoundWithGuidance() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(2);

        Track track0 = mock(Track.class);
        Track track1 = mock(Track.class);
        when(mockTrackBank.getItemAt(0)).thenReturn(track0);
        when(mockTrackBank.getItemAt(1)).thenReturn(track1);

        com.bitwig.extension.controller.api.BooleanValue existsTrue = boolValue(true);
        when(track0.exists()).thenReturn(existsTrue);
        when(track1.exists()).thenReturn(existsTrue);
        SettableStringValue bass = stringValue("Bass");
        SettableStringValue lead = stringValue("Lead");
        when(track0.name()).thenReturn(bass);
        when(track1.name()).thenReturn(lead);

        BitwigApiException exception = assertThrows(BitwigApiException.class, () ->
            bitwigApiFacade.resolveTrack("drums", "resolve_track")
        );

        assertEquals(ErrorCode.TRACK_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("list_tracks"));
    }
}
