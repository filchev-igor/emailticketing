package lt.dev.emailticketing;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GmailService {
    private Gmail gmail;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper(); // For JSON serialization
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String PROCESSED_EMAILS_FILE = "processed_emails.txt";
    private Set<String> processedEmailIds;

    @Value("${apex.rest.endpoint}")
    private String apexEndpoint;

    @PostConstruct
    public void init() throws Exception {
        loadProcessedEmailIds();

        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(new ClassPathResource("credentials.json").getInputStream()));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets,
                Collections.singleton("https://www.googleapis.com/auth/gmail.modify"))
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("EmailTicketing")
                .build();
    }

    @Scheduled(fixedRate = 60000)
    public void scanInbox() throws Exception {
        List<Message> messages = gmail.users().messages().list("me").setQ("in:inbox").execute().getMessages();
        if (messages != null) {
            for (Message msg : messages) {
                String emailId = msg.getId();
                if (!processedEmailIds.contains(emailId)) {
                    Message fullMsg = gmail.users().messages().get("me", emailId).execute();
                    String sender = fullMsg.getPayload().getHeaders().stream()
                            .filter(h -> h.getName().equals("From"))
                            .findFirst().map(h -> h.getValue()).orElse("Unknown");
                    String subject = fullMsg.getPayload().getHeaders().stream()
                            .filter(h -> h.getName().equals("Subject"))
                            .findFirst().map(h -> h.getValue()).orElse("No Subject");
                    String body = fullMsg.getSnippet();

                    sendToApex(emailId, sender, subject, body);
                    processedEmailIds.add(emailId);
                    saveProcessedEmailIds();
                }
            }
        }
    }

    private void sendToApex(String emailId, String sender, String subject, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        EmailData emailData = new EmailData(emailId, sender, subject, body);
        String json;
        try {
            json = objectMapper.writeValueAsString(emailData);
            System.out.println("Sending JSON to APEX: " + json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize email data to JSON", e);
        }

        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(apexEndpoint, entity, String.class);
            System.out.println("APEX Response Status: " + response.getStatusCode());
            System.out.println("APEX Response Body: " + (response.getBody() != null ? response.getBody() : "Empty"));
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("HTTP Error from APEX: " + e.getStatusCode() + " - " + e.getStatusText());
            System.err.println("Response Body: " + (e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Empty"));
            throw e;
        } catch (Exception e) {
            System.err.println("Failed to send to APEX: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void loadProcessedEmailIds() {
        processedEmailIds = new HashSet<>();
        File file = new File(PROCESSED_EMAILS_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processedEmailIds.add(line.trim());
                }
            } catch (IOException e) {
                System.err.println("Failed to load processed email IDs: " + e.getMessage());
            }
        }
    }

    private void saveProcessedEmailIds() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PROCESSED_EMAILS_FILE))) {
            for (String id : processedEmailIds) {
                writer.write(id);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to save processed email IDs: " + e.getMessage());
        }
    }

    // DTO for JSON serialization
    private static class EmailData {
        private final String email_id;
        private final String sender;
        private final String subject;
        private final String body;

        public EmailData(String email_id, String sender, String subject, String body) {
            this.email_id = email_id;
            this.sender = sender;
            this.subject = subject;
            this.body = body;
        }

        public String getEmail_id() {
            return email_id;
        }

        public String getSender() {
            return sender;
        }

        public String getSubject() {
            return subject;
        }

        public String getBody() {
            return body;
        }
    }
}