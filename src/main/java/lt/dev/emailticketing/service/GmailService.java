package lt.dev.emailticketing.service;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lt.dev.emailticketing.client.GmailClientService;
import lt.dev.emailticketing.dto.ProcessedEmailIdDto;
import lt.dev.emailticketing.dto.ProcessedEmailsResponseDto;
import lt.dev.emailticketing.dto.SendReplyDto;
import lt.dev.emailticketing.util.ExecutorServiceWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.Executors;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Properties;

@Service
public class GmailService {
    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);

    private final GmailClientService gmailClientService;
    private final EmailProcessingService emailProcessingService;

    private final RestTemplate restTemplate = new RestTemplate();
    private Set<String> processedEmailIds;

    @Value("${apex.processed_emails.endpoint}")
    private String apexProcessedEmailsEndpoint;

    @Value("${apex.api.key}")
    private String apexApiKey;

    @Value("${gmail.thread-pool-size:3}")
    private int threadPoolSize;

    public GmailService(
            GmailClientService gmailClientService,
            EmailProcessingService emailProcessingService
    ) {
        this.gmailClientService = gmailClientService;
        this.emailProcessingService = emailProcessingService;
    }

    @PostConstruct
    public void init() throws Exception {
        logger.info("Initializing GmailService...");
        loadProcessedEmailIdsWithRetries();
        gmailClientService.initClient();
    }

    @Scheduled(fixedRate = 60000)
    public void scanInbox() throws Exception {
        logger.info("📥 Scanning Gmail inbox for new messages...");

        try {
            List<Message> messages = gmailClientService.fetchInboxMessages();

            if (messages != null) {
                Collections.reverse(messages);

                try (ExecutorServiceWrapper executorWrapper = new ExecutorServiceWrapper(
                        Executors.newFixedThreadPool(threadPoolSize))) {

                    for (Message msg : messages) {
                        String emailId = msg.getId();

                        if (!processedEmailIds.contains(emailId)) {
                            executorWrapper.submit(() -> emailProcessingService.processEmail(emailId, processedEmailIds));
                        } else {
                            logger.debug("🔁 Skipping already processed email with ID: {}", emailId);
                        }
                    }
                }
            } else {
                logger.info("No new messages found in inbox.");
            }

        } catch (TokenResponseException e) {
            if (e.getDetails() != null && "invalid_grant".equals(e.getDetails().getError())) {
                logger.warn("⚠️ OAuth token expired or revoked. Reauthorizing...");
                gmailClientService.reauthorize();
                logger.info("✅ Reauthorization successful. Will retry inbox scan later.");
            } else {
                throw e;
            }
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void refreshProcessedEmailIds() {
        logger.info("🔄 Refreshing processed email IDs...");
        loadProcessedEmailIds();
        logger.info("✅ Refreshed processed email IDs: {}", processedEmailIds);
    }

    private void loadProcessedEmailIdsWithRetries() throws InterruptedException {
        int retries = 3;
        processedEmailIds = new HashSet<>();

        while (retries > 0) {
            try {
                loadProcessedEmailIds();
                if (!processedEmailIds.isEmpty()) {
                    logger.debug("✅ Loaded processed email IDs: {}", processedEmailIds);
                    return;
                }
                logger.warn("⚠️ Processed email list is empty. Retrying...");
            } catch (Exception e) {
                logger.error("❌ Failed to load processed email IDs: {}", e.getMessage(), e);
            }
            retries--;
            if (retries > 0) {
                Thread.sleep(2000);
            }
        }

        logger.warn("⚠️ Gave up loading processed emails after 3 retries. Will proceed with empty list.");
    }

    private void loadProcessedEmailIds() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apexApiKey);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            logger.debug("🔍 Fetching processed email IDs from APEX at {}", apexProcessedEmailsEndpoint);

            ResponseEntity<ProcessedEmailsResponseDto> response = restTemplate.exchange(
                    apexProcessedEmailsEndpoint,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK &&
                    response.getBody() != null &&
                    response.getBody().getItems() != null) {

                processedEmailIds.addAll(response.getBody().getItems().stream()
                        .map(ProcessedEmailIdDto::getEmailId)
                        .toList());

                logger.debug("✅ Loaded {} processed email IDs from APEX.", processedEmailIds.size());

            } else {
                logger.warn("⚠️ APEX responded OK but no items found.");
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("❌ HTTP error while loading email IDs: {} - {}", e.getStatusCode(), e.getStatusText());
            logger.error("📩 Response body: {}", e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("❌ Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("Unexpected error loading processed emails", e);
        }
    }

    public void sendReplyEmail(SendReplyDto dto) throws Exception {
        logger.info("📤 Sending reply email to {}", dto.getTo());

        // 🔍 Fetch original Gmail message using internal emailId from APEX
        Message originalMessage = gmailClientService.getGmail()
                .users()
                .messages()
                .get("me", dto.getEmailId())
                .execute();

        // 🧵 Extract and set threadId
        String threadId = originalMessage.getThreadId();
        dto.setThreadId(threadId);
        logger.info("🧵 Retrieved threadId: {}", threadId);

        // 📎 Try to get the real Message-ID header for proper threading
        String messageIdHeader = null;

        if (originalMessage.getPayload() != null &&
                originalMessage.getPayload().getHeaders() != null) {

            messageIdHeader = originalMessage.getPayload().getHeaders().stream()
                    .filter(h -> "Message-ID".equalsIgnoreCase(h.getName()))
                    .map(MessagePartHeader::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // 🧠 Use valid Message-ID if found
        if (messageIdHeader != null) {
            dto.setMessageId(messageIdHeader);
            logger.info("📎 Found Message-ID header: {}", messageIdHeader);
        } else {
            dto.setMessageId(null); // Avoid invalid fallback
            logger.warn("⚠️ No Message-ID header found in Gmail message {}. Skipping reply headers.", dto.getEmailId());
        }

        // ✉️ Create the reply email
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
        message.setFrom(new InternetAddress(dto.getFrom()));
        message.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(dto.getTo()));
        message.setSubject(dto.getSubject());
        message.setText(dto.getBody());

        // 🧷 Set In-Reply-To and References only if we have a valid Message-ID
        if (dto.getMessageId() != null) {
            message.setHeader("In-Reply-To", dto.getMessageId());
            message.setHeader("References", dto.getMessageId());
            logger.info("📎 Set reply headers with Message-ID: {}", dto.getMessageId());
        }

        // ✉️ Encode and prepare for sending via Gmail API
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        message.writeTo(buffer);
        byte[] rawMessageBytes = buffer.toByteArray();
        String encodedEmail = Base64.getUrlEncoder().encodeToString(rawMessageBytes);

        Message gmailMessage = new Message();
        gmailMessage.setRaw(encodedEmail);
        gmailMessage.setThreadId(threadId); // ✅ Always set for Gmail threading

        gmailClientService.getGmail().users().messages().send("me", gmailMessage).execute();
        logger.info("✅ Reply email sent successfully in thread {}", threadId);
    }
}
