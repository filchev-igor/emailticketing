package lt.dev.emailticketing.controller;

import lt.dev.emailticketing.dto.SendReplyDto;
import lt.dev.emailticketing.service.GmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/send-email")
public class ReplyController {

    private static final Logger logger = LoggerFactory.getLogger(ReplyController.class);
    private final GmailService gmailService;

    @Value("${apex.api.key}")
    private String apexApiKey;

    public ReplyController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    @PostMapping
    public ResponseEntity<String> sendReply(
            @RequestHeader(value = "x-api-key", required = true) String apiKey,
            @RequestBody SendReplyDto dto
    ) {
        logger.info("📥 Incoming reply from APEX to: {}", dto.getTo());

        if (!apexApiKey.equals(apiKey)) {
            logger.warn("❌ Unauthorized request with invalid API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API key");
        }

        try {
            gmailService.sendReplyEmail(dto);
            return ResponseEntity.ok("✅ Email sent");
        } catch (Exception e) {
            logger.error("❌ Error sending reply: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send email");
        }
    }
}