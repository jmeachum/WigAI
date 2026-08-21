package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.controller.api.*;
import io.github.fabb.wigai.common.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Shared Mockito harness for the BitwigApiFacade test suite.
 *
 * <p>Owns the full Bitwig API mock graph and the constructed facade, so the per-domain
 * test classes carry only their own assertions. Split out of the original 1634-line
 * BitwigApiFacadeTest along the same seams as the production sub-facades.
 */
abstract class BitwigApiFacadeTestSupport {

    @Mock
    protected ControllerHost mockHost;

    @Mock
    protected Transport mockTransport;

    @Mock
    protected CursorTrack mockCursorTrack;

    @Mock
    protected PinnableCursorDevice mockCursorDevice;

    @Mock
    protected CursorRemoteControlsPage mockParameterBank;

    @Mock
    protected RemoteControl mockRemoteControl;

    @Mock
    protected TrackBank mockTrackBank;
    @Mock
    protected com.bitwig.extension.controller.api.BooleanValue mockTrackBankCanScrollForwards;
    @Mock
    protected com.bitwig.extension.controller.api.BooleanValue mockTrackBankCanScrollBackwards;

    @Mock
    protected Track mockTrack;

    @Mock
    protected ClipLauncherSlotBank mockClipLauncherSlotBank;

    @Mock
    protected ClipLauncherSlot mockClipLauncherSlot;

    @Mock
    protected ClipLauncherSlotBank mockCursorClipLauncherSlotBank;

    @Mock
    protected ClipLauncherSlot mockCursorClipLauncherSlotActive;

    @Mock
    protected ClipLauncherSlot mockCursorClipLauncherSlotIdle;

    @Mock
    protected SceneBank mockSceneBank;

    @Mock
    protected Scene mockScene;

    @Mock
    protected Application mockApplication;

    @Mock
    protected MasterTrack mockMasterTrack;

    @Mock
    protected CursorRemoteControlsPage mockProjectParameterBank;

    @Mock
    protected RemoteControl mockProjectRemoteControl;

    @Mock
    protected Logger mockLogger;

    @Mock
    protected DeviceBank mockDeviceBank;

    @Mock
    protected Device mockDevice;

    @Mock
    protected SendBank mockSendBank;

    @Mock
    protected Send mockSend;

    protected BitwigApiFacade bitwigApiFacade;

    @BeforeEach
    protected void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup basic mocks
        when(mockHost.createTransport()).thenReturn(mockTransport);
        when(mockHost.createApplication()).thenReturn(mockApplication);
        when(mockHost.createCursorTrack(0, 128)).thenReturn(mockCursorTrack);
        when(mockCursorTrack.createCursorDevice()).thenReturn(mockCursorDevice);
        when(mockCursorDevice.createCursorRemoteControlsPage(8)).thenReturn(mockParameterBank);

        // Setup new mocks for story 5.2
        when(mockHost.createMasterTrack(0)).thenReturn(mockMasterTrack);
        when(mockMasterTrack.createCursorRemoteControlsPage(8)).thenReturn(mockProjectParameterBank);

        // Setup TrackBank mocks (new for clip launching) - use smaller sizes for testing
        when(mockHost.createTrackBank(128, 0, 128)).thenReturn(mockTrackBank);
        when(mockTrackBank.getSizeOfBank()).thenReturn(8); // Reduced from 128 to 8 for testing
        when(mockTrackBank.getItemAt(anyInt())).thenReturn(mockTrack);
        when(mockTrack.clipLauncherSlotBank()).thenReturn(mockClipLauncherSlotBank);
        when(mockClipLauncherSlotBank.getSizeOfBank()).thenReturn(8); // Reduced from 128 to 8 for testing
        when(mockClipLauncherSlotBank.getItemAt(anyInt())).thenReturn(mockClipLauncherSlot);

        // Setup SendBank mocks
        when(mockTrack.sendBank()).thenReturn(mockSendBank);
        when(mockSendBank.getSizeOfBank()).thenReturn(8); // Default to 8 sends for testing
        when(mockSendBank.getItemAt(anyInt())).thenReturn(mockSend);

        // Setup SceneBank mocks (new for scene launching) - use smaller sizes for testing
        when(mockHost.createSceneBank(128)).thenReturn(mockSceneBank);
        when(mockSceneBank.getItemAt(anyInt())).thenReturn(mockScene);
        when(mockSceneBank.getSizeOfBank()).thenReturn(8); // Reduced from 128 to 8 for testing

        // Setup parameter mocks with lenient stubbing to avoid NPEs
        lenient().when(mockParameterBank.getParameter(anyInt())).thenReturn(mockRemoteControl);
        lenient().when(mockProjectParameterBank.getParameter(anyInt())).thenReturn(mockProjectRemoteControl);

        // Use lenient mocking for the chain calls to avoid NPEs during construction
        lenient().when(mockCursorDevice.exists()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorDevice.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockCursorDevice.isEnabled()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        lenient().when(mockCursorDevice.deviceType()).thenReturn(mock(com.bitwig.extension.controller.api.EnumValue.class));
        lenient().when(mockRemoteControl.exists()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockRemoteControl.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockRemoteControl.value()).thenReturn(mock(com.bitwig.extension.controller.api.SettableRangedValue.class));
        lenient().when(mockRemoteControl.displayedValue()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));

        // Setup project parameter mocks
        lenient().when(mockProjectRemoteControl.exists()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockProjectRemoteControl.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockProjectRemoteControl.value()).thenReturn(mock(com.bitwig.extension.controller.api.SettableRangedValue.class));
        lenient().when(mockProjectRemoteControl.displayedValue()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));

        // Setup TrackBank related mocks with lenient stubbing
        lenient().when(mockTrack.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockTrack.exists()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockTrack.trackType()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockTrack.isGroup()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockTrack.isActivated()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        lenient().when(mockTrack.color()).thenReturn(mock(com.bitwig.extension.controller.api.SettableColorValue.class));

        // Setup device bank mocks for tracks - use smaller sizes for testing
        lenient().when(mockTrack.createDeviceBank(128)).thenReturn(mockDeviceBank);
        lenient().when(mockDeviceBank.getSizeOfBank()).thenReturn(8);
        lenient().when(mockDeviceBank.getItemAt(anyInt())).thenReturn(mockDevice);
        lenient().when(mockDevice.exists()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockDevice.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockDevice.isEnabled()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        lenient().when(mockDevice.deviceType()).thenReturn(mock(com.bitwig.extension.controller.api.EnumValue.class));

        lenient().when(mockClipLauncherSlot.hasContent()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockClipLauncherSlot.isPlaying()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));

        // Setup SceneBank related mocks with lenient stubbing
        lenient().when(mockScene.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockScene.exists()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockScene.color()).thenReturn(mock(com.bitwig.extension.controller.api.SettableColorValue.class));

        // Setup Application mocks for story 5.2
        lenient().when(mockApplication.projectName()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockApplication.hasActiveEngine()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));

        // Setup Transport mocks for story 5.2
        lenient().when(mockTransport.isPlaying()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        lenient().when(mockTransport.isArrangerRecordEnabled()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        lenient().when(mockTransport.isArrangerLoopEnabled()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        lenient().when(mockTransport.isMetronomeEnabled()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        com.bitwig.extension.controller.api.Parameter mockTempo = mock(com.bitwig.extension.controller.api.Parameter.class);
        lenient().when(mockTempo.value()).thenReturn(mock(com.bitwig.extension.controller.api.SettableRangedValue.class));
        lenient().when(mockTransport.tempo()).thenReturn(mockTempo);
        lenient().when(mockTransport.timeSignature()).thenReturn(mock(com.bitwig.extension.controller.api.TimeSignatureValue.class));
        lenient().when(mockTransport.getPosition()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBeatTimeValue.class));
        lenient().when(mockTransport.playPositionInSeconds()).thenReturn(mock(com.bitwig.extension.controller.api.SettableDoubleValue.class));

        // Setup CursorTrack mocks for story 5.2
        lenient().when(mockCursorTrack.exists()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorTrack.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockCursorTrack.trackType()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockCursorTrack.isGroup()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorTrack.mute()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        lenient().when(mockCursorTrack.solo()).thenReturn(mock(com.bitwig.extension.controller.api.SoloValue.class));
        lenient().when(mockCursorTrack.arm()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        com.bitwig.extension.controller.api.IntegerValue mockCursorPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        lenient().when(mockCursorPosition.get()).thenReturn(0);
        lenient().when(mockCursorTrack.position()).thenReturn(mockCursorPosition);

        // Additional stubs for newly monitored track channel controls
        lenient().when(mockTrack.mute()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        lenient().when(mockTrack.solo()).thenReturn(mock(com.bitwig.extension.controller.api.SoloValue.class));
        lenient().when(mockTrack.arm()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));
        // Volume
        SettableRangedValue mockVolumeValue = mock(SettableRangedValue.class);
        SettableStringValue mockVolumeDisplay = mock(SettableStringValue.class);
        lenient().when(mockVolumeValue.get()).thenReturn(0.5);
        lenient().when(mockVolumeDisplay.get()).thenReturn("-6.0 dB");
        RemoteControl mockVolume = mock(RemoteControl.class);
        lenient().when(mockVolume.value()).thenReturn(mockVolumeValue);
        lenient().when(mockVolume.displayedValue()).thenReturn(mockVolumeDisplay);
        lenient().when(mockTrack.volume()).thenReturn(mockVolume);
        // Pan
        SettableRangedValue mockPanValue = mock(SettableRangedValue.class);
        SettableStringValue mockPanDisplay = mock(SettableStringValue.class);
        lenient().when(mockPanValue.get()).thenReturn(0.5);
        lenient().when(mockPanDisplay.get()).thenReturn("C");
        RemoteControl mockPan = mock(RemoteControl.class);
        lenient().when(mockPan.value()).thenReturn(mockPanValue);
        lenient().when(mockPan.displayedValue()).thenReturn(mockPanDisplay);
        lenient().when(mockTrack.pan()).thenReturn(mockPan);
        // Monitoring
        com.bitwig.extension.controller.api.BooleanValue mockIsMonitoring = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        lenient().when(mockIsMonitoring.get()).thenReturn(true);
        lenient().when(mockTrack.isMonitoring()).thenReturn(mockIsMonitoring);
        SettableEnumValue mockMonitorMode = mock(SettableEnumValue.class);
        lenient().when(mockMonitorMode.get()).thenReturn("AUTO");
        lenient().when(mockTrack.monitorMode()).thenReturn(mockMonitorMode);
        when(mockTrackBank.canScrollForwards()).thenReturn(mockTrackBankCanScrollForwards);
        when(mockTrackBank.canScrollBackwards()).thenReturn(mockTrackBankCanScrollBackwards);
        lenient().when(mockTrackBankCanScrollForwards.get()).thenReturn(false);
        lenient().when(mockTrackBankCanScrollBackwards.get()).thenReturn(false);
        com.bitwig.extension.controller.api.IntegerValue mockTrackPosition = mock(com.bitwig.extension.controller.api.IntegerValue.class);
        lenient().when(mockTrackPosition.get()).thenReturn(0);
        lenient().when(mockTrack.position()).thenReturn(mockTrackPosition);
        // Cursor track additional channel controls
        RemoteControl mockCursorVolume = mock(RemoteControl.class);
        SettableRangedValue mockCursorVolVal = mock(SettableRangedValue.class);
        SettableStringValue mockCursorVolDisp = mock(SettableStringValue.class);
        lenient().when(mockCursorVolVal.get()).thenReturn(0.4);
        lenient().when(mockCursorVolDisp.get()).thenReturn("-8.0 dB");
        lenient().when(mockCursorVolume.value()).thenReturn(mockCursorVolVal);
        lenient().when(mockCursorVolume.displayedValue()).thenReturn(mockCursorVolDisp);
        lenient().when(mockCursorTrack.volume()).thenReturn(mockCursorVolume);
        RemoteControl mockCursorPan = mock(RemoteControl.class);
        SettableRangedValue mockCursorPanVal = mock(SettableRangedValue.class);
        SettableStringValue mockCursorPanDisp = mock(SettableStringValue.class);
        lenient().when(mockCursorPanVal.get()).thenReturn(0.5);
        lenient().when(mockCursorPanDisp.get()).thenReturn("C");
        lenient().when(mockCursorPan.value()).thenReturn(mockCursorPanVal);
        lenient().when(mockCursorPan.displayedValue()).thenReturn(mockCursorPanDisp);
        lenient().when(mockCursorTrack.pan()).thenReturn(mockCursorPan);
        com.bitwig.extension.controller.api.BooleanValue mockCursorMonitoring = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        lenient().when(mockCursorMonitoring.get()).thenReturn(true);
        lenient().when(mockCursorTrack.isMonitoring()).thenReturn(mockCursorMonitoring);
        SettableEnumValue mockCursorMonitorMode = mock(SettableEnumValue.class);
        lenient().when(mockCursorMonitorMode.get()).thenReturn("AUTO");
        lenient().when(mockCursorTrack.monitorMode()).thenReturn(mockCursorMonitorMode);
        // Clip slot extra properties
        lenient().when(mockClipLauncherSlot.isRecording()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockClipLauncherSlot.isPlaybackQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockClipLauncherSlot.isRecordingQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockClipLauncherSlot.isStopQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockClipLauncherSlot.color()).thenReturn(mock(com.bitwig.extension.controller.api.SettableColorValue.class));
        lenient().when(mockClipLauncherSlot.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockCursorClipLauncherSlotIdle.hasContent()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotIdle.isPlaying()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotIdle.isRecording()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotIdle.isPlaybackQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotIdle.isRecordingQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotIdle.isStopQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotIdle.color()).thenReturn(mock(com.bitwig.extension.controller.api.SettableColorValue.class));
        lenient().when(mockCursorClipLauncherSlotIdle.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        lenient().when(mockCursorClipLauncherSlotActive.hasContent()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotActive.isPlaying()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotActive.isRecording()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotActive.isPlaybackQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotActive.isRecordingQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotActive.isStopQueued()).thenReturn(mock(com.bitwig.extension.controller.api.BooleanValue.class));
        lenient().when(mockCursorClipLauncherSlotActive.color()).thenReturn(mock(com.bitwig.extension.controller.api.SettableColorValue.class));
        lenient().when(mockCursorClipLauncherSlotActive.name()).thenReturn(mock(com.bitwig.extension.controller.api.SettableStringValue.class));
        when(mockCursorTrack.clipLauncherSlotBank()).thenReturn(mockCursorClipLauncherSlotBank);
        when(mockCursorClipLauncherSlotBank.getSizeOfBank()).thenReturn(8);
        when(mockCursorClipLauncherSlotBank.getItemAt(anyInt())).thenReturn(mockCursorClipLauncherSlotIdle);
        when(mockCursorClipLauncherSlotBank.getItemAt(0)).thenReturn(mockCursorClipLauncherSlotIdle);
        when(mockCursorClipLauncherSlotBank.getItemAt(1)).thenReturn(mockCursorClipLauncherSlotActive);

        // Setup Send mock properties
        lenient().when(mockSend.name()).thenReturn(mock(com.bitwig.extension.controller.api.StringValue.class));
        lenient().when(mockSend.value()).thenReturn(mock(com.bitwig.extension.controller.api.Parameter.class));
        lenient().when(mockSend.displayedValue()).thenReturn(mock(com.bitwig.extension.controller.api.StringValue.class));
        lenient().when(mockSend.isEnabled()).thenReturn(mock(com.bitwig.extension.controller.api.SettableBooleanValue.class));

        // Setup parameter count mocks
        when(mockParameterBank.getParameterCount()).thenReturn(8);  // Default to 8 for device parameters
        when(mockProjectParameterBank.getParameterCount()).thenReturn(8);  // Default to 8 for project parameters

        bitwigApiFacade = new BitwigApiFacade(mockHost, mockLogger);
    }

    protected com.bitwig.extension.controller.api.BooleanValue boolValue(boolean value) {
        com.bitwig.extension.controller.api.BooleanValue bool = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(bool.get()).thenReturn(value);
        return bool;
    }

    protected SettableStringValue stringValue(String value) {
        SettableStringValue str = mock(SettableStringValue.class);
        when(str.get()).thenReturn(value);
        return str;
    }

    protected void setupSlotBooleanStates(
        ClipLauncherSlot slot,
        boolean playing,
        boolean recording,
        boolean playbackQueued,
        boolean recordingQueued,
        boolean stopQueued
    ) {
        com.bitwig.extension.controller.api.BooleanValue playingValue = boolValue(playing);
        com.bitwig.extension.controller.api.BooleanValue recordingValue = boolValue(recording);
        com.bitwig.extension.controller.api.BooleanValue playbackValue = boolValue(playbackQueued);
        com.bitwig.extension.controller.api.BooleanValue recordingQueuedValue = boolValue(recordingQueued);
        com.bitwig.extension.controller.api.BooleanValue stopQueuedValue = boolValue(stopQueued);

        when(slot.isPlaying()).thenReturn(playingValue);
        when(slot.isRecording()).thenReturn(recordingValue);
        when(slot.isPlaybackQueued()).thenReturn(playbackValue);
        when(slot.isRecordingQueued()).thenReturn(recordingQueuedValue);
        when(slot.isStopQueued()).thenReturn(stopQueuedValue);
    }
}
