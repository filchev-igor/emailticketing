package lt.dev.emailticketing.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lt.dev.emailticketing.dto.EmailRequestDto;
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

    // ✅ Add this constructor
    public ApexSenderService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean sendToApex(EmailRequestDto emailRequestDto) {
        try {
            String json = objectMapper.writeValueAsString(emailRequestDto);
            logger.debug("Sending email DTO to APEX: {}", json);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apexApiKey);

            HttpEntity<String> request = new HttpEntity<>(json, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apexTicketsEndpoint, request, String.class);

            logger.debug("APEX Response Status: {}", response.getStatusCode());
            logger.debug("APEX Response Body: {}", response.getBody() != null ? response.getBody() : "Empty");

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.error("Failed to send email to APEX", e);
            return false;
        }
    }
}