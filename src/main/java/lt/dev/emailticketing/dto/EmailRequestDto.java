package lt.dev.emailticketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EmailRequestDto {

    @JsonProperty("email_id")
    private String emailId;

    @JsonProperty("sender_name")
    private String senderName;

    @JsonProperty("sender_email")
    private String senderEmail;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("body")
    private String body;

    public EmailRequestDto(String emailId, String senderName, String senderEmail, String subject, String body) {
        this.emailId = emailId;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.subject = subject;
        this.body = body;
    }
}
