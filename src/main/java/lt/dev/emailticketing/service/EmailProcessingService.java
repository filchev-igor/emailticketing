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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Set;

@Service
public class EmailProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(EmailProcessingService.class);

    private final GmailClientService gmailClientService;
    private final EmailParserService emailParserService;
    private final ApexSenderService apexSenderService;
    private final TicketService ticketService;
    private final RestTemplate restTemplate;

    @Value("${apex.api.key}")
    private String apexApiKey;

    @Value("${apex.processed_replies.endpoint}")
    private String processedRepliesEndpoint;

    public EmailProcessingService(
            GmailClientService gmailClientService,
            EmailParserService emailParserService,
            ApexSenderService apexSenderService,
            TicketService ticketService,
            RestTemplate restTemplate
    ) {
        this.gmailClientService = gmailClientService;
        this.emailParserService = emailParserService;
        this.apexSenderService = apexSenderService;
        this.ticketService = ticketService;
        this.restTemplate = restTemplate;
    }

    public void processEmail(String emailId, Set<String> processedEmailIds) {
        try {
            if (processedEmailIds.contains(emailId)) {
                logger.debug("Email {} already processed locally, skipping", emailId);
                return;
            }

            Message fullMsg = gmailClientService.fetchFullMessage(emailId);
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
                if (isReplyProcessed(emailId)) {
                    logger.debug("Reply email {} already processed in database, skipping", emailId);
                    processedEmailIds.add(emailId);
                    return;
                }

                Long ticketId = ticketService.getTicketIdByThreadId(emailThreadId);
                if (ticketId != null) {
                    MessageReplyDto replyDto = new MessageReplyDto(
                            emailId, senderInfo.email(), subject, body, gmailDate,
                            messageId, emailThreadId, ticketId.toString());
                    boolean success = apexSenderService.sendToApex(replyDto);
                    if (success) {
                        processedEmailIds.add(emailId);
                        logger.info("✅ Reply processed ({}): {}", emailId);
                    } else {
                        logger.warn("⚠️ Failed to process reply: {}", emailId);
                    }
                } else {
                    logger.warn("No ticket found for thread {}", emailThreadId);
                }
            } else {
                if (isEmailProcessed(emailId)) {
                    logger.debug("Initial email {} already processed in database, skipping", emailId);
                    processedEmailIds.add(emailId);
                    return;
                }

                EmailRequestDto dto = new EmailRequestDto(
                        emailId, senderInfo.name(), senderInfo.email(), subject,
                        body, gmailDate, messageId, emailThreadId);
                boolean success = apexSenderService.sendToApex(dto);
                if (success) {
                    processedEmailIds.add(emailId);
                    logger.info("✅ New ticket processed ({}): {}", emailId);
                } else {
                    logger.warn("⚠️ Failed to process new ticket: {}", emailId);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing email ID {}: {}", emailId, e.getMessage(), e);
        }
    }

    private boolean isEmailProcessed(String emailId) {
        try {
            String endpoint = "https://apex.oracle.com/pls/apex/request_system/tickets_api/processed_emails/" + emailId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apexApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            restTemplate.getForEntity(endpoint, String.class);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            logger.error("Error checking processed email {}: {}", emailId, e.getMessage());
            return true;
        } catch (Exception e) {
            logger.error("Unexpected error checking processed email {}: {}", emailId, e.getMessage());
            return true;
        }
    }

    private boolean isReplyProcessed(String emailId) {
        try {
            String endpoint = processedRepliesEndpoint + "/" + emailId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apexApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            restTemplate.getForEntity(endpoint, String.class);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            logger.error("Error checking processed reply {}: {}", emailId, e.getMessage());
            return true;
        } catch (Exception e) {
            logger.error("Unexpected error checking processed reply {}: {}", emailId, e.getMessage());
            return true;
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
}