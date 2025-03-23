package lt.dev.emailticketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ProcessedEmailIdDto {

    @JsonProperty("email_id")
    private String emailId;
}
