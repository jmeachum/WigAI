package io.github.fabb.wigai.mcp;
import io.github.fabb.wigai.mcp.tool.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;

/**
 * Request correlation extraction and sanitization.
 *
 * <p>Covers {@link RequestContextExtractor}: request_id bounds, control-character
 * rejection, and logging-safe parameter summaries (shapes and counts, never values).
 *
 * <p>Split out of the original 1718-line McpErrorHandlerTest along the same seams
 * as the production extraction.
 */
class RequestContextExtractorTest {

    @Test
    void testSanitizeRequestId_ValidString_ReturnsUnchanged() {
        String result = RequestContextExtractor.sanitizeRequestId("test-correlation-123");
        assertEquals("test-correlation-123", result);
    }

    @Test
    void testSanitizeRequestId_Null_ReturnsNull() {
        String result = RequestContextExtractor.sanitizeRequestId(null);
        assertNull(result);
    }

    @Test
    void testSanitizeRequestId_EmptyString_ReturnsNull() {
        String result = RequestContextExtractor.sanitizeRequestId("");
        assertNull(result);
    }

    @Test
    void testSanitizeRequestId_WhitespaceOnly_ReturnsNull() {
        assertNull(RequestContextExtractor.sanitizeRequestId("   "), "Spaces-only should return null");
        assertNull(RequestContextExtractor.sanitizeRequestId("\t\t"), "Tabs-only should return null");
        assertNull(RequestContextExtractor.sanitizeRequestId("  \t  "), "Mixed whitespace should return null");
    }

    @Test
    void testSanitizeRequestId_NonStringType_ReturnsNull() {
        // Integer
        assertNull(RequestContextExtractor.sanitizeRequestId(12345));
        // Boolean
        assertNull(RequestContextExtractor.sanitizeRequestId(true));
        // Object
        assertNull(RequestContextExtractor.sanitizeRequestId(new Object()));
        // Array/List
        assertNull(RequestContextExtractor.sanitizeRequestId(java.util.List.of("a", "b")));
    }

    @Test
    void testSanitizeRequestId_OversizedString_Truncated() {
        // Create a string longer than 256 chars
        String longId = "x".repeat(300);
        String result = RequestContextExtractor.sanitizeRequestId(longId);

        assertNotNull(result);
        assertEquals(256, result.length(), "Should truncate to 256 chars");
        assertEquals("x".repeat(256), result);
    }

    @Test
    void testSanitizeRequestId_ControlCharacters_Stripped() {
        // Newline, tab, carriage return, null byte
        String withControlChars = "test\n\t\r\0id";
        String result = RequestContextExtractor.sanitizeRequestId(withControlChars);

        assertEquals("testid", result, "Control characters should be stripped");
    }

    @Test
    void testSanitizeRequestId_OnlyControlCharacters_ReturnsNull() {
        String onlyControl = "\n\t\r\0";
        String result = RequestContextExtractor.sanitizeRequestId(onlyControl);

        assertNull(result, "String with only control chars should return null");
    }

    @Test
    void testSanitizeRequestId_DeleteCharacter_Stripped() {
        // ASCII 127 (DEL) should be stripped
        String withDel = "test" + (char) 127 + "id";
        String result = RequestContextExtractor.sanitizeRequestId(withDel);

        assertEquals("testid", result, "DEL character (127) should be stripped");
    }

    @Test
    void testSanitizeRequestId_UuidFormat_Unchanged() {
        // Standard UUID format should pass through unchanged
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String result = RequestContextExtractor.sanitizeRequestId(uuid);

        assertEquals(uuid, result);
    }

    @Test
    void testSanitizeRequestId_SpecialCharacters_Preserved() {
        // Printable special characters should be preserved
        String withSpecial = "req-123_test.abc@example";
        String result = RequestContextExtractor.sanitizeRequestId(withSpecial);

        assertEquals(withSpecial, result);
    }

    @Test
    void testSanitizeRequestId_ExactlyMaxLength_NotTruncated() {
        String exactMax = "x".repeat(256);
        String result = RequestContextExtractor.sanitizeRequestId(exactMax);

        assertEquals(256, result.length());
        assertEquals(exactMax, result);
    }

    @Test
    void testExtractLoggingParameters_IncludesArgCountForNonCorrelationArgs() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 3);
        arguments.put("value", 0.5);
        arguments.put("request_id", "test-123");

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals("test-123", result.get("request_id"));
        assertEquals(2, result.get("arg_count"), "arg_count should count non-correlation args");
    }

    @Test
    void testExtractLoggingParameters_IncludesCollectionCountForListArgs() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", List.of(
            Map.of("parameter_index", 0, "value", 0.5),
            Map.of("parameter_index", 1, "value", 0.7)
        ));
        arguments.put("request_id", "test-456");

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals("test-456", result.get("request_id"));
        assertEquals(1, result.get("arg_count"), "arg_count should count non-correlation args");
        assertEquals(2, result.get("parameters_count"), "Should include count of list items");
    }

    @Test
    void testExtractLoggingParameters_OnlyRequestIdNoArgCount() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("request_id", "test-789");

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals("test-789", result.get("request_id"));
        assertNull(result.get("arg_count"), "No non-correlation args means no arg_count");
    }

    @Test
    void testExtractLoggingParameters_NoRequestIdStillHasArgCount() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 5);
        arguments.put("value", 0.3);

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertNull(result.get("request_id"));
        assertEquals(2, result.get("arg_count"));
    }

    @Test
    void testExtractLoggingParameters_DoesNotLeakArgumentValues() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameter_index", 3);
        arguments.put("value", 0.5);
        arguments.put("secret_data", "should-not-appear");

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertNull(result.get("parameter_index"), "Actual argument values must not be in logging params");
        assertNull(result.get("value"), "Actual argument values must not be in logging params");
        assertNull(result.get("secret_data"), "Actual argument values must not be in logging params");
    }

    @Test
    void testExtractLoggingParameters_ListOfMaps_IncludesItemKeys() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", List.of(
            Map.of("parameter_index", 0, "value", 0.5),
            Map.of("parameter_index", 1, "value", 0.7)
        ));

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals(2, result.get("parameters_count"));
        @SuppressWarnings("unchecked")
        java.util.Set<String> itemKeys = (java.util.Set<String>) result.get("parameters_item_keys");
        assertNotNull(itemKeys, "Should include item keys for List<Map> arguments");
        assertTrue(itemKeys.contains("parameter_index"));
        assertTrue(itemKeys.contains("value"));
    }

    @Test
    void testExtractLoggingParameters_MapArgument_IncludesKeys() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("config", Map.of("host", "localhost", "port", 8080));

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        java.util.Set<String> configKeys = (java.util.Set<String>) result.get("config_keys");
        assertNotNull(configKeys, "Should include keys for Map arguments");
        assertTrue(configKeys.contains("host"));
        assertTrue(configKeys.contains("port"));
    }

    @Test
    void testExtractLoggingParameters_EmptyCollection_NoItemKeys() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("parameters", List.of());

        Map<String, Object> result = RequestContextExtractor.extractLoggingParameters(arguments);

        assertNotNull(result);
        assertEquals(0, result.get("parameters_count"));
        assertNull(result.get("parameters_item_keys"), "Empty collection should not have item keys");
    }

    @Test
    void testExtractRawRequestId_ValidString_ReturnsUnchanged() {
        assertEquals("test-123", RequestContextExtractor.extractRawRequestId("test-123"));
    }

    @Test
    void testExtractRawRequestId_Null_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId(null));
    }

    @Test
    void testExtractRawRequestId_NonString_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId(12345));
    }

    @Test
    void testExtractRawRequestId_Blank_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId("   "));
    }

    @Test
    void testExtractRawRequestId_LongString_NoTruncation() {
        // extractRawRequestId must preserve full string for cache keying (no truncation)
        String longId = "x".repeat(500);
        String result = RequestContextExtractor.extractRawRequestId(longId);
        assertEquals(500, result.length(), "Raw request_id must not be truncated for cache keying");
        assertEquals(longId, result);
    }

    @Test
    void testExtractRawRequestId_ExactlyMaxLength_Accepted() {
        String exactMax = "x".repeat(RequestContextExtractor.MAX_RAW_REQUEST_ID_LENGTH);
        String result = RequestContextExtractor.extractRawRequestId(exactMax);
        assertNotNull(result, "request_id at exactly max length must be accepted");
        assertEquals(RequestContextExtractor.MAX_RAW_REQUEST_ID_LENGTH, result.length());
    }

    @Test
    void testExtractRawRequestId_ExceedsMaxLength_ReturnsNull() {
        String oversized = "x".repeat(RequestContextExtractor.MAX_RAW_REQUEST_ID_LENGTH + 1);
        String result = RequestContextExtractor.extractRawRequestId(oversized);
        assertNull(result, "Oversized request_id must be rejected (skip dedupe) to avoid memory pressure");
    }

    @Test
    void testExtractRawRequestId_ControlCharacters_ReturnsNull() {
        // request_id with embedded control chars must be rejected for dedupe keying
        assertNull(RequestContextExtractor.extractRawRequestId("test\nid"),
            "Newline in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("test\tid"),
            "Tab in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("test\0id"),
            "Null byte in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("test\rid"),
            "Carriage return in request_id must cause rejection");
    }

    @Test
    void testExtractRawRequestId_DeleteCharacter_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId("test" + (char) 127 + "id"),
            "DEL character (127) in request_id must cause rejection");
    }

    @Test
    void testExtractRawRequestId_OnlyControlCharacters_ReturnsNull() {
        assertNull(RequestContextExtractor.extractRawRequestId("\n\t\r\0"),
            "All-control request_id must be rejected");
    }

    @Test
    void testExtractRawRequestId_PrintableCharactersOnly_Accepted() {
        // Standard UUIDs, alphanumeric, hyphens, dots, underscores
        assertEquals("550e8400-e29b-41d4-a716-446655440000",
            RequestContextExtractor.extractRawRequestId("550e8400-e29b-41d4-a716-446655440000"));
        assertEquals("req-123_test.abc@example",
            RequestContextExtractor.extractRawRequestId("req-123_test.abc@example"));
    }

    @Test
    void testExtractRawRequestId_NonAsciiCharacters_ReturnsNull() {
        // Characters above ASCII 126 must be rejected for strict printable-ASCII keying
        assertNull(RequestContextExtractor.extractRawRequestId("test\u0080id"),
            "Extended ASCII (128) in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("café"),
            "Non-ASCII accented characters in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("test\u00FFid"),
            "Latin-1 Supplement (255) in request_id must cause rejection");
        assertNull(RequestContextExtractor.extractRawRequestId("\u4E2D\u6587"),
            "CJK characters in request_id must cause rejection");
    }

    @Test
    void testExtractRawRequestId_BoundaryPrintableAscii_Accepted() {
        // Space (32) and tilde (126) are the boundaries of printable ASCII
        assertEquals(" test ", RequestContextExtractor.extractRawRequestId(" test "),
            "Space (ASCII 32) must be accepted");
        assertEquals("~test~", RequestContextExtractor.extractRawRequestId("~test~"),
            "Tilde (ASCII 126) must be accepted");
        assertEquals("test!@#$%^&*(){}|", RequestContextExtractor.extractRawRequestId("test!@#$%^&*(){}|"),
            "All printable ASCII symbols must be accepted");
    }
}
