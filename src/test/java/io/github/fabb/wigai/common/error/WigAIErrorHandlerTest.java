package io.github.fabb.wigai.common.error;

import io.github.fabb.wigai.common.Logger;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for WigAIErrorHandler.
 */
class WigAIErrorHandlerTest {

    @Test
    void testHandleBitwigApiExceptionWithClientErrorUsesWarnAndIncludesContext() {
        Logger logger = mock(Logger.class);
        Map<String, Object> context = Map.of("track", "Bass");
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.INVALID_PARAMETER,
            "set_track_volume",
            "Invalid track index",
            context
        );

        Map<String, Object> response = WigAIErrorHandler.handleBitwigApiException(exception, logger);

        assertEquals("error", response.get("status"));
        Map<String, Object> error = getErrorMap(response);
        assertEquals("INVALID_PARAMETER", error.get("code"));
        assertEquals("Invalid track index", error.get("message"));
        assertEquals("set_track_volume", error.get("operation"));
        assertEquals(context, error.get("context"));
        assertNotNull(error.get("timestamp"));

        verify(logger).warn(contains("INVALID_PARAMETER"));
        verify(logger, never()).error(eq("Stack trace:"), eq(exception.getCause()));
    }

    @Test
    void testHandleBitwigApiExceptionWithServerErrorLogsStackTrace() {
        Logger logger = mock(Logger.class);
        RuntimeException cause = new RuntimeException("Disk unavailable");
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.INTERNAL_ERROR,
            "save_session",
            "Write failed",
            Map.of("project", "Demo"),
            cause
        );

        Map<String, Object> response = WigAIErrorHandler.handleBitwigApiException(exception, logger);

        assertEquals("error", response.get("status"));
        Map<String, Object> error = getErrorMap(response);
        assertEquals("INTERNAL_ERROR", error.get("code"));
        assertEquals("save_session", error.get("operation"));

        verify(logger).error(contains("INTERNAL_ERROR"));
        verify(logger).error("Stack trace:", cause);
    }

    @Test
    void testHandleGenericExceptionConvertsToStructuredError() {
        Logger logger = mock(Logger.class);

        Map<String, Object> response = WigAIErrorHandler.handleGenericException(
            new IllegalArgumentException("Bad input"),
            "update_parameter",
            logger
        );

        Map<String, Object> error = getErrorMap(response);
        assertEquals("INVALID_PARAMETER", error.get("code"));
        assertEquals("Bad input", error.get("message"));
        assertEquals("update_parameter", error.get("operation"));
    }

    @Test
    void testCreateSuccessResponseWrapsData() {
        Map<String, Object> data = Map.of("ok", true);
        Map<String, Object> response = WigAIErrorHandler.createSuccessResponse(data);

        assertEquals("success", response.get("status"));
        assertEquals(data, response.get("data"));
    }

    @Test
    void testCreateErrorResponseIncludesExpectedFields() {
        Map<String, Object> response = WigAIErrorHandler.createErrorResponse(
            ErrorCode.TRANSPORT_ERROR,
            "Transport unavailable",
            "transport_start"
        );

        assertEquals("error", response.get("status"));
        Map<String, Object> error = getErrorMap(response);
        assertEquals("TRANSPORT_ERROR", error.get("code"));
        assertEquals("Transport unavailable", error.get("message"));
        assertEquals("transport_start", error.get("operation"));
        assertNotNull(error.get("timestamp"));
    }

    @Test
    void testToJsonStringSerializesResponse() {
        Map<String, Object> response = WigAIErrorHandler.createSuccessResponse(Map.of("value", 42));

        String json = WigAIErrorHandler.toJsonString(response);

        assertTrue(json.contains("\"status\":\"success\""));
        assertTrue(json.contains("\"value\":42"));
    }

    @Test
    void testToJsonStringReturnsFallbackOnSerializationError() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("self", response);

        String json = WigAIErrorHandler.toJsonString(response);

        assertTrue(json.contains("\"status\":\"error\""));
        assertTrue(json.contains("\"code\":\"SERIALIZATION_ERROR\""));
    }

    @Test
    void testCreateJsonResponseHelpersProduceExpectedStatus() {
        String successJson = WigAIErrorHandler.createJsonSuccessResponse(Map.of("name", "track-1"));
        String errorJson = WigAIErrorHandler.createJsonErrorResponse(
            ErrorCode.BITWIG_API_ERROR,
            "API failed",
            "status"
        );

        assertTrue(successJson.contains("\"status\":\"success\""));
        assertTrue(errorJson.contains("\"status\":\"error\""));
        assertTrue(errorJson.contains("\"code\":\"BITWIG_API_ERROR\""));
    }

    @Test
    void testIsClientErrorClassifiesKnownClientAndServerCodes() {
        assertTrue(WigAIErrorHandler.isClientError(ErrorCode.MCP_PARSING_ERROR));
        assertTrue(WigAIErrorHandler.isClientError(ErrorCode.TRACK_NOT_FOUND));
        assertFalse(WigAIErrorHandler.isClientError(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void testExecuteWithErrorHandlingSupplierRethrowsBitwigApiExceptionAsIs() {
        BitwigApiException original = new BitwigApiException(
            ErrorCode.SCENE_NOT_FOUND,
            "scene_lookup",
            "Scene missing"
        );

        BitwigApiException thrown = assertThrows(
            BitwigApiException.class,
            () -> WigAIErrorHandler.executeWithErrorHandling("another_op", () -> {
                throw original;
            })
        );

        assertSame(original, thrown);
    }

    @Test
    void testExecuteWithErrorHandlingSupplierWrapsGenericException() {
        BitwigApiException thrown = assertThrows(
            BitwigApiException.class,
            () -> WigAIErrorHandler.executeWithErrorHandling("refresh_status", () -> {
                throw new RuntimeException("Network down");
            })
        );

        assertEquals(ErrorCode.OPERATION_FAILED, thrown.getErrorCode());
        assertEquals("refresh_status", thrown.getOperation());
        assertEquals("Network down", thrown.getMessage());
        assertNotNull(thrown.getCause());
    }

    @Test
    void testExecuteWithErrorHandlingRunnableVariants() {
        assertDoesNotThrow(() -> WigAIErrorHandler.executeWithErrorHandling("noop", () -> {
            // success path
        }));

        BitwigApiException thrown = assertThrows(
            BitwigApiException.class,
            () -> WigAIErrorHandler.executeWithErrorHandling("set_parameter", () -> {
                throw new IllegalArgumentException("Value must be between 0 and 1");
            })
        );

        assertEquals(ErrorCode.INVALID_PARAMETER, thrown.getErrorCode());
        assertEquals("set_parameter", thrown.getOperation());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getErrorMap(Map<String, Object> response) {
        Object error = response.get("error");
        assertInstanceOf(Map.class, error);
        return (Map<String, Object>) error;
    }
}
