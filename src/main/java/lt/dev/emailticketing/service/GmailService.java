package lt.dev.emailticketing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GmailService {

    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);

    private Gmail gmail;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private Set<String> processedEmailIds;

    @Value("${apex.tickets.endpoint}")
    private String apexTicketsEndpoint;

    @Value("${apex.processed_emails.endpoint}")
    private String apexProcessedEmailsEndpoint;

    @Value("${oauth2.local.server.port:8888}")
    private int oauth2LocalServerPort;

    @PostConstruct
    public void init() throws Exception {
        loadProcessedEmailIds();
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(new ClassPathResource("credentials.json").getInputStream()));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, Collections.singleton("https://www.googleapis.com/auth/gmail.modify"))
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(oauth2LocalServerPort).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("EmailTicketing")
                .build();
    }

    @Scheduled(fixedRate = 60000)
    public void scanInbox() throws Exception {
        logger.info("Scanning Gmail inbox for new messages...");
        List<Message> messages = gmail.users().messages().list("me").setQ("in:inbox").execute().getMessages();
        if (messages != null) {
            for (Message msg : messages) {
                String emailId = msg.getId();
                if (!processedEmailIds.contains(emailId)) {
                    logger.info("Processing new email with ID: {}", emailId);
                    Message fullMsg = gmail.users().messages().get("me", emailId).execute();
                    String fromHeader = fullMsg.getPayload().getHeaders().stream()
                            .filter(h -> h.getName().equals("From"))
                            .findFirst().map(h -> h.getValue()).orElse("Unknown");
                    SenderInfo senderInfo = extractSenderInfo(fromHeader);
                    String subject = fullMsg.getPayload().getHeaders().stream()
                            .filter(h -> h.getName().equals("Subject"))
                            .findFirst().map(h -> h.getValue()).orElse("No Subject");
                    String body = extractBody(fullMsg);

                    sendToApex(emailId, senderInfo.getName(), senderInfo.getEmail(), subject, body);
                } else {
                    logger.info("Skipping already processed email with ID: {}", emailId);
                }
            }
        } else {
            logger.info("No new messages found in inbox.");
        }
    }

    private void sendToApex(String emailId, String senderName, String senderEmail, String subject, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        EmailData emailData = new EmailData(emailId, senderName, senderEmail, subject, body);
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
            processedEmailIds.add(emailId); // Add to in-memory set after successful POST
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP Error from APEX: {} - {}", e.getStatusCode(), e.getStatusText());
            logger.error("Response Body: {}", e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Empty");
            throw e;
        } catch (Exception e) {
            logger.error("Failed to send to APEX: {} - {}", e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
    }

    private void loadProcessedEmailIds() {
        processedEmailIds = new HashSet<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            logger.info("Attempting to load processed email IDs from: {}", apexProcessedEmailsEndpoint);
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    apexProcessedEmailsEndpoint,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                processedEmailIds.addAll(response.getBody());
                logger.info("Loaded {} processed email IDs from APEX: {}", processedEmailIds.size(), processedEmailIds);
            } else {
                logger.warn("Failed to load processed email IDs. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP Error loading processed email IDs: {} - {}", e.getStatusCode(), e.getStatusText());
            logger.error("Response Body: {}", e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Empty");
        } catch (Exception e) {
            logger.error("Failed to load processed email IDs: {}", e.getMessage(), e);
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
        return !body.isEmpty() ? body.toString() : message.getSnippet();
    }

    private String decodeBase64(String encodedData) {
        if (encodedData == null) return "";
        try {
            String standardBase64 = encodedData.replace('-', '+').replace('_', '/');
            int padding = (4 - standardBase64.length() % 4) % 4;
            for (int i = 0; i < padding; i++) {
                standardBase64 += "=";
            }
            return new String(Base64.getDecoder().decode(standardBase64));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to decode Base64 data: {}", encodedData, e);
            return "";
        }
    }

    private static class SenderInfo {
        private final String name;
        private final String email;

        public SenderInfo(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }

    private static class EmailData {
        private final String email_id;
        private final String sender_name;
        private final String sender_email;
        private final String subject;
        private final String body;

        public EmailData(String email_id, String sender_name, String sender_email, String subject, String body) {
            this.email_id = email_id;
            this.sender_name = sender_name;
            this.sender_email = sender_email;
            this.subject = subject;
            this.body = body;
        }

        public String getEmail_id() {
            return email_id;
        }

        public String getSender_name() {
            return sender_name;
        }

        public String getSender_email() {
            return sender_email;
        }

        public String getSubject() {
            return subject;
        }

        public String getBody() {
            return body;
        }
    }
}