package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.error.WigAIErrorHandler;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.common.validation.TrackTargetingContract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Track resolution and targeting against the shared Bitwig track bank.
 *
 * <p>Owns the selector precedence ({@code track_index -> track_name -> selected track}),
 * deterministic fuzzy candidate resolution, and track-bank pagination. Operates on the
 * bank and cursor already created and marked-interested by {@link BitwigApiFacade} rather
 * than creating its own, so track state stays single-sourced.
 */
class TrackTargetingFacade {

    /** Upper bound on track-bank pagination steps, guarding against runaway scrolling. */
    static final int MAX_TRACK_PAGINATION_STEPS = 1024;

    private final ControllerHost host;
    private final TrackBank trackBank;
    private final CursorTrack cursorTrack;
    private final Logger logger;

    TrackTargetingFacade(ControllerHost host, TrackBank trackBank, CursorTrack cursorTrack, Logger logger) {
        this.host = host;
        this.trackBank = trackBank;
        this.cursorTrack = cursorTrack;
        this.logger = logger;
    }

    /**
     * Finds a track by index.
     *
     * @param index The index of the track to find
     * @return Optional containing the track if found and exists, empty otherwise
     */
    Optional<Track> findTrackByIndex(int index) {
        if (index < 0 || index >= trackBank.getSizeOfBank()) {
            return Optional.empty();
        }

        Track track = trackBank.getItemAt(index);
        return track.exists().get() ? Optional.of(track) : Optional.empty();
    }

    Track requireTrackByIndex(int index, String operation) throws BitwigApiException {
        if (index < 0 || index >= trackBank.getSizeOfBank()) {
            throw new BitwigApiException(
                ErrorCode.INVALID_PARAMETER_INDEX,
                operation,
                "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + index,
                Map.of("index", index, "max_index", trackBank.getSizeOfBank() - 1)
            );
        }

        Track track = trackBank.getItemAt(index);
        if (!track.exists().get()) {
            throw new BitwigApiException(
                ErrorCode.TRACK_NOT_FOUND,
                operation,
                "Track at index " + index + " does not exist",
                Map.of("index", index)
            );
        }
        return track;
    }

    /**
     * Returns the number of tracks in the track bank.
     *
     * @return the size of the track bank
     */
    public int getTrackBankSize() {
        return trackBank.getSizeOfBank();
    }

    /**
     * Returns the name of the track at the given index.
     *
     * @param index the track index
     * @return the track name
     * @throws BitwigApiException if the index is invalid or track doesn't exist
     */
    public String getTrackNameByIndex(int index) throws BitwigApiException {
        final String operation = "getTrackNameByIndex";

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Validate track index
            if (index < 0 || index >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER_INDEX,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + index,
                    Map.of("index", index, "max_index", trackBank.getSizeOfBank() - 1)
                );
            }

            Track track = trackBank.getItemAt(index);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + index + " does not exist",
                    Map.of("index", index)
                );
            }

            return track.name().get();
        });
    }

    /**
     * Finds all exact-name track candidates using deterministic index order.
     * Scans only the currently materialized track bank window.
     */
    private List<Map<String, Object>> getTrackCandidatesByName(String trackName) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        if (trackName == null || trackName.trim().isEmpty()) {
            return candidates;
        }

        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            Track track = trackBank.getItemAt(i);
            if (!track.exists().get()) {
                continue;
            }
            String currentTrackName = track.name().get();
            if (TrackTargetingContract.namesMatchNormalized(trackName, currentTrackName)) {
                Map<String, Object> candidate = new LinkedHashMap<>();
                candidate.put("track_index", i);
                candidate.put("track_name", currentTrackName);
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /**
     * Finds a track by exact normalized name (trim + case-insensitive).
     *
     * @param trackName The name of the track to find
     * @return The track index if found
     * @throws BitwigApiException if the track is not found or if duplicate exact-name matches exist
     */
    public int findTrackIndexByName(String trackName) throws BitwigApiException {
        final String operation = "findTrackIndexByName";
        logger.info("BitwigApiFacade: Searching for track '" + trackName + "'");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "trackName", operation);
            List<Map<String, Object>> candidates = getTrackCandidatesByName(trackName);
            if (candidates.isEmpty()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track '" + trackName + "' not found",
                    Map.of("trackName", trackName)
                );
            }

            if (candidates.size() > 1) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "Ambiguous track_name '" + trackName + "'. Provide track_index to confirm target.",
                    Map.of(
                        "reason", "ambiguous_track_name",
                        "track_name", trackName,
                        "confirmation_parameter", "track_index",
                        "candidates", candidates
                    )
                );
            }

            int index = (Integer) candidates.getFirst().get("track_index");
            logger.info("BitwigApiFacade: Found track '" + trackName + "' at index " + index);
            return index;
        });
    }

    private record ResolveTrackCandidate(int trackIndex, String trackName, String matchType) {
        int matchPrecedence() {
            if ("exact".equals(matchType)) {
                return 0;
            }
            if ("prefix".equals(matchType)) {
                return 1;
            }
            if ("substring".equals(matchType)) {
                return 2;
            }
            throw new IllegalStateException("Unsupported match type: " + matchType);
        }
    }

    private static String determineTrackMatchType(String normalizedQuery, String normalizedTrackName) {
        if (normalizedTrackName.equals(normalizedQuery)) {
            return "exact";
        }
        if (normalizedTrackName.startsWith(normalizedQuery)) {
            return "prefix";
        }
        if (normalizedTrackName.contains(normalizedQuery)) {
            return "substring";
        }
        return null;
    }

    /**
     * Resolves deterministic fuzzy track candidates for a user query.
     *
     * <p>Matching precedence is case-insensitive and normalization-aware:
     * exact -> prefix -> substring. Results are deterministic: first by
     * match precedence, then by ascending track_index.</p>
     *
     * @param query user query string
     * @param operation operation/tool name for error context
     * @return payload containing ambiguity flag and ordered candidate list
     * @throws BitwigApiException when query is invalid or no tracks match
     */
    public Map<String, Object> resolveTrack(String query, String operation) throws BitwigApiException {
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            String validatedQuery = ParameterValidator.validateNotEmpty(query, "query", operation);
            String normalizedQuery = TrackTargetingContract.normalizeTrackName(validatedQuery);

            List<ResolveTrackCandidate> resolved = collectResolveTrackCandidates(normalizedQuery);

            resolved.sort(
                Comparator.comparingInt(ResolveTrackCandidate::matchPrecedence)
                    .thenComparingInt(ResolveTrackCandidate::trackIndex)
            );

            if (resolved.isEmpty()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "No tracks matched query '" + validatedQuery + "'. Use list_tracks to inspect available tracks.",
                    Map.of(
                        "query", validatedQuery,
                        "suggestion", "list_tracks"
                    )
                );
            }

            List<Map<String, Object>> candidates = new ArrayList<>();
            for (ResolveTrackCandidate candidate : resolved) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("track_index", candidate.trackIndex());
                out.put("track_name", candidate.trackName());
                out.put("match_type", candidate.matchType());
                candidates.add(out);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ambiguous", candidates.size() > 1);
            payload.put("candidates", candidates);
            return payload;
        });
    }

    private List<ResolveTrackCandidate> collectResolveTrackCandidates(String normalizedQuery) {
        Map<Integer, ResolveTrackCandidate> candidatesByIndex = new TreeMap<>();
        resetTrackBankToStart();
        host.requestFlush();

        int iterationCount = 0;
        try {
            while (true) {
                captureVisibleResolveTrackCandidates(normalizedQuery, candidatesByIndex);
                boolean canScrollForward = trackBank.canScrollForwards().get();
                if (!canScrollForward || iterationCount >= MAX_TRACK_PAGINATION_STEPS) {
                    if (iterationCount >= MAX_TRACK_PAGINATION_STEPS) {
                        logger.warn("BitwigApiFacade: Reached track pagination limit while resolving track candidates");
                    }
                    break;
                }
                trackBank.scrollPageForwards();
                host.requestFlush();
                iterationCount++;
            }
            return new ArrayList<>(candidatesByIndex.values());
        } finally {
            resetTrackBankToStart();
            host.requestFlush();
        }
    }

    private void captureVisibleResolveTrackCandidates(
            String normalizedQuery,
            Map<Integer, ResolveTrackCandidate> candidatesByIndex) {

        for (int slotIndex = 0; slotIndex < trackBank.getSizeOfBank(); slotIndex++) {
            Track track = trackBank.getItemAt(slotIndex);
            if (!track.exists().get()) {
                continue;
            }
            String trackName = track.name().get();
            String normalizedTrackName = TrackTargetingContract.normalizeTrackName(trackName);
            String matchType = determineTrackMatchType(normalizedQuery, normalizedTrackName);
            if (matchType == null) {
                continue;
            }
            int projectIndex = resolveTrackProjectIndex(track, slotIndex);
            candidatesByIndex.putIfAbsent(projectIndex, new ResolveTrackCandidate(projectIndex, trackName, matchType));
        }
    }

    /**
     * Resolves a target track index using the shared selector precedence:
     * {@code track_index -> track_name -> selected track fallback}.
     *
     * <p>When both selectors are provided, {@code track_index} is authoritative and
     * {@code track_name} acts as confirmation against the resolved index.</p>
     *
     * @param trackIndex optional explicit index selector
     * @param trackName optional exact normalized name selector (trim + case-insensitive)
     * @param useSelectedTrackFallback whether to fall back to selected track when selectors are absent
     * @param operation operation/tool name for error context
     * @return resolved track index
     * @throws BitwigApiException when resolution fails or selectors conflict
     */
    public int resolveTrackIndex(
        Integer trackIndex,
        String trackName,
        boolean useSelectedTrackFallback,
        String operation
    ) throws BitwigApiException {
        if (trackIndex != null) {
            Track indexedTrack = requireTrackByIndex(trackIndex, operation);
            String actualTrackName = indexedTrack.name().get();
            if (trackName != null && !TrackTargetingContract.namesMatchNormalized(trackName, actualTrackName)) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("track_index", trackIndex);
                details.put("track_name", trackName);
                details.put("resolved_track_name", actualTrackName);
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "track_index " + trackIndex + " does not match track_name '" + trackName + "'",
                    details
                );
            }
            return trackIndex;
        }

        if (trackName != null) {
            return findTrackIndexByName(trackName);
        }

        if (!useSelectedTrackFallback) {
            throw new BitwigApiException(
                ErrorCode.INVALID_PARAMETER,
                operation,
                "No valid track selector provided"
            );
        }

        Integer selectedTrackIndex = getSelectedTrackProjectIndex();
        if (selectedTrackIndex == null
            || selectedTrackIndex < 0
            || selectedTrackIndex >= trackBank.getSizeOfBank()) {
            throw new BitwigApiException(
                ErrorCode.TRACK_NOT_FOUND,
                operation,
                "No track is currently selected. Provide track_index or track_name."
            );
        }

        Track selectedTrack = trackBank.getItemAt(selectedTrackIndex);
        if (!selectedTrack.exists().get()) {
            throw new BitwigApiException(
                ErrorCode.TRACK_NOT_FOUND,
                operation,
                "No track is currently selected. Provide track_index or track_name."
            );
        }

        return selectedTrackIndex;
    }

    void resetTrackBankToStart() {
        int safetyCounter = 0;
        while (trackBank.canScrollBackwards().get() && safetyCounter < MAX_TRACK_PAGINATION_STEPS) {
            trackBank.scrollPageBackwards();
            host.requestFlush();
            safetyCounter++;
        }
    }

    /**
     * Resolves the parent group index for the provided track using the actual project index.
     */
    Integer resolveParentGroupIndex(Track track) {
        try {
            Track parentTrack = track.createParentTrack(0, 0);
            if (parentTrack == null) {
                return null;
            }
            parentTrack.exists().markInterested();
            parentTrack.isGroup().markInterested();
            parentTrack.position().markInterested();

            if (parentTrack.exists().get() && parentTrack.isGroup().get()) {
                return parentTrack.position().get();
            }
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error determining parent for track " + track.name().get() + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns the global project index for the given track, falling back to the provided index if unavailable.
     */
    int resolveTrackProjectIndex(Track track, int fallbackIndex) {
        try {
            return track.position().get();
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Unable to resolve project index for track " + track.name().get() + ": " + e.getMessage());
            return fallbackIndex;
        }
    }

    /**
     * Returns the project index for the currently selected track, if any.
     */
    Integer getSelectedTrackProjectIndex() {
        try {
            if (cursorTrack.exists().get()) {
                return cursorTrack.position().get();
            }
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Unable to resolve selected track index: " + e.getMessage());
        }
        return null;
    }
}
