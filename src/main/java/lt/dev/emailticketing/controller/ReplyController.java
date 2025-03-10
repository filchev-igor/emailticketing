package lt.dev.emailticketing.controller;

import jakarta.validation.Valid;
import lt.dev.emailticketing.model.ReplyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

@RestController
public class ReplyController {

    private static final Logger logger = LoggerFactory.getLogger(ReplyController.class);

    private final JavaMailSender mailSender;

    public ReplyController(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostMapping("/sendReply")
    public ResponseEntity<Map<String, String>> sendReply(@Valid @RequestBody ReplyRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(request.getRecipient());
            helper.setSubject("Re: " + request.getSubject());
            helper.setText(request.getReplyText());
            mailSender.send(message);
            logger.info("Reply email sent to {}", request.getRecipient());

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Reply sent successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to send reply email to {}", request.getRecipient(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send reply: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}