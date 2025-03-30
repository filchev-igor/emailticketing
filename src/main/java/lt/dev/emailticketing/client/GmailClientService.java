package lt.dev.emailticketing.client;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.client.auth.oauth2.Credential;
import lombok.Getter;
import lt.dev.emailticketing.auth.GmailAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GmailClientService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final GmailAuthService gmailAuthService;
    @Getter
    private Gmail gmail;

    @Value("${gmail.user.id}")
    private String gmailUserId;

    @Value("${gmail.query}")
    private String gmailQuery;

    @Value("${gmail.max-results}")
    private long maxResults;

    public GmailClientService(GmailAuthService gmailAuthService) {
        this.gmailAuthService = gmailAuthService;
    }

    public void initClient() throws Exception {
        Credential credential = gmailAuthService.authorize();
        gmail = new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName("EmailTicketing")
                .build();
    }

    public List<Message> fetchInboxMessages() throws Exception {
        return gmail.users().messages()
                .list(gmailUserId)
                .setQ(gmailQuery)
                .setMaxResults(maxResults)
                .execute()
                .getMessages();
    }

    public Message fetchFullMessage(String messageId) throws Exception {
        return gmail.users().messages()
                .get(gmailUserId, messageId)
                .execute();
    }

    public void reauthorize() throws Exception {
        gmailAuthService.clearStoredToken();
        initClient();
    }
}