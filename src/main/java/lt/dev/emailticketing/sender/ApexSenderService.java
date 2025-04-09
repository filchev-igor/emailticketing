package lt.dev.emailticketing.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lt.dev.emailticketing.dto.EmailRequestDto;
import lt.dev.emailticketing.dto.MessageReplyDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ApexSenderService {

    private static final Logger logger = LoggerFactory.getLogger(ApexSenderService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ✅ Optional setters (for test)
    @Setter
    @Value("${apex.tickets.endpoint}")
    private String apexTicketsEndpoint;

    @Setter
    @Value("${apex.api.key}")
    private String apexApiKey;

    @Setter
    @Value("${apex.messages.endpoint}")
    private String apexMessagesEndpoint;

    // ✅ Add this constructor
    public ApexSenderService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean sendToApex(Object dto) {
        try {
            String endpoint;

            if (dto instanceof EmailRequestDto) {
                endpoint = apexTicketsEndpoint;
            } else if (dto instanceof MessageReplyDto) {
                endpoint = apexMessagesEndpoint;
            } else {
                throw new IllegalArgumentException("Unsupported DTO type");
            }

            String json = objectMapper.writeValueAsString(dto);
            logger.debug("Sending DTO to APEX: {}", json);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apexApiKey);

            HttpEntity<String> request = new HttpEntity<>(json, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);

            logger.debug("APEX Response Status: {}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.error("Failed to send to APEX", e);
            return false;
        }
    }
}