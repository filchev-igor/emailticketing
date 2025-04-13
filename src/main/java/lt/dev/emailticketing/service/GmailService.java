package lt.dev.emailticketing.service;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.services.gmail.model.Message;
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
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.Executors;

@Service
public class GmailService {
    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);

    private final GmailClientService gmailClientService;
    private final EmailProcessingService emailProcessingService;
    private final RestTemplate restTemplate;

    private Set<String> processedEmailIds;

    @Value("${apex.processed_emails.endpoint}")
    private String apexProcessedEmailsEndpoint;

    @Value("${apex.processed_replies.endpoint}")
    private String apexProcessedRepliesEndpoint;

    @Value("${apex.api.key}")
    private String apexApiKey;

    @Value("${gmail.thread-pool-size:2}")
    private int threadPoolSize;

    public GmailService(
            GmailClientService gmailClientService,
            EmailProcessingService emailProcessingService,
            RestTemplate restTemplate
    ) {
        this.gmailClientService = gmailClientService;
        this.emailProcessingService = emailProcessingService;
        this.restTemplate = restTemplate;
        this.processedEmailIds = new HashSet<>();
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
                        executorWrapper.submit(() -> {
                            emailProcessingService.processEmail(emailId, processedEmailIds);
                        });
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

    @Scheduled(fixedRate = 300000)
    public void refreshProcessedEmailIds() {
        logger.info("🔄 Refreshing processed email IDs...");
        try {
            loadProcessedEmailIds();
            logger.info("✅ Refreshed processed email IDs, count: {}", processedEmailIds.size());
        } catch (Exception e) {
            logger.error("Failed to refresh processed email IDs: {}", e.getMessage());
        }
    }

    private void loadProcessedEmailIdsWithRetries() throws InterruptedException {
        int retries = 3;
        processedEmailIds = new HashSet<>();
        while (retries > 0) {
            try {
                loadProcessedEmailIds();
                if (!processedEmailIds.isEmpty()) {
                    logger.debug("✅ Loaded processed email IDs: {}", processedEmailIds.size());
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
        logger.warn("⚠️ Gave up loading processed emails after 3 retries. Proceeding with empty list.");
    }

    private void loadProcessedEmailIds() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apexApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            logger.debug("🔍 Fetching processed email IDs from APEX at {}", apexProcessedEmailsEndpoint);
            ResponseEntity<ProcessedEmailsResponseDto> emailResponse = restTemplate.exchange(
                    apexProcessedEmailsEndpoint,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            if (emailResponse.getStatusCode() == HttpStatus.OK &&
                    emailResponse.getBody() != null &&
                    emailResponse.getBody().getItems() != null) {
                processedEmailIds.addAll(emailResponse.getBody().getItems().stream()
                        .map(ProcessedEmailIdDto::getEmailId)
                        .toList());
            }
        } catch (Exception e) {
            logger.error("❌ Error loading processed_emails: {}", e.getMessage());
        }

        try {
            logger.debug("🔍 Fetching processed reply IDs from APEX at {}", apexProcessedRepliesEndpoint);
            ResponseEntity<Map<String, List<String>>> replyResponse = restTemplate.exchange(
                    apexProcessedRepliesEndpoint,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            if (replyResponse.getStatusCode() == HttpStatus.OK && replyResponse.getBody() != null) {
                List<String> replyIds = replyResponse.getBody().get("items");
                if (replyIds != null) {
                    processedEmailIds.addAll(replyIds);
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error loading processed_replies: {}", e.getMessage());
        }

        logger.debug("✅ Loaded total {} processed IDs", processedEmailIds.size());
    }

    public void sendReplyEmail(SendReplyDto dto) throws Exception {
        logger.info("📤 Sending reply email to {}", dto.getTo());
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
        message.setFrom(new InternetAddress(dto.getFrom()));
        message.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(dto.getTo()));
        message.setSubject(dto.getSubject());
        message.setText(dto.getBody());
        if (dto.getMessageId() != null && !dto.getMessageId().isEmpty()) {
            message.setHeader("In-Reply-To", dto.getMessageId());
            message.setHeader("References", dto.getMessageId());
            logger.debug("🔗 Added In-Reply-To + References: {}", dto.getMessageId());
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        message.writeTo(buffer);
        byte[] rawMessageBytes = buffer.toByteArray();
        String encodedEmail = Base64.getUrlEncoder().encodeToString(rawMessageBytes);
        Message gmailMessage = new Message();
        gmailMessage.setRaw(encodedEmail);
        if (dto.getThreadId() != null && !dto.getThreadId().isEmpty()) {
            gmailMessage.setThreadId(dto.getThreadId());
        }
        gmailClientService.getGmail().users().messages().send("me", gmailMessage).execute();
        logger.info("✅ Reply email sent successfully");
    }
}