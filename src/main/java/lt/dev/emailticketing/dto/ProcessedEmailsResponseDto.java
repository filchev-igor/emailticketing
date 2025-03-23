package lt.dev.emailticketing.dto;

import lombok.Data;
import java.util.List;

@Data
public class ProcessedEmailsResponseDto {
    private List<ProcessedEmailIdDto> items;
}
