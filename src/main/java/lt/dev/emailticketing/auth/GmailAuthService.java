package lt.dev.emailticketing.auth;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStreamReader;
import java.util.Collections;

@Service
public class GmailAuthService {
    private static final Logger logger = LoggerFactory.getLogger(GmailAuthService.class);
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Value("${gmail.token.path}")
    private String tokenPath;

    @Value("${oauth2.local.server.port:8888}")
    private int oauth2LocalServerPort;

    @Value("${gmail.oauth.user}")
    private String oauthUser;

    public Credential authorize() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY,
                new InputStreamReader(new ClassPathResource("credentials.json").getInputStream())
        );

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, Collections.singleton("https://www.googleapis.com/auth/gmail.modify")
        )
                .setDataStoreFactory(new FileDataStoreFactory(new File(tokenPath)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(oauth2LocalServerPort)
                .build();

        logger.info("Starting Gmail OAuth2 flow...");
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(oauthUser);
        logger.info("OAuth2 authorization successful.");
        return credential;
    }

    public boolean clearStoredToken() {
        try {
            File tokenFile = new File(tokenPath + "/StoredCredential");
            if (tokenFile.exists() && tokenFile.delete()) {
                logger.info("Deleted expired stored token.");
                return true;
            } else {
                logger.warn("Stored token not found or could not be deleted.");
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to delete stored token: {}", e.getMessage(), e);
            return false;
        }
    }
}
