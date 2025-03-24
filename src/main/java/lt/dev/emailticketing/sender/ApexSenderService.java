package lt.dev.emailticketing.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.dev.emailticketing.dto.EmailRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class ApexSenderService {

    private static final Logger logger = LoggerFactory.getLogger(ApexSenderService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${apex.api.key}")
    private String apexApiKey;

    @Value("${apex.tickets.endpoint}")
    private String apexTicketsEndpoint;

    public boolean sendToApex(EmailRequestDto dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apexApiKey);

        try {
            String json = objectMapper.writeValueAsString(dto);
            logger.debug("Sending JSON to APEX: {}", json);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apexTicketsEndpoint, entity, String.class);
            logger.info("APEX Response Status: {}", response.getStatusCode());
            logger.debug("APEX Response Body: {}", response.getBody() != null ? response.getBody() : "Empty");
            return true;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP Error from APEX: {} - {}", e.getStatusCode(), e.getStatusText());
            logger.error("Response Body: {}", e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("Failed to send to APEX: {} - {}", e.getClass().getName(), e.getMessage(), e);
        }
        return false;
    }
}
