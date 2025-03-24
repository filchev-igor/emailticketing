package lt.dev.emailticketing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GmailUtilsTest {

    @Test
    void decodeBase64_shouldDecodeStandardInput() {
        String encoded = "SGVsbG8gV29ybGQ="; // "Hello World"
        String decoded = GmailUtils.decodeBase64(encoded);
        assertEquals("Hello World", decoded);
    }

    @Test
    void decodeBase64_shouldHandleUrlSafeEncoding() {
        String encoded = "SGVsbG9fV29ybGQ"; // "Hello_World" URL-safe
        String decoded = GmailUtils.decodeBase64(encoded);
        assertEquals("Hello_World", decoded);
    }

    @Test
    void decodeBase64_shouldReturnEmptyStringOnInvalidInput() {
        String encoded = "##invalid-base64";
        String decoded = GmailUtils.decodeBase64(encoded);
        assertEquals("", decoded);
    }

    @Test
    void decodeBase64_shouldReturnEmptyStringOnNull() {
        String decoded = GmailUtils.decodeBase64(null);
        assertEquals("", decoded);
    }

    @Test
    void normalizeParagraphs_shouldJoinWrappedLines() {
        String input = "This is a\nmulti-line sentence\nthat should be\njoined.";
        String expected = "This is a multi-line sentence that should be joined.";
        String output = GmailUtils.normalizeParagraphs(input);
        assertEquals(expected, output);
    }

    @Test
    void normalizeParagraphs_shouldPreserveParagraphs() {
        String input = "First paragraph.\nLine continues.\n\nSecond paragraph.\nStill going.";
        String expected = "First paragraph. Line continues.\n\nSecond paragraph. Still going.";
        String output = GmailUtils.normalizeParagraphs(input);
        assertEquals(expected, output);
    }

    @Test
    void normalizeParagraphs_shouldHandleEmptyLines() {
        String input = "\n\n";
        String expected = ""; // After trimming and skipping lines, result is empty
        String output = GmailUtils.normalizeParagraphs(input);
        assertEquals(expected, output);
    }
}
