package lt.dev.emailticketing.dto;

import lombok.Data;
import java.util.List;

@Data
public class TicketByEmailResponseDto {
    private List<TicketByEmailDto> items;
}
