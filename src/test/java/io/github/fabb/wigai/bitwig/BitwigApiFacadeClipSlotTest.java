package io.github.fabb.wigai.bitwig;
import com.bitwig.extension.controller.api.*;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Clip launcher slot reporting and track existence checks.
 *
 * <p>Mock harness lives in {@link BitwigApiFacadeTestSupport}.
 */
class BitwigApiFacadeClipSlotTest extends BitwigApiFacadeTestSupport {

    @Test
    void testGetSelectedClipSlotInfo_NoTrackSelected() {
        com.bitwig.extension.controller.api.BooleanValue trackExists = boolValue(false);
        when(mockCursorTrack.exists()).thenReturn(trackExists);

        assertNull(bitwigApiFacade.getSelectedClipSlotInfo());
    }

    @Test
    void testGetSelectedClipSlotInfo_WithActiveSlot() {
        var cursorTrackExists = boolValue(true);
        when(mockCursorTrack.exists()).thenReturn(cursorTrackExists);
        var cursorTrackName = stringValue("Track 1");
        when(mockCursorTrack.name()).thenReturn(cursorTrackName);

        var bankTrackExists = boolValue(true);
        when(mockTrack.exists()).thenReturn(bankTrackExists);
        var bankTrackName = stringValue("Track 1");
        when(mockTrack.name()).thenReturn(bankTrackName);

        var sceneExists = boolValue(true);
        when(mockScene.exists()).thenReturn(sceneExists);
        var sceneNameValue = stringValue("Intro");
        when(mockScene.name()).thenReturn(sceneNameValue);

        setupSlotBooleanStates(mockCursorClipLauncherSlotIdle, false, false, false, false, false);
        var idleHasContent = boolValue(false);
        when(mockCursorClipLauncherSlotIdle.hasContent()).thenReturn(idleHasContent);

        setupSlotBooleanStates(mockCursorClipLauncherSlotActive, true, false, false, false, false);
        var activeHasContent = boolValue(true);
        when(mockCursorClipLauncherSlotActive.hasContent()).thenReturn(activeHasContent);
        var clipName = stringValue("Clip A");
        when(mockCursorClipLauncherSlotActive.name()).thenReturn(clipName);

        Map<String, Object> info = bitwigApiFacade.getSelectedClipSlotInfo();

        assertNotNull(info);
        assertEquals("Track 1", info.get("track_name"));
        assertEquals(0, info.get("track_index"));
        assertEquals(1, info.get("slot_index"));
        assertEquals(1, info.get("scene_index"));
        assertEquals("Intro", info.get("scene_name"));
        assertEquals(true, info.get("has_content"));
        assertEquals("Clip A", info.get("clip_name"));
        assertEquals(true, info.get("is_playing"));
        assertEquals(false, info.get("is_recording"));
    }

    @Test
    void testGetSelectedClipSlotInfo_BlankSceneNameReturnsNull() {
        var cursorTrackExists = boolValue(true);
        when(mockCursorTrack.exists()).thenReturn(cursorTrackExists);
        var cursorTrackName = stringValue("Track 1");
        when(mockCursorTrack.name()).thenReturn(cursorTrackName);

        var bankTrackExists = boolValue(true);
        when(mockTrack.exists()).thenReturn(bankTrackExists);
        var bankTrackName = stringValue("Track 1");
        when(mockTrack.name()).thenReturn(bankTrackName);

        var sceneExists = boolValue(true);
        when(mockScene.exists()).thenReturn(sceneExists);
        var sceneNameValue = stringValue("");
        when(mockScene.name()).thenReturn(sceneNameValue);

        setupSlotBooleanStates(mockCursorClipLauncherSlotIdle, true, false, false, false, false);
        var slotHasContent = boolValue(true);
        when(mockCursorClipLauncherSlotIdle.hasContent()).thenReturn(slotHasContent);
        var clipName = stringValue("Clip A");
        when(mockCursorClipLauncherSlotIdle.name()).thenReturn(clipName);

        Map<String, Object> info = bitwigApiFacade.getSelectedClipSlotInfo();

        assertNotNull(info);
        assertNull(info.get("scene_name"));
    }

    @Test
    void testGetSelectedClipSlotInfo_NoActiveSlotDefaultsToSlotZero() {
        var cursorTrackExists = boolValue(true);
        when(mockCursorTrack.exists()).thenReturn(cursorTrackExists);
        var cursorTrackName = stringValue("Track 1");
        when(mockCursorTrack.name()).thenReturn(cursorTrackName);
        var cursorTrackPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(cursorTrackPosition.get()).thenReturn(0);
        when(mockCursorTrack.position()).thenReturn(cursorTrackPosition);

        var bankTrackExists = boolValue(true);
        when(mockTrack.exists()).thenReturn(bankTrackExists);
        var bankTrackName = stringValue("Track 1");
        when(mockTrack.name()).thenReturn(bankTrackName);

        var sceneExists = boolValue(true);
        when(mockScene.exists()).thenReturn(sceneExists);
        var sceneNameValue = stringValue(null);
        when(mockScene.name()).thenReturn(sceneNameValue);

        setupSlotBooleanStates(mockCursorClipLauncherSlotIdle, false, false, false, false, false);
        var idleHasContent = boolValue(false);
        when(mockCursorClipLauncherSlotIdle.hasContent()).thenReturn(idleHasContent);

        setupSlotBooleanStates(mockCursorClipLauncherSlotActive, false, false, false, false, false);
        var activeHasContent = boolValue(false);
        when(mockCursorClipLauncherSlotActive.hasContent()).thenReturn(activeHasContent);

        Map<String, Object> info = bitwigApiFacade.getSelectedClipSlotInfo();

        assertNotNull(info);
        assertEquals("Track 1", info.get("track_name"));
        assertEquals(0, info.get("track_index"));
        assertEquals(0, info.get("slot_index"));
        assertEquals(0, info.get("scene_index"));
        assertNull(info.get("scene_name"));
        assertEquals(false, info.get("has_content"));
        assertNull(info.get("clip_name"));
        assertEquals(false, info.get("is_playing"));
        assertEquals(false, info.get("is_recording"));
    }

    @Test
    void testTrackExists_ReturnsFalseOnlyForTrackNotFound() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(1);
        Track onlyTrack = mock(Track.class);
        when(mockTrackBank.getItemAt(0)).thenReturn(onlyTrack);
        com.bitwig.extension.controller.api.BooleanValue existsTrue = boolValue(true);
        when(onlyTrack.exists()).thenReturn(existsTrue);
        SettableStringValue bassName = stringValue("Bass");
        when(onlyTrack.name()).thenReturn(bassName);

        assertFalse(bitwigApiFacade.trackExists("Drums"));
    }

    @Test
    void testTrackExists_ThrowsForAmbiguousTrackName() {
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

        SettableStringValue drumsNameA = stringValue("Drums");
        SettableStringValue drumsNameB = stringValue("Drums");
        SettableStringValue bassName = stringValue("Bass");
        when(track0.name()).thenReturn(drumsNameA);
        when(track1.name()).thenReturn(drumsNameB);
        when(track2.name()).thenReturn(bassName);

        BitwigApiException exception = assertThrows(BitwigApiException.class, () ->
            bitwigApiFacade.trackExists("Drums")
        );
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
    }
}
