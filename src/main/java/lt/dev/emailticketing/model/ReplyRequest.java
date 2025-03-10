package lt.dev.emailticketing.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ReplyRequest {

    @Email(message = "Recipient must be a valid email address")
    @NotBlank(message = "Recipient cannot be empty")
    private String recipient;

    @NotBlank(message = "Subject cannot be empty")
    private String subject;

    @NotBlank(message = "Reply text cannot be empty")
    private String replyText;
}