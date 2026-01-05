package io.github.fabb.wigai.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpErrorHandler, focusing on request_id sanitization.
 */
class McpErrorHandlerTest {

    // === request_id sanitization tests (Story 1.4 AI-Review follow-up) ===

    @Test
    void testSanitizeRequestId_ValidString_ReturnsUnchanged() {
        String result = McpErrorHandler.sanitizeRequestId("test-correlation-123");
        assertEquals("test-correlation-123", result);
    }

    @Test
    void testSanitizeRequestId_Null_ReturnsNull() {
        String result = McpErrorHandler.sanitizeRequestId(null);
        assertNull(result);
    }

    @Test
    void testSanitizeRequestId_EmptyString_ReturnsNull() {
        String result = McpErrorHandler.sanitizeRequestId("");
        assertNull(result);
    }

    @Test
    void testSanitizeRequestId_NonStringType_ReturnsNull() {
        // Integer
        assertNull(McpErrorHandler.sanitizeRequestId(12345));
        // Boolean
        assertNull(McpErrorHandler.sanitizeRequestId(true));
        // Object
        assertNull(McpErrorHandler.sanitizeRequestId(new Object()));
        // Array/List
        assertNull(McpErrorHandler.sanitizeRequestId(java.util.List.of("a", "b")));
    }

    @Test
    void testSanitizeRequestId_OversizedString_Truncated() {
        // Create a string longer than 256 chars
        String longId = "x".repeat(300);
        String result = McpErrorHandler.sanitizeRequestId(longId);

        assertNotNull(result);
        assertEquals(256, result.length(), "Should truncate to 256 chars");
        assertEquals("x".repeat(256), result);
    }

    @Test
    void testSanitizeRequestId_ControlCharacters_Stripped() {
        // Newline, tab, carriage return, null byte
        String withControlChars = "test\n\t\r\0id";
        String result = McpErrorHandler.sanitizeRequestId(withControlChars);

        assertEquals("testid", result, "Control characters should be stripped");
    }

    @Test
    void testSanitizeRequestId_OnlyControlCharacters_ReturnsNull() {
        String onlyControl = "\n\t\r\0";
        String result = McpErrorHandler.sanitizeRequestId(onlyControl);

        assertNull(result, "String with only control chars should return null");
    }

    @Test
    void testSanitizeRequestId_DeleteCharacter_Stripped() {
        // ASCII 127 (DEL) should be stripped
        String withDel = "test" + (char) 127 + "id";
        String result = McpErrorHandler.sanitizeRequestId(withDel);

        assertEquals("testid", result, "DEL character (127) should be stripped");
    }

    @Test
    void testSanitizeRequestId_UuidFormat_Unchanged() {
        // Standard UUID format should pass through unchanged
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String result = McpErrorHandler.sanitizeRequestId(uuid);

        assertEquals(uuid, result);
    }

    @Test
    void testSanitizeRequestId_SpecialCharacters_Preserved() {
        // Printable special characters should be preserved
        String withSpecial = "req-123_test.abc@example";
        String result = McpErrorHandler.sanitizeRequestId(withSpecial);

        assertEquals(withSpecial, result);
    }

    @Test
    void testSanitizeRequestId_ExactlyMaxLength_NotTruncated() {
        String exactMax = "x".repeat(256);
        String result = McpErrorHandler.sanitizeRequestId(exactMax);

        assertEquals(256, result.length());
        assertEquals(exactMax, result);
    }
}
