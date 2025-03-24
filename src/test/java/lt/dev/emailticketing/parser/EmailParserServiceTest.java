package lt.dev.emailticketing.parser;

import com.google.api.services.gmail.model.*;
import lt.dev.emailticketing.internal.SenderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailParserServiceTest {

    private EmailParserService emailParserService;

    @BeforeEach
    void setUp() {
        emailParserService = new EmailParserService();
    }

    @Test
    void extractSenderInfo_withNameAndEmail_shouldExtractCorrectly() {
        String header = "John Doe <john@example.com>";
        SenderInfo info = emailParserService.extractSenderInfo(header);
        assertEquals("John Doe", info.getName());
        assertEquals("john@example.com", info.getEmail());
    }

    @Test
    void extractSenderInfo_withOnlyEmail_shouldUseEmailAsName() {
        String header = "jane@example.com";
        SenderInfo info = emailParserService.extractSenderInfo(header);
        assertEquals("jane", info.getName());
        assertEquals("jane@example.com", info.getEmail());
    }

    @Test
    void extractSenderInfo_withNull_shouldReturnUnknown() {
        SenderInfo info = emailParserService.extractSenderInfo(null);
        assertEquals("Unknown", info.getName());
        assertEquals("unknown@unknown.com", info.getEmail());
    }

    @Test
    void extractBody_withPlainTextInMainPayload_shouldExtractCorrectly() {
        MessagePartBody body = new MessagePartBody();
        body.setData("SGVsbG8gdGVzdA=="); // "Hello test"
        MessagePart payload = new MessagePart();
        payload.setBody(body);
        Message message = new Message();
        message.setPayload(payload);

        String result = emailParserService.extractBody(message);
        assertEquals("Hello test", result.trim());
    }

    @Test
    void extractBody_withPlainTextPart_shouldExtractCorrectly() {
        MessagePartBody partBody = new MessagePartBody();
        partBody.setData("VGhpcyBpcyBhIHRlc3QgcGFydA=="); // "This is a test part"
        MessagePart part = new MessagePart();
        part.setMimeType("text/plain");
        part.setBody(partBody);

        MessagePart payload = new MessagePart();
        payload.setParts(List.of(part));
        Message message = new Message();
        message.setPayload(payload);

        String result = emailParserService.extractBody(message);
        assertEquals("This is a test part", result.trim());
    }
}
