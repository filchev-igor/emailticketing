package lt.dev.emailticketing;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

@Service
public class GmailService {
    private Gmail gmail;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens"; // Stores tokens in project dir

    @Value("${apex.rest.endpoint}")
    private String apexEndpoint;

    @PostConstruct
    public void init() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        // Load client secrets from credentials.json
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(new ClassPathResource("credentials.json").getInputStream()));

        // Build OAuth 2.0 flow and trigger user authorization
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets,
                Collections.singleton("https://www.googleapis.com/auth/gmail.modify"))
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        // Build Gmail service
        gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("EmailTicketing")
                .build();
    }

    @Scheduled(fixedRate = 60000)
    public void scanInbox() throws Exception {
        List<Message> messages = gmail.users().messages().list("me").setQ("in:inbox").execute().getMessages();
        if (messages != null) {
            for (Message msg : messages) {
                Message fullMsg = gmail.users().messages().get("me", msg.getId()).execute();
                String sender = fullMsg.getPayload().getHeaders().stream()
                        .filter(h -> h.getName().equals("From"))
                        .findFirst().map(h -> h.getValue()).orElse("Unknown");
                String subject = fullMsg.getPayload().getHeaders().stream()
                        .filter(h -> h.getName().equals("Subject"))
                        .findFirst().map(h -> h.getValue()).orElse("No Subject");
                String body = fullMsg.getSnippet();

                sendToApex(msg.getId(), sender, subject, body);
            }
        }
    }

    private void sendToApex(String emailId, String sender, String subject, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = String.format("{\"email_id\":\"%s\",\"sender\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\"}",
                emailId, sender, subject, body);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        restTemplate.postForEntity(apexEndpoint, entity, String.class);
    }
}