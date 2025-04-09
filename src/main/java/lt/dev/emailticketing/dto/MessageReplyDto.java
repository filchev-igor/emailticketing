package lt.dev.emailticketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MessageReplyDto {
    @JsonProperty("email_id")
    private String emailId;

    @JsonProperty("sender_email")
    private String senderEmail;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("body")
    private String body;

    @JsonProperty("gmail_date")
    private String gmailDate;

    @JsonProperty("email_message_id")
    private String emailMessageId;

    @JsonProperty("email_thread_id")
    private String emailThreadId;

    @JsonProperty("ticket_id")
    private String ticketId; // Added to associate with existing ticket
}