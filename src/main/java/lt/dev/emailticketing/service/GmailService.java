package lt.dev.emailticketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import lt.dev.emailticketing.auth.GmailAuthService;
import lt.dev.emailticketing.dto.EmailRequestDto;
import lt.dev.emailticketing.dto.ProcessedEmailsResponseDto;
import lt.dev.emailticketing.dto.ProcessedEmailIdDto;
import lt.dev.emailticketing.internal.SenderInfo;
import static lt.dev.emailticketing.util.GmailUtils.*;
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

@Service
public class GmailService {
    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private Gmail gmail;
    private Credential credential;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Set<String> processedEmailIds;

    private final GmailAuthService gmailAuthService;

    @Value("${apex.tickets.endpoint}")
    private String apexTicketsEndpoint;

    @Value("${apex.processed_emails.endpoint}")
    private String apexProcessedEmailsEndpoint;

    @Value("${apex.api.key}")
    private String apexApiKey;

    @Value("${gmail.user.id}")
    private String gmailUserId;

    @Value("${gmail.query}")
    private String gmailQuery;

    @Value("${gmail.max-results}")
    private long maxResults;

    @PostConstruct
    public void init() throws Exception {
        loadProcessedEmailIdsWithRetries();

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        credential = gmailAuthService.authorize();

        gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("EmailTicketing")
                .build();
    }

    @Scheduled(fixedRate = 60000)
    public void scanInbox() throws Exception {
        logger.info("Scanning Gmail inbox for new messages...");
        try {
            List<Message> messages = gmail.users().messages().list(gmailUserId)
                    .setQ(gmailQuery)
                    .setMaxResults(maxResults)
                    .execute().getMessages();

            if (messages != null) {
                Collections.reverse(messages);

                for (Message msg : messages) {
                    String emailId = msg.getId();
                    if (!processedEmailIds.contains(emailId)) {
                        logger.info("Processing new email with ID: {}", emailId);
                        Message fullMsg = gmail.users().messages().get(gmailUserId, emailId).execute();
                        String fromHeader = fullMsg.getPayload().getHeaders().stream()
                                .filter(h -> h.getName().equals("From"))
                                .findFirst().map(MessagePartHeader::getValue).orElse("Unknown");
                        SenderInfo senderInfo = extractSenderInfo(fromHeader);
                        String subject = fullMsg.getPayload().getHeaders().stream()
                                .filter(h -> h.getName().equals("Subject"))
                                .findFirst().map(MessagePartHeader::getValue).orElse("No Subject");
                        String body = extractBody(fullMsg);
                        sendToApex(emailId, senderInfo.getName(), senderInfo.getEmail(), subject, body);
                    } else {
                        logger.info("Skipping already processed email with ID: {}", emailId);
                    }
                }
            } else {
                logger.info("No new messages found in inbox.");
            }
        } catch (TokenResponseException e) {
            if (e.getDetails() != null && "invalid_grant".equals(e.getDetails().getError())) {
                logger.warn("OAuth token expired or revoked. Reauthorizing...");

                NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

                boolean cleared = gmailAuthService.clearStoredToken();

                if (!cleared) {
                    logger.warn("Could not delete stored token. Proceeding anyway.");
                }

                credential = gmailAuthService.authorize();

                gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                        .setApplicationName("EmailTicketing")
                        .build();
                logger.info("Reauthorization successful. Will retry inbox scan on next scheduled run.");
            } else {
                throw e;
            }
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void refreshProcessedEmailIds() {
        logger.info("Refreshing processed email IDs to sync with external updates...");
        loadProcessedEmailIds();
        logger.info("Refreshed processed email IDs: {}", processedEmailIds);
    }

    private void sendToApex(String emailId, String senderName, String senderEmail, String subject, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apexApiKey);
        EmailRequestDto emailData = new EmailRequestDto(emailId, senderName, senderEmail, subject, body);
        String json;
        try {
            json = objectMapper.writeValueAsString(emailData);
            logger.info("Sending JSON to APEX: {}", json);
        } catch (Exception e) {
            logger.error("Failed to serialize email data to JSON", e);
            throw new RuntimeException("Failed to serialize email data to JSON", e);
        }
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(apexTicketsEndpoint, entity, String.class);
            logger.info("APEX Response Status: {}", response.getStatusCode());
            logger.info("APEX Response Body: {}", response.getBody() != null ? response.getBody() : "Empty");
            processedEmailIds.add(emailId);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP Error from APEX: {} - {}", e.getStatusCode(), e.getStatusText());
            logger.error("Response Body: {}", e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Empty");
            throw e;
        } catch (Exception e) {
            logger.error("Failed to send to APEX: {} - {}", e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
    }

    private void loadProcessedEmailIdsWithRetries() throws InterruptedException {
        int retries = 3;
        processedEmailIds = new HashSet<>();
        while (retries > 0) {
            try {
                loadProcessedEmailIds();
                if (!processedEmailIds.isEmpty()) {
                    logger.info("Successfully loaded processed email IDs: {}", processedEmailIds);
                    return;
                }
                logger.warn("Processed email IDs list is empty, retrying...");
            } catch (Exception e) {
                logger.error("Failed to load processed email IDs: {}", e.getMessage(), e);
            }
            retries--;
            if (retries > 0) {
                Thread.sleep(2000);
            }
        }
        logger.warn("Failed to load processed email IDs after 3 retries. Proceeding with empty set, which may cause reprocessing.");
    }

    private void loadProcessedEmailIds() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apexApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            logger.info("Attempting to load processed email IDs from: {}", apexProcessedEmailsEndpoint);
            ResponseEntity<ProcessedEmailsResponseDto> response = restTemplate.exchange(
                    apexProcessedEmailsEndpoint,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().getItems() != null) {
                processedEmailIds.addAll(response.getBody().getItems().stream()
                        .map(ProcessedEmailIdDto::getEmailId)
                        .toList());
                logger.info("Loaded {} processed email IDs from APEX: {}", processedEmailIds.size(), processedEmailIds);
            } else {
                logger.warn("Received 200 OK but body or items list is null or empty from {}", apexProcessedEmailsEndpoint);
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP Error loading processed email IDs: {} - {}", e.getStatusCode(), e.getStatusText());
            logger.error("Response Body: {}", e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Empty");
            throw e;
        } catch (Exception e) {
            logger.error("Failed to load processed email IDs: {}", e.getMessage(), e);
            throw new RuntimeException("Unexpected error loading processed email IDs", e);
        }
    }

    private SenderInfo extractSenderInfo(String fromHeader) {
        String name = "Unknown";
        String email = "unknown@unknown.com";
        if (fromHeader != null && !fromHeader.isEmpty()) {
            if (fromHeader.contains("<")) {
                name = fromHeader.substring(0, fromHeader.indexOf("<")).trim();
                email = fromHeader.substring(fromHeader.indexOf("<") + 1, fromHeader.indexOf(">")).trim();
            } else {
                email = fromHeader.trim();
                name = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
            }
        }
        return new SenderInfo(name, email);
    }

    private String extractBody(Message message) {
        StringBuilder body = new StringBuilder();
        if (message.getPayload() != null) {
            if (message.getPayload().getBody() != null && message.getPayload().getBody().getData() != null) {
                body.append(decodeBase64(message.getPayload().getBody().getData()));
            }
            List<MessagePart> parts = message.getPayload().getParts();
            if (parts != null) {
                for (MessagePart part : parts) {
                    if ("text/plain".equals(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
                        body.append(decodeBase64(part.getBody().getData()));
                    }
                }
            }
        }

        // Gmail line-wrap fix: combine lines into paragraphs
        return normalizeParagraphs(body.toString());
    }

    public GmailService(GmailAuthService gmailAuthService) {
        this.gmailAuthService = gmailAuthService;
    }
}
