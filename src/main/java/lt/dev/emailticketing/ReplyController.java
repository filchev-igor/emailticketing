package lt.dev.emailticketing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.mail.internet.MimeMessage;

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

@Setter
@Getter
class ReplyRequest {
    private String recipient;
    private String subject;
    private String replyText;

}