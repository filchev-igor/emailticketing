package lt.dev.emailticketing.service;

import com.google.api.services.gmail.model.Message;
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

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class EmailProcessingServiceTest {

    private GmailClientService gmailClientService;
    private EmailParserService emailParserService;
    private ApexSenderService apexSenderService;
    private EmailProcessingService emailProcessingService;

    @BeforeEach
    void setUp() {
        gmailClientService = mock(GmailClientService.class);
        emailParserService = mock(EmailParserService.class);
        apexSenderService = mock(ApexSenderService.class);

        emailProcessingService = new EmailProcessingService(
                gmailClientService, emailParserService, apexSenderService
        );
    }

    @Test
    void testProcessEmail_successfullyProcessesAndAddsToSet() throws Exception {
        // Arrange
        String emailId = "abc123";
        Set<String> processedEmailIds = new HashSet<>();

        MessagePartHeader fromHeader = new MessagePartHeader();
        fromHeader.setName("From");
        fromHeader.setValue("User Name <user@example.com>");

        MessagePartHeader subjectHeader = new MessagePartHeader();
        subjectHeader.setName("Subject");
        subjectHeader.setValue("Test subject");

        Message message = new Message();
        message.setInternalDate(System.currentTimeMillis());
        message.setPayload(new Message.Part().setHeaders(List.of(fromHeader, subjectHeader)));

        when(gmailClientService.fetchFullMessage(emailId)).thenReturn(message);
        when(emailParserService.extractSenderInfo(anyString())).thenReturn(new SenderInfo("User Name", "user@example.com"));
        when(emailParserService.extractBody(any(Message.class))).thenReturn("Test body");
        when(apexSenderService.sendToApex(any())).thenReturn(true);

        // Act
        emailProcessingService.processEmail(emailId, processedEmailIds);

        // Assert
        assertTrue(processedEmailIds.contains(emailId), "Email ID should be added to processed set");
        verify(gmailClientService).fetchFullMessage(emailId);
        verify(apexSenderService).sendToApex(any());
        verify(emailParserService).extractSenderInfo(anyString());
        verify(emailParserService).extractBody(any(Message.class));
    }
}
