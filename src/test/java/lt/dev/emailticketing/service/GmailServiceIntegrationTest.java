package lt.dev.emailticketing.service;

import lt.dev.emailticketing.EmailticketingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = EmailticketingApplication.class)
class GmailServiceIntegrationTest {

    @Autowired
    private GmailService gmailService;

    @Test
    void contextLoads() {
        assertNotNull(gmailService);
    }
}
