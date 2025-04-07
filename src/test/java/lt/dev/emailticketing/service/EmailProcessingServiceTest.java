package lt.dev.emailticketing.service;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import lt.dev.emailticketing.client.GmailClientService;
import lt.dev.emailticketing.internal.SenderInfo;
import lt.dev.emailticketing.parser.EmailParserService;
import lt.dev.emailticketing.sender.ApexSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailProcessingServiceTest {

    private GmailClientService gmailClientService;
    private EmailParserService emailParserService;
    private ApexSenderService apexSenderService;
    private EmailProcessingService emailProcessingService;

    @BeforeEach
    void setup() {
        gmailClientService = mock(GmailClientService.class);
        emailParserService = mock(EmailParserService.class);
        apexSenderService = mock(ApexSenderService.class);
        emailProcessingService = new EmailProcessingService(gmailClientService, emailParserService, apexSenderService);
    }

    @Test
    void testProcessEmail_successfulProcessing() throws Exception {
        // given
        String emailId = "abc123";
        Set<String> processedIds = new HashSet<>();

        Message gmailMessage = new Message();
        gmailMessage.setId(emailId);
        gmailMessage.setInternalDate(System.currentTimeMillis());

        MessagePartHeader fromHeader = new MessagePartHeader();
        fromHeader.setName("From");
        fromHeader.setValue("John <john@example.com>");

        MessagePartHeader subjectHeader = new MessagePartHeader();
        subjectHeader.setName("Subject");
        subjectHeader.setValue("Help needed");

        MessagePart part = new MessagePart();
        part.setHeaders(List.of(fromHeader, subjectHeader));
        gmailMessage.setPayload(part);

        when(gmailClientService.fetchFullMessage(emailId)).thenReturn(gmailMessage);
        when(emailParserService.extractSenderInfo("John <john@example.com>"))
                .thenReturn(new SenderInfo("John", "john@example.com"));
        when(emailParserService.extractBody(gmailMessage)).thenReturn("Hello, I need help.");
        when(apexSenderService.sendToApex(any())).thenReturn(true);

        // when
        emailProcessingService.processEmail(emailId, processedIds);

        // then
        assertTrue(processedIds.contains(emailId));
        verify(gmailClientService).fetchFullMessage(emailId);
        verify(emailParserService).extractSenderInfo(any());
        verify(emailParserService).extractBody(any());
        verify(apexSenderService).sendToApex(any());
    }

    @Test
    void testProcessEmail_failedToSendToApex() throws Exception {
        // given
        String emailId = "def456";
        Set<String> processedIds = new HashSet<>();

        Message gmailMessage = new Message();
        gmailMessage.setId(emailId);
        gmailMessage.setInternalDate(System.currentTimeMillis());

        MessagePartHeader fromHeader = new MessagePartHeader();
        fromHeader.setName("From");
        fromHeader.setValue("Alice <alice@example.com>");

        MessagePartHeader subjectHeader = new MessagePartHeader();
        subjectHeader.setName("Subject");
        subjectHeader.setValue("Issue with order");

        MessagePart part = new MessagePart();
        part.setHeaders(List.of(fromHeader, subjectHeader));
        gmailMessage.setPayload(part);

        when(gmailClientService.fetchFullMessage(emailId)).thenReturn(gmailMessage);
        when(emailParserService.extractSenderInfo("Alice <alice@example.com>"))
                .thenReturn(new SenderInfo("Alice", "alice@example.com"));
        when(emailParserService.extractBody(gmailMessage)).thenReturn("I have a problem with my order.");
        when(apexSenderService.sendToApex(any())).thenReturn(false); // simulate failure

        // when
        emailProcessingService.processEmail(emailId, processedIds);

        // then
        assertFalse(processedIds.contains(emailId));
        verify(gmailClientService).fetchFullMessage(emailId);
        verify(emailParserService).extractSenderInfo(any());
        verify(emailParserService).extractBody(any());
        verify(apexSenderService).sendToApex(any());
    }
}
