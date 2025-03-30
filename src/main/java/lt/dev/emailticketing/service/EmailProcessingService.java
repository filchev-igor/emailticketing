package lt.dev.emailticketing.service;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import lt.dev.emailticketing.client.GmailClientService;
import lt.dev.emailticketing.dto.EmailRequestDto;
import lt.dev.emailticketing.internal.SenderInfo;
import lt.dev.emailticketing.parser.EmailParserService;
import lt.dev.emailticketing.sender.ApexSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
public class EmailProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(EmailProcessingService.class);

    private final GmailClientService gmailClientService;
    private final EmailParserService emailParserService;
    private final ApexSenderService apexSenderService;

    public EmailProcessingService(
            GmailClientService gmailClientService,
            EmailParserService emailParserService,
            ApexSenderService apexSenderService
    ) {
        this.gmailClientService = gmailClientService;
        this.emailParserService = emailParserService;
        this.apexSenderService = apexSenderService;
    }

    public void processEmail(String emailId, Set<String> processedEmailIds) {
        try {
            logger.debug("➡️ Processing email ID: {}", emailId);

            Message fullMsg = gmailClientService.fetchFullMessage(emailId);

            String fromHeader = fullMsg.getPayload().getHeaders().stream()
                    .filter(h -> "From".equalsIgnoreCase(h.getName()))
                    .findFirst()
                    .map(MessagePartHeader::getValue)
                    .orElse("Unknown");

            String subject = fullMsg.getPayload().getHeaders().stream()
                    .filter(h -> "Subject".equalsIgnoreCase(h.getName()))
                    .findFirst()
                    .map(MessagePartHeader::getValue)
                    .orElse("No Subject");

            SenderInfo senderInfo = emailParserService.extractSenderInfo(fromHeader);
            String body = emailParserService.extractBody(fullMsg);

            String gmailDate = fullMsg.getInternalDate() != null
                    ? Instant.ofEpochMilli(fullMsg.getInternalDate()).toString()
                    : Instant.now().toString();

            EmailRequestDto dto = new EmailRequestDto(
                    emailId,
                    senderInfo.name(),
                    senderInfo.email(),
                    subject,
                    body,
                    gmailDate
            );

            boolean success = apexSenderService.sendToApex(dto);

            if (success) {
                processedEmailIds.add(emailId); // ✅ без synchronized
                logger.info("✅ Email ID {} processed and stored in APEX", emailId);
            } else {
                logger.warn("⚠️ Email ID {} failed to send to APEX", emailId);
            }

        } catch (Exception e) {
            logger.error("❌ Error processing email ID {}: {}", emailId, e.getMessage(), e);
        }
    }
}