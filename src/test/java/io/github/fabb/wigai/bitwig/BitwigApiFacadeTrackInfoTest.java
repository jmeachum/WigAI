package io.github.fabb.wigai.bitwig;
import com.bitwig.extension.api.Color;
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
 * Track inventory and detailed-track payloads.
 *
 * <p>Mock harness lives in {@link BitwigApiFacadeTestSupport}.
 */
class BitwigApiFacadeTrackInfoTest extends BitwigApiFacadeTestSupport {

    @Test
    void testGetAllTracksInfo_WithFilterAndActivation() {
        // Arrange - setup tracks with proper activation status
        when(mockTrackBank.getSizeOfBank()).thenReturn(3);

        // Mock cursor track for selection detection
        com.bitwig.extension.controller.api.BooleanValue mockCursorExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(mockCursorExists.get()).thenReturn(true);
        when(mockCursorTrack.exists()).thenReturn(mockCursorExists);

        com.bitwig.extension.controller.api.SettableStringValue mockCursorName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
        when(mockCursorName.get()).thenReturn("Track 2");
        when(mockCursorTrack.name()).thenReturn(mockCursorName);
        com.bitwig.extension.controller.api.IntegerValue mockCursorPositionValue = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(mockCursorPositionValue.get()).thenReturn(1);
        when(mockCursorTrack.position()).thenReturn(mockCursorPositionValue);

        // Setup 3 different tracks
        Track[] tracks = new Track[3];
        for (int i = 0; i < 3; i++) {
            tracks[i] = mock(Track.class);

            // Track exists
            com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
            when(mockExists.get()).thenReturn(true);
            when(tracks[i].exists()).thenReturn(mockExists);

            // Track name
            com.bitwig.extension.controller.api.SettableStringValue mockName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
            when(mockName.get()).thenReturn("Track " + (i + 1));
            when(tracks[i].name()).thenReturn(mockName);

            // Track type
            com.bitwig.extension.controller.api.SettableStringValue mockType = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
            String trackType = (i == 0) ? "AUDIO" : (i == 1) ? "INSTRUMENT" : "GROUP";
            when(mockType.get()).thenReturn(trackType);
            when(tracks[i].trackType()).thenReturn(mockType);

            // Track group status
            com.bitwig.extension.controller.api.BooleanValue mockIsGroup = mock(com.bitwig.extension.controller.api.BooleanValue.class);
            when(mockIsGroup.get()).thenReturn(i == 2); // Only track 3 is a group
            when(tracks[i].isGroup()).thenReturn(mockIsGroup);

            // Track activation status - test the new functionality
            com.bitwig.extension.controller.api.SettableBooleanValue mockActivated = mock(com.bitwig.extension.controller.api.SettableBooleanValue.class);
            when(mockActivated.get()).thenReturn(i != 1); // Track 2 is deactivated
            when(tracks[i].isActivated()).thenReturn(mockActivated);

            // Track color
            com.bitwig.extension.controller.api.SettableColorValue mockColor = mock(com.bitwig.extension.controller.api.SettableColorValue.class);
            when(tracks[i].color()).thenReturn(mockColor);
            com.bitwig.extension.controller.api.IntegerValue mockPositionValue = mock(com.bitwig.extension.controller.api.IntegerValue.class);
            when(mockPositionValue.get()).thenReturn(i);
            when(tracks[i].position()).thenReturn(mockPositionValue);

            // No parent track for this test
            when(tracks[i].createParentTrack(0, 0)).thenReturn(null);

            // Mock device bank for each track
            DeviceBank mockDeviceBank = mock(DeviceBank.class);
            when(tracks[i].createDeviceBank(8)).thenReturn(mockDeviceBank);
            when(mockDeviceBank.getSizeOfBank()).thenReturn(0); // No devices for simplicity

            when(mockTrackBank.getItemAt(i)).thenReturn(tracks[i]);
        }

        // Act - get all tracks without filter
        java.util.List<java.util.Map<String, Object>> allTracks = bitwigApiFacade.getAllTracksInfo(null);

        // Act - get tracks with audio filter
        java.util.List<java.util.Map<String, Object>> audioTracks = bitwigApiFacade.getAllTracksInfo("audio");

        // Assert - all tracks
        assertEquals(3, allTracks.size());

        // Verify Track 1 (Audio, activated)
        java.util.Map<String, Object> track1 = allTracks.get(0);
        assertEquals(0, track1.get("index"));
        assertEquals("Track 1", track1.get("name"));
        assertEquals("audio", track1.get("type"));
        assertEquals(false, track1.get("is_group"));
        assertEquals(null, track1.get("parent_group_index"));
        assertEquals(true, track1.get("activated")); // Activated
        assertEquals("rgb(128,128,128)", track1.get("color"));
        assertEquals(false, track1.get("is_selected"));

        // Verify Track 2 (Instrument, deactivated, selected)
        java.util.Map<String, Object> track2 = allTracks.get(1);
        assertEquals(1, track2.get("index"));
        assertEquals("Track 2", track2.get("name"));
        assertEquals("instrument", track2.get("type"));
        assertEquals(false, track2.get("is_group"));
        assertEquals(null, track2.get("parent_group_index"));
        assertEquals(false, track2.get("activated")); // Deactivated
        assertEquals("rgb(128,128,128)", track2.get("color"));
        assertEquals(true, track2.get("is_selected")); // Selected

        // Verify Track 3 (Group, activated)
        java.util.Map<String, Object> track3 = allTracks.get(2);
        assertEquals(2, track3.get("index"));
        assertEquals("Track 3", track3.get("name"));
        assertEquals("group", track3.get("type"));
        assertEquals(true, track3.get("is_group"));
        assertEquals(null, track3.get("parent_group_index"));
        assertEquals(true, track3.get("activated")); // Activated
        assertEquals("rgb(128,128,128)", track3.get("color"));
        assertEquals(false, track3.get("is_selected"));

        // Assert - filtered tracks (only audio)
        assertEquals(1, audioTracks.size());
        java.util.Map<String, Object> filteredTrack = audioTracks.get(0);
        assertEquals("Track 1", filteredTrack.get("name"));
        assertEquals("audio", filteredTrack.get("type"));

        // Verify logging
        verify(mockLogger, times(2)).info(contains("Getting all tracks info"));
    }

    @Test
    void testGetAllTracksInfo_UsesProjectIndexForSelectionAndParent() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(2);

        com.bitwig.extension.controller.api.BooleanValue cursorExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(cursorExists.get()).thenReturn(true);
        when(mockCursorTrack.exists()).thenReturn(cursorExists);
        com.bitwig.extension.controller.api.IntegerValue cursorPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(cursorPosition.get()).thenReturn(1);
        when(mockCursorTrack.position()).thenReturn(cursorPosition);

        Track groupTrack = mock(Track.class);
        Track childTrack = mock(Track.class);
        Track[] tracks = new Track[] { groupTrack, childTrack };

        for (int i = 0; i < tracks.length; i++) {
            Track current = tracks[i];
            com.bitwig.extension.controller.api.BooleanValue exists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
            when(exists.get()).thenReturn(true);
            when(current.exists()).thenReturn(exists);

            com.bitwig.extension.controller.api.SettableStringValue trackName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
            when(trackName.get()).thenReturn("Bass");
            when(current.name()).thenReturn(trackName);

            com.bitwig.extension.controller.api.SettableStringValue trackType = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
            when(trackType.get()).thenReturn(i == 0 ? "GROUP" : "AUDIO");
            when(current.trackType()).thenReturn(trackType);

            com.bitwig.extension.controller.api.BooleanValue isGroup = mock(com.bitwig.extension.controller.api.BooleanValue.class);
            when(isGroup.get()).thenReturn(i == 0);
            when(current.isGroup()).thenReturn(isGroup);

            com.bitwig.extension.controller.api.SettableBooleanValue activated = mock(com.bitwig.extension.controller.api.SettableBooleanValue.class);
            when(activated.get()).thenReturn(true);
            when(current.isActivated()).thenReturn(activated);

            com.bitwig.extension.controller.api.SettableColorValue colorValue = mock(com.bitwig.extension.controller.api.SettableColorValue.class);
            when(current.color()).thenReturn(colorValue);

            com.bitwig.extension.controller.api.IntegerValue positionValue = mock(com.bitwig.extension.controller.api.IntegerValue.class);
            when(positionValue.get()).thenReturn(i);
            when(current.position()).thenReturn(positionValue);

            DeviceBank mockBank = mock(DeviceBank.class);
            when(current.createDeviceBank(8)).thenReturn(mockBank);
            when(mockBank.getSizeOfBank()).thenReturn(0);

            when(current.createParentTrack(0, 0)).thenReturn(null);

            when(mockTrackBank.getItemAt(i)).thenReturn(current);
        }

        // Child should report parent index 0 even though names are identical
        Track parentTrack = mock(Track.class);
        com.bitwig.extension.controller.api.BooleanValue parentExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(parentExists.get()).thenReturn(true);
        when(parentTrack.exists()).thenReturn(parentExists);
        com.bitwig.extension.controller.api.BooleanValue parentIsGroup = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(parentIsGroup.get()).thenReturn(true);
        when(parentTrack.isGroup()).thenReturn(parentIsGroup);
        com.bitwig.extension.controller.api.IntegerValue parentPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(parentPosition.get()).thenReturn(0);
        when(parentTrack.position()).thenReturn(parentPosition);
        com.bitwig.extension.controller.api.SettableStringValue parentName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
        when(parentName.get()).thenReturn("Bass");
        when(parentTrack.name()).thenReturn(parentName);
        when(childTrack.createParentTrack(0, 0)).thenReturn(parentTrack);

        java.util.List<java.util.Map<String, Object>> tracksInfo = bitwigApiFacade.getAllTracksInfo(null);
        assertEquals(2, tracksInfo.size());

        java.util.Map<String, Object> first = tracksInfo.get(0);
        assertEquals(0, first.get("index"));
        assertFalse((Boolean) first.get("is_selected"));
        assertNull(first.get("parent_group_index"));

        java.util.Map<String, Object> second = tracksInfo.get(1);
        assertEquals(1, second.get("index"));
        assertTrue((Boolean) second.get("is_selected"));
        assertEquals(0, second.get("parent_group_index"));
    }

    @Test
    void testGetAllTracksInfo_PaginatesBeyondVisibleWindow() {
        java.util.concurrent.atomic.AtomicInteger page = new java.util.concurrent.atomic.AtomicInteger(0);

        when(mockTrackBank.getSizeOfBank()).thenReturn(1);

        com.bitwig.extension.controller.api.BooleanValue slotExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(slotExists.get()).thenReturn(true);
        when(mockTrack.exists()).thenReturn(slotExists);

        SettableStringValue slotName = mock(SettableStringValue.class);
        when(mockTrack.name()).thenReturn(slotName);
        when(slotName.get()).thenAnswer(inv -> page.get() == 0 ? "Track 1" : "Track 2");

        SettableStringValue slotType = mock(SettableStringValue.class);
        when(mockTrack.trackType()).thenReturn(slotType);
        when(slotType.get()).thenReturn("AUDIO");

        com.bitwig.extension.controller.api.BooleanValue slotIsGroup = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(slotIsGroup.get()).thenReturn(false);
        when(mockTrack.isGroup()).thenReturn(slotIsGroup);

        SettableBooleanValue slotActivated = mock(SettableBooleanValue.class);
        when(slotActivated.get()).thenReturn(true);
        when(mockTrack.isActivated()).thenReturn(slotActivated);

        SettableColorValue slotColor = mock(SettableColorValue.class);
        Color mockColor = mock(Color.class);
        when(mockColor.getRed()).thenReturn(0.2);
        when(mockColor.getGreen()).thenReturn(0.2);
        when(mockColor.getBlue()).thenReturn(0.2);
        when(slotColor.get()).thenReturn(mockColor);
        when(mockTrack.color()).thenReturn(slotColor);

        com.bitwig.extension.controller.api.IntegerValue slotPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(slotPosition.get()).thenAnswer(inv -> page.get());
        when(mockTrack.position()).thenReturn(slotPosition);

        when(mockTrack.createParentTrack(anyInt(), anyInt())).thenReturn(null);

        when(mockTrackBankCanScrollForwards.get()).thenAnswer(inv -> page.get() == 0);
        when(mockTrackBankCanScrollBackwards.get()).thenAnswer(inv -> page.get() > 0);

        doAnswer(inv -> { page.incrementAndGet(); return null; }).when(mockTrackBank).scrollPageForwards();
        doAnswer(inv -> { page.decrementAndGet(); return null; }).when(mockTrackBank).scrollPageBackwards();

        bitwigApiFacade = new BitwigApiFacade(mockHost, mockLogger);

        java.util.List<java.util.Map<String, Object>> tracks = bitwigApiFacade.getAllTracksInfo(null);
        assertEquals(2, tracks.size());
        assertEquals(0, tracks.get(0).get("index"));
        assertEquals(1, tracks.get(1).get("index"));
    }

    @Test
    void testGetAllTracksInfo_MapsDeviceTypes() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(1);

        when(mockTrackBank.getItemAt(anyInt())).thenReturn(mockTrack);

        com.bitwig.extension.controller.api.BooleanValue trackExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(trackExists.get()).thenReturn(true);
        when(mockTrack.exists()).thenReturn(trackExists);

        SettableStringValue trackName = mock(SettableStringValue.class);
        when(trackName.get()).thenReturn("Track 1");
        when(mockTrack.name()).thenReturn(trackName);

        SettableStringValue trackType = mock(SettableStringValue.class);
        when(trackType.get()).thenReturn("AUDIO");
        when(mockTrack.trackType()).thenReturn(trackType);

        com.bitwig.extension.controller.api.BooleanValue isGroup = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(isGroup.get()).thenReturn(false);
        when(mockTrack.isGroup()).thenReturn(isGroup);

        SettableBooleanValue activated = mock(SettableBooleanValue.class);
        when(activated.get()).thenReturn(true);
        when(mockTrack.isActivated()).thenReturn(activated);

        SettableColorValue color = mock(SettableColorValue.class);
        Color rawColor = mock(Color.class);
        when(rawColor.getRed()).thenReturn(0.5);
        when(rawColor.getGreen()).thenReturn(0.5);
        when(rawColor.getBlue()).thenReturn(0.5);
        when(color.get()).thenReturn(rawColor);
        when(mockTrack.color()).thenReturn(color);

        com.bitwig.extension.controller.api.IntegerValue position = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(position.get()).thenReturn(0);
        when(mockTrack.position()).thenReturn(position);

        when(mockTrack.createParentTrack(anyInt(), anyInt())).thenReturn(null);

        com.bitwig.extension.controller.api.BooleanValue cursorExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(cursorExists.get()).thenReturn(false);
        when(mockCursorTrack.exists()).thenReturn(cursorExists);

        when(mockTrackBankCanScrollForwards.get()).thenReturn(false);
        when(mockTrackBankCanScrollBackwards.get()).thenReturn(false);

        com.bitwig.extension.controller.api.BooleanValue deviceExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(deviceExists.get()).thenReturn(true);
        Device mockDevice = mock(Device.class);
        when(mockDevice.exists()).thenReturn(deviceExists);

        SettableStringValue deviceName = mock(SettableStringValue.class);
        when(deviceName.get()).thenReturn("My Device");
        when(mockDevice.name()).thenReturn(deviceName);

        com.bitwig.extension.controller.api.EnumValue deviceType = mock(com.bitwig.extension.controller.api.EnumValue.class);
        when(deviceType.get()).thenReturn("DEVICE_AUDIO");
        when(mockDevice.deviceType()).thenReturn(deviceType);

        SettableBooleanValue deviceEnabled = mock(SettableBooleanValue.class);
        when(deviceEnabled.get()).thenReturn(true);
        when(mockDevice.isEnabled()).thenReturn(deviceEnabled);

        when(mockDeviceBank.getSizeOfBank()).thenReturn(1);
        when(mockDeviceBank.getItemAt(0)).thenReturn(mockDevice);

        bitwigApiFacade = new BitwigApiFacade(mockHost, mockLogger);

        java.util.List<java.util.Map<String, Object>> tracks = bitwigApiFacade.getAllTracksInfo(null);
        assertEquals(1, tracks.size());
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> devices = (java.util.List<java.util.Map<String, Object>>) tracks.get(0).get("devices");
        assertEquals(1, devices.size());
        assertEquals("AudioFX", devices.get(0).get("type"));
    }

    @Test
    void testGetAllTracksInfo_PropagatesBitwigFailures() {
        when(mockTrackBank.getSizeOfBank()).thenThrow(new RuntimeException("track bank failure"));

        BitwigApiException exception = assertThrows(
            BitwigApiException.class,
            () -> bitwigApiFacade.getAllTracksInfo(null)
        );

        assertEquals("list_tracks", exception.getOperation());
        assertEquals(ErrorCode.OPERATION_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("track bank failure"));
    }

    @Test
    void testGetSelectedTrackDetails_SelectedTrackOutsideMaterializedBankUsesCursorFallbackPayload() {
        when(mockTrackBank.getSizeOfBank()).thenReturn(8);

        com.bitwig.extension.controller.api.BooleanValue cursorExists = boolValue(true);
        when(mockCursorTrack.exists()).thenReturn(cursorExists);

        com.bitwig.extension.controller.api.IntegerValue cursorPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        when(cursorPosition.get()).thenReturn(64);
        when(mockCursorTrack.position()).thenReturn(cursorPosition);

        SettableStringValue cursorTrackName = stringValue("Lead");
        when(mockCursorTrack.name()).thenReturn(cursorTrackName);
        SettableStringValue cursorTrackType = stringValue("Instrument");
        when(mockCursorTrack.trackType()).thenReturn(cursorTrackType);
        com.bitwig.extension.controller.api.BooleanValue cursorIsGroup = boolValue(false);
        when(mockCursorTrack.isGroup()).thenReturn(cursorIsGroup);

        RemoteControl cursorVolume = mock(RemoteControl.class);
        SettableRangedValue cursorVolumeValue = mock(SettableRangedValue.class);
        when(cursorVolumeValue.get()).thenReturn(0.42);
        when(cursorVolume.value()).thenReturn(cursorVolumeValue);
        SettableStringValue cursorVolumeDisplay = stringValue("-7.0 dB");
        when(cursorVolume.displayedValue()).thenReturn(cursorVolumeDisplay);
        when(mockCursorTrack.volume()).thenReturn(cursorVolume);

        RemoteControl cursorPan = mock(RemoteControl.class);
        SettableRangedValue cursorPanValue = mock(SettableRangedValue.class);
        when(cursorPanValue.get()).thenReturn(0.5);
        when(cursorPan.value()).thenReturn(cursorPanValue);
        SettableStringValue cursorPanDisplay = stringValue("C");
        when(cursorPan.displayedValue()).thenReturn(cursorPanDisplay);
        when(mockCursorTrack.pan()).thenReturn(cursorPan);

        SettableBooleanValue muted = mock(SettableBooleanValue.class);
        when(muted.get()).thenReturn(false);
        when(mockCursorTrack.mute()).thenReturn(muted);
        SoloValue soloed = mock(SoloValue.class);
        when(soloed.get()).thenReturn(false);
        when(mockCursorTrack.solo()).thenReturn(soloed);
        SettableBooleanValue armed = mock(SettableBooleanValue.class);
        when(armed.get()).thenReturn(false);
        when(mockCursorTrack.arm()).thenReturn(armed);
        com.bitwig.extension.controller.api.BooleanValue monitoring = boolValue(true);
        when(mockCursorTrack.isMonitoring()).thenReturn(monitoring);
        SettableEnumValue monitorMode = mock(SettableEnumValue.class);
        when(monitorMode.get()).thenReturn("AUTO");
        when(mockCursorTrack.monitorMode()).thenReturn(monitorMode);

        Map<String, Object> info = bitwigApiFacade.getSelectedTrackDetails();

        assertNotNull(info);
        assertEquals(64, info.get("index"));
        assertEquals("Lead", info.get("name"));
        assertEquals("instrument", info.get("type"));
        assertEquals(false, info.get("is_group"));
        assertEquals(true, info.get("is_selected"));
        assertEquals(List.of(), info.get("devices"));
        assertEquals(0.42, ((Number) info.get("volume")).doubleValue(), 1e-9);
        assertEquals("-7.0 dB", info.get("volume_str"));
        assertEquals(0.5, ((Number) info.get("pan")).doubleValue(), 1e-9);
        assertEquals("C", info.get("pan_str"));
        assertEquals(false, info.get("muted"));
        assertEquals(false, info.get("soloed"));
        assertEquals(false, info.get("armed"));
        assertEquals(true, info.get("monitor_enabled"));
        assertEquals(true, info.get("auto_monitor_enabled"));
        assertEquals(List.of(), info.get("sends"));
        assertEquals(List.of(), info.get("clips"));
    }
}
