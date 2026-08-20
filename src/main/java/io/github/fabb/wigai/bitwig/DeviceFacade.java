package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.RemoteControlsPage;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.error.WigAIErrorHandler;
import io.github.fabb.wigai.common.validation.ParameterValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Device enumeration, inspection, and parameter control.
 *
 * <p>Covers the cursor device and its remote-control page as well as per-track device banks.
 * Operates on the cursor, banks, and track-targeting collaborator already created by
 * {@link BitwigApiFacade} rather than creating its own, so device state stays single-sourced.
 */
class DeviceFacade {

    private final CursorDevice cursorDevice;
    private final RemoteControlsPage deviceParameterBank;
    private final TrackBank trackBank;
    private final CursorTrack cursorTrack;
    private final List<DeviceBank> trackDeviceBanks;
    private final TrackTargetingFacade trackTargeting;
    private final Logger logger;

    DeviceFacade(
        CursorDevice cursorDevice,
        RemoteControlsPage deviceParameterBank,
        TrackBank trackBank,
        CursorTrack cursorTrack,
        List<DeviceBank> trackDeviceBanks,
        TrackTargetingFacade trackTargeting,
        Logger logger
    ) {
        this.cursorDevice = cursorDevice;
        this.deviceParameterBank = deviceParameterBank;
        this.trackBank = trackBank;
        this.cursorTrack = cursorTrack;
        this.trackDeviceBanks = trackDeviceBanks;
        this.trackTargeting = trackTargeting;
        this.logger = logger;
    }

    /**
     * Checks if a device is currently selected.
     *
     * @return true if a device is selected, false otherwise
     */
    public boolean isDeviceSelected() {
        logger.info("BitwigApiFacade: Checking if device is selected");
        return cursorDevice.exists().get();
    }

    /**
     * Gets the name of the currently selected device.
     *
     * @return The device name
     * @throws BitwigApiException if no device is selected
     */
    public String getSelectedDeviceName() throws BitwigApiException {
        final String operation = "getSelectedDeviceName";
        logger.info("BitwigApiFacade: Getting selected device name");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!isDeviceSelected()) {
                throw new BitwigApiException(
                    ErrorCode.DEVICE_NOT_SELECTED,
                    operation,
                    "No device is currently selected"
                );
            }
            return cursorDevice.name().get();
        });
    }

    /**
     * Gets the parameters of the currently selected device.
     *
     * @return A list of ParameterInfo objects representing all addressable parameters
     */
    public List<ParameterInfo> getSelectedDeviceParameters() {
        logger.info("BitwigApiFacade: Getting selected device parameters");
        List<ParameterInfo> parameters = new ArrayList<>();

        if (!isDeviceSelected()) {
            logger.info("BitwigApiFacade: No device selected, returning empty parameters list");
            return parameters;
        }

        for (int i = 0; i < deviceParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = deviceParameterBank.getParameter(i);
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

        logger.info("BitwigApiFacade: Retrieved " + parameters.size() + " parameters");
        return parameters;
    }

    /**
     * Sets the value of a specific parameter for the currently selected device.
     *
     * @param parameterIndex The index of the parameter to set (0 to parameterCount-1)
     * @param value          The value to set (0.0-1.0)
     * @throws BitwigApiException if parameterIndex is out of range, value is out of range, no device is selected, or Bitwig API error occurs
     */
    public void setSelectedDeviceParameter(int parameterIndex, double value) throws BitwigApiException {
        final String operation = "setSelectedDeviceParameter";
        logger.info("BitwigApiFacade: Setting parameter " + parameterIndex + " to " + value);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if device is selected
            if (!isDeviceSelected()) {
                throw new BitwigApiException(
                    ErrorCode.DEVICE_NOT_SELECTED,
                    operation,
                    "No device is currently selected"
                );
            }

            // Validate parameter index against actual parameter count
            int parameterCount = deviceParameterBank.getParameterCount();
            ParameterValidator.validateParameterIndex(parameterIndex, parameterCount, operation);

            // Validate value range
            ParameterValidator.validateParameterValue(value, operation);

            // Set the parameter value
            RemoteControl parameter = deviceParameterBank.getParameter(parameterIndex);
            parameter.value().set(value);

            logger.info("BitwigApiFacade: Successfully set parameter " + parameterIndex + " to " + value);
        });
    }

    /**
     * Gets information about the currently selected device including track context, device info, and parameters.
     *
     * @return A map containing selected device information, or null if no device is selected
     * @throws BitwigApiException if device is selected but info cannot be retrieved due to API error
     */
    public Map<String, Object> getSelectedDeviceInfo() throws BitwigApiException {
        final String operation = "getSelectedDeviceInfo";
        logger.info("BitwigApiFacade: Getting selected device information");

        if (!cursorDevice.exists().get()) {
            logger.info("BitwigApiFacade: No device selected");
            return null;
        }

        Map<String, Object> deviceInfo = new LinkedHashMap<>();

        try {
            // Get track information where the device is located
            // Use cursorTrack.position() for project-absolute track index
            // (consistent with selected_track.index and selected_clip_slot.track_index)
            String trackName = cursorTrack.name().get();
            int trackIndex = cursorTrack.position().get();

            deviceInfo.put("track_name", trackName);
            deviceInfo.put("track_index", trackIndex);

            // Get device position/index in the device chain
            // Note: Bitwig API doesn't directly expose device index in chain, so we use 0 as default
            // This could be enhanced in the future with more complex logic to determine actual position
            deviceInfo.put("index", 0);

            // Get device name and bypass status
            deviceInfo.put("name", cursorDevice.name().get());
            deviceInfo.put("bypassed", !cursorDevice.isEnabled().get());

            // Get device parameters
            List<Map<String, Object>> parametersArray = new ArrayList<>();
            for (ParameterInfo p : getSelectedDeviceParameters()) {
                    Map<String, Object> paramMap = new LinkedHashMap<>();
                    paramMap.put("index", p.index());
                    paramMap.put("name", p.name());
                    paramMap.put("value", p.value());
                    paramMap.put("display_value", p.display_value());
                    parametersArray.add(paramMap);
                            }
            deviceInfo.put("parameters", parametersArray);

            logger.info("BitwigApiFacade: Retrieved selected device info: " + cursorDevice.name().get());
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected device info: " + e.getMessage());
            throw new BitwigApiException(
                ErrorCode.BITWIG_API_ERROR,
                operation,
                "Failed to retrieve selected device info: " + e.getMessage()
            );
        }

        return deviceInfo;
    }

    /**
     * Gets detailed device information including remote controls and pages.
     *
     * @param trackIndex The track index (nullable)
     * @param trackName The track name (nullable)
     * @param deviceIndex The device index (nullable)
     * @param deviceName The device name (nullable)
     * @param getForSelectedDevice Whether to get selected device (nullable)
     * @return DeviceDetailsResult containing complete device information
     * @throws BitwigApiException if device/track not found or parameters invalid
     */
    public io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getDeviceDetails(
            Integer trackIndex, String trackName, Integer deviceIndex, String deviceName, Boolean getForSelectedDevice)
            throws BitwigApiException {
        final String operation = "getDeviceDetails";

        try {
            // Determine operation mode
            boolean isSelectedDeviceMode = Boolean.TRUE.equals(getForSelectedDevice) ||
                (trackIndex == null && trackName == null && deviceIndex == null && deviceName == null);

            if (isSelectedDeviceMode) {
                return getSelectedDeviceDetails();
            } else {
                return getTargetDeviceDetails(trackIndex, trackName, deviceIndex, deviceName);
            }

        } catch (BitwigApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("BitwigApiFacade: Unexpected error in " + operation + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to get device details: " + e.getMessage());
        }
    }

    /**
     * Gets details for the currently selected device.
     */
    private io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getSelectedDeviceDetails()
            throws BitwigApiException {
        final String operation = "getSelectedDeviceDetails";

        // Check if device is selected
        if (!cursorDevice.exists().get()) {
            throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation,
                "No device is currently selected");
        }

        // Check if cursor track exists
        if (!cursorTrack.exists().get()) {
            throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                "No track is currently selected");
        }

        // Get track index directly from cursor track position
        int resolvedTrackIndex = cursorTrack.position().get();
        String selectedTrackName = cursorTrack.name().get();

        // Verify the position is within our track bank range
        if (resolvedTrackIndex < 0 || resolvedTrackIndex >= trackBank.getSizeOfBank()) {
            throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                "Selected track position " + resolvedTrackIndex + " is outside track bank range [0, " + (trackBank.getSizeOfBank() - 1) + "]");
        }

        // Get device basic properties
        String deviceName = cursorDevice.name().get();
        String rawDeviceType = cursorDevice.deviceType().get();
        String mappedType = mapDeviceType(rawDeviceType);
        boolean isEnabled = cursorDevice.isEnabled().get();
        boolean isBypassed = !isEnabled;

        // Find device index by comparing with devices in the track
        int deviceIndex = findDeviceIndexInTrack(resolvedTrackIndex, deviceName);

        // Get remote controls for the currently selected page
        List<ParameterInfo> remoteControls = getDeviceRemoteControlsFromCursor();

        return new io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult(
            resolvedTrackIndex,
            selectedTrackName,
            deviceIndex,
            deviceName,
            mappedType,
            isBypassed,
            true, // is_selected = true since this is the selected device
            remoteControls
        );
    }

    /**
     * Gets details for a device specified by track and device identifiers.
     */
    private io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getTargetDeviceDetails(
            Integer trackIndex, String trackName, Integer deviceIndex, String deviceName)
            throws BitwigApiException {
        final String operation = "getTargetDeviceDetails";

        // Resolve target track
        Track targetTrack;
        int resolvedTrackIndex;

        resolvedTrackIndex = trackTargeting.resolveTrackIndex(trackIndex, trackName, true, operation);
        targetTrack = trackTargeting.requireTrackByIndex(resolvedTrackIndex, operation);

        // Resolve target device
        DeviceBank deviceBank = trackDeviceBanks.get(resolvedTrackIndex);
        Device targetDevice = null;
        int resolvedDeviceIndex = -1;

        if (deviceIndex != null) {
            if (deviceIndex < 0 || deviceIndex >= deviceBank.getSizeOfBank()) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER_INDEX, operation,
                    "Device index " + deviceIndex + " is out of range [0, " + (deviceBank.getSizeOfBank() - 1) + "]");
            }
            targetDevice = deviceBank.getItemAt(deviceIndex);
            if (!targetDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, operation,
                    "Device at index " + deviceIndex + " does not exist on track");
            }
            resolvedDeviceIndex = deviceIndex;
        } else if (deviceName != null) {
            for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
                Device device = deviceBank.getItemAt(i);
                if (device.exists().get() && deviceName.equals(device.name().get())) {
                    targetDevice = device;
                    resolvedDeviceIndex = i;
                    break;
                }
            }
            if (targetDevice == null) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, operation,
                    "No device found with name '" + deviceName + "' on track");
            }
        } else {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                "Either deviceIndex or deviceName must be provided");
        }

        // Get device basic properties
        String actualDeviceName = targetDevice.name().get();
        String rawDeviceType = targetDevice.deviceType().get();
        String mappedType = mapDeviceType(rawDeviceType);
        boolean isEnabled = targetDevice.isEnabled().get();
        boolean isBypassed = !isEnabled;

        // Determine if this device is selected by comparing with cursor device
        boolean isSelected = isDeviceSelectedComparison(resolvedTrackIndex, resolvedDeviceIndex, actualDeviceName);

        // For non-selected devices, remote control access is limited
        List<ParameterInfo> remoteControls = getDeviceRemoteControlsFromDevice(targetDevice);

        return new io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult(
            resolvedTrackIndex,
            targetTrack.name().get(),
            resolvedDeviceIndex,
            actualDeviceName,
            mappedType,
            isBypassed,
            isSelected,
            remoteControls
        );
    }

    /**
     * Gets detailed device information for a specific track identified by index, name, or selected track.
     *
     * @param trackIndex The 0-based track index (optional)
     * @param trackName The exact track name (optional)
     * @param getSelected Whether to get devices for the selected track (optional)
     * @return List of device summary objects with detailed information
     * @throws BitwigApiException if the track is not found or API access fails
     */
    public List<Map<String, Object>> getDevicesOnTrack(Integer trackIndex, String trackName, Boolean getSelected)
            throws BitwigApiException {
        final String operation = "list_devices_on_track";

        try {
            int resolvedTrackIndex = trackTargeting.resolveTrackIndex(
                trackIndex,
                trackName,
                Boolean.TRUE.equals(getSelected),
                operation
            );
            Track targetTrack = trackTargeting.requireTrackByIndex(resolvedTrackIndex, operation);

            // Get devices for the resolved track
            return getDetailedTrackDevices(resolvedTrackIndex, targetTrack);

        } catch (BitwigApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("BitwigApiFacade: Unexpected error in " + operation + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to get devices for track: " + e.getMessage());
        }
    }

    /**
     * Gets detailed device information for a specific track with enhanced device details.
     *
     * @param trackIndex The resolved track index
     * @param track The target track object
     * @return List of detailed device information maps
     */
    private List<Map<String, Object>> getDetailedTrackDevices(int trackIndex, Track track) {
        List<Map<String, Object>> devices = new ArrayList<>();

        try {
            // Use the pre-existing device bank for this track
            if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
                logger.warn("BitwigApiFacade: Invalid track index for devices: " + trackIndex);
                return devices;
            }

            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);

            // Get cursor device info for selection comparison (only if we have a selected track and device)
            String selectedDeviceName = null;
            Integer selectedTrackIndex = trackTargeting.getSelectedTrackProjectIndex();
            boolean isSelectedTrack = selectedTrackIndex != null && selectedTrackIndex == trackIndex;
            if (isSelectedTrack && cursorDevice.exists().get()) {
                selectedDeviceName = cursorDevice.name().get();
            }

            // Iterate through device bank with proper enumeration
            for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
                Device device = deviceBank.getItemAt(i);

                // Check if device exists
                if (!device.exists().get()) {
                    continue;
                }

                Map<String, Object> deviceInfo = new LinkedHashMap<>();
                deviceInfo.put("index", i);

                // Get device name
                String deviceName = device.name().get();
                deviceInfo.put("name", deviceName);

                // Get and map device type
                String rawDeviceType = device.deviceType().get();
                String mappedType = mapDeviceType(rawDeviceType);
                deviceInfo.put("type", mappedType);

                // Get device bypassed status (bypassed = !enabled)
                boolean isEnabled = device.isEnabled().get();
                deviceInfo.put("bypassed", !isEnabled);

                // Determine if this device is selected
                boolean isDeviceSelected = false;
                if (isSelectedTrack && selectedDeviceName != null) {
                    // Use name matching for device selection comparison
                    isDeviceSelected = deviceName.equals(selectedDeviceName);
                }
                deviceInfo.put("is_selected", isDeviceSelected);

                // Optional UI state fields - only include if available
                // Per story requirements, omit these fields if not available from API
                // deviceInfo.put("is_expanded", null);  // Omitted - not available from Controller API
                // deviceInfo.put("is_window_open", null);  // Omitted - not available from Controller API

                devices.add(deviceInfo);
            }

            logger.info("BitwigApiFacade: Found " + devices.size() + " devices on track: " + track.name().get());

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting detailed devices for track index " + trackIndex + ": " + e.getMessage());
        }

        return devices;
    }

    /**
     * Gets device information for a specific track by index.
     *
     * @param trackIndex The index of the track to get devices from
     * @return A list of device information maps
     * @throws RuntimeException if device enumeration fails
     */
    List<Map<String, Object>> getTrackDevices(int trackIndex) {
        List<Map<String, Object>> devices = new ArrayList<>();

        // Use the pre-existing device bank for this track that was created in the constructor
        // and already has its properties marked as interested
        if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
            logger.warn("BitwigApiFacade: Invalid track index for devices: " + trackIndex);
            return devices;
        }

        DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
        Track track = trackBank.getItemAt(trackIndex);

        // Create device info for each existing device
        for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
            Device device = deviceBank.getItemAt(i);

            // Check if device exists - this should work since markInterested() was called in constructor
            if (!device.exists().get()) {
                continue;
            }

            Map<String, Object> deviceInfo = new LinkedHashMap<>();
            deviceInfo.put("index", i);

            // Get device name
            String deviceName = device.name().get();
            deviceInfo.put("name", deviceName);

            // Get device type
            String deviceType = device.deviceType().get();
            deviceInfo.put("type", mapDeviceType(deviceType));

            devices.add(deviceInfo);
        }

        logger.info("BitwigApiFacade: Found " + devices.size() + " devices on track: " + track.name().get());

        return devices;
    }

    /**
     * Maps Bitwig device types to standardized type names.
     *
     * @param rawDeviceType The raw device type from Bitwig API
     * @return Mapped device type: "Instrument", "AudioFX", "NoteFX", or "Unknown"
     */
    private String mapDeviceType(String rawDeviceType) {
        if (rawDeviceType == null) {
            return "Unknown";
        }

        String lowerType = rawDeviceType.toLowerCase();

        if (lowerType.contains("instrument")) {
            return "Instrument";
        } else if (lowerType.contains("note") || lowerType.contains("midi")) {
            return "NoteFX";
        } else if (lowerType.contains("audio") || lowerType.contains("effect") || lowerType.contains("fx")) {
            return "AudioFX";
        } else {
            return "Unknown";
        }
    }

    /**
     * Gets remote controls from the cursor device (selected device).
     *
     * This directly returns the existing device parameters since they represent
     * the same data (remote controls for the currently selected page).
     */
    private List<ParameterInfo> getDeviceRemoteControlsFromCursor() {
        // Direct access to selected device parameters - no conversion needed
        return getSelectedDeviceParameters();
    }

    /**
     * Gets remote controls from a specific device (non-cursor).
     *
     * Note: The Bitwig Controller API does not easily expose remote controls
     * for non-selected devices without temporarily selecting them, which would
     * disrupt the user experience. Therefore, this method returns an empty list.
     */
    private List<ParameterInfo> getDeviceRemoteControlsFromDevice(Device device) {
        // Limitation: Bitwig Controller API does not provide easy access to
        // remote controls for non-selected devices
        return new ArrayList<>();
    }

    /**
     * Finds the index of a device in a track by comparing names.
     */
    private int findDeviceIndexInTrack(int trackIndex, String deviceName) {
        if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
            return -1;
        }

        DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
        for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
            Device device = deviceBank.getItemAt(i);
            if (device.exists().get() && deviceName.equals(device.name().get())) {
                return i;
            }
        }
        return -1; // Not found
    }

    /**
     * Determines if a device is selected by comparing with cursor device.
     */
    private boolean isDeviceSelectedComparison(int trackIndex, int deviceIndex, String deviceName) {
        // Check if cursor device exists
        if (!cursorDevice.exists().get() || !cursorTrack.exists().get()) {
            return false;
        }

        // Compare track by project-absolute index semantics
        Integer selectedTrackIndex = trackTargeting.getSelectedTrackProjectIndex();
        if (selectedTrackIndex == null || selectedTrackIndex != trackIndex) {
            return false;
        }

        // Compare device name
        String selectedDeviceName = cursorDevice.name().get();
        return deviceName.equals(selectedDeviceName);
    }
}
