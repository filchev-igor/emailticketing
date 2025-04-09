package lt.dev.emailticketing.service;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import lt.dev.emailticketing.client.GmailClientService;
import lt.dev.emailticketing.dto.EmailRequestDto;
import lt.dev.emailticketing.dto.MessageReplyDto;
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
    private final TicketService ticketService;

    public EmailProcessingService(
            GmailClientService gmailClientService,
            EmailParserService emailParserService,
            ApexSenderService apexSenderService,
            TicketService ticketService
    ) {
        this.gmailClientService = gmailClientService;
        this.emailParserService = emailParserService;
        this.apexSenderService = apexSenderService;
        this.ticketService = ticketService;
    }

    // In EmailProcessingService.java - enhance processEmail method
    public void processEmail(String emailId, Set<String> processedEmailIds) {
        try {
            Message fullMsg = gmailClientService.fetchFullMessage(emailId);

            // Extract headers and content
            String fromHeader = getHeaderValue(fullMsg, "From");
            String subject = getHeaderValue(fullMsg, "Subject");
            String messageId = getHeaderValue(fullMsg, "Message-ID");
            String references = getHeaderValue(fullMsg, "References");
            String inReplyTo = getHeaderValue(fullMsg, "In-Reply-To");

            SenderInfo senderInfo = emailParserService.extractSenderInfo(fromHeader);
            String body = emailParserService.extractBody(fullMsg);
            String gmailDate = formatDate(fullMsg.getInternalDate());
            String emailThreadId = fullMsg.getThreadId();

            boolean isReply = (references != null && !references.isEmpty()) ||
                    (inReplyTo != null && !inReplyTo.isEmpty());

            if (isReply) {
                // Get ticket ID from email ID (you may need to call APEX to get this)
                Long ticketId = ticketService.getTicketIdByEmailId(emailId);

                if (ticketId != null) {
                    MessageReplyDto replyDto = new MessageReplyDto(
                            emailId,
                            senderInfo.email(),
                            subject,
                            body,
                            gmailDate,
                            messageId,
                            emailThreadId,
                            ticketId.toString()
                    );

                    boolean success = apexSenderService.sendToApex(replyDto);
                    handleProcessingResult(emailId, processedEmailIds, success, "reply");
                } else {
                    logger.warn("No ticket found for thread {}", emailThreadId);
                }
            } else {
                EmailRequestDto dto = new EmailRequestDto(
                        emailId,
                        senderInfo.name(),
                        senderInfo.email(),
                        subject,
                        body,
                        gmailDate,
                        messageId,
                        emailThreadId
                );

                boolean success = apexSenderService.sendToApex(dto);
                handleProcessingResult(emailId, processedEmailIds, success, "new ticket");
            }
        } catch (Exception e) {
            logger.error("Error processing email ID {}: {}", emailId, e.getMessage(), e);
        }
    }

    private String getHeaderValue(Message message, String headerName) {
        return message.getPayload().getHeaders().stream()
                .filter(h -> headerName.equalsIgnoreCase(h.getName()))
                .findFirst()
                .map(MessagePartHeader::getValue)
                .orElse(null);
    }

    private String formatDate(Long internalDate) {
        return internalDate != null
                ? Instant.ofEpochMilli(internalDate).toString()
                : Instant.now().toString();
    }

    private void handleProcessingResult(String emailId, Set<String> processedEmailIds,
                                        boolean success, String type) {
        if (success) {
            processedEmailIds.add(emailId);
            logger.info("✅ {} processed ({}): {}", type, emailId);
        } else {
            logger.warn("⚠️ Failed to process {}: {}", type, emailId);
        }
    }
}