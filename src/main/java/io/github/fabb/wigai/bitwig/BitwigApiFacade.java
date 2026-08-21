package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.controller.api.*;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Facade for Bitwig API interactions.
 * This class abstracts the Bitwig API and provides simplified methods for common operations.
 */
public class BitwigApiFacade {

    /**
     * Constants used throughout the BitwigApiFacade.
     */
    private static final class Constants {
        public static final String DEFAULT_PROJECT_NAME = "Unknown Project";
        public static final int MAX_TRACKS = 128;
        public static final int MAX_SCENES = 128;
        public static final int MAX_DEVICES_PER_TRACK = 128;
        public static final int DEVICE_PARAMETER_COUNT = 8;
        public static final int PROJECT_PARAMETER_COUNT = 8;

        private Constants() {} // Prevent instantiation
    }

    private final ControllerHost host;
    private final Transport transport;
    private final Application application;
    private final Logger logger;
    private final SceneBankFacade sceneBankFacade;
    private final TrackTargetingFacade trackTargeting;
    private final DeviceFacade deviceFacade;
    private final TransportFacade transportFacade;
    private final ClipSlotFacade clipSlotFacade;
    private final TrackInfoFacade trackInfoFacade;
    private final RemoteControlsPage projectParameterBank;

    /**
     * Creates a new BitwigApiFacade instance.
     *
     * @param host   The Bitwig ControllerHost
     * @param logger The logger for logging operations
     */
    public BitwigApiFacade(ControllerHost host, Logger logger) {
        this.host = host;
        this.transport = host.createTransport();
        this.application = host.createApplication();
        this.logger = logger;
        this.transportFacade = new TransportFacade(transport, logger);

        // Mark transport properties as interested for status queries
        transport.isPlaying().markInterested();
        transport.isArrangerRecordEnabled().markInterested();
        transport.isArrangerLoopEnabled().markInterested();
        transport.isMetronomeEnabled().markInterested();
        transport.tempo().markInterested();
        transport.tempo().value().markInterested();
        transport.timeSignature().markInterested();
        transport.getPosition().markInterested();
        transport.playPositionInSeconds().markInterested();

        // Mark application properties as interested for status queries
        application.projectName().markInterested();
        application.hasActiveEngine().markInterested();

        // Initialize device control - use CursorTrack.createCursorDevice() instead of deprecated host.createCursorDevice()
        CursorTrack cursorTrack = host.createCursorTrack(0, Constants.MAX_SCENES);
        CursorDevice cursorDevice = cursorTrack.createCursorDevice();
        RemoteControlsPage deviceParameterBank = cursorDevice.createCursorRemoteControlsPage(Constants.DEVICE_PARAMETER_COUNT);
        ClipLauncherSlotBank cursorClipLauncherSlotBank = cursorTrack.clipLauncherSlotBank();

        // Initialize project parameter access via MasterTrack (project parameters)
        MasterTrack masterTrack = host.createMasterTrack(0);
        this.projectParameterBank = masterTrack.createCursorRemoteControlsPage(Constants.PROJECT_PARAMETER_COUNT);

        // Initialize track bank for clip launching (support up to 128 tracks and 128 scenes for full functionality)
        TrackBank trackBank = host.createTrackBank(Constants.MAX_TRACKS, 0, Constants.MAX_SCENES);
        trackBank.canScrollForwards().markInterested();
        trackBank.canScrollBackwards().markInterested();
        this.sceneBankFacade = new SceneBankFacade(host, logger, Constants.MAX_SCENES); // Support up to 128 scenes for full functionality
        this.trackTargeting = new TrackTargetingFacade(host, trackBank, cursorTrack, logger);

        // Initialize device banks for each track to enable device enumeration
        List<DeviceBank> trackDeviceBanks = new ArrayList<>();
        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            Track track = trackBank.getItemAt(i);
            DeviceBank deviceBank = track.createDeviceBank(Constants.MAX_DEVICES_PER_TRACK);
            trackDeviceBanks.add(deviceBank);
        }
        this.deviceFacade = new DeviceFacade(cursorDevice, deviceParameterBank, trackBank, cursorTrack, trackDeviceBanks, trackTargeting, logger);
        this.clipSlotFacade = new ClipSlotFacade(trackBank, cursorTrack, cursorClipLauncherSlotBank, sceneBankFacade, trackTargeting, logger);
        this.trackInfoFacade = new TrackInfoFacade(host, trackBank, cursorTrack, sceneBankFacade, trackTargeting, deviceFacade, logger);

        // Mark interest in device properties to enable value access
        cursorDevice.exists().markInterested();
        cursorDevice.name().markInterested();
        cursorDevice.isEnabled().markInterested();
        cursorDevice.deviceType().markInterested();

        // Mark interest in all device parameter properties to enable value access
        for (int i = 0; i < deviceParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = deviceParameterBank.getParameter(i);
            parameter.exists().markInterested();
            parameter.name().markInterested();
            parameter.value().markInterested();
            parameter.displayedValue().markInterested();
        }

        // Mark interest in project parameters to enable value access
        for (int i = 0; i < projectParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = projectParameterBank.getParameter(i);
            parameter.exists().markInterested();
            parameter.name().markInterested();
            parameter.value().markInterested();
            parameter.displayedValue().markInterested();
        }

        // Mark interest in cursor track properties for selected track details
        cursorTrack.exists().markInterested();
        cursorTrack.name().markInterested();
        cursorTrack.trackType().markInterested();
        cursorTrack.isGroup().markInterested();
        cursorTrack.mute().markInterested();
        cursorTrack.solo().markInterested();
        cursorTrack.arm().markInterested();
        cursorTrack.position().markInterested();
        cursorTrack.isMonitoring().markInterested();
        cursorTrack.monitorMode().markInterested();
        cursorTrack.volume().value().markInterested();
        cursorTrack.volume().displayedValue().markInterested();
        cursorTrack.pan().value().markInterested();
        cursorTrack.pan().displayedValue().markInterested();

        if (cursorClipLauncherSlotBank == null) {
            logger.warn("BitwigApiFacade: Cursor clip launcher slot bank unavailable - selected clip slot info disabled");
        } else {
            int cursorSlotBankSize = cursorClipLauncherSlotBank.getSizeOfBank();
            for (int slotIndex = 0; slotIndex < cursorSlotBankSize; slotIndex++) {
                ClipLauncherSlot slot = cursorClipLauncherSlotBank.getItemAt(slotIndex);
                slot.hasContent().markInterested();
                slot.isPlaying().markInterested();
                slot.isRecording().markInterested();
                slot.isPlaybackQueued().markInterested();
                slot.isRecordingQueued().markInterested();
                slot.isStopQueued().markInterested();
                slot.color().markInterested();
                slot.name().markInterested();
            }
        }

        // Mark interest in track properties for clip launching and track listing
        for (int trackIndex = 0; trackIndex < trackBank.getSizeOfBank(); trackIndex++) {
            Track track = trackBank.getItemAt(trackIndex);
            track.name().markInterested();
            track.exists().markInterested();
            track.trackType().markInterested();
            track.isGroup().markInterested();
            track.isActivated().markInterested();
            track.color().markInterested();
            track.position().markInterested();

            // Mark interest in device properties for this track
            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
            for (int deviceIndex = 0; deviceIndex < deviceBank.getSizeOfBank(); deviceIndex++) {
                Device device = deviceBank.getItemAt(deviceIndex);
                device.exists().markInterested();
                device.name().markInterested();
                device.isEnabled().markInterested();
                device.deviceType().markInterested();
            }

            // Mark interest in commonly used channel controls
            track.mute().markInterested();
            track.solo().markInterested();
            track.arm().markInterested();
            track.volume().value().markInterested();
            track.volume().displayedValue().markInterested();
            track.pan().value().markInterested();
            track.pan().displayedValue().markInterested();
            track.isMonitoring().markInterested();
            track.monitorMode().markInterested();

            // Mark interest in send properties - only if send bank exists and has sends
            try {
                SendBank sendBank = track.sendBank();
                int sendBankSize = sendBank.getSizeOfBank();
                if (sendBankSize > 0) {
                    for (int sendIndex = 0; sendIndex < sendBankSize; sendIndex++) {
                        Send send = sendBank.getItemAt(sendIndex);
                        send.name().markInterested();
                        send.value().markInterested();
                        send.displayedValue().markInterested();
                        send.isEnabled().markInterested();
                    }
                }
            } catch (Exception e) {
                // Some tracks may not have send banks (e.g., master track)
            }

            ClipLauncherSlotBank trackSlots = track.clipLauncherSlotBank();
            for (int slotIndex = 0; slotIndex < trackSlots.getSizeOfBank(); slotIndex++) {
                ClipLauncherSlot slot = trackSlots.getItemAt(slotIndex);
                slot.hasContent().markInterested();
                slot.isPlaying().markInterested();
                slot.isRecording().markInterested();
                slot.isPlaybackQueued().markInterested();
                slot.isRecordingQueued().markInterested();
                slot.isStopQueued().markInterested();
                slot.color().markInterested();
                slot.name().markInterested();
            }
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    // ========================================
    // Public API Methods
    // ========================================

    /**
     * Get the ControllerHost instance.
     *
     * @return The ControllerHost
     */
    public ControllerHost getHost() {
        return host;
    }

    /**
     * Finds the first scene index with the given name (case-sensitive).
     * Returns -1 if not found.
     */
    public int findSceneByName(String sceneName) {
        return sceneBankFacade.findSceneByName(sceneName);
    }

    /**
     * Gets the name of the scene at the given index, or null if not present.
     */
    public String getSceneName(int index) {
        return sceneBankFacade.getSceneName(index);
    }

    /**
     * Gets the number of scenes in the scene bank.
     */
    public int getSceneCount() {
        return sceneBankFacade.getSceneCount();
    }

    /**
     * Gets all scenes in the project with their details.
     *
     * @return A list of scene information maps containing index, name, and color
     */
    public List<Map<String, Object>> getAllScenesInfo() {
        logger.info("BitwigApiFacade: Getting all scenes info");
        return sceneBankFacade.getAllScenesInfo();
    }

    /**
     * Gets the current project name.
     *
     * @return The project name or "Unknown Project" if not available
     */
    public String getProjectName() {
        logger.info("BitwigApiFacade: Getting project name");
        String projectName = application.projectName().get();
        return projectName != null && !projectName.trim().isEmpty() ? projectName : Constants.DEFAULT_PROJECT_NAME;
    }

    /**
     * Checks if the audio engine is currently active.
     *
     * @return true if the audio engine is active, false otherwise
     */
    public boolean isAudioEngineActive() {
        logger.info("BitwigApiFacade: Checking audio engine status");
        return application.hasActiveEngine().get();
    }

    /**
     * Gets the project parameters from the project's remote controls page.
     * Only returns parameters where exists() is true.
     *
     * @return A list of ParameterInfo objects representing the existing project parameters
     * @throws BitwigApiException if project parameters cannot be retrieved due to API error
     */
    public List<ParameterInfo> getProjectParameters() throws BitwigApiException {
        final String operation = "getProjectParameters";
        logger.info("BitwigApiFacade: Getting project parameters");
        List<ParameterInfo> parameters = new ArrayList<>();

        try {
            for (int i = 0; i < projectParameterBank.getParameterCount(); i++) {
                RemoteControl parameter = projectParameterBank.getParameter(i);
                boolean exists = parameter.exists().get();

                if (exists) {
                    String name = parameter.name().get();
                    double value = parameter.value().get();
                    String displayValue = parameter.displayedValue().get();

                    // Handle null or empty names
                    if (name != null && name.trim().isEmpty()) {
                        name = null;
                    }

                    parameters.add(new ParameterInfo(i, name, value, displayValue));
                }
            }

            logger.info("BitwigApiFacade: Retrieved " + parameters.size() + " existing project parameters");
            return parameters;
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting project parameters: " + e.getMessage());
            throw new BitwigApiException(
                ErrorCode.BITWIG_API_ERROR,
                operation,
                "Failed to retrieve project parameters: " + e.getMessage()
            );
        }
    }

    // ========================================
    // Track Targeting (delegated to TrackTargetingFacade)
    // ========================================

    /**
     * Returns the number of tracks in the track bank.
     */
    public int getTrackBankSize() {
        return trackTargeting.getTrackBankSize();
    }

    /**
     * Returns the name of the track at the given index.
     */
    public String getTrackNameByIndex(int index) throws BitwigApiException {
        return trackTargeting.getTrackNameByIndex(index);
    }

    /**
     * Finds the index of a track by exact normalized name.
     */
    public int findTrackIndexByName(String trackName) throws BitwigApiException {
        return trackTargeting.findTrackIndexByName(trackName);
    }

    /**
     * Resolves deterministic fuzzy track candidates for a user query.
     */
    public Map<String, Object> resolveTrack(String query, String operation) throws BitwigApiException {
        return trackTargeting.resolveTrack(query, operation);
    }

    /**
     * Resolves a target track index using the shared selector precedence:
     * {@code track_index -> track_name -> selected track fallback}.
     */
    public int resolveTrackIndex(
        Integer trackIndex,
        String trackName,
        boolean useSelectedTrackFallback,
        String operation
    ) throws BitwigApiException {
        return trackTargeting.resolveTrackIndex(trackIndex, trackName, useSelectedTrackFallback, operation);
    }


    // ========================================
    // Devices (delegated to DeviceFacade)
    // ========================================

    /**
     * Checks whether a device is currently selected.
     */
    public boolean isDeviceSelected() {
        return deviceFacade.isDeviceSelected();
    }

    /**
     * Gets the name of the currently selected device.
     */
    public String getSelectedDeviceName() throws BitwigApiException {
        return deviceFacade.getSelectedDeviceName();
    }

    /**
     * Gets the remote-control parameters of the currently selected device.
     */
    public List<ParameterInfo> getSelectedDeviceParameters() {
        return deviceFacade.getSelectedDeviceParameters();
    }

    /**
     * Sets a parameter value on the currently selected device.
     */
    public void setSelectedDeviceParameter(int parameterIndex, double value) throws BitwigApiException {
        deviceFacade.setSelectedDeviceParameter(parameterIndex, value);
    }

    /**
     * Gets summary information about the currently selected device.
     */
    public Map<String, Object> getSelectedDeviceInfo() throws BitwigApiException {
        return deviceFacade.getSelectedDeviceInfo();
    }

    /**
     * Lists the devices on a resolved target track.
     */
    public List<Map<String, Object>> getDevicesOnTrack(Integer trackIndex, String trackName, Boolean getSelected)
            throws BitwigApiException {
        return deviceFacade.getDevicesOnTrack(trackIndex, trackName, getSelected);
    }

    /**
     * Gets detailed information about a resolved target device.
     */
    public io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getDeviceDetails(
            Integer trackIndex, String trackName, Integer deviceIndex, String deviceName, Boolean getForSelectedDevice)
            throws BitwigApiException {
        return deviceFacade.getDeviceDetails(trackIndex, trackName, deviceIndex, deviceName, getForSelectedDevice);
    }


    // ========================================
    // Transport (delegated to TransportFacade)
    // ========================================

    /**
     * Starts the transport playback.
     */
    public void startTransport() {
        transportFacade.startTransport();
    }

    /**
     * Stops the transport playback.
     */
    public void stopTransport() {
        transportFacade.stopTransport();
    }

    /**
     * Gets the current transport status (playback state, tempo, position).
     */
    public java.util.Map<String, Object> getTransportStatus() throws BitwigApiException {
        return transportFacade.getTransportStatus();
    }


    // ========================================
    // Clip Launcher Slots (delegated to ClipSlotFacade)
    // ========================================

    /**
     * Checks whether a track exists at the given index.
     */
    public boolean trackExists(String trackName) {
        return clipSlotFacade.trackExists(trackName);
    }

    /**
     * Returns the clip slot count for a track resolved by name.
     */
    public int getTrackClipCount(String trackName) {
        return clipSlotFacade.getTrackClipCount(trackName);
    }

    /**
     * Returns the clip slot count for a track resolved by index.
     */
    public int getTrackClipCountByIndex(int trackIndex) {
        return clipSlotFacade.getTrackClipCountByIndex(trackIndex);
    }

    /**
     * Launches a clip on the track at the given index.
     */
    public void launchClipByTrackIndex(int trackIndex, int clipIndex) throws BitwigApiException {
        clipSlotFacade.launchClipByTrackIndex(trackIndex, clipIndex);
    }

    /**
     * Launches a clip on the track with the given name.
     */
    public void launchClip(String trackName, int clipIndex) throws BitwigApiException {
        clipSlotFacade.launchClip(trackName, clipIndex);
    }

    /**
     * Gets details for the clip slots in a scene.
     */
    public Map<String, Object> getClipSlotDetails(int trackIndex, String trackName, int sceneIndex) {
        return clipSlotFacade.getClipSlotDetails(trackIndex, trackName, sceneIndex);
    }

    /**
     * Gets information about the currently selected clip slot.
     */
    public Map<String, Object> getSelectedClipSlotInfo() throws BitwigApiException {
        return clipSlotFacade.getSelectedClipSlotInfo();
    }


    // ========================================
    // Track Info (delegated to TrackInfoFacade)
    // ========================================

    /**
     * Gets summary information about the currently selected track.
     */
    public Map<String, Object> getSelectedTrackInfo() throws BitwigApiException {
        return trackInfoFacade.getSelectedTrackInfo();
    }

    /**
     * Gets summary information for every track in the project.
     */
    public List<Map<String, Object>> getAllTracksInfo(String typeFilter) throws BitwigApiException {
        return trackInfoFacade.getAllTracksInfo(typeFilter);
    }

    /**
     * Gets detailed information about a track by index.
     */
    public Map<String, Object> getTrackDetailsByIndex(int index) throws BitwigApiException {
        return trackInfoFacade.getTrackDetailsByIndex(index);
    }

    /**
     * Gets detailed information about a track by exact normalized name.
     */
    public Map<String, Object> getTrackDetailsByName(String trackName) throws BitwigApiException {
        return trackInfoFacade.getTrackDetailsByName(trackName);
    }

    /**
     * Gets detailed information about the currently selected track.
     */
    public Map<String, Object> getSelectedTrackDetails() throws BitwigApiException {
        return trackInfoFacade.getSelectedTrackDetails();
    }

}
