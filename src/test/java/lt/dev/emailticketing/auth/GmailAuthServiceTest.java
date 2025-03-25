package lt.dev.emailticketing.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class GmailAuthServiceTest {

    private GmailAuthService authService;

    @BeforeEach
    void setUp() {
        authService = new GmailAuthService();

        // Inject values using reflection (because @Value doesn't work in unit test)
        try {
            Field tokenPath = GmailAuthService.class.getDeclaredField("tokenPath");
            tokenPath.setAccessible(true);
            tokenPath.set(authService, "build/test-tokens");

            Field oauthUser = GmailAuthService.class.getDeclaredField("oauthUser");
            oauthUser.setAccessible(true);
            oauthUser.set(authService, "test-user");

            Field port = GmailAuthService.class.getDeclaredField("oauth2LocalServerPort");
            port.setAccessible(true);
            port.set(authService, 9999);
        } catch (Exception e) {
            fail("Failed to inject test fields: " + e.getMessage());
        }
    }

    @Test
    void clearStoredToken_shouldReturnFalseIfFileDoesNotExist() {
        File tokenFile = new File("build/test-tokens/StoredCredential");
        if (tokenFile.exists()) {
            boolean deleted = tokenFile.delete();
            assertTrue(deleted, "Test setup failed: could not delete existing file");
        }

        boolean result = authService.clearStoredToken();
        assertFalse(result);
    }

    @Test
    void clearStoredToken_shouldReturnTrueIfFileExists() throws Exception {
        File dir = new File("build/test-tokens");
        boolean dirsOk = dir.mkdirs() || dir.exists();
        assertTrue(dirsOk, "Failed to create test-tokens directory");

        File tokenFile = new File(dir, "StoredCredential");
        boolean created = tokenFile.createNewFile();
        assertTrue(created || tokenFile.exists(), "Failed to create StoredCredential file");

        boolean result = authService.clearStoredToken();
        assertTrue(result);
        assertFalse(tokenFile.exists());
    }
}
