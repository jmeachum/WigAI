package io.github.fabb.wigai.features;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.validation.TrackTargetingContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for clip and scene launching operations in Bitwig Studio.
 * Handles the business logic for session control including clip launching,
 * track finding, and validation of clip operations.
 */
public class ClipSceneController {

    private final BitwigApiFacade bitwigApiFacade;
    private final Logger logger;

    /**
     * Constructs a ClipSceneController with required dependencies.
     *
     * @param bitwigApiFacade The facade for Bitwig API interactions
     * @param logger The logger service for operation logging
     */
    public ClipSceneController(BitwigApiFacade bitwigApiFacade, Logger logger) {
        this.bitwigApiFacade = bitwigApiFacade;
        this.logger = logger;
    }

    /**
     * Launches all clips in the specified scene index across all tracks.
     *
     * @param sceneIndex The zero-based index of the scene to launch
     * @return SceneLaunchResult indicating success/failure and any error details
     */
    public SceneLaunchResult launchSceneByIndex(int sceneIndex) {
        try {
            // Validate scene index
            if (sceneIndex < 0) {
                return SceneLaunchResult.error("INVALID_PARAMETER_INDEX", "Scene index must be non-negative");
            }

            int trackCount = bitwigApiFacade.getTrackBankSize();
            int launchedCount = 0;
            boolean anyTrack = false;
            boolean anyClipAtIndex = false;
            BitwigApiException lastLaunchError = null;

            for (int trackIdx = 0; trackIdx < trackCount; trackIdx++) {
                try {
                    // Use index-based API to avoid duplicate-track-name ambiguity
                    bitwigApiFacade.getTrackNameByIndex(trackIdx);
                    anyTrack = true;
                    int clipCount = bitwigApiFacade.getTrackClipCountByIndex(trackIdx);
                    if (sceneIndex < clipCount) {
                        anyClipAtIndex = true;
                        bitwigApiFacade.launchClipByTrackIndex(trackIdx, sceneIndex);
                        launchedCount++;
                    }
                } catch (BitwigApiException e) {
                    if (e.getErrorCode() == ErrorCode.TRACK_NOT_FOUND
                            || e.getErrorCode() == ErrorCode.INVALID_PARAMETER_INDEX) {
                        // Track doesn't exist or index-related issue — skip to next track
                        continue;
                    }
                    // Non-index failure (e.g., BITWIG_API_ERROR) — preserve for reporting
                    lastLaunchError = e;
                }
            }

            if (!anyTrack) {
                return SceneLaunchResult.error("SCENE_NOT_FOUND", "No tracks found in Bitwig session");
            }

            if (launchedCount > 0) {
                String msg = "Scene " + sceneIndex + " launched on " + launchedCount + " track(s).";
                return SceneLaunchResult.success(msg);
            } else if (anyClipAtIndex && lastLaunchError != null) {
                // Clips existed at this scene index but all launches failed for non-index reasons
                return SceneLaunchResult.error(lastLaunchError.getErrorCode().getCode(),
                    "Failed to launch scene " + sceneIndex + ": " + lastLaunchError.getMessage());
            } else {
                return SceneLaunchResult.error("INVALID_PARAMETER_INDEX", "Scene index " + sceneIndex + " is out of bounds for all tracks");
            }
        } catch (Exception e) {
            return SceneLaunchResult.error("BITWIG_ERROR", "Internal error occurred while launching scene: " + e.getMessage());
        }
    }

    /**
     * Launches all clips in the scene with the given name (case-sensitive, first match wins).
     *
     * @param sceneName The name of the scene to launch
     * @return SceneLaunchResult indicating success/failure and any error details
     */
    public SceneLaunchResult launchSceneByName(String sceneName) {
        logger.info("Received request to launch scene by name: '" + sceneName + "'");
        if (sceneName == null || sceneName.trim().isEmpty()) {
            logger.warn("Scene name is empty or null");
            return SceneLaunchResult.error("SCENE_NOT_FOUND", "scene_name must be a non-empty string");
        }
        int sceneIndex = bitwigApiFacade.findSceneByName(sceneName);
        logger.info("Searching for scene '" + sceneName + "' (case-sensitive)");
        if (sceneIndex < 0) {
            logger.warn("Scene not found: '" + sceneName + "'");
            return SceneLaunchResult.error("SCENE_NOT_FOUND", "Scene '" + sceneName + "' not found");
        }
        logger.info("Found scene '" + sceneName + "' at index " + sceneIndex + ". Launching...");
        SceneLaunchResult result = launchSceneByIndex(sceneIndex);
        if (result.isSuccess()) {
            String msg = "Scene '" + sceneName + "' launched.";
            logger.info(msg);
            return SceneLaunchResult.success(msg + " (index: " + sceneIndex + ")");
        } else {
            logger.error("Failed to launch scene by name: '" + sceneName + "' - " + result.getMessage());
            return result;
        }
    }

    public BitwigApiFacade getBitwigApiFacade() {
        return bitwigApiFacade;
    }

    /**
     * Gets detailed information for all clips within a specific scene.
     *
     * @param sceneIndex The zero-based index of the scene (optional if sceneName provided)
     * @param sceneName The name of the scene (optional if sceneIndex provided)
     * @return List of clip slot objects for the specified scene
     */
    public Object getClipsInScene(Integer sceneIndex, String sceneName) {
        try {
            logger.info("Getting clips in scene - Index: " + sceneIndex + ", Name: '" + sceneName + "'");

            // Determine target scene index
            int targetSceneIndex;
            if (sceneName != null && !sceneName.trim().isEmpty()) {
                // Scene name takes precedence - case-insensitive comparison
                String normalizedName = sceneName.trim();
                targetSceneIndex = findSceneByNameCaseInsensitive(normalizedName);
                if (targetSceneIndex < 0) {
                    throw new BitwigApiException(
                        ErrorCode.SCENE_NOT_FOUND,
                        "get_clips_in_scene",
                        "Scene not found: " + sceneName,
                        Map.of("scene_name", sceneName)
                    );
                }
                logger.info("Found scene '" + sceneName + "' at index " + targetSceneIndex);
            } else if (sceneIndex != null) {
                targetSceneIndex = sceneIndex;
                // Validate scene index exists by checking if any track has clips at this index
                if (!isSceneIndexValid(targetSceneIndex)) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_PARAMETER_INDEX,
                        "get_clips_in_scene",
                        "Scene index out of bounds: " + targetSceneIndex,
                        Map.of("scene_index", targetSceneIndex)
                    );
                }
                logger.info("Using scene index " + targetSceneIndex);
            } else {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    "get_clips_in_scene",
                    "At least one of scene_index or scene_name must be provided",
                    Map.of()
                );
            }

            // Get clips from all tracks at the target scene index
            List<Map<String, Object>> clipSlots = new ArrayList<>();
            int trackCount = bitwigApiFacade.getTrackBankSize();

            for (int trackIndex = 0; trackIndex < trackCount; trackIndex++) {
                try {
                    String trackName = bitwigApiFacade.getTrackNameByIndex(trackIndex);
                    if (trackName == null || trackName.trim().isEmpty()) {
                        continue; // Skip tracks that don't exist
                    }

                    Map<String, Object> clipSlot = bitwigApiFacade.getClipSlotDetails(trackIndex, trackName, targetSceneIndex);
                    if (clipSlot != null) {
                        clipSlots.add(clipSlot);
                    } else {
                        // Create default empty slot entry for tracks that don't have this scene index
                        Map<String, Object> emptySlot = new LinkedHashMap<>();
                        emptySlot.put("track_index", trackIndex);
                        emptySlot.put("track_name", trackName);
                        emptySlot.put("has_content", false);
                        emptySlot.put("clip_name", null);
                        emptySlot.put("clip_color", null);
                        emptySlot.put("is_playing", false);
                        emptySlot.put("is_recording", false);
                        emptySlot.put("is_playback_queued", false);
                        emptySlot.put("is_recording_queued", false);
                        emptySlot.put("is_stop_queued", false);
                        clipSlots.add(emptySlot);
                    }
                } catch (Exception e) {
                    logger.warn("Error getting clip info for track " + trackIndex + " at scene " + targetSceneIndex + ": " + e.getMessage());
                    // Continue with next track
                }
            }

            logger.info("Retrieved " + clipSlots.size() + " clip slots for scene " + targetSceneIndex);
            return clipSlots;

        } catch (BitwigApiException e) {
            logger.error("Failed to get clips in scene: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting clips in scene: " + e.getMessage(), e);
            throw new BitwigApiException(
                ErrorCode.INTERNAL_ERROR,
                "get_clips_in_scene",
                "Internal error occurred while getting clips in scene: " + e.getMessage(),
                Map.of()
            );
        }
    }

    /**
     * Finds a scene by name using case-insensitive comparison.
     * Returns the first matching scene index, or -1 if not found.
     */
    private int findSceneByNameCaseInsensitive(String sceneName) {
        int sceneCount = bitwigApiFacade.getSceneCount();
        for (int i = 0; i < sceneCount; i++) {
            String currentSceneName = bitwigApiFacade.getSceneName(i);
            if (currentSceneName != null && currentSceneName.trim().equalsIgnoreCase(sceneName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Validates that a scene index is valid by checking if the scene bank contains it.
     */
    private boolean isSceneIndexValid(int sceneIndex) {
        if (sceneIndex < 0) {
            return false;
        }
        int sceneCount = bitwigApiFacade.getSceneCount();
        return sceneIndex < sceneCount;
    }

    /**
     * Launches a clip at the specified track and clip index.
     *
     * @param trackName The name of the track containing the clip (exact match after trim + case-insensitive normalization)
     * @param clipIndex The zero-based index of the clip slot to launch
     * @return ClipLaunchResult indicating success/failure and any error details
     */
    public ClipLaunchResult launchClip(String trackName, int clipIndex) {
        return launchClipWithSelectors(null, trackName, clipIndex);
    }

    /**
     * Launches a clip with optional explicit track index confirmation.
     *
     * @param trackName The exact track name
     * @param clipIndex The zero-based clip slot index
     * @param trackIndex Optional explicit track index to confirm target when duplicates exist
     * @return ClipLaunchResult indicating success/failure and any ambiguity guidance
     */
    public ClipLaunchResult launchClip(String trackName, int clipIndex, Integer trackIndex) {
        return launchClipWithSelectors(trackIndex, trackName, clipIndex);
    }

    /**
     * Launches a clip using the shared track-targeting selector contract.
     *
     * @param trackIndex Optional explicit track index (authoritative when present)
     * @param trackName Optional track name selector or confirmation
     * @param clipIndex The zero-based clip slot index
     * @return ClipLaunchResult indicating success/failure and any ambiguity guidance
     */
    public ClipLaunchResult launchClipWithSelectors(Integer trackIndex, String trackName, int clipIndex) {
        try {
            int resolvedTrackIndex;
            String resolvedTrackName;
            if (trackIndex != null) {
                String actualTrackName = bitwigApiFacade.getTrackNameByIndex(trackIndex);
                if (trackName != null && !TrackTargetingContract.namesMatchNormalized(trackName, actualTrackName)) {
                    return ClipLaunchResult.error(
                        ErrorCode.INVALID_PARAMETER.getCode(),
                        "track_index " + trackIndex + " does not match track_name '" + trackName + "'"
                    );
                }
                resolvedTrackIndex = trackIndex;
                resolvedTrackName = actualTrackName;
            } else {
                resolvedTrackIndex = bitwigApiFacade.findTrackIndexByName(trackName);
                resolvedTrackName = trackName == null ? null : trackName.trim();
            }

            int trackClipCount = bitwigApiFacade.getTrackClipCountByIndex(resolvedTrackIndex);
            String trackNameForMessage = resolvedTrackName == null || resolvedTrackName.isBlank()
                ? "track_index " + resolvedTrackIndex
                : "'" + resolvedTrackName + "'";
            if (clipIndex < 0 || clipIndex >= trackClipCount) {
                return ClipLaunchResult.error(
                    ErrorCode.INVALID_PARAMETER_INDEX.getCode(),
                    "Clip index " + clipIndex + " is out of bounds for track " + trackNameForMessage
                );
            }

            bitwigApiFacade.launchClipByTrackIndex(resolvedTrackIndex, clipIndex);
            String targetLabel = resolvedTrackName == null || resolvedTrackName.isBlank()
                ? ("#" + resolvedTrackIndex)
                : resolvedTrackName;
            return ClipLaunchResult.success(
                "Clip at " + targetLabel + "[" + clipIndex + "] launched.",
                resolvedTrackIndex,
                resolvedTrackName
            );
        } catch (BitwigApiException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_PARAMETER) {
                List<Map<String, Object>> candidates = extractAmbiguityCandidates(e.getContext());
                if (!candidates.isEmpty()) {
                    return ClipLaunchResult.ambiguity(e.getMessage(), candidates);
                }
            }
            return ClipLaunchResult.error(e.getErrorCode().getCode(), e.getMessage());
        } catch (Exception e) {
            return ClipLaunchResult.error(
                ErrorCode.BITWIG_API_ERROR.getCode(),
                "Internal error occurred while launching clip: " + e.getMessage()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractAmbiguityCandidates(Object context) {
        if (!(context instanceof Map<?, ?> contextMap)) {
            return List.of();
        }
        Object rawCandidates = contextMap.get("candidates");
        if (!(rawCandidates instanceof List<?> candidateList)) {
            return List.of();
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Object entry : candidateList) {
            if (entry instanceof Map<?, ?> candidate) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                if (candidate.containsKey("track_index")) {
                    normalized.put("track_index", candidate.get("track_index"));
                }
                if (candidate.containsKey("track_name")) {
                    normalized.put("track_name", candidate.get("track_name"));
                }
                if (!normalized.isEmpty()) {
                    candidates.add(normalized);
                }
            }
        }
        return candidates;
    }
}
