package lt.dev.emailticketing.dto;

import lombok.Data;

@Data
public class TicketByEmailDto {
    private String emailId;

    private Long ticketId;
}
