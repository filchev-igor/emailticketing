package lt.dev.emailticketing;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;

@Service
public class GmailService {
    private Gmail gmail;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Value("${apex.rest.endpoint}")
    private String apexEndpoint;

    @PostConstruct
    public void init() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream("src/main/resources/credentials.json"))
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/gmail.modify"));
        gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
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