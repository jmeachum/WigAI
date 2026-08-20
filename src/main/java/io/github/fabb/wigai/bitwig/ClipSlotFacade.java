package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.CursorTrack;
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

/**
 * Clip launcher slot inspection and clip/scene launching.
 *
 * <p>Reads and triggers slots on the shared track bank and on the cursor track's slot bank,
 * both already created and marked-interested by {@link BitwigApiFacade}. Track resolution is
 * delegated to {@link TrackTargetingFacade} so selector precedence stays single-sourced.
 */
class ClipSlotFacade {

    private final TrackBank trackBank;
    private final CursorTrack cursorTrack;
    private final ClipLauncherSlotBank cursorClipLauncherSlotBank;
    private final SceneBankFacade sceneBankFacade;
    private final TrackTargetingFacade trackTargeting;
    private final Logger logger;

    ClipSlotFacade(
        TrackBank trackBank,
        CursorTrack cursorTrack,
        ClipLauncherSlotBank cursorClipLauncherSlotBank,
        SceneBankFacade sceneBankFacade,
        TrackTargetingFacade trackTargeting,
        Logger logger
    ) {
        this.trackBank = trackBank;
        this.cursorTrack = cursorTrack;
        this.cursorClipLauncherSlotBank = cursorClipLauncherSlotBank;
        this.sceneBankFacade = sceneBankFacade;
        this.trackTargeting = trackTargeting;
        this.logger = logger;
    }

    /**
     * Checks whether a uniquely matching track exists by exact normalized name.
     *
     * @param trackName the name of the track to check
     * @return {@code true} when exactly one matching track exists; {@code false} when no track matches
     * @throws BitwigApiException when lookup fails due to ambiguity (duplicate exact-name matches) or other
     * semantic/system errors
     */
    public boolean trackExists(String trackName) {
        try {
            trackTargeting.findTrackIndexByName(trackName);
            return true;
        } catch (BitwigApiException e) {
            if (e.getErrorCode() == ErrorCode.TRACK_NOT_FOUND) {
                return false;
            }
            // Preserve ambiguity and other semantic failures for callers that need explicit handling.
            throw e;
        }
    }

    /**
     * Gets the number of clip slots available for a uniquely resolved track name.
     * Prefer {@link #getTrackClipCountByIndex(int)} for deterministic targeting.
     *
     * @param trackName the track name to resolve
     * @return the number of clip slots, or {@code 0} when the track is not found
     * @throws BitwigApiException when track-name lookup is ambiguous or fails for non-{@code TRACK_NOT_FOUND}
     * reasons
     */
    @Deprecated(forRemoval = false)
    public int getTrackClipCount(String trackName) {
        try {
            int resolvedTrackIndex = trackTargeting.findTrackIndexByName(trackName);
            return getTrackClipCountByIndex(resolvedTrackIndex);
        } catch (BitwigApiException e) {
            if (e.getErrorCode() == ErrorCode.TRACK_NOT_FOUND) {
                logger.warn("BitwigApiFacade: Track '" + trackName + "' not found for clip count check");
                return 0;
            }
            throw e;
        }
    }

    /**
     * Gets the number of clip slots available for a track by index.
     * Avoids name-based lookup ambiguity when multiple tracks share the same name.
     *
     * @param trackIndex The zero-based track index
     * @return The number of clip slots, or 0 if track not found at index
     */
    public int getTrackClipCountByIndex(int trackIndex) {
        Optional<Track> trackOpt = trackTargeting.findTrackByIndex(trackIndex);
        if (trackOpt.isPresent()) {
            return trackOpt.get().clipLauncherSlotBank().getSizeOfBank();
        }
        return 0;
    }

    /**
     * Launches a clip at the specified track index and clip index.
     * Avoids name-based lookup ambiguity when multiple tracks share the same name.
     *
     * @param trackIndex The zero-based track index
     * @param clipIndex The zero-based clip slot index to launch
     * @throws BitwigApiException if track not found, clip index invalid, or launch fails
     */
    public void launchClipByTrackIndex(int trackIndex, int clipIndex) throws BitwigApiException {
        final String operation = "launchClipByTrackIndex";

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateClipIndex(clipIndex, operation);

            Optional<Track> trackOpt = trackTargeting.findTrackByIndex(trackIndex);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + trackIndex + " does not exist",
                    Map.of("trackIndex", trackIndex)
                );
            }

            Track targetTrack = trackOpt.get();
            ClipLauncherSlotBank slotBank = targetTrack.clipLauncherSlotBank();
            if (clipIndex >= slotBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER_INDEX,
                    operation,
                    "Clip index " + clipIndex + " out of bounds for track at index " + trackIndex,
                    Map.of("trackIndex", trackIndex, "clipIndex", clipIndex, "maxIndex", slotBank.getSizeOfBank() - 1)
                );
            }

            ClipLauncherSlot slot = slotBank.getItemAt(clipIndex);
            slot.launch();

            logger.info("BitwigApiFacade: Successfully launched clip at track[" + trackIndex + "][" + clipIndex + "]");
        });
    }

    /**
     * Launches a clip at the specified track and clip index.
     * Prefer {@link #launchClipByTrackIndex(int, int)} for deterministic targeting.
     *
     * @param trackName The name of the track containing the clip
     * @param clipIndex The zero-based index of the clip slot to launch
     * @throws BitwigApiException if track is not found, clip index is invalid, or launch fails
     */
    @Deprecated(forRemoval = false)
    public void launchClip(String trackName, int clipIndex) throws BitwigApiException {
        int resolvedTrackIndex = trackTargeting.findTrackIndexByName(trackName);
        launchClipByTrackIndex(resolvedTrackIndex, clipIndex);
    }

    /**
     * Gets detailed clip slot information for a specific track and scene index.
     *
     * @param trackIndex The 0-based track index
     * @param trackName The name of the track
     * @param sceneIndex The 0-based scene index
     * @return Map containing detailed clip slot information
     */
    public Map<String, Object> getClipSlotDetails(int trackIndex, String trackName, int sceneIndex) {
        logger.info("BitwigApiFacade: Getting clip slot details for track " + trackIndex + " (" + trackName + ") at scene " + sceneIndex);

        Map<String, Object> slotInfo = new LinkedHashMap<>();

        try {
            // Get the track
            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                return null; // Track doesn't exist
            }

            // Basic track information
            slotInfo.put("track_index", trackIndex);
            slotInfo.put("track_name", trackName);

            // Get the clip launcher slot at the scene index
            ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            if (sceneIndex >= slotBank.getSizeOfBank()) {
                // Scene index is beyond the available slots for this track
                return null;
            }

            ClipLauncherSlot slot = slotBank.getItemAt(sceneIndex);

            // Check if slot has content (marked as interested in constructor)
            boolean hasContent = slot.hasContent().get();
            slotInfo.put("has_content", hasContent);

            // Clip name (only if has content, marked as interested in constructor)
            String clipName = null;
            if (hasContent) {
                String name = slot.name().get();
                clipName = (name != null && name.trim().isEmpty()) ? null : name;
            }
            slotInfo.put("clip_name", clipName);

            // Clip color (only if has content, marked as interested in constructor)
            String clipColor = null;
            if (hasContent) {
                Color color = slot.color().get();
                if (color != null) {
                    clipColor = String.format("#%02X%02X%02X",
                        (int) (color.getRed() * 255),
                        (int) (color.getGreen() * 255),
                        (int) (color.getBlue() * 255));
                }
            }
            slotInfo.put("clip_color", clipColor);

            // Playback state flags (all properties marked as interested in constructor)
            slotInfo.put("is_playing", slot.isPlaying().get());
            slotInfo.put("is_recording", slot.isRecording().get());
            slotInfo.put("is_playback_queued", slot.isPlaybackQueued().get());
            slotInfo.put("is_recording_queued", slot.isRecordingQueued().get());
            slotInfo.put("is_stop_queued", slot.isStopQueued().get());

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting clip slot details: " + e.getMessage());
            // Return basic structure with safe defaults
            slotInfo.put("track_index", trackIndex);
            slotInfo.put("track_name", trackName);
            slotInfo.put("has_content", false);
            slotInfo.put("clip_name", null);
            slotInfo.put("clip_color", null);
            slotInfo.put("is_playing", false);
            slotInfo.put("is_recording", false);
            slotInfo.put("is_playback_queued", false);
            slotInfo.put("is_recording_queued", false);
            slotInfo.put("is_stop_queued", false);
        }

        return slotInfo;
    }

    /**
     * Gets information about the currently selected clip slot including track context,
     * slot position, content status, and playback state.
     *
     * @return A map containing selected clip slot information, or null if no track is selected
     * @throws BitwigApiException if track is selected but clip slot info cannot be retrieved due to API error
     */
    public Map<String, Object> getSelectedClipSlotInfo() throws BitwigApiException {
        final String operation = "getSelectedClipSlotInfo";
        logger.info("BitwigApiFacade: Getting selected clip slot information");

        if (!cursorTrack.exists().get()) {
            logger.info("BitwigApiFacade: No track selected");
            return null;
        }

        Map<String, Object> clipSlotInfo = new LinkedHashMap<>();

        try {
            // Get track context
            String trackName = cursorTrack.name().get();
            int trackIndex = cursorTrack.position().get();

            clipSlotInfo.put("track_name", trackName);
            clipSlotInfo.put("track_index", trackIndex);

            // Get clip launcher slot bank for the selected track
            ClipLauncherSlotBank slotBank = cursorClipLauncherSlotBank;
            if (slotBank == null) {
                logger.info("BitwigApiFacade: Cursor clip launcher slot bank unavailable");
                return null;
            }
            int slotBankSize = slotBank.getSizeOfBank();
            if (slotBankSize == 0) {
                logger.info("BitwigApiFacade: Selected track has no clip launcher slots");
                return null;
            }

            // Try to find a slot that is clearly active (playing/queued/recording).
            // If none meet that heuristic, fall back to slot 0 as the inferred selection.
            int selectedSlotIndex = 0;
            boolean slotDetected = false;

            for (int i = 0; i < slotBankSize; i++) {
                ClipLauncherSlot slot = slotBank.getItemAt(i);
                if (slot.isPlaying().get() || slot.isRecording().get() ||
                    slot.isPlaybackQueued().get() || slot.isRecordingQueued().get() ||
                    slot.isStopQueued().get()) {
                    selectedSlotIndex = i;
                    slotDetected = true;
                    break;
                }
            }

            if (!slotDetected) {
                logger.info("BitwigApiFacade: No active slot detected, using slot 0 as default");
            }

            // Slot position
            clipSlotInfo.put("slot_index", selectedSlotIndex);
            clipSlotInfo.put("scene_index", selectedSlotIndex); // Scene index aligns with slot index

            // Scene information
            String sceneName = sceneBankFacade.getSceneName(selectedSlotIndex);
            if (sceneName != null && sceneName.trim().isEmpty()) {
                sceneName = null;
            }
            clipSlotInfo.put("scene_name", sceneName);

            // Get the selected slot
            ClipLauncherSlot selectedSlot = slotBank.getItemAt(selectedSlotIndex);

            // Content status
            boolean hasContent = selectedSlot.hasContent().get();
            clipSlotInfo.put("has_content", hasContent);

            String clipName = null;
            if (hasContent) {
                String name = selectedSlot.name().get();
                clipName = (name != null && name.trim().isEmpty()) ? null : name;
            }
            clipSlotInfo.put("clip_name", clipName);

            // Playback state flags
            clipSlotInfo.put("is_playing", selectedSlot.isPlaying().get());
            clipSlotInfo.put("is_recording", selectedSlot.isRecording().get());
            clipSlotInfo.put("is_playback_queued", selectedSlot.isPlaybackQueued().get());
            clipSlotInfo.put("is_recording_queued", selectedSlot.isRecordingQueued().get());
            clipSlotInfo.put("is_stop_queued", selectedSlot.isStopQueued().get());

            logger.info("BitwigApiFacade: Retrieved selected clip slot info: track=" + trackName + ", slot=" + selectedSlotIndex);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected clip slot info: " + e.getMessage());
            throw new BitwigApiException(
                ErrorCode.BITWIG_API_ERROR,
                operation,
                "Failed to retrieve selected clip slot info: " + e.getMessage()
            );
        }

        return clipSlotInfo;
    }
}
