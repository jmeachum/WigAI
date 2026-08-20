package io.github.fabb.wigai.features;

import io.github.fabb.wigai.common.error.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * Result class for clip launch operations.
 */
public class ClipLaunchResult {
    private final boolean success;
    private final String errorCode;
    private final String message;
    private final Integer trackIndex;
    private final String trackName;
    private final List<Map<String, Object>> candidates;
    private final String confirmationParameter;

    private ClipLaunchResult(
        boolean success,
        String errorCode,
        String message,
        Integer trackIndex,
        String trackName,
        List<Map<String, Object>> candidates,
        String confirmationParameter
    ) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.trackIndex = trackIndex;
        this.trackName = trackName;
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
        this.confirmationParameter = confirmationParameter;
    }

    /**
     * Creates a successful result.
     *
     * @param message Success message
     * @return Successful ClipLaunchResult
     */
    public static ClipLaunchResult success(String message) {
        return success(message, null, null);
    }

    /**
     * Creates a successful result with the resolved track index.
     */
    public static ClipLaunchResult success(String message, Integer trackIndex) {
        return success(message, trackIndex, null);
    }

    /**
     * Creates a successful result with resolved track metadata.
     */
    public static ClipLaunchResult success(String message, Integer trackIndex, String trackName) {
        return new ClipLaunchResult(true, null, message, trackIndex, trackName, List.of(), null);
    }

    /**
     * Creates an error result.
     *
     * @param errorCode Error code for the failure
     * @param message Error message
     * @return Error ClipLaunchResult
     */
    public static ClipLaunchResult error(String errorCode, String message) {
        return new ClipLaunchResult(false, errorCode, message, null, null, List.of(), null);
    }

    /**
     * Creates an ambiguity result with deterministic candidate guidance.
     */
    public static ClipLaunchResult ambiguity(String message, List<Map<String, Object>> candidates) {
        return new ClipLaunchResult(
            false,
            ErrorCode.INVALID_PARAMETER.getCode(),
            message,
            null,
            null,
            candidates,
            "track_index"
        );
    }

    /**
     * Returns whether the operation was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the error code if the operation failed.
     *
     * @return error code or null if successful
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the result message.
     *
     * @return success or error message
     */
    public String getMessage() {
        return message;
    }

    public Integer getTrackIndex() {
        return trackIndex;
    }

    public String getTrackName() {
        return trackName;
    }

    public boolean isAmbiguous() {
        return ErrorCode.INVALID_PARAMETER.getCode().equals(errorCode) && !candidates.isEmpty();
    }

    public List<Map<String, Object>> getCandidates() {
        return candidates;
    }

    public String getConfirmationParameter() {
        return confirmationParameter;
    }
}
