package io.github.fabb.wigai.features;

/**
 * Result class for scene launch operations.
 */
public class SceneLaunchResult {
    private final boolean success;
    private final String errorCode;
    private final String message;

    private SceneLaunchResult(boolean success, String errorCode, String message) {
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static SceneLaunchResult success(String message) {
        return new SceneLaunchResult(true, null, message);
    }

    public static SceneLaunchResult error(String errorCode, String message) {
        return new SceneLaunchResult(false, errorCode, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}
