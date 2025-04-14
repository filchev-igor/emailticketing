package lt.dev.emailticketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TicketByThreadDto {
    @JsonProperty("email_thread_id")
    private String emailThreadId;

    @JsonProperty("ticket_id")
    private Long ticketId;

    @JsonProperty("email_id")
    private String emailId;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("status")
    private String status;
}