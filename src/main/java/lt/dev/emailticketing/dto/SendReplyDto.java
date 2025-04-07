package lt.dev.emailticketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SendReplyDto {

    @JsonProperty("to")
    private String to;

    @JsonProperty("from")
    private String from;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("body")
    private String body;

    @JsonProperty("emailId")
    private String emailId;

    @JsonProperty("emailThreadId")
    private String threadId;

    @JsonProperty("emailMessageId")
    private String messageId;
}