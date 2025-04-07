package lt.dev.emailticketing.controller;

import lt.dev.emailticketing.dto.SendReplyDto;
import lt.dev.emailticketing.service.GmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.beans.factory.annotation.Value;

@WebMvcTest(ReplyController.class)
class ReplyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GmailService gmailService;

    @Value("${apex.api.key}")
    private String apiKey;

    @Test
    void testValidApiKey_shouldSendEmail() throws Exception {
        String requestBody = """
        {
            "to": "user@example.com",
            "from": "admin@example.com",
            "subject": "Reply",
            "body": "Resolved.",
            "inReplyTo": "abc123"
        }
        """;

        mockMvc.perform(post("/send-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-api-key", apiKey)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("✅ Email sent"));

        verify(gmailService, times(1)).sendReplyEmail(any(SendReplyDto.class));
    }

    @Test
    void testInvalidApiKey_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/send-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-api-key", "invalid-key")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid API key"));

        verify(gmailService, never()).sendReplyEmail(any());
    }

    @Test
    void testExceptionDuringSend_shouldReturnServerError() throws Exception {
        doThrow(new RuntimeException("Boom")).when(gmailService).sendReplyEmail(any());

        String requestBody = """
        {
            "to": "user@example.com",
            "from": "admin@example.com",
            "subject": "Reply",
            "body": "Oops",
            "inReplyTo": "abc123"
        }
        """;

        mockMvc.perform(post("/send-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-api-key", apiKey)
                        .content(requestBody))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to send email"));
    }
}
