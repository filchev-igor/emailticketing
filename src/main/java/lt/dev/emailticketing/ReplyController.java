package lt.dev.emailticketing;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.internet.MimeMessage;

@RestController
public class ReplyController {
    private final JavaMailSender mailSender;

    public ReplyController(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostMapping("/sendReply")
    public String sendReply(@RequestBody ReplyRequest request) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(request.getRecipient());
        helper.setSubject("Re: " + request.getSubject());
        helper.setText(request.getReplyText());
        mailSender.send(message);
        return "Reply sent successfully";
    }
}

class ReplyRequest {
    private String recipient;
    private String subject;
    private String replyText;

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }
}