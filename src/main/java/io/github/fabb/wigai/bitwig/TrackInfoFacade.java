package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Send;
import com.bitwig.extension.controller.api.SendBank;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.error.WigAIErrorHandler;
import io.github.fabb.wigai.common.validation.ParameterValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Track inventory and detail reporting.
 *
 * <p>Builds the track summary and detailed-track payloads returned by the track query tools,
 * paginating the shared track bank created by {@link BitwigApiFacade}. Track resolution is
 * delegated to {@link TrackTargetingFacade} and device enumeration to {@link DeviceFacade},
 * so this class owns presentation of track state rather than its lookup.
 */
class TrackInfoFacade {

    private static final String DEFAULT_COLOR = "rgb(128,128,128)";

    private final ControllerHost host;
    private final TrackBank trackBank;
    private final CursorTrack cursorTrack;
    private final SceneBankFacade sceneBankFacade;
    private final TrackTargetingFacade trackTargeting;
    private final DeviceFacade deviceFacade;
    private final Logger logger;

    TrackInfoFacade(
        ControllerHost host,
        TrackBank trackBank,
        CursorTrack cursorTrack,
        SceneBankFacade sceneBankFacade,
        TrackTargetingFacade trackTargeting,
        DeviceFacade deviceFacade,
        Logger logger
    ) {
        this.host = host;
        this.trackBank = trackBank;
        this.cursorTrack = cursorTrack;
        this.sceneBankFacade = sceneBankFacade;
        this.trackTargeting = trackTargeting;
        this.deviceFacade = deviceFacade;
        this.logger = logger;
    }

    /**
     * Gets information about the currently selected track.
     *
     * @return A map containing selected track information, or null if no track is selected
     * @throws BitwigApiException if track is selected but info cannot be retrieved due to API error
     */
    public Map<String, Object> getSelectedTrackInfo() throws BitwigApiException {
        final String operation = "getSelectedTrackInfo";
        logger.info("BitwigApiFacade: Getting selected track information");

        if (!cursorTrack.exists().get()) {
            logger.info("BitwigApiFacade: No track selected");
            return null;
        }

        Map<String, Object> trackInfo = new LinkedHashMap<>();

        try {
            // Use cursorTrack.position() for project-absolute track index
            // (consistent with selected_clip_slot.track_index)
            String trackName = cursorTrack.name().get();
            int trackIndex = cursorTrack.position().get();

            trackInfo.put("index", trackIndex);
            trackInfo.put("name", trackName);
            trackInfo.put("type", cursorTrack.trackType().get().toLowerCase());
            trackInfo.put("is_group", cursorTrack.isGroup().get());
            trackInfo.put("muted", cursorTrack.mute().get());
            trackInfo.put("soloed", cursorTrack.solo().get());
            trackInfo.put("armed", cursorTrack.arm().get());

            logger.info("BitwigApiFacade: Retrieved selected track info: " + trackName);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected track info: " + e.getMessage());
            throw new BitwigApiException(
                ErrorCode.BITWIG_API_ERROR,
                operation,
                "Failed to retrieve selected track info: " + e.getMessage()
            );
        }

        return trackInfo;
    }

    /**
     * Gets a list of all tracks in the project with summary information.
     *
     * @param typeFilter Optional filter by track type (e.g., "audio", "instrument", "group", "effect", "master")
     * @return A list of track information maps
     */
    public List<Map<String, Object>> getAllTracksInfo(String typeFilter) {
        final String operation = "list_tracks";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            logger.info("BitwigApiFacade: Getting all tracks info" + (typeFilter != null ? " filtered by type: " + typeFilter : ""));
            Integer selectedTrackIndex = trackTargeting.getSelectedTrackProjectIndex();
            List<Map<String, Object>> tracksInfo = collectAllTrackSummaries(typeFilter, selectedTrackIndex);
            logger.info("BitwigApiFacade: Retrieved " + tracksInfo.size() + " tracks");
            return tracksInfo;
        });
    }

    private List<Map<String, Object>> collectAllTrackSummaries(String typeFilter, Integer selectedTrackIndex) {
        Map<Integer, Map<String, Object>> tracksByIndex = new TreeMap<>();
        trackTargeting.resetTrackBankToStart();
        host.requestFlush();

        int iterationCount = 0;
        while (true) {
            captureVisibleTracks(typeFilter, selectedTrackIndex, tracksByIndex);
            boolean canScrollForward = trackBank.canScrollForwards().get();
            if (!canScrollForward || iterationCount >= TrackTargetingFacade.MAX_TRACK_PAGINATION_STEPS) {
                if (iterationCount >= TrackTargetingFacade.MAX_TRACK_PAGINATION_STEPS) {
                    logger.warn("BitwigApiFacade: Reached track pagination limit, stopping enumeration early");
                }
                break;
            }
            trackBank.scrollPageForwards();
            host.requestFlush();
            iterationCount++;
        }

        trackTargeting.resetTrackBankToStart();
        host.requestFlush();
        return new ArrayList<>(tracksByIndex.values());
    }

    private void captureVisibleTracks(
            String typeFilter,
            Integer selectedTrackIndex,
            Map<Integer, Map<String, Object>> tracksByIndex) {

        for (int slotIndex = 0; slotIndex < trackBank.getSizeOfBank(); slotIndex++) {
            Track track = trackBank.getItemAt(slotIndex);
            if (!track.exists().get()) {
                continue;
            }

            String trackType = track.trackType().get().toLowerCase();
            if (typeFilter != null && !typeFilter.equals(trackType)) {
                continue;
            }

            Map<String, Object> trackInfo = buildTrackSummary(track, slotIndex, trackType, selectedTrackIndex);
            Integer projectIndex = (Integer) trackInfo.get("index");
            tracksByIndex.putIfAbsent(projectIndex, trackInfo);
        }
    }

    private Map<String, Object> buildTrackSummary(Track track, int slotIndex, String trackType, Integer selectedTrackIndex) {
            Map<String, Object> trackInfo = new LinkedHashMap<>();

            int projectIndex = trackTargeting.resolveTrackProjectIndex(track, slotIndex);
            trackInfo.put("index", projectIndex);
            trackInfo.put("name", track.name().get());
            trackInfo.put("type", trackType);
            trackInfo.put("is_group", track.isGroup().get());
            trackInfo.put("parent_group_index", trackTargeting.resolveParentGroupIndex(track));
            trackInfo.put("activated", track.isActivated().get());
            trackInfo.put("color", formatTrackColor(track.color().get()));
            boolean isSelected = selectedTrackIndex != null && selectedTrackIndex == projectIndex;
            trackInfo.put("is_selected", isSelected);
            trackInfo.put("devices", deviceFacade.getTrackDevices(slotIndex));
            return trackInfo;
        }

    /**
     * Gets detailed information about a track by absolute project index.
     */
    public Map<String, Object> getTrackDetailsByIndex(int index) throws BitwigApiException {
        final String operation = "get_track_details";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            Track track = trackTargeting.requireTrackByIndex(index, operation);
            return buildDetailedTrackInfo(track, index);
        });
    }

    /**
     * Gets detailed information about a track by exact normalized name (trim + case-insensitive).
     */
    public Map<String, Object> getTrackDetailsByName(String trackName) throws BitwigApiException {
        final String operation = "get_track_details";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "track_name", operation);
            int index = trackTargeting.findTrackIndexByName(trackName);
            return getTrackDetailsByIndex(index);
        });
    }

    /**
     * Gets detailed information about the currently selected track, or null if none.
     */
    public Map<String, Object> getSelectedTrackDetails() {
        try {
            Integer selectedTrackIndex = trackTargeting.getSelectedTrackProjectIndex();
            if (selectedTrackIndex == null) {
                return null;
            }
            Optional<Track> selectedTrack = trackTargeting.findTrackByIndex(selectedTrackIndex);
            if (selectedTrack.isPresent()) {
                return buildDetailedTrackInfo(selectedTrack.get(), selectedTrackIndex);
            }

            // Fallback when selected track cannot be materialized in the current bank window.
            String name = cursorTrack.name().get();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("index", selectedTrackIndex);
            info.put("name", name);
            info.put("type", cursorTrack.trackType().get().toLowerCase());
            info.put("is_group", cursorTrack.isGroup().get());
            info.put("parent_group_index", null);
            info.put("activated", true);
            info.put("color", DEFAULT_COLOR);
            info.put("is_selected", true);
            info.put("devices", List.of());
            info.put("volume", cursorTrack.volume().value().get());
            info.put("volume_str", safeDisplay(cursorTrack.volume().displayedValue().get()));
            info.put("pan", cursorTrack.pan().value().get());
            info.put("pan_str", safeDisplay(cursorTrack.pan().displayedValue().get()));
            info.put("muted", cursorTrack.mute().get());
            info.put("soloed", cursorTrack.solo().get());
            info.put("armed", cursorTrack.arm().get());
            info.put("monitor_enabled", cursorTrack.isMonitoring().get());
            String mode = cursorTrack.monitorMode().get();
            boolean cursorAuto = mode != null && mode.toLowerCase().contains("auto");
            info.put("auto_monitor_enabled", cursorAuto);
            info.put("sends", List.of());
            info.put("clips", List.of());
            return info;
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected track details: " + e.getMessage());
            return null;
        }
    }

    /**
     * Builds a detailed track info map including base fields, device summaries, channel params,
     * sends and clip launcher slots.
     */
    private Map<String, Object> buildDetailedTrackInfo(Track track, int index) {
        Map<String, Object> trackInfo = new LinkedHashMap<>();
        try {
            // Basic fields similar to getAllTracksInfo
            int projectIndex = trackTargeting.resolveTrackProjectIndex(track, index);
            trackInfo.put("index", projectIndex);
            String trackName = track.name().get();
            trackInfo.put("name", trackName);
            String trackType = track.trackType().get().toLowerCase();
            trackInfo.put("type", trackType);
            trackInfo.put("is_group", track.isGroup().get());
            trackInfo.put("parent_group_index", trackTargeting.resolveParentGroupIndex(track));
            trackInfo.put("activated", track.isActivated().get());
            trackInfo.put("color", formatTrackColor(track.color().get()));
            // Selected state
            Integer selectedIndex = trackTargeting.getSelectedTrackProjectIndex();
            boolean isSelected = selectedIndex != null && selectedIndex == projectIndex;
            trackInfo.put("is_selected", isSelected);
            // Devices
            trackInfo.put("devices", deviceFacade.getTrackDevices(index));

            // Channel parameters
            trackInfo.put("volume", track.volume().value().get());
            trackInfo.put("volume_str", safeDisplay(track.volume().displayedValue().get()));
            trackInfo.put("pan", track.pan().value().get());
            trackInfo.put("pan_str", safeDisplay(track.pan().displayedValue().get()));
            trackInfo.put("muted", track.mute().get());
            trackInfo.put("soloed", track.solo().get());
            trackInfo.put("armed", track.arm().get());
            // Monitoring (properties marked as interested in constructor)
            boolean monitoring = track.isMonitoring().get();
            String mode = track.monitorMode().get();
            boolean autoMon = mode != null && mode.toLowerCase().contains("auto");
            trackInfo.put("monitor_enabled", monitoring);
            trackInfo.put("auto_monitor_enabled", autoMon);

            // Sends
            List<Map<String, Object>> sends = new ArrayList<>();
            try {
                SendBank sendBank = track.sendBank();
                int sendCount = sendBank.getSizeOfBank();
                for (int i = 0; i < sendCount; i++) {
                    Send send = sendBank.getItemAt(i);
                    Map<String, Object> sendMap = new LinkedHashMap<>();
                    sendMap.put("name", send.name().get());
                    sendMap.put("volume", send.value().get());
                    sendMap.put("volume_str", safeDisplay(send.displayedValue().get()));
                    sendMap.put("activated", send.isEnabled().get());
                    sends.add(sendMap);
                }
            } catch (Exception e) {
                logger.warn("BitwigApiFacade: Error reading sends for track " + trackName + ": " + e.getMessage());
            }
            trackInfo.put("sends", sends);

            // Clips
            List<Map<String, Object>> clips = new ArrayList<>();
            try {
                ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
                int slots = slotBank.getSizeOfBank();
                for (int s = 0; s < slots; s++) {
                    ClipLauncherSlot slot = slotBank.getItemAt(s);
                    Map<String, Object> slotMap = new LinkedHashMap<>();
                    slotMap.put("slot_index", s);
                    // Scene name from scene bank facade
                    String sceneName = sceneBankFacade.getSceneName(s);
                    slotMap.put("scene_name", sceneName);
                    boolean hasContent = false;
                    try { hasContent = slot.hasContent().get(); } catch (Exception ignored) {}
                    slotMap.put("has_content", hasContent);

                    // Clip name from slot name value if available
                    String clipName = null;
                    try {
                        clipName = slot.name().get();
                        if (clipName != null && clipName.trim().isEmpty()) clipName = null;
                    } catch (Exception ignored) {}
                    slotMap.put("clip_name", clipName);
                    try {
                        Color c = slot.color().get();
                        slotMap.put("clip_color", c != null ? formatTrackColor(c) : null);
                    } catch (Exception e) {
                        slotMap.put("clip_color", null);
                    }
                    // Removed unsupported length / is_looping fields

                    // Playback state flags
                    try { slotMap.put("is_playing", slot.isPlaying().get()); } catch (Exception e) { slotMap.put("is_playing", null); }
                    try { slotMap.put("is_recording", slot.isRecording().get()); } catch (Exception e) { slotMap.put("is_recording", null); }
                    try { slotMap.put("is_playback_queued", slot.isPlaybackQueued().get()); } catch (Exception e) { slotMap.put("is_playback_queued", null); }

                    clips.add(slotMap);
                }
            } catch (Exception e) {
                logger.warn("BitwigApiFacade: Error reading clip slots for track " + trackName + ": " + e.getMessage());
            }
            trackInfo.put("clips", clips);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error building detailed track info: " + e.getMessage());
        }
        return trackInfo;
    }

    private String safeDisplay(String value) {
        return value != null ? value : "";
    }

    /**
     * Formats a ColorValue object into an RGB string format.
     */
    private String formatTrackColor(Color colorValue) {
        try {
            return String.format("rgb(%d,%d,%d)",
                (int) (colorValue.getRed() * 255),
                (int) (colorValue.getGreen() * 255),
                (int) (colorValue.getBlue() * 255));

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error formatting track color: " + e.getMessage());
            return DEFAULT_COLOR; // Default gray fallback
        }
    }
}
